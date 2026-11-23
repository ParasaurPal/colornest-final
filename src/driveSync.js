// Google Drive sync for the ColorNest PWA. Import is automatic and
// preview-only: when the app opens (and whenever the user taps Import
// previews), new Drive files are added to the library as preview-only
// entries holding just a small thumbnail — the full files are never
// downloaded to the device. A preview's larger temporary preview is
// fetched only while viewing it and is never stored; sharing sends the
// original from Drive. The Drive browser lists files the same way, and a
// file is downloaded in full only when the user explicitly downloads it
// there — downloading a preview entry upgrades it to a full page in
// place. The browser's IndexedDB library remains the offline working
// copy; pages already in the library keep their Drive link so edits can
// move/rename the Drive copy and deletes can trash it.
//
// Layout in Drive: a top-level "ColorNest" folder holds one folder per
// category, mirroring the nested category tree
// (ColorNest/Animals/Mammals/Dogs). Pages without a category live in
// ColorNest/Untagged.
//
// Deletion rule: deleting a page in the app moves its Drive file to the
// trash, never a permanent delete. Trashed Drive files stay recoverable
// from the Drive trash for 30 days.
import { db } from './db';
import {
  getOrCreateFolder,
  findFolder,
  listTree,
  listChildren,
  downloadFile,
  fetchThumbnailDataUrl,
  largeThumbnailUrl,
  moveFile,
  renameFile,
  trashFile,
  isDriveConnected,
  findFileByName,
  getFileMetadata,
} from './driveService';
import { DRIVE_ROOT_FOLDER_NAME, DRIVE_UNTAGGED_FOLDER_NAME } from './driveConfig';
import { convertPdfToImages } from './pdfConverter';
import { removeWhiteBackground, WHITE_BACKGROUND_VERSION } from './whiteBackground';
import { tryCreateThumbnail } from './thumbnails';

const KEY_PROCESSED_PDF_IDS = 'driveProcessedPdfIds';
const PDF_INBOX_FOLDER_NAME = 'PDF Inbox';
const PROCESSED_PDFS_FOLDER_NAME = 'Processed PDFs';
const METADATA_FOLDER_NAME = 'Metadata';
const KEY_SCAN_ON_OPEN = 'driveScanOnOpen';
const KEY_LAST_SYNC = 'driveLastSync';
const KEY_TOMBSTONES = 'driveTombstones';
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

// ---------- Preview-only Drive browsing ----------
//
// Nothing is downloaded from Drive automatically. The Drive browser lists
// files (metadata + thumbnails only) and each file is downloaded only when
// the user explicitly downloads it from there.

/**
 * Lists the images and PDFs in the ColorNest Drive folder tree for the
 * Drive browser. Metadata and thumbnails only — no file contents are
 * downloaded. Each entry carries its metadata sidecar (if any), the local
 * category its folder maps to, and whether it is already in the library.
 */
export async function listDriveFiles() {
  if (!isDriveConnected()) {
    return { notConnected: true, files: [] };
  }
  const rootId = await findFolder(DRIVE_ROOT_FOLDER_NAME, 'root');
  if (!rootId) return { notConnected: false, files: [] };
  const metadataFolderId = await findFolder(METADATA_FOLDER_NAME, rootId);

  // Read metadata sidecars only to enrich the listing (title, tags,
  // category path) for files downloaded later.
  const metadataByDriveId = new Map();
  const metadataFiles = [
    ...(metadataFolderId ? await listChildren(metadataFolderId) : []),
    ...(await listChildren(rootId)).filter(file => file.name?.startsWith(PAGE_META_PREFIX)),
  ];
  for (const file of metadataFiles) {
    if (!file.name?.startsWith(PAGE_META_PREFIX) || !file.name.endsWith('.json')) continue;
    try {
      const blob = await downloadFile(file.id);
      const metadata = JSON.parse(await blob.text());
      if (metadata.driveFileId) metadataByDriveId.set(metadata.driveFileId, metadata);
    } catch (error) {
      console.warn('Skipping unreadable page metadata:', error.message);
    }
  }

  const remoteFiles = await listTree(rootId);
  const pages = await db.pages.toArray();
  const localDriveIds = new Set(pages.map(page => page.driveFileId).filter(Boolean));
  const previewOnlyDriveIds = new Set(pages.filter(page => page.driveFileId && page.isPreviewOnly === 1).map(page => page.driveFileId));
  const processedPdfIds = new Set((await getSetting(KEY_PROCESSED_PDF_IDS) || '').split(',').filter(Boolean));
  const categories = await db.categories.toArray();
  const categoryIdByFolder = new Map(categories.filter(category => category.driveFolderId).map(category => [category.driveFolderId, category.id]));

  const files = [];
  for (const remote of remoteFiles) {
    if (remote.name?.startsWith(PAGE_META_PREFIX)) continue;
    const isPdf = remote.mimeType === 'application/pdf' || remote.name.toLowerCase().endsWith('.pdf');
    const isImage = remote.mimeType?.startsWith('image/');
    if (!isPdf && !isImage) continue;
    const metadata = metadataByDriveId.get(remote.id) || null;
    files.push({
      id: remote.id,
      name: remote.name,
      mimeType: remote.mimeType,
      thumbnailLink: remote.thumbnailLink || null,
      parentIds: remote.parentIds || [],
      isPdf,
      metadata,
      categoryId: categoryIdByFolder.get(remote.parentIds?.[0]) ?? null,
      inLibrary: localDriveIds.has(remote.id),
      libraryPreviewOnly: previewOnlyDriveIds.has(remote.id),
      importedPdf: isPdf && processedPdfIds.has(remote.id),
    });
  }
  files.sort((a, b) => a.name.localeCompare(b.name));
  return { notConnected: false, files };
}

/**
 * Downloads one Drive file on demand (from the Drive browser) and adds it
 * to the library. Images are imported as a single page; PDFs are converted
 * page by page. Returns { pagesAdded }.
 */
export async function downloadDriveFile(entry) {
  if (!isDriveConnected()) throw new Error('Not connected to Google Drive');
  if (entry.isPdf) return downloadDrivePdf(entry);

  const alreadyThere = await db.pages.filter(page => page.driveFileId === entry.id).first();
  if (alreadyThere && alreadyThere.isPreviewOnly !== 1) throw new Error('This file is already in your library.');
  const blob = await downloadFile(entry.id);
  const localImageUrl = await removeWhiteBackground(blob);
  const thumbnailUrl = await tryCreateThumbnail(localImageUrl);
  const metadata = entry.metadata || null;
  // Downloading a preview-only entry upgrades it to a full page in place,
  // keeping the title, category, tags and progress the preview already had.
  if (alreadyThere) {
    await db.pages.update(alreadyThere.id, {
      imageUrl: localImageUrl,
      ...(thumbnailUrl ? { thumbnailUrl } : {}),
      isPreviewOnly: 0,
      whiteBackgroundProcessed: true,
      whiteBackgroundVersion: WHITE_BACKGROUND_VERSION,
    });
    return { pagesAdded: 1 };
  }
  let categoryId = entry.categoryId ?? null;
  if (metadata?.categoryPath?.length) {
    categoryId = await ensureLocalCategoryPath(metadata.categoryPath);
  }
  await db.pages.add({
    updatedAt: Number(metadata?.updatedAt || Date.now()),
    title: metadata?.title || (entry.name.includes('.') ? entry.name.substring(0, entry.name.lastIndexOf('.')) || 'Untitled page' : entry.name),
    categoryId,
    complexity: metadata?.complexity || 'Beginner',
    time: metadata?.time || 'TBD',
    tool: metadata?.tool || 'TBD',
    imageUrl: localImageUrl,
    ...(thumbnailUrl ? { thumbnailUrl } : {}),
    isFavorite: metadata?.isFavorite ? 1 : 0,
    isCompleted: metadata?.isCompleted ? 1 : 0,
    progress: Number(metadata?.progress || 0),
    isUncategorized: metadata ? (metadata.isUncategorized ? 1 : 0) : (categoryId === null ? 1 : 0),
    driveFileId: entry.id,
    syncId: metadata?.syncId || undefined,
    tags: Array.isArray(metadata?.tags) ? metadata.tags : [],
    whiteBackgroundProcessed: true,
    whiteBackgroundVersion: WHITE_BACKGROUND_VERSION,
  });
  return { pagesAdded: 1 };
}

async function downloadDrivePdf(entry) {
  const processedPdfIds = new Set((await getSetting(KEY_PROCESSED_PDF_IDS) || '').split(',').filter(Boolean));
  // A PDF imported as a preview entry can still be downloaded in full: the
  // preview entry is replaced by the converted pages below. A PDF already
  // converted into pages (no preview entry left) stays converted.
  const previewEntry = await db.pages.filter(page => page.driveFileId === entry.id && page.isPreviewOnly === 1).first();
  if (processedPdfIds.has(entry.id) && !previewEntry) throw new Error('This PDF was already imported.');

  const rootId = await getOrCreateFolder(DRIVE_ROOT_FOLDER_NAME, 'root');
  const pdfInboxId = await findFolder(PDF_INBOX_FOLDER_NAME, rootId);
  const processedPdfsId = await getOrCreateFolder(PROCESSED_PDFS_FOLDER_NAME, rootId);

  const pdfBlob = await downloadFile(entry.id);
  const pageImages = await convertPdfToImages(await pdfBlob.arrayBuffer());
  if (!pageImages.length) throw new Error('The PDF did not contain any pages.');
  const transparentPageImages = [];
  for (const imageUrl of pageImages) {
    transparentPageImages.push(await removeWhiteBackground(imageUrl));
  }
  const baseTitle = entry.name.replace(/\.pdf$/i, '') || 'Imported PDF';
  const now = Date.now();
  const pageThumbnails = await Promise.all(transparentPageImages.map((imageUrl) => tryCreateThumbnail(imageUrl)));
  const newPages = transparentPageImages.map((imageUrl, pageIndex) => ({
    updatedAt: now,
    title: `${baseTitle} - Page ${pageIndex + 1}`,
    categoryId: entry.categoryId ?? null,
    complexity: 'Beginner',
    time: 'TBD',
    tool: 'TBD',
    imageUrl,
    ...(pageThumbnails[pageIndex] ? { thumbnailUrl: pageThumbnails[pageIndex] } : {}),
    isFavorite: 0,
    isCompleted: 0,
    progress: 0,
    isUncategorized: entry.categoryId ? 0 : 1,
    driveFileId: null,
    tags: [],
    whiteBackgroundProcessed: true,
    whiteBackgroundVersion: WHITE_BACKGROUND_VERSION,
  }));
  await db.transaction('rw', db.pages, db.settings, async () => {
    if (previewEntry) await db.pages.delete(previewEntry.id);
    await db.pages.bulkAdd(newPages);
    await setSetting(KEY_PROCESSED_PDF_IDS, [...processedPdfIds, entry.id].join(','));
  });
  // Only move to Processed PDFs when it came from the PDF Inbox.
  if (pdfInboxId && (entry.parentIds || []).includes(pdfInboxId)) {
    await moveFile(entry.id, entry.parentIds || [], processedPdfsId);
  }
  return { pagesAdded: pageImages.length };
}

/**
 * Downloads a preview-only library page in full, from the viewer. PDFs are
 * converted page by page and replace the preview entry; images upgrade the
 * preview entry to a full page in place. The file type is read from Drive,
 * since preview entries carry no type flag. Returns { pagesAdded, isPdf }.
 */
export async function downloadPreviewPage(page) {
  if (!isDriveConnected()) throw new Error('Not connected to Google Drive');
  if (!page.driveFileId) throw new Error('This page is not linked to a Drive file.');
  const meta = await getFileMetadata(page.driveFileId);
  const isPdf = meta.mimeType === 'application/pdf' || (meta.name || '').toLowerCase().endsWith('.pdf');
  const result = await downloadDriveFile({
    id: page.driveFileId,
    name: meta.name || page.title,
    mimeType: meta.mimeType,
    parentIds: meta.parents || [],
    isPdf,
    metadata: null,
    categoryId: page.categoryId ?? null,
  });
  return { ...result, isPdf };
}

// ---------- Preview-only import ----------
//
// Imports previews of new Drive files into the library. Only small
// preview thumbnails are stored — the full files are never downloaded.

export function formatSyncSummary(result) {
  if (result.notConnected) return 'Not connected to Google Drive';
  const parts = [];
  if (result.previewsImported > 0) parts.push(`${result.previewsImported} preview${result.previewsImported === 1 ? '' : 's'} imported`);
  if (result.pdfsArchived > 0) parts.push(`${result.pdfsArchived} PDF${result.pdfsArchived === 1 ? '' : 's'} archived`);
  return parts.length === 0
    ? 'Sync complete — everything is up to date'
    : 'Sync complete — ' + parts.join(', ');
}

/**
 * Adds one Drive file to the library as a preview-only entry: a small
 * preview thumbnail is stored, but the full file is never downloaded.
 * The full image is only fetched temporarily when the page is opened, or
 * in full when the user shares it.
 */
async function importPreviewEntry(remote, metadata, categoryIdByFolder) {
  let categoryId = categoryIdByFolder.get(remote.parentIds?.[0]) ?? null;
  if (metadata?.categoryPath?.length) {
    categoryId = await ensureLocalCategoryPath(metadata.categoryPath);
  }
  const thumbnailDataUrl = remote.thumbnailLink ? await fetchThumbnailDataUrl(remote.thumbnailLink) : null;
  await db.pages.add({
    updatedAt: Number(metadata?.updatedAt || Date.now()),
    title: metadata?.title || (remote.name.includes('.') ? remote.name.substring(0, remote.name.lastIndexOf('.')) || 'Untitled page' : remote.name),
    categoryId,
    complexity: metadata?.complexity || 'Beginner',
    time: metadata?.time || 'TBD',
    tool: metadata?.tool || 'TBD',
    imageUrl: null,
    ...(thumbnailDataUrl || remote.thumbnailLink ? { thumbnailUrl: thumbnailDataUrl || remote.thumbnailLink } : {}),
    ...(remote.thumbnailLink ? { driveThumbnailLink: remote.thumbnailLink } : {}),
    isPreviewOnly: 1,
    isFavorite: metadata?.isFavorite ? 1 : 0,
    isCompleted: metadata?.isCompleted ? 1 : 0,
    progress: Number(metadata?.progress || 0),
    isUncategorized: metadata ? (metadata.isUncategorized ? 1 : 0) : (categoryId === null ? 1 : 0),
    driveFileId: remote.id,
    syncId: metadata?.syncId || undefined,
    tags: Array.isArray(metadata?.tags) ? metadata.tags : [],
  });
}

/**
 * Fetches a larger temporary preview for a preview-only page, for the
 * viewer. Returns a data URL (or the Drive thumbnail link when the
 * download fails), or null when no preview exists. The caller keeps it
 * only in memory — it is never written to storage.
 */
export async function fetchTempPreviewUrl(page) {
  const link = page.driveThumbnailLink || (typeof page.thumbnailUrl === 'string' && page.thumbnailUrl.startsWith('http') ? page.thumbnailUrl : null);
  if (!link) return null;
  const large = largeThumbnailUrl(link);
  return (await fetchThumbnailDataUrl(large)) || large;
}

/**
 * Imports previews of new Drive files into the library. Only small preview
 * thumbnails are stored — the full files are never downloaded. Returns a
 * result object; throws on network/API errors so the caller can show the
 * failure.
 */
export async function syncDrive() {
  if (!isDriveConnected()) {
    return { notConnected: true, previewsImported: 0, pdfsArchived: 0 };
  }
  const rootId = await getOrCreateFolder(DRIVE_ROOT_FOLDER_NAME, 'root');
  const metadataFolderId = await getOrCreateFolder(METADATA_FOLDER_NAME, rootId);
  const pdfInboxId = await getOrCreateFolder(PDF_INBOX_FOLDER_NAME, rootId);
  const processedPdfsId = await getOrCreateFolder(PROCESSED_PDFS_FOLDER_NAME, rootId);

  let previewsImported = 0;
  let pdfsArchived = 0;
  const processedPdfIds = new Set((await getSetting(KEY_PROCESSED_PDF_IDS) || '').split(',').filter(Boolean));

  const pages = await db.pages.toArray();
  const localDriveIds = new Set(pages.map(page => page.driveFileId).filter(Boolean));
  const categories = await db.categories.toArray();
  const categoryIdByFolder = new Map(categories.filter(category => category.driveFolderId).map(category => [category.driveFolderId, category.id]));

  // PDF Inbox: import a preview-only entry for each new PDF, then move the
  // original to Processed PDFs.
  reportScanProgress({ phase: 'pdfs', percent: 8, message: 'Checking PDF Inbox…' });
  const inboxFiles = await listChildren(pdfInboxId);
  const pdfFiles = inboxFiles.filter(file => file.mimeType === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf'));
  for (let index = 0; index < pdfFiles.length; index++) {
    const file = pdfFiles[index];
    reportScanProgress({ phase: 'pdfs', percent: 10 + Math.round(((index + 1) / Math.max(pdfFiles.length, 1)) * 25), message: `Checking PDF ${index + 1} of ${pdfFiles.length}: ${file.name}` });
    try {
      if (!processedPdfIds.has(file.id) && !localDriveIds.has(file.id)) {
        await importPreviewEntry(file, null, categoryIdByFolder);
        localDriveIds.add(file.id);
        processedPdfIds.add(file.id);
        await setSetting(KEY_PROCESSED_PDF_IDS, [...processedPdfIds].join(','));
        previewsImported++;
      }
      await moveFile(file.id, file.parentIds || [], processedPdfsId);
      pdfsArchived++;
    } catch (error) {
      console.warn(`Could not process PDF ${file.name}:`, error);
    }
  }

  reportScanProgress({ phase: 'drive', percent: 40, message: 'Looking for new Drive files…' });
  const remoteFiles = await listTree(rootId);
  const tombstones = await readTombstones();

  // Read metadata sidecars only to enrich newly imported previews.
  const metadataFiles = [
    ...(await listChildren(metadataFolderId)),
    ...(await listChildren(rootId)).filter(file => file.name?.startsWith(PAGE_META_PREFIX)),
  ];
  const metadataByDriveId = new Map();
  for (const file of metadataFiles) {
    if (!file.name?.startsWith(PAGE_META_PREFIX) || !file.name.endsWith('.json')) continue;
    try {
      const blob = await downloadFile(file.id);
      const metadata = JSON.parse(await blob.text());
      if (metadata.driveFileId) metadataByDriveId.set(metadata.driveFileId, metadata);
    } catch (error) {
      console.warn('Skipping unreadable page metadata:', error.message);
    }
  }

  const remoteMedia = remoteFiles.filter(file => file.mimeType?.startsWith('image/') || file.mimeType === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf'));
  for (let index = 0; index < remoteMedia.length; index++) {
    const remote = remoteMedia[index];
    reportScanProgress({ phase: 'downloads', percent: 45 + Math.round(((index + 1) / Math.max(remoteMedia.length, 1)) * 50), message: `Importing previews ${index + 1} of ${remoteMedia.length}` });
    if (localDriveIds.has(remote.id) || tombstones.has(remote.id)) continue;
    if (processedPdfIds.has(remote.id)) continue;

    const metadata = metadataByDriveId.get(remote.id);
    await importPreviewEntry(remote, metadata, categoryIdByFolder);
    localDriveIds.add(remote.id);
    previewsImported++;
  }

  await setSetting(KEY_LAST_SYNC, Date.now());
  const result = { notConnected: false, previewsImported, pdfsArchived };
  finishScanProgress({ message: 'Previews imported', result });
  return result;
}


// ---------- Automatic preview import ----------
//
// New Drive files are imported automatically as preview-only entries when
// the app opens (and again whenever it comes back online after opening
// offline). Only small preview thumbnails are stored — the full files are
// never downloaded. Repeat runs are cheap no-ops when nothing is new:
// files already in the library and deleted files (tombstones) are
// skipped. Unlike the original import this is not one-time — previews are
// light enough to check for on every open. It can be turned off in
// Settings ("Import new Drive files when the app opens").

export function triggerBackgroundSync() {
  if (syncInFlight) return syncInFlight;
  syncInFlight = (async () => {
    try {
      if (!isDriveConnected()) return null;
      if (!(await isScanOnOpenEnabled())) return null;
      return await syncDrive();
    } catch (e) {
      console.log('Automatic Drive import failed:', e.message);
      finishScanProgress({ active: false, error: true, message: `Import failed: ${e.message}` });
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
 * tombstone so a later import never re-imports it. Best-effort: never throws.
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

