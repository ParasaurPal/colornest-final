import React, { useState, useEffect } from 'react';
import { db } from './db';
import { X, Tags, ShieldAlert, RefreshCw, AlertTriangle, Cloud, CheckCircle2, Copy } from 'lucide-react';
import { seedMassiveCategories } from './prepopulate';
import { removeWhiteBackground, WHITE_BACKGROUND_VERSION } from './whiteBackground';
import { tryCreateThumbnail } from './thumbnails';
import { isDriveConfigured } from './driveConfig';
import { connectDrive, disconnectDrive, isDriveConnected, getDriveAccountEmail } from './driveService';
import {
  syncDrive,
  formatSyncSummary,
  trashRootFolder,
  getLastSyncMillis,
  isScanOnOpenEnabled,
  setScanOnOpenEnabled,
  triggerBackgroundSync,
} from './driveSync';
import DriveBrowserModal from './DriveBrowserModal';
import DuplicatesModal from './DuplicatesModal';

// A page needs (re)processing when it was never processed, or when it was
// processed with an older version of the white-background filter.
// Preview-only pages have no local image, so they are never reprocessed.
const needsWhiteBackgroundReprocessing = (page) =>
  Boolean(page.imageUrl) && (page.whiteBackgroundProcessed !== true || (page.whiteBackgroundVersion || 0) < WHITE_BACKGROUND_VERSION);

export default function SettingsModal({ onClose }) {
  const [isSeeding, setIsSeeding] = useState(false);
  const [galleryFullImages, setGalleryFullImages] = useState(false);

  // Wipe App State
  const [wipeStep, setWipeStep] = useState(0);
  const [confirmText, setConfirmText] = useState("");
  const [deleteCloud, setDeleteCloud] = useState(false);

  // Google Drive State
  const [driveConnected, setDriveConnected] = useState(false);
  const [driveAccount, setDriveAccount] = useState(null);
  const [driveStatus, setDriveStatus] = useState(null);
  const [driveLastSync, setDriveLastSync] = useState(null);
  const [isSyncing, setIsSyncing] = useState(false);
  const [driveScanOnOpen, setDriveScanOnOpen] = useState(true);
  const [isDriveBrowserOpen, setIsDriveBrowserOpen] = useState(false);
  const [isDuplicatesOpen, setIsDuplicatesOpen] = useState(false);
  const [storageUsage, setStorageUsage] = useState(null);
  const [backupBusy, setBackupBusy] = useState(false);
  const [imageProcessing, setImageProcessing] = useState(false);
  const [imageProgress, setImageProgress] = useState({ done: 0, total: 0 });
  const [imageProcessStatus, setImageProcessStatus] = useState('');
  const [unprocessedImageCount, setUnprocessedImageCount] = useState(0);

  const refreshDriveState = async () => {
    setDriveConnected(isDriveConnected());
    setDriveAccount(getDriveAccountEmail());
    setDriveLastSync(await getLastSyncMillis());
    setDriveScanOnOpen(await isScanOnOpenEnabled());
  };

  // Load the Drive state when the modal opens. The reads are async (Dexie),
  // so they resolve in a callback rather than during render.
  useEffect(() => {
    let active = true;
    (async () => {
      const connected = isDriveConnected();
      const account = getDriveAccountEmail();
      const lastSync = await getLastSyncMillis();
      const scanOnOpen = await isScanOnOpenEnabled();
      if (!active) return;
      setDriveConnected(connected);
      setDriveAccount(account);
      setDriveLastSync(lastSync);
      setDriveScanOnOpen(scanOnOpen);
    })();
    return () => { active = false; };
  }, []);

  useEffect(() => {
    let active = true;
    db.settings.get('galleryFullImages').then(setting => {
      if (active) setGalleryFullImages(setting?.value === true);
    });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    let active = true;
    db.pages.filter(needsWhiteBackgroundReprocessing).count()
      .then(count => { if (active) setUnprocessedImageCount(count); })
      .catch(error => console.warn('Could not count unprocessed pages:', error));
    return () => { active = false; };
  }, []);

  useEffect(() => {
    let active = true;
    (async () => {
      try {
        const estimate = await navigator.storage?.estimate?.();
        if (active && estimate) {
          const used = estimate.usage || 0;
          const quota = estimate.quota || 0;
          setStorageUsage({ used, quota });
        }
        await navigator.storage?.persist?.();
      } catch (error) {
        console.info('Persistent browser storage is unavailable:', error);
      }
    })();
    return () => { active = false; };
  }, []);

  const handleExportBackup = async () => {
    setBackupBusy(true);
    try {
      const backup = {
        format: 'ColorNest backup',
        version: 1,
        exportedAt: new Date().toISOString(),
        pages: await db.pages.toArray(),
        categories: await db.categories.toArray(),
        tags: await db.tags.toArray(),
      };
      const blob = new Blob([JSON.stringify(backup)], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `ColorNest-backup-${new Date().toISOString().slice(0, 10)}.json`;
      link.click();
      window.setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (error) {
      alert('Backup export failed: ' + error.message);
    } finally {
      setBackupBusy(false);
    }
  };

  const handleImportBackup = async (event) => {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) return;
    if (!window.confirm('Restore this backup? This replaces the pages, categories, and tags currently stored in this browser. Export a backup of the current library first if you want to keep it.')) return;
    setBackupBusy(true);
    try {
      const backup = JSON.parse(await file.text());
      if (backup.format !== 'ColorNest backup' || !Array.isArray(backup.pages) || !Array.isArray(backup.categories)) {
        throw new Error('This is not a valid ColorNest backup file.');
      }
      await db.transaction('rw', db.pages, db.categories, db.tags, async () => {
        await db.pages.clear();
        await db.categories.clear();
        await db.tags.clear();
        if (backup.categories.length) await db.categories.bulkAdd(backup.categories);
        if (backup.pages.length) await db.pages.bulkAdd(backup.pages);
        if (Array.isArray(backup.tags) && backup.tags.length) await db.tags.bulkAdd(backup.tags);
      });
      triggerBackgroundSync();
      alert('Backup restored. Your library is ready on this device.');
    } catch (error) {
      alert('Backup restore failed: ' + error.message);
    } finally {
      setBackupBusy(false);
    }
  };

  const handleProcessExistingImages = async () => {
    if (imageProcessing) return;
    try {
      const pendingPages = await db.pages
        .filter(needsWhiteBackgroundReprocessing)
        .toArray();
      if (!pendingPages.length) {
        setUnprocessedImageCount(0);
        setImageProcessStatus('All existing pages have already been processed.');
        return;
      }
      if (!window.confirm(`Remove white backgrounds from ${pendingPages.length} existing pages? This updates their stored images but keeps all page details and organization.`)) return;

      setImageProcessing(true);
      setImageProgress({ done: 0, total: pendingPages.length });
      setImageProcessStatus('Preparing your library…');
      let processed = 0;
      let failed = 0;

      for (let index = 0; index < pendingPages.length; index++) {
        const page = pendingPages[index];
        setImageProcessStatus(`Processing ${index + 1} of ${pendingPages.length}: ${page.title || 'Untitled page'}`);
        try {
          const transparentImage = await removeWhiteBackground(page.imageUrl);
          const thumbnailUrl = await tryCreateThumbnail(transparentImage);
          await db.pages.update(page.id, {
            imageUrl: transparentImage,
            // Replace any stale thumbnail of the unprocessed image; null
            // clears it so the gallery falls back to the full image and the
            // backfill retries it on the next app open.
            thumbnailUrl: thumbnailUrl || null,
            whiteBackgroundProcessed: true,
            whiteBackgroundVersion: WHITE_BACKGROUND_VERSION,
          });
          processed++;
        } catch (error) {
          failed++;
          console.warn(`Could not remove white background from ${page.title || page.id}:`, error);
        }
        setImageProgress({ done: index + 1, total: pendingPages.length });
      }

      const remaining = await db.pages
        .filter(needsWhiteBackgroundReprocessing)
        .count();
      setUnprocessedImageCount(remaining);
      setImageProcessStatus(`Finished: ${processed} processed, ${failed} failed.${remaining ? ` ${remaining} pages remain and can be retried.` : ''}`);
    } catch (error) {
      setImageProcessStatus('Could not process library: ' + error.message);
    } finally {
      setImageProcessing(false);
    }
  };

  const handleDriveConnect = async () => {
    try {
      setDriveStatus("Connecting…");
      await connectDrive();
      await refreshDriveState();
      setDriveStatus("Connected — importing new Drive files…");
      handleDriveSyncNow();
    } catch (e) {
      setDriveStatus("Could not connect: " + e.message);
    }
  };

  const handleDriveDisconnect = async () => {
    await disconnectDrive();
    await refreshDriveState();
    setDriveStatus("Disconnected from Google Drive");
  };

  const handleDriveSyncNow = async () => {
    if (isSyncing) return;
    setIsSyncing(true);
    setDriveStatus("Checking for new Drive files…");
    try {
      const result = await syncDrive();
      setDriveStatus(formatSyncSummary(result));
    } catch (e) {
      setDriveStatus("Sync failed: " + e.message.slice(0, 120));
    } finally {
      setDriveLastSync(await getLastSyncMillis());
      setIsSyncing(false);
    }
  };

  const handleDriveScanToggle = async (enabled) => {
    setDriveScanOnOpen(enabled);
    await setScanOnOpenEnabled(enabled);
  };

  const handleClearAllTags = async () => {
    if (window.confirm("Remove all tags from every page? Titles, categories, favorites, and completion status will not change.")) {
      const allPages = await db.pages.toArray();
      await Promise.all(allPages.map(p => db.pages.update(p.id, { tags: [], updatedAt: Date.now() })));
      await db.tags.clear();
      alert("All tags have been cleared.");
    }
  };

  const handleSeedCategories = async () => {
    if (window.confirm("This will replace all current categories with the massive default list. Continue?")) {
      setIsSeeding(true);
      await seedMassiveCategories();
      setIsSeeding(false);
      alert("Categories successfully populated!");
      onClose();
    }
  };

  const handleGalleryFullImagesToggle = async (enabled) => {
    setGalleryFullImages(enabled);
    await db.settings.put({ key: 'galleryFullImages', value: enabled });
  };

  const handleWipeApp = async () => {
    if (confirmText !== "DELETE") return;

    try {
      // Move the ColorNest folder in Google Drive to trash first (trash
      // only — files stay recoverable from the Drive trash for 30 days).
      if (deleteCloud) {
        await trashRootFolder();
      }

      await db.delete();
      alert("All data has been erased. The app will now restart.");
      window.location.reload();

    } catch (error) {
      alert("Error wiping app: " + error.message);
    }
  };

  // Always go to Step 3 after Step 2
  const handleNextWipeStep = () => {
    if (wipeStep === 2) {
      setWipeStep(3); 
    } else if (wipeStep === 3) {
      setWipeStep(4); 
    }
  };

  return (
    <div className="fixed inset-0 bg-black/60 flex justify-center items-center z-[200] p-4">
      <div className="bg-white dark:bg-gray-800 rounded-3xl w-full max-w-md p-6 relative max-h-[90vh] overflow-y-auto">
        <button onClick={onClose} className="absolute top-4 right-4 text-gray-500 dark:text-gray-300 z-50 p-1"><X size={24} /></button>
        <h2 className="text-2xl font-bold mb-6 text-gray-800 dark:text-white">Settings</h2>

        <div className="space-y-4">
          <div className="p-4 bg-purple-50 dark:bg-purple-900/30 rounded-xl">
            <h3 className="font-bold text-purple-800 dark:text-purple-300 flex items-center gap-2 mb-2">
              <RefreshCw size={18} /> Setup Categories
            </h3>
            <button onClick={handleSeedCategories} disabled={isSeeding} className="w-full bg-purple-600 text-white py-2 rounded-lg font-bold flex items-center justify-center gap-2">
              {isSeeding ? "Building Tree..." : "Load Default Categories"}
            </button>
          </div>

          <div className="p-4 bg-purple-50 dark:bg-purple-900/30 rounded-xl">
            <h3 className="font-bold text-purple-800 dark:text-purple-300 mb-2">Gallery</h3>
            <label className="flex items-center gap-3 cursor-pointer">
              <input type="checkbox" checked={galleryFullImages} onChange={(e) => handleGalleryFullImagesToggle(e.target.checked)} className="w-5 h-5 accent-purple-600" />
              <span className="text-sm font-bold text-gray-800 dark:text-white">Show full images in the gallery</span>
            </label>
            <p className="text-xs text-gray-600 dark:text-gray-400 mt-1">Shows the high-quality full image on every gallery card instead of a small preview thumbnail. Uses significantly more memory.</p>
          </div>

          <div className="p-4 bg-gray-50 dark:bg-gray-900 rounded-xl border border-gray-200 dark:border-gray-700">
            <h3 className="font-bold text-gray-800 dark:text-white mb-1">This device & backups</h3>
            <p className="text-xs text-gray-600 dark:text-gray-400 mb-3">ColorNest stores your pages in this browser's local database on this phone or computer. It works offline. Export a portable backup to move the whole library to another device.</p>
            {storageUsage && <p className="text-xs text-gray-500 dark:text-gray-400 mb-3">Browser storage: {(storageUsage.used / 1048576).toFixed(1)} MB used of {(storageUsage.quota / 1048576).toFixed(0)} MB available (estimated).</p>}
            <div className="flex gap-2">
              <button onClick={handleExportBackup} disabled={backupBusy} className="flex-1 bg-purple-600 text-white py-2 rounded-lg font-bold disabled:opacity-50">{backupBusy ? 'Working…' : 'Export backup'}</button>
              <label className="flex-1 text-center bg-white dark:bg-gray-700 border border-gray-300 dark:border-gray-600 text-gray-800 dark:text-white py-2 rounded-lg font-bold cursor-pointer">Restore backup<input type="file" accept="application/json,.json" onChange={handleImportBackup} disabled={backupBusy || imageProcessing} className="hidden" /></label>
            </div>
            <div className="mt-4 pt-3 border-t border-gray-200 dark:border-gray-700">
              <div className="flex items-center justify-between gap-2 mb-2">
                <h4 className="text-sm font-bold text-gray-800 dark:text-white">White background removal</h4>
                <span className="text-xs text-gray-500 dark:text-gray-400">{unprocessedImageCount} remaining</span>
              </div>
              <p className="text-xs text-gray-600 dark:text-gray-400 mb-3">Process pages already in your library. Titles, categories, tags, favorites, and other details are preserved. Pages already processed with the current filter are skipped; pages done with an older filter are refreshed.</p>
              <button onClick={handleProcessExistingImages} disabled={imageProcessing || unprocessedImageCount === 0} className="w-full bg-purple-600 text-white py-2 rounded-lg font-bold disabled:opacity-50">
                {imageProcessing ? `Processing ${imageProgress.done} of ${imageProgress.total}…` : unprocessedImageCount ? 'Process existing library' : 'Library already processed'}
              </button>
              {imageProcessing && (
                <div className="mt-2">
                  <div className="flex justify-between text-[11px] text-gray-500 dark:text-gray-400 mb-1"><span>Processing pages</span><span>{Math.round((imageProgress.done / Math.max(imageProgress.total, 1)) * 100)}%</span></div>
                  <div className="h-2 rounded-full bg-purple-100 dark:bg-gray-700 overflow-hidden"><div className="h-full bg-purple-600 transition-all" style={{ width: `${(imageProgress.done / Math.max(imageProgress.total, 1)) * 100}%` }} /></div>
                </div>
              )}
              {imageProcessStatus && <p className="text-xs text-gray-600 dark:text-gray-300 mt-2" role="status" aria-live="polite">{imageProcessStatus}</p>}
            </div>
            <div className="mt-4 pt-3 border-t border-gray-200 dark:border-gray-700">
              <h4 className="text-sm font-bold text-gray-800 dark:text-white mb-2">Duplicate pages</h4>
              <p className="text-xs text-gray-600 dark:text-gray-400 mb-3">Find pages with the exact same image and delete the extra copies. The copy in a category is kept by default — you can pick a different one to keep.</p>
              <button onClick={() => setIsDuplicatesOpen(true)} className="w-full bg-purple-600 text-white py-2 rounded-lg font-bold flex items-center justify-center gap-2">
                <Copy size={16} /> Find duplicates
              </button>
            </div>
          </div>

          <div className="p-4 bg-purple-50 dark:bg-purple-900/30 rounded-xl">
            <h3 className="font-bold text-purple-800 dark:text-purple-300 flex items-center gap-2 mb-2">
              <Cloud size={18} /> Google Drive
            </h3>
            <p className="text-sm font-bold text-gray-800 dark:text-white">
              {driveConnected
                ? `Connected${driveAccount ? ` as ${driveAccount}` : ''}`
                : 'Not connected'}
            </p>
            {!driveConnected && (
              <p className="text-xs text-gray-600 dark:text-gray-400 mt-1 mb-2">
                {isDriveConfigured()
                  ? 'Connect to browse your ColorNest folder in Google Drive. New files are imported automatically as small previews — full files stay in Drive until you download the ones you want.'
                  : 'Google Drive is not set up yet — add your Web OAuth client ID to a .env file first (see DRIVE_SETUP.md in the project).'}
              </p>
            )}
            {driveLastSync && (
              <p className="text-xs text-gray-600 dark:text-gray-400 mt-1">
                Last synced {new Date(driveLastSync).toLocaleString([], { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' })}
              </p>
            )}
            {driveStatus && (
              <p className="text-xs text-gray-600 dark:text-gray-400 mt-1">{driveStatus}</p>
            )}
            {driveConnected ? (
              <>
                <div className="flex gap-2 mt-3">
                  <button onClick={() => setIsDriveBrowserOpen(true)} className="flex-1 bg-purple-600 text-white py-2 rounded-lg font-bold">
                    Browse Google Drive
                  </button>
                  <button onClick={handleDriveDisconnect} className="flex-1 bg-white dark:bg-gray-700 border border-gray-300 dark:border-gray-600 text-gray-800 dark:text-white py-2 rounded-lg font-bold">
                    Disconnect
                  </button>
                </div>
                <button onClick={handleDriveSyncNow} disabled={isSyncing} className="w-full bg-purple-600 text-white py-2 rounded-lg font-bold mt-2 disabled:opacity-50">
                  {isSyncing ? 'Syncing…' : 'Import previews'}
                </button>
                <label className="flex items-center gap-3 mt-3 cursor-pointer">
                  <input type="checkbox" checked={driveScanOnOpen} onChange={(e) => handleDriveScanToggle(e.target.checked)} className="w-5 h-5 accent-purple-600" />
                  <span className="text-sm font-bold text-gray-800 dark:text-white">Import new Drive files when the app opens</span>
                </label>
                <p className="text-xs text-gray-600 dark:text-gray-400 mt-1">New Drive files are imported automatically as small previews when the app opens — full files are never downloaded unless you download them from the browser or share them. Import previews checks for new files now. PDFs from ColorNest/PDF Inbox are moved to Processed PDFs after their preview is imported. Downloading a PDF from the browser converts it into pages.</p>
              </>
            ) : (
              <button onClick={handleDriveConnect} disabled={!isDriveConfigured()} className="w-full bg-purple-600 text-white py-2 rounded-lg font-bold mt-2 disabled:opacity-50">
                Connect Google Drive
              </button>
            )}
          </div>

          <div className="border border-red-200 dark:border-red-900 rounded-xl p-4">
            <h3 className="font-bold text-red-600 dark:text-red-400 flex items-center gap-2 mb-2"><ShieldAlert size={18} /> Danger Zone</h3>
            
            <button onClick={handleClearAllTags} className="w-full mb-2 bg-red-100 dark:bg-red-900/30 text-red-600 dark:text-red-400 py-2 rounded-lg font-bold flex items-center justify-center gap-2">
              <Tags size={16} /> Clear All Tags
            </button>

            <button onClick={() => setWipeStep(1)} className="w-full bg-red-600 text-white py-2 rounded-lg font-bold flex items-center justify-center gap-2">
              <AlertTriangle size={16} /> Erase All Images & Data
            </button>
          </div>
        </div>
      </div>

      {/* WIPE MODAL OVERLAY */}
      {wipeStep > 0 && (
        <div className="fixed inset-0 bg-black/80 flex justify-center items-center z-[300] p-4">
          <div className="bg-white dark:bg-gray-800 rounded-2xl p-6 w-full max-w-sm">
            
            {/* Step 1: Main Warning & Checkbox */}
            {wipeStep === 1 && (
              <div className="text-center">
                <AlertTriangle size={48} className="mx-auto text-red-500 mb-4" />
                <h3 className="text-xl font-bold text-gray-800 dark:text-white mb-2">Erase App Data?</h3>
                <p className="text-sm text-gray-600 dark:text-gray-400 mb-4">This will permanently delete every uploaded image, PDF, tag, and category from your device.</p>
                
                <label className="flex items-center gap-3 p-3 bg-gray-50 dark:bg-gray-700 rounded-lg cursor-pointer mb-6 border border-gray-200 dark:border-gray-600">
                  <input type="checkbox" checked={deleteCloud} onChange={(e) => setDeleteCloud(e.target.checked)} className="w-5 h-5 accent-red-600" />
                  <div className="text-left">
                    <span className="text-sm font-bold text-gray-800 dark:text-white flex items-center gap-1"><Cloud size={14}/> Also move the Drive folder to trash</span>
                    <span className="text-xs text-gray-500 dark:text-gray-400">Moves the ColorNest folder in Google Drive to trash (recoverable for 30 days)</span>
                  </div>
                </label>

                <div className="flex gap-2">
                  <button onClick={() => { setWipeStep(0); setDeleteCloud(false); }} className="flex-1 bg-gray-200 dark:bg-gray-700 py-2 rounded-lg font-bold text-gray-800 dark:text-white">Cancel</button>
                  <button onClick={() => setWipeStep(2)} className="flex-1 bg-red-500 text-white py-2 rounded-lg font-bold">Continue</button>
                </div>
              </div>
            )}

            {/* Step 2: Local Data Warning */}
            {wipeStep === 2 && (
              <div className="text-center">
                <ShieldAlert size={48} className="mx-auto text-red-600 mb-4" />
                <h3 className="text-xl font-bold text-red-600 mb-2">Final Warning!</h3>
                <p className="text-sm text-gray-600 dark:text-gray-400 mb-6">There is no undo. All images saved on this device will be permanently lost.</p>
                <div className="flex gap-2">
                  <button onClick={() => setWipeStep(0)} className="flex-1 bg-gray-200 dark:bg-gray-700 py-2 rounded-lg font-bold text-gray-800 dark:text-white">Cancel</button>
                  <button onClick={handleNextWipeStep} className="flex-1 bg-red-600 text-white py-2 rounded-lg font-bold">I Understand</button>
                </div>
              </div>
            )}

            {/* Step 3: Cloud Warning (ALWAYS SHOWN, Text changes based on checkbox) */}
            {wipeStep === 3 && (
              <div className="text-center">
                {deleteCloud ? (
                  <>
                    <Cloud size={48} className="mx-auto text-red-600 mb-4" />
                    <h3 className="text-xl font-bold text-red-600 mb-2">Drive Folder Trashed!</h3>
                    <p className="text-sm text-gray-600 dark:text-gray-400 mb-6">You have chosen to move the ColorNest folder in Google Drive to trash. The files stay recoverable from the Drive trash for 30 days; sync never permanently deletes anything.</p>
                    <div className="flex gap-2">
                      <button onClick={() => setWipeStep(0)} className="flex-1 bg-gray-200 dark:bg-gray-700 py-2 rounded-lg font-bold text-gray-800 dark:text-white">Cancel</button>
                      <button onClick={handleNextWipeStep} className="flex-1 bg-red-600 text-white py-2 rounded-lg font-bold">Trash Drive Too</button>
                    </div>
                  </>
                ) : (
                  <>
                    <CheckCircle2 size={48} className="mx-auto text-green-500 mb-4" />
                    <h3 className="text-xl font-bold text-gray-800 dark:text-white mb-2">Drive Folder Safe</h3>
                    <p className="text-sm text-gray-600 dark:text-gray-400 mb-6">You did not select the Drive folder. Your Google Drive backup will remain intact.</p>
                    <div className="flex gap-2">
                      <button onClick={() => setWipeStep(0)} className="flex-1 bg-gray-200 dark:bg-gray-700 py-2 rounded-lg font-bold text-gray-800 dark:text-white">Cancel</button>
                      <button onClick={handleNextWipeStep} className="flex-1 bg-red-600 text-white py-2 rounded-lg font-bold">Keep Drive Data</button>
                    </div>
                  </>
                )}
              </div>
            )}

            {/* Step 4: Type to Confirm */}
            {wipeStep === 4 && (
              <div className="text-center">
                <h3 className="text-lg font-bold text-gray-800 dark:text-white mb-2">Type "DELETE" to confirm</h3>
                <input 
                  type="text" 
                  value={confirmText} 
                  onChange={(e) => setConfirmText(e.target.value)}
                  className="w-full p-3 border-2 border-red-300 rounded-lg text-center font-bold text-lg mb-4 bg-red-50 dark:bg-gray-700 dark:text-white focus:outline-none focus:border-red-500"
                  placeholder="DELETE"
                />
                <div className="flex gap-2">
                  <button onClick={() => { setWipeStep(0); setConfirmText(""); }} className="flex-1 bg-gray-200 dark:bg-gray-700 py-2 rounded-lg font-bold text-gray-800 dark:text-white">Cancel</button>
                  <button 
                    onClick={handleWipeApp} 
                    disabled={confirmText !== "DELETE"}
                    className="flex-1 bg-red-600 text-white py-2 rounded-lg font-bold disabled:opacity-50"
                  >
                    {deleteCloud ? "Wipe App & Trash Drive" : "Wipe App Only"}
                  </button>
                </div>
              </div>
            )}

          </div>
        </div>
      )}
      {isDriveBrowserOpen && <DriveBrowserModal onClose={() => setIsDriveBrowserOpen(false)} />}
      {isDuplicatesOpen && <DuplicatesModal onClose={() => setIsDuplicatesOpen(false)} />}
    </div>
  );
}

