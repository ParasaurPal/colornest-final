// Two-way Google Drive synchronization for the ColorNest PWA.
// The browser's IndexedDB library remains the offline working copy.
//
// Layout in Drive: a top-level "ColorNest" folder holds one folder per
// category, mirroring the nested category tree
// (ColorNest/Animals/Mammals/Dogs). Pages without a category live in
// ColorNest/Untagged.
//
// Deletion rule: sync NEVER permanently deletes anything from Drive.
// Deleting a page in the app moves its Drive file to the trash, and a page
// whose Drive file was trashed is removed from the app. Trashed Drive files
// stay recoverable from the Drive trash for 30 days.
import { db } from './db';
import {
  getOrCreateFolder,
  findFolder,
  listTree,
  listChildren,
  uploadFile,
  downloadFile,
  moveFile,
  renameFile,
  trashFile,
  checkRemoteState,
  isDriveConnected,
  findFileByName,
  updateFileContent,
} from './driveService';
import { DRIVE_ROOT_FOLDER_NAME, DRIVE_UNTAGGED_FOLDER_NAME } from './driveConfig';
import { convertPdfToImages } from './pdfConverter';

const KEY_SCAN_ON_OPEN = 'driveScanOnOpen';
const KEY_LAST_SYNC = 'driveLastSync';
const KEY_TOMBSTONES = 'driveTombstones';
const KEY_PROCESSED_PDF_IDS = 'driveProcessedPdfIds';
const PDF_INBOX_FOLDER_NAME = 'PDF Inbox';
const PROCESSED_PDFS_FOLDER_NAME = 'Processed PDFs';
const METADATA_FOLDER_NAME = 'Metadata';
const MAX_TOMBSTONES = 500;

let syncInFlight = null;
const scanProgressListeners = new Set();
export function subscribeScanProgress(listener) { scanProgressListeners.add(listener); return () => scanProgressListeners.delete(listener); }
function reportScanProgress(progress) { const snapshot = { active: true, percent: 0, message: 'Scanning Google Drive…', ...progress }; for (const listener of scanProgressListeners) { try { listener(snapshot); } catch (error) { console.warn('Scan progress listener failed:', error); } } }
function finishScanProgress(result = {}) { const snapshot = { active: false, percent: 100, message: 'Scan complete', ...result }; for (const listener of scanProgressListeners) { try { listener(snapshot); } catch (error) { console.warn('Scan progress listener failed:', error); } } }

// ---------- Settings helpers ----------

async function getSetting(key) {
  const row = await db.settings.get(key);
  return row ? row.value : undefined;
}

async function setSetting(key, value) {
  await db.settings.put({ key, value });
}

export async function isScanOnOpenEnabled() {
  const value = await getSetting(KEY_SCAN_ON_OPEN);
  return value === undefined ? true : value === true;
}

export async function setScanOnOpenEnabled(enabled) {
  await setSetting(KEY_SCAN_ON_OPEN, enabled);
}

export async function getLastSyncMillis() {
  const value = await getSetting(KEY_LAST_SYNC);
  return value === undefined ? null : value;
}

async function readTombstones() {
  const raw = (await getSetting(KEY_TOMBSTONES)) || '';
  if (!raw) return new Set();
  return new Set(raw.split(',').map((s) => s.trim()).filter(Boolean));
}

async function addTombstone(driveFileId) {
  const tombstones = await readTombstones();
  tombstones.add(driveFileId);
  const kept = [...tombstones].slice(-MAX_TOMBSTONES);
  await setSetting(KEY_TOMBSTONES, kept.join(','));
}

// ---------- Naming ----------

function sanitizeTitle(title) {
  // Strip control characters (Drive names must not contain them).
  const cleaned = [...(title || '')]
    .filter((ch) => ch.charCodeAt(0) >= 32)
    .join('')
    .trim()
    .slice(0, 100);
  return cleaned || 'Untitled page';
}

function extensionForImageUrl(imageUrl) {
  if (imageUrl.startsWith('data:')) {
    const match = imageUrl.match(/^data:image\/([a-z0-9+]+);/i);
    if (match) return match[1].replace('jpeg', 'jpg');
    return 'jpg';
  }
  const clean = imageUrl.split('?')[0];
  const ext = clean.substring(clean.lastIndexOf('.') + 1).toLowerCase();
  return ext && ext.length <= 5 ? ext : 'jpg';
}

function driveFileName(page) {
  return `${sanitizeTitle(page.title)}.${extensionForImageUrl(page.imageUrl)}`;
}

function mimeTypeForExtension(ext) {
  switch (ext.toLowerCase()) {
    case 'png': return 'image/png';
    case 'webp': return 'image/webp';
    case 'gif': return 'image/gif';
    default: return 'image/jpeg';
  }
}

// ---------- Local image bytes ----------

async function imageUrlToBlob(imageUrl) {
  const res = await fetch(imageUrl);
  if (!res.ok) throw new Error(`Could not read local image (${res.status})`);
  return res.blob();
}

async function saveDownloadedImage(blob) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onloadend = () => resolve(reader.result);
    reader.onerror = () => reject(new Error('Failed to read downloaded image'));
    reader.readAsDataURL(blob);
  });
}

// ---------- Folder helpers ----------

async function ensureCategoryFolder(rootId, allCategories, category) {
  const chain = [];
  let current = category;
  let guard = 0;
  while (current && guard++ < 50) {
    chain.unshift(current);
    current = allCategories.find((c) => c.id === current.parentId);
  }
  let parentId = rootId;
  for (const node of chain) {
    const folderId = await getOrCreateFolder(node.name, parentId);
    if (node.driveFolderId !== folderId) {
      await db.categories.update(node.id, { driveFolderId: folderId });
      node.driveFolderId = folderId;
    }
    parentId = folderId;
  }
  return parentId;
}


// ---------- Cross-device page metadata ----------

const PAGE_META_PREFIX = '.colornest-page-';

function pageMetadataName(syncId) {
  return `${PAGE_META_PREFIX}${syncId}.json`;
}

function categoryPathFor(categoryId, categories) {
  const path = [];
  let current = categories.find((item) => item.id === categoryId);
  let guard = 0;
  while (current && guard++ < 50) {
    path.unshift(current.name);
    current = categories.find((item) => item.id === current.parentId);
  }
  return path;
}

async function ensureLocalCategoryPath(path) {
  let parentId = null;
  for (const name of path || []) {
    let category = await db.categories.where('name').equals(name).filter((item) => item.parentId === parentId).first();
    if (!category) {
      const id = await db.categories.add({ name, parentId });
      category = { id, name, parentId };
    }
    parentId = category.id;
  }
  return parentId;
}

function buildPageMetadata(page, categories) {
  return {
    schemaVersion: 1,
    syncId: page.syncId,
    driveFileId: page.driveFileId || null,
    title: page.title || 'Untitled page',
    categoryPath: categoryPathFor(page.categoryId, categories),
    tags: Array.isArray(page.tags) ? page.tags : [],
    complexity: page.complexity || 'Beginner',
    time: page.time || 'TBD',
    tool: page.tool || 'TBD',
    isFavorite: page.isFavorite ? 1 : 0,
    isCompleted: page.isCompleted ? 1 : 0,
    progress: Number(page.progress || 0),
    isUncategorized: page.isUncategorized ? 1 : 0,
    updatedAt: Number(page.updatedAt || Date.now()),
  };
}

async function syncPageMetadata(rootId, metadataFolderId, remoteFiles) {
  const categories = await db.categories.toArray();
  const pages = await db.pages.toArray();
  const remoteByDriveId = new Map();

  const tombstones = await readTombstones();
  for (const file of remoteFiles) {
    if (!file.name?.startsWith(PAGE_META_PREFIX) || !file.name.endsWith('.json')) continue;
    try {
      const raw = await downloadFile(file.id);
      const metadata = JSON.parse(await raw.text());
      if (metadata.driveFileId && tombstones.has(metadata.driveFileId)) {
        await trashFile(file.id).catch(() => {});
        continue;
      }
      if (metadata.syncId && metadata.driveFileId) remoteByDriveId.set(metadata.driveFileId, { file, metadata });
    } catch (error) {
      console.warn('Skipping unreadable ColorNest metadata file:', error.message);
    }
  }

  let metadataUploaded = 0;
  let metadataDownloaded = 0;

  for (const originalPage of pages) {
    if (!originalPage.driveFileId) continue;
    let page = originalPage;
    const importedFromDrive = !page.syncId;
    let syncId = page.syncId;
    let remoteEntry = remoteByDriveId.get(page.driveFileId);

    // A page added directly from Drive adopts the stable identity and
    // complete metadata associated with its image.
    if (!syncId && remoteEntry) {
      syncId = remoteEntry.metadata.syncId;
      page = { ...page, syncId };
      await db.pages.update(page.id, { syncId });
    }
    if (!syncId) {
      syncId = globalThis.crypto?.randomUUID?.() || `page-${page.id}-${Date.now()}`;
      page = { ...page, syncId, updatedAt: page.updatedAt || Date.now() };
      await db.pages.update(page.id, { syncId, updatedAt: page.updatedAt });
    }

    const remoteMeta = remoteEntry?.metadata;
    const localUpdatedAt = Number(page.updatedAt || 0);
    if (remoteMeta && (importedFromDrive || Number(remoteMeta.updatedAt || 0) > localUpdatedAt)) {
      const categoryId = await ensureLocalCategoryPath(remoteMeta.categoryPath || []);
      const changes = {
        title: remoteMeta.title || page.title,
        categoryId,
        tags: Array.isArray(remoteMeta.tags) ? remoteMeta.tags : [],
        complexity: remoteMeta.complexity || page.complexity,
        time: remoteMeta.time || page.time,
        tool: remoteMeta.tool || page.tool,
        isFavorite: remoteMeta.isFavorite ? 1 : 0,
        isCompleted: remoteMeta.isCompleted ? 1 : 0,
        progress: Number(remoteMeta.progress || 0),
        isUncategorized: remoteMeta.isUncategorized ? 1 : 0,
        syncId,
        updatedAt: Number(remoteMeta.updatedAt || Date.now()),
      };
      await db.pages.update(page.id, changes);
      metadataDownloaded++;
    } else {
      const metadata = buildPageMetadata(page, categories);
      const name = pageMetadataName(syncId);
      const blob = new Blob([JSON.stringify(metadata, null, 2)], { type: 'application/json' });
      const existing = remoteEntry?.file || await findFileByName(name, metadataFolderId) || await findFileByName(name, rootId);
      if (existing) {
        const existingParents = existing.parentIds || existing.parents || [];
        if (!existingParents.includes(metadataFolderId)) {
          await moveFile(existing.id, existingParents, metadataFolderId);
        }
        await updateFileContent(existing.id, blob, 'application/json');
      } else {
        await uploadFile(blob, name, metadataFolderId, 'application/json');
      }
      metadataUploaded++;
    }
  }

  return { metadataUploaded, metadataDownloaded };
}

// ---------- Full sync ----------

export function formatSyncSummary(result) {
  if (result.notConnected) return 'Not connected to Google Drive';
  const parts = [];
  if (result.pdfsConverted > 0) parts.push(`${result.pdfsConverted} PDFs converted (${result.pdfPagesCreated} pages)`);
  if (result.pdfsArchived > 0) parts.push(`${result.pdfsArchived} original PDFs archived`);
  if (result.uploaded > 0) parts.push(`${result.uploaded} uploaded`);
  if (result.downloaded > 0) parts.push(`${result.downloaded} downloaded`);
  if (result.moved > 0) parts.push(`${result.moved} moved`);
  if (result.renamed > 0) parts.push(`${result.renamed} renamed`);
  if (result.removedLocal > 0) parts.push(`${result.removedLocal} removed`);
  if (result.metadataUploaded > 0) parts.push(`${result.metadataUploaded} metadata records uploaded`);
  if (result.metadataDownloaded > 0) parts.push(`${result.metadataDownloaded} metadata records updated`);
  return parts.length === 0
    ? 'Sync complete — everything is up to date'
    : 'Sync complete — ' + parts.join(', ');
}

/**
 * Runs a full two-way sync. Returns a result object; throws on network/API
 * errors so the caller can show the failure.
 */
export async function syncDrive() {
  if (!isDriveConnected()) {
    return { notConnected: true, uploaded: 0, downloaded: 0, moved: 0, renamed: 0, removedLocal: 0 };
  }

  let uploaded = 0;
  let downloaded = 0;
  let moved = 0;
  let renamed = 0;
  let removedLocal = 0;
  let metadataUploaded = 0;
  let metadataDownloaded = 0;

  reportScanProgress({ phase: 'connect', percent: 2, message: 'Connecting to Google Drive…' });
  const rootId = await getOrCreateFolder(DRIVE_ROOT_FOLDER_NAME, 'root');
  const metadataFolderId = await getOrCreateFolder(METADATA_FOLDER_NAME, rootId);
  const untaggedId = await getOrCreateFolder(DRIVE_UNTAGGED_FOLDER_NAME, rootId);
  const pdfInboxId = await getOrCreateFolder(PDF_INBOX_FOLDER_NAME, rootId);
  const processedPdfsId = await getOrCreateFolder(PROCESSED_PDFS_FOLDER_NAME, rootId);
  let pdfsConverted = 0;
  let pdfPagesCreated = 0;
  let pdfsArchived = 0;

  // Convert PDFs placed in ColorNest/PDF Inbox. Record each completed
  // conversion before moving the source so a failed move never duplicates pages.
  const processedPdfIds = new Set((await getSetting(KEY_PROCESSED_PDF_IDS) || '').split(',').filter(Boolean));
  reportScanProgress({ phase: 'pdfs', percent: 8, message: 'Checking PDF Inbox…' });
  const inboxFiles = await listChildren(pdfInboxId);
  const pdfFiles = inboxFiles.filter(file => file.mimeType === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf'));
  let pdfIndex = 0;
  for (const file of inboxFiles) {
    if (file.mimeType !== 'application/pdf' && !file.name.toLowerCase().endsWith('.pdf')) continue;
    pdfIndex++;
    reportScanProgress({ phase: 'pdfs', percent: 10 + Math.round((pdfIndex / Math.max(pdfFiles.length, 1)) * 20), message: `Processing PDF ${pdfIndex} of ${pdfFiles.length}: ${file.name}`, current: pdfIndex, total: pdfFiles.length });
    try {
      if (!processedPdfIds.has(file.id)) {
        const pdfBlob = await downloadFile(file.id);
        const pageImages = await convertPdfToImages(await pdfBlob.arrayBuffer());
        if (!pageImages.length) throw new Error('The PDF did not contain any pages.');
        const baseTitle = file.name.replace(/\.pdf$/i, '') || 'Imported PDF';
        const now = Date.now();
        const newPages = pageImages.map((imageUrl, index) => ({
          updatedAt: now,
          title: `${baseTitle} - Page ${index + 1}`,
          categoryId: null,
          complexity: 'Beginner',
          time: 'TBD',
          tool: 'TBD',
          imageUrl,
          isFavorite: 0,
          isCompleted: 0,
          progress: 0,
          isUncategorized: 1,
          driveFileId: null,
          tags: [],
        }));
        // Save all pages and the processed marker atomically to prevent
        // duplicate imports if the browser closes during conversion.
        await db.transaction('rw', db.pages, db.settings, async () => {
          await db.pages.bulkAdd(newPages);
          await setSetting(KEY_PROCESSED_PDF_IDS, [...processedPdfIds, file.id].join(','));
        });
        processedPdfIds.add(file.id);
        pdfsConverted++;
        pdfPagesCreated += pageImages.length;
      }
      await moveFile(file.id, file.parentIds, processedPdfsId);
      pdfsArchived++;
    } catch (error) {
      console.warn(`Could not process PDF ${file.name}:`, error);
    }
  }

  reportScanProgress({ phase: 'categories', percent: 32, message: 'Checking collection folders…' });
  // Ensure every category has a folder, mirroring the nested tree.
  const categories = await db.categories.toArray();
  const folderIdByCategory = {};
  for (let index = 0; index < categories.length; index++) {
    const category = categories[index];
    folderIdByCategory[category.id] = await ensureCategoryFolder(rootId, categories, category);
    reportScanProgress({ phase: 'categories', percent: 32 + Math.round(((index + 1) / Math.max(categories.length, 1)) * 13), message: `Checking folders ${index + 1} of ${categories.length}` });
  }

  reportScanProgress({ phase: 'drive', percent: 47, message: 'Reading files in Google Drive…' });
  // One recursive listing of the whole ColorNest tree.
  const remoteFiles = await listTree(rootId);
  const remoteById = new Map(remoteFiles.map((f) => [f.id, f]));
  const categoryIdByFolder = new Map(
    categories.filter((c) => c.driveFolderId).map((c) => [c.driveFolderId, c.id])
  );
  const tombstones = await readTombstones();
  const pages = await db.pages.toArray();
  const localDriveIds = new Set(pages.map((p) => p.driveFileId).filter(Boolean));

  // Upload new pages; move/rename changed ones; drop pages whose Drive file
  // was trashed or deleted.
  for (let index = 0; index < pages.length; index++) {
    const page = pages[index];
    reportScanProgress({ phase: 'pages', percent: 50 + Math.round(((index + 1) / Math.max(pages.length, 1)) * 25), message: `Syncing library pages ${index + 1} of ${pages.length}` });
    const driveId = page.driveFileId;
    if (!driveId) {
      try {
        const blob = await imageUrlToBlob(page.imageUrl);
        const folderId = folderIdByCategory[page.categoryId] || untaggedId;
        const newId = await uploadFile(
          blob,
          driveFileName(page),
          folderId,
          mimeTypeForExtension(extensionForImageUrl(page.imageUrl))
        );
        await db.pages.update(page.id, { driveFileId: newId });
        uploaded++;
      } catch (e) {
        // Skip pages whose local image is unreadable; the next sync retries.
        console.log(`Skipping upload for page ${page.id}:`, e.message);
      }
      continue;
    }

    const remote = remoteById.get(driveId);
    if (!remote) {
      // Not in the tree: trashed, deleted, or moved out by the user.
      const state = await checkRemoteState(driveId);
      if (state === 'trashed') {
        await db.pages.delete(page.id);
        if (page.syncId) {
          const metadataFile = await findFileByName(pageMetadataName(page.syncId), metadataFolderId) || await findFileByName(pageMetadataName(page.syncId), rootId);
          if (metadataFile) await trashFile(metadataFile.id).catch(() => {});
        }
        removedLocal++;
      } else if (state === 'gone') {
        // A 404 can also mean this Drive account cannot access a file ID
        // from an imported backup. Keep the local page and upload it again.
        await db.pages.update(page.id, { driveFileId: null, updatedAt: Date.now() });
      }
      // 'exists-elsewhere': the user moved it out of the ColorNest tree;
      // leave the local page alone.
      continue;
    }

    const expectedFolder = folderIdByCategory[page.categoryId] || untaggedId;
    if (!remote.parentIds.includes(expectedFolder)) {
      await moveFile(driveId, remote.parentIds, expectedFolder);
      moved++;
    }
    const expectedName = driveFileName(page);
    if (remote.name !== expectedName) {
      await renameFile(driveId, expectedName);
      renamed++;
    }
  }

  // Download images the user added to Drive directly.
  let remoteIndex = 0;
  const remoteImages = remoteFiles.filter(remote => remote.mimeType?.startsWith('image/'));
  for (const remote of remoteFiles) {
    if (!remote.mimeType.startsWith('image/')) continue;
    remoteIndex++;
    reportScanProgress({ phase: 'downloads', percent: 76 + Math.round((remoteIndex / Math.max(remoteImages.length, 1)) * 17), message: `Checking Drive images ${remoteIndex} of ${remoteImages.length}` });
    if (localDriveIds.has(remote.id) || tombstones.has(remote.id)) continue;
    const folderId = remote.parentIds[0];
    if (!folderId) continue;
    const categoryId = categoryIdByFolder.get(folderId);
    const isUntagged = folderId === untaggedId || categoryId === undefined;
    const extension = remote.name.includes('.')
      ? remote.name.substring(remote.name.lastIndexOf('.') + 1)
      : 'jpg';
    const blob = await downloadFile(remote.id);
    const fileName = `drive_${remote.id}_${Date.now()}.${extension}`;
    const localImageUrl = await saveDownloadedImage(blob, fileName);
    await db.pages.add({
      updatedAt: Date.now(),
      title: remote.name.includes('.')
        ? remote.name.substring(0, remote.name.lastIndexOf('.')) || 'Untitled page'
        : remote.name,
      categoryId: isUntagged ? null : categoryId,
      complexity: 'Beginner',
      time: 'TBD',
      tool: 'TBD',
      imageUrl: localImageUrl,
      isFavorite: 0,
      isCompleted: 0,
      progress: 0,
      isUncategorized: isUntagged ? 1 : 0,
      driveFileId: remote.id,
      tags: [],
    });
    downloaded++;
  }

  reportScanProgress({ phase: 'metadata', percent: 95, message: 'Updating page details…' });
  const metadataResult = await syncPageMetadata(rootId, metadataFolderId, remoteFiles);
  metadataUploaded = metadataResult.metadataUploaded;
  metadataDownloaded = metadataResult.metadataDownloaded;

  await setSetting(KEY_LAST_SYNC, Date.now());
  const result = { notConnected: false, uploaded, downloaded, moved, renamed, removedLocal, metadataUploaded, metadataDownloaded, pdfsConverted, pdfPagesCreated, pdfsArchived };
  finishScanProgress({ message: 'Google Drive scan complete', result });
  return result;
}

/**
 * Fire-and-forget background sync (used on app open and after imports).
 * Never throws; concurrent calls share one in-flight sync.
 */
export function triggerBackgroundSync(force = false) {
  if (syncInFlight) return syncInFlight;
  syncInFlight = (async () => {
    try {
      if (!isDriveConnected()) return null;
      if (!force && !(await isScanOnOpenEnabled())) return null;
      return await syncDrive();
    } catch (e) {
      console.log('Background Drive sync failed:', e.message);
      finishScanProgress({ active: false, error: true, message: `Scan failed: ${e.message}` });
      return null;
    } finally {
      syncInFlight = null;
    }
  })();
  return syncInFlight;
}

// ---------- Single-page propagation ----------

/**
 * Trash the Drive copy of a page being deleted in the app, and record a
 * tombstone so a later scan never re-downloads it. Best-effort: never throws.
 */
export async function trashPageFile(page) {
  const driveId = page.driveFileId;
  if (!driveId) return;
  await addTombstone(driveId);
  if (!isDriveConnected()) return;
  try {
    await trashFile(driveId);
    if (page.syncId) {
      const rootId = await findFolder(DRIVE_ROOT_FOLDER_NAME, 'root');
      if (rootId) {
        const metadataFolderId = await findFolder(METADATA_FOLDER_NAME, rootId);
        const metadataFile = (metadataFolderId ? await findFileByName(pageMetadataName(page.syncId), metadataFolderId) : null) || await findFileByName(pageMetadataName(page.syncId), rootId);
        if (metadataFile) await trashFile(metadataFile.id);
      }
    }
  } catch (e) {
    // Offline or already gone — the tombstone covers the re-download case.
    console.log('Could not trash Drive file:', e.message);
  }
}

/** Deletes a page locally and trashes its Drive copy (see trashPageFile). */
export async function deletePageWithDrive(page) {
  await trashPageFile(page);
  await db.pages.delete(page.id);
}

/**
 * After a local title/category edit, move and/or rename the Drive copy to
 * match. Best-effort: never throws.
 */
export async function propagatePageChange(pageId) {
  try {
    const page = await db.pages.get(pageId);
    if (!page || !page.driveFileId || !isDriveConnected()) return;
    const rootId = await getOrCreateFolder(DRIVE_ROOT_FOLDER_NAME, 'root');
    const untaggedId = await getOrCreateFolder(DRIVE_UNTAGGED_FOLDER_NAME, rootId);
    const categories = await db.categories.toArray();
    let expectedFolder = untaggedId;
    if (page.categoryId) {
      const category = categories.find((c) => c.id === page.categoryId);
      if (category) {
        expectedFolder = await ensureCategoryFolder(rootId, categories, category);
      }
    }
    // Re-read the remote file to compare parents and name.
    const remoteFiles = await listTree(rootId);
    const remote = remoteFiles.find((f) => f.id === page.driveFileId);
    if (!remote) return; // Gone from Drive; the next full sync reconciles.
    if (!remote.parentIds.includes(expectedFolder)) {
      await moveFile(page.driveFileId, remote.parentIds, expectedFolder);
    }
    const expectedName = driveFileName(page);
    if (remote.name !== expectedName) {
      await renameFile(page.driveFileId, expectedName);
    }
  } catch (e) {
    // Offline or the Drive file is gone; the next full sync reconciles.
    console.log('Could not propagate page change to Drive:', e.message);
  }
}

/**
 * Moves the whole ColorNest folder in Drive to the trash (used by the
 * "Also wipe Cloud Folder" option). Trash only — never a permanent delete.
 * Best-effort: never throws.
 */
export async function trashRootFolder() {
  try {
    if (!isDriveConnected()) return;
    const rootId = await findFolder(DRIVE_ROOT_FOLDER_NAME, 'root');
    if (!rootId) return;
    await trashFile(rootId);
  } catch (e) {
    console.log('Could not trash Drive folder:', e.message);
  }
}
