import React, { useState, useEffect, useMemo, useRef } from 'react';
import { Search, Heart, Plus, Trash2, Moon, Sun, Menu, Settings, ExternalLink, Tags, Maximize2, Minimize2, Download, Share2, X, CheckCircle2 } from 'lucide-react';
import { useLiveQuery } from 'dexie-react-hooks';
import { db } from './db';
import AddPageModal from './AddPageModal';
import SideMenu from './SideMenu';
import CategorizeModal from './CategorizeModal';
import SettingsModal from './SettingsModal';
import Onboarding from './Onboarding';
import { triggerBackgroundSync, deletePageWithDrive, subscribeScanProgress, fetchTempPreviewUrl, downloadPreviewPage } from './driveSync';
import { downloadFile } from './driveService';
import { tryCreateThumbnail } from './thumbnails';
import { convertPdfToImages } from './pdfConverter';
import { savePageToDb } from './pageImport';

function App() {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);
  const [categorizingPage, setCategorizingPage] = useState(null);
  const [isDarkMode, setIsDarkMode] = useState(false);
  const [activeCategoryId, setActiveCategoryId] = useState(null);
  const [searchQuery, setSearchQuery] = useState("");
  const [showNoTagsOnly, setShowNoTagsOnly] = useState(false);
  const [activeTags, setActiveTags] = useState([]);
  const [tagMatchMode, setTagMatchMode] = useState('all');
  const [sortBy, setSortBy] = useState('recent');
  const [isTagBrowserOpen, setIsTagBrowserOpen] = useState(false);
  const [showTutorial, setShowTutorial] = useState(false);
  const [installPrompt, setInstallPrompt] = useState(null);
  const [isFullscreen, setIsFullscreen] = useState(Boolean(document.fullscreenElement || window.matchMedia('(display-mode: fullscreen)').matches));
  const [viewingPage, setViewingPage] = useState(null);
  const [scanProgress, setScanProgress] = useState(null);
  const [tempPreviewUrl, setTempPreviewUrl] = useState(null);
  const [isTempPreviewLoading, setIsTempPreviewLoading] = useState(false);
  const [shareStatus, setShareStatus] = useState('');
  const [isDownloadingPreview, setIsDownloadingPreview] = useState(false);
  const [downloadStatus, setDownloadStatus] = useState('');
  const [downloadedPdfPages, setDownloadedPdfPages] = useState(null);

  const pages = useLiveQuery(() => db.pages.toArray());
  const categories = useLiveQuery(() => db.categories.toArray());
  const savedTags = useLiveQuery(() => db.tags.toArray());
  const galleryFullImagesSetting = useLiveQuery(() => db.settings.get('galleryFullImages'));
  const galleryFullImages = galleryFullImagesSetting?.value === true;

  useEffect(() => {
    if (localStorage.getItem('theme') === 'dark') {
      setIsDarkMode(true);
      document.documentElement.classList.add('dark');
    }
    db.settings.get('hasSeenTutorial').then(setting => {
      if (!setting || !setting.value) setShowTutorial(true);
    });
    const unsubscribeScanProgress = subscribeScanProgress(setScanProgress);
    triggerBackgroundSync();
    const retryDriveSync = () => { if (navigator.onLine) triggerBackgroundSync(); };
    window.addEventListener('online', retryDriveSync);

    const captureInstall = (event) => {
      event.preventDefault();
      setInstallPrompt(event);
    };
    const updateFullscreen = () => setIsFullscreen(Boolean(document.fullscreenElement || window.matchMedia('(display-mode: fullscreen)').matches));
    window.addEventListener('beforeinstallprompt', captureInstall);
    document.addEventListener('fullscreenchange', updateFullscreen);
    return () => {
      unsubscribeScanProgress();
      window.removeEventListener('online', retryDriveSync);
      window.removeEventListener('beforeinstallprompt', captureInstall);
      document.removeEventListener('fullscreenchange', updateFullscreen);
    };
  }, []);

  // One-time backfill for pages saved before thumbnails existed: generate a
  // thumbnail for each page that has an image but no thumbnail yet, a few
  // at a time with a yield between batches so the UI stays responsive.
  // It runs in the background without blocking first paint, never touches
  // pages that already have a thumbnail, and silently skips images that
  // fail to load (those pages keep using the full image in the gallery).
  useEffect(() => {
    let cancelled = false;
    const backfillThumbnails = async () => {
      try {
        const pending = await db.pages.filter(page => page.imageUrl && !page.thumbnailUrl).toArray();
        const batchSize = 3;
        for (let i = 0; i < pending.length && !cancelled; i += batchSize) {
          const batch = pending.slice(i, i + batchSize);
          await Promise.all(batch.map(async (page) => {
            const thumbnailUrl = await tryCreateThumbnail(page.imageUrl);
            if (thumbnailUrl && !cancelled) {
              await db.pages.update(page.id, { thumbnailUrl });
            }
          }));
          await new Promise(resolve => setTimeout(resolve, 0));
        }
      } catch (error) {
        console.warn('Thumbnail backfill failed:', error);
      }
    };
    backfillThumbnails();
    return () => { cancelled = true; };
  }, []);

  // Preview-only pages hold just a small thumbnail. Opening one fetches a
  // larger temporary preview from Drive, kept only in memory — it is never
  // written to storage and disappears when the viewer closes or the app
  // closes.
  useEffect(() => {
    setShareStatus('');
    if (!viewingPage?.isPreviewOnly) {
      setTempPreviewUrl(null);
      setIsTempPreviewLoading(false);
      return;
    }
    let cancelled = false;
    setIsTempPreviewLoading(true);
    setTempPreviewUrl(null);
    fetchTempPreviewUrl(viewingPage).then((url) => {
      if (!cancelled) {
        setTempPreviewUrl(url);
        setIsTempPreviewLoading(false);
      }
    }).catch(() => {
      if (!cancelled) setIsTempPreviewLoading(false);
    });
    return () => { cancelled = true; };
  }, [viewingPage]);

  const completeTutorial = async () => {
    await db.settings.put({ key: 'hasSeenTutorial', value: true });
    setShowTutorial(false);
  };

  const toggleTheme = () => {
    const next = !isDarkMode;
    setIsDarkMode(next);
    document.documentElement.classList.toggle('dark', next);
    localStorage.setItem('theme', next ? 'dark' : 'light');
  };

  const toggleFullscreen = async () => {
    try {
      if (window.matchMedia('(display-mode: fullscreen)').matches && !document.fullscreenElement) return;
      if (document.fullscreenElement) await document.exitFullscreen();
      else await document.documentElement.requestFullscreen();
    } catch (error) {
      alert('Fullscreen is not available in this browser. Install ColorNest from your browser menu for the app-like display.');
    }
  };

  const installApp = async () => {
    if (!installPrompt) return;
    installPrompt.prompt();
    await installPrompt.userChoice;
    setInstallPrompt(null);
  };

  // Shares a page with another program. Pages linked to Drive share the
  // original file from Drive (downloaded just for the share, never stored);
  // local pages share the stored image. The system share sheet lets the
  // user pick any app that can open the file. Where the device can't share
  // files, the file opens in a new tab instead, so the browser's own share
  // or "open in app" options can still pick an app — and if even that is
  // blocked, the file is downloaded.
  const handleSharePage = async (page) => {
    setShareStatus('');
    try {
      let blob;
      if (page.driveFileId) {
        blob = await downloadFile(page.driveFileId);
      } else if (page.imageUrl) {
        blob = await (await fetch(page.imageUrl)).blob();
      } else {
        setShareStatus('Nothing to share for this page.');
        return;
      }
      const ext = blob.type === 'application/pdf' ? 'pdf' : (blob.type.split('/')[1] || 'png');
      const fileName = `${(page.title || 'coloring-page').replace(/[\\/:*?"<>|]/g, '')}.${ext}`;
      const file = new File([blob], fileName, { type: blob.type });
      if (navigator.share && navigator.canShare && navigator.canShare({ files: [file] })) {
        await navigator.share({ files: [file], title: page.title || 'Coloring page' });
        return;
      }
      const url = URL.createObjectURL(blob);
      const openedTab = window.open(url, '_blank');
      if (openedTab) {
        setShareStatus("Sharing is not supported here — the file opened in a new tab. Use your browser's share menu to choose an app.");
        setTimeout(() => URL.revokeObjectURL(url), 60000);
      } else {
        const link = document.createElement('a');
        link.href = url;
        link.download = fileName;
        link.click();
        URL.revokeObjectURL(url);
        setShareStatus('Sharing is not supported here — the original was downloaded instead.');
      }
    } catch (error) {
      if (error.name === 'AbortError') return;
      setShareStatus('Could not share: ' + (error.message || 'Unknown error'));
    }
  };

  // Downloads a preview-only page in full. A PDF is converted to pages and
  // replaces its preview entry; an image upgrades to a full page in place.
  const handleDownloadPreview = async (page) => {
    if (isDownloadingPreview) return;
    setIsDownloadingPreview(true);
    setDownloadStatus('Downloading…');
    try {
      const result = await downloadPreviewPage(page);
      if (result.isPdf) {
        setDownloadedPdfPages(result.pagesAdded);
        setDownloadStatus('');
      } else {
        const fresh = await db.pages.get(page.id);
        if (fresh) setViewingPage(fresh);
        setDownloadStatus('Downloaded — this page is now saved on this device.');
      }
    } catch (error) {
      setDownloadStatus('Download failed: ' + (error.message || 'Unknown error'));
    } finally {
      setIsDownloadingPreview(false);
    }
  };

  // Open large/data-URL images inside the app. Sending a huge base64 URL
  // to window.open can crash or force-close mobile browsers.
  const openExternal = (page) => {
    setDownloadStatus('');
    setDownloadedPdfPages(null);
    setViewingPage(page);
  };

  // Files shared to ColorNest from another Android app (via the share
  // target) wait in a cache the service worker fills. Import them on open:
  // PDFs are converted to pages exactly like an upload, then the cache
  // entry is removed so a file is never imported twice.
  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        if (!('caches' in window)) return;
        const cache = await caches.open('share-target-pending');
        const keys = await cache.keys();
        const pending = keys.filter((request) => request.url.includes('/share-target/'));
        for (const request of pending) {
          if (cancelled) return;
          const response = await cache.match(request);
          if (!response) {
            await cache.delete(request);
            continue;
          }
          const blob = await response.blob();
          const encodedName = request.url.split('/').pop() || 'shared.pdf';
          const fileName = decodeURIComponent(encodedName.replace(/^\d+-\d+-/, ''));
          const isPdf = blob.type === 'application/pdf' || fileName.toLowerCase().endsWith('.pdf');
          if (!isPdf) {
            await cache.delete(request);
            continue;
          }
          const images = await convertPdfToImages(await blob.arrayBuffer());
          const baseTitle = fileName.replace(/\.pdf$/i, '');
          for (let i = 0; i < images.length; i++) {
            if (cancelled) return;
            await savePageToDb(images[i], `${baseTitle} - Page ${i + 1}`);
          }
          await cache.delete(request);
        }
      } catch (error) {
        console.warn('Could not import a shared file:', error);
      }
    })();
    return () => { cancelled = true; };
  }, []);

  const getCategoryAndChildren = (catId, allCats) => {
    let ids = [catId];
    allCats.filter(category => category.parentId === catId).forEach(child => {
      ids = [...ids, ...getCategoryAndChildren(child.id, allCats)];
    });
    return ids;
  };

  const tagCounts = useMemo(() => {
    const counts = new Map((savedTags || []).map(tag => [tag.name, 0]));
    (pages || []).forEach(page => (page.tags || []).forEach(tag => {
      const normalized = String(tag).trim();
      if (normalized) counts.set(normalized, (counts.get(normalized) || 0) + 1);
    }));
    return [...counts.entries()].sort((a, b) => a[0].localeCompare(b[0]));
  }, [pages, savedTags]);

  const createTag = async () => {
    const name = window.prompt('Name the new tag:')?.trim();
    if (!name) return;
    try { await db.tags.add({ name, createdAt: Date.now() }); }
    catch { alert('That tag already exists.'); }
  };

  const renameTag = async (oldName) => {
    const newName = window.prompt(`Rename "${oldName}" to:`, oldName)?.trim();
    if (!newName || newName === oldName) return;
    const existing = await db.tags.where('name').equals(newName).first();
    if (existing && !window.confirm(`"${newName}" already exists. Merge "${oldName}" into it?`)) return;
    await db.transaction('rw', db.pages, db.tags, async () => {
      await db.pages.toCollection().modify(page => {
        if ((page.tags || []).includes(oldName)) {
          page.tags = [...new Set(page.tags.map(tag => tag === oldName ? newName : tag))];
          page.updatedAt = Date.now();
        }
      });
      const oldTag = await db.tags.where('name').equals(oldName).first();
      if (oldTag) await db.tags.delete(oldTag.id);
      if (!existing) await db.tags.add({ name: newName, createdAt: Date.now() });
    });
    setActiveTags(tags => tags.map(tag => tag === oldName ? newName : tag));
  };

  const deleteTag = async (name) => {
    if (!window.confirm(`Delete the tag "${name}" from the library and all pages?`)) return;
    await db.transaction('rw', db.pages, db.tags, async () => {
      await db.pages.toCollection().modify(page => {
        if ((page.tags || []).includes(name)) {
          page.tags = page.tags.filter(tag => tag !== name);
          page.updatedAt = Date.now();
        }
      });
      const row = await db.tags.where('name').equals(name).first();
      if (row) await db.tags.delete(row.id);
    });
    setActiveTags(tags => tags.filter(tag => tag !== name));
  };

  // Drag-and-drop import: files dropped anywhere on the app are imported
  // exactly like an upload (images as single pages, PDFs converted page by
  // page). A counter tracks dragenter/dragleave pairs so the overlay doesn't
  // flicker while moving between elements.
  const [isDraggingFiles, setIsDraggingFiles] = useState(false);
  const [dropStatus, setDropStatus] = useState('');
  const [isImportingDrop, setIsImportingDrop] = useState(false);
  const dragCounterRef = useRef(0);
  const dropStatusTimeoutRef = useRef(null);

  const isFileDrag = (event) => [...(event.dataTransfer?.types || [])].includes('Files');

  const handleDragEnter = (event) => {
    if (!isFileDrag(event)) return;
    event.preventDefault();
    dragCounterRef.current += 1;
    setIsDraggingFiles(true);
  };

  const handleDragOver = (event) => {
    if (!isFileDrag(event)) return;
    event.preventDefault();
    event.dataTransfer.dropEffect = 'copy';
  };

  const handleDragLeave = (event) => {
    if (!isFileDrag(event)) return;
    dragCounterRef.current -= 1;
    if (dragCounterRef.current <= 0) {
      dragCounterRef.current = 0;
      setIsDraggingFiles(false);
    }
  };

  const readFileAsDataUrl = (file) => new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result);
    reader.onerror = () => reject(new Error('Could not read ' + file.name));
    reader.readAsDataURL(file);
  });

  const importDroppedFiles = async (files) => {
    const list = [...files];
    const importable = list.filter((file) =>
      file.type === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf') || file.type.startsWith('image/'));
    if (dropStatusTimeoutRef.current) {
      window.clearTimeout(dropStatusTimeoutRef.current);
      dropStatusTimeoutRef.current = null;
    }
    if (importable.length === 0) {
      setDropStatus('No images or PDFs in what was dropped.');
      return;
    }
    setIsImportingDrop(true);
    let pagesAdded = 0;
    let failed = 0;
    for (let i = 0; i < importable.length; i++) {
      const file = importable[i];
      setDropStatus(`Importing ${i + 1} of ${importable.length}: ${file.name}`);
      try {
        const isPdf = file.type === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf');
        if (isPdf) {
          const images = await convertPdfToImages(await file.arrayBuffer());
          const baseTitle = file.name.replace(/\.pdf$/i, '') || 'Imported PDF';
          for (let pageIndex = 0; pageIndex < images.length; pageIndex++) {
            await savePageToDb(images[pageIndex], `${baseTitle} - Page ${pageIndex + 1}`);
            pagesAdded++;
          }
        } else {
          const dataUrl = await readFileAsDataUrl(file);
          const title = file.name.includes('.') ? file.name.substring(0, file.name.lastIndexOf('.')) || 'Untitled page' : file.name;
          await savePageToDb(dataUrl, title);
          pagesAdded++;
        }
      } catch (error) {
        console.warn(`Could not import ${file.name}:`, error);
        failed++;
      }
    }
    setIsImportingDrop(false);
    const skipped = list.length - importable.length;
    const parts = [`Imported ${pagesAdded} page${pagesAdded === 1 ? '' : 's'}`];
    if (failed > 0) parts.push(`${failed} file${failed === 1 ? '' : 's'} could not be imported`);
    if (skipped > 0) parts.push(`${skipped} non-image file${skipped === 1 ? '' : 's'} skipped`);
    setDropStatus(parts.join('. ') + '.');
    dropStatusTimeoutRef.current = window.setTimeout(() => setDropStatus(''), 8000);
  };

  const handleDrop = (event) => {
    event.preventDefault();
    dragCounterRef.current = 0;
    setIsDraggingFiles(false);
    if (isImportingDrop) return;
    const files = event.dataTransfer?.files;
    if (files && files.length > 0) importDroppedFiles(files);
  };

  const filteredPages = (pages || []).filter(page => {
    if (activeCategoryId !== null) {
      const validIds = getCategoryAndChildren(activeCategoryId, categories || []);
      if (!validIds.includes(page.categoryId)) return false;
    }
    if (activeTags.length && (tagMatchMode === 'all' ? !activeTags.every(tag => (page.tags || []).includes(tag)) : !activeTags.some(tag => (page.tags || []).includes(tag)))) return false;
    if (searchQuery) {
      const q = searchQuery.toLowerCase();
      const matchTitle = (page.title || '').toLowerCase().includes(q);
      const matchTags = (page.tags || []).some(tag => tag.toLowerCase().includes(q));
      if (!matchTitle && !matchTags) return false;
    }
    if (showNoTagsOnly && (page.tags || []).length > 0) return false;
    return true;
  });

  const sortedPages = [...filteredPages].sort((a, b) => {
    if (sortBy === 'title-asc') return (a.title || '').localeCompare(b.title || '');
    if (sortBy === 'title-desc') return (b.title || '').localeCompare(a.title || '');
    if (sortBy === 'favorites') return Number(b.isFavorite || 0) - Number(a.isFavorite || 0);
    if (sortBy === 'oldest') return Number(a.updatedAt || 0) - Number(b.updatedAt || 0);
    return Number(b.updatedAt || 0) - Number(a.updatedAt || 0);
  });

  return (
    <div
      className="min-h-screen bg-[#F8F9FA] dark:bg-[#121212] font-sans pb-10 transition-colors duration-300"
      onDragEnter={handleDragEnter}
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
      onDrop={handleDrop}
    >
      {showTutorial && <Onboarding onComplete={completeTutorial} />}
      <AddPageModal isOpen={isModalOpen} onClose={() => setIsModalOpen(false)} />
      <SideMenu isOpen={isMenuOpen} onClose={() => setIsMenuOpen(false)} categories={categories || []} activeCategoryId={activeCategoryId} setActiveCategoryId={(id) => { setActiveCategoryId(id); setIsMenuOpen(false); }} onOpenSettings={() => { setIsMenuOpen(false); setIsSettingsOpen(true); }} />
      {categorizingPage && <CategorizeModal page={categorizingPage} categories={categories || []} onClose={() => setCategorizingPage(null)} />}
      {isSettingsOpen && <SettingsModal onClose={() => setIsSettingsOpen(false)} />}

      <header className="flex flex-col p-4 pt-[max(1rem,env(safe-area-inset-top))] bg-white dark:bg-gray-900 gap-4 sticky top-0 z-30">
        <div className="flex justify-between items-center">
          <div className="flex items-center gap-3">
            <button onClick={() => setIsMenuOpen(true)} className="p-2 -ml-2 text-gray-600 dark:text-gray-300" aria-label="Open collections"><Menu size={24} /></button>
            <div>
              <h1 className="text-xl font-bold text-gray-800 dark:text-white leading-tight">ColorNest</h1>
              <p className="text-xs text-gray-500 dark:text-gray-400">{activeTags.length ? `Tags: ${activeTags.join(', ')}` : activeCategoryId ? categories?.find(c => c.id === activeCategoryId)?.name : 'All Pages'}</p>
            </div>
          </div>
          <div className="flex items-center gap-1 text-gray-600 dark:text-gray-300">
            {installPrompt && <button onClick={installApp} className="p-2 text-purple-600" aria-label="Install ColorNest" title="Install ColorNest"><Download size={19} /></button>}
            {!window.matchMedia('(display-mode: fullscreen)').matches && <button onClick={toggleFullscreen} className="p-2" aria-label={isFullscreen ? 'Exit fullscreen' : 'Enter fullscreen'} title="Fullscreen">{isFullscreen ? <Minimize2 size={19} /> : <Maximize2 size={19} />}</button>}
            <button onClick={() => setIsSettingsOpen(true)} className="p-2" aria-label="Settings"><Settings size={19} /></button>
            <button onClick={toggleTheme} className="p-2" aria-label="Toggle theme">{isDarkMode ? <Sun size={19} className="text-yellow-400" /> : <Moon size={19} />}</button>
          </div>
        </div>
        <div className="relative">
          <Search size={18} className="absolute left-3 top-2.5 text-gray-400" />
          <input type="search" placeholder="Search pages and tags..." value={searchQuery} onChange={event => setSearchQuery(event.target.value)} className="w-full bg-gray-100 dark:bg-gray-800 text-gray-800 dark:text-white pl-10 pr-4 py-2 rounded-xl outline-none" />
        </div>
        <div className="flex gap-2 items-center">
          <button onClick={() => setIsTagBrowserOpen(value => !value)} className={`px-3 py-1.5 rounded-full text-xs font-bold flex items-center gap-1 ${isTagBrowserOpen ? 'bg-purple-600 text-white' : 'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-300'}`}>
            <Tags size={13} /> Browse tags ({tagCounts.length})
          </button>
          <button onClick={() => { setShowNoTagsOnly(value => !value); setActiveTags([]); }} className={`px-3 py-1.5 rounded-full text-xs font-bold ${showNoTagsOnly ? 'bg-purple-600 text-white' : 'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-300'}`}>
            Untagged
          </button>
          {(activeTags.length || activeCategoryId !== null || searchQuery || showNoTagsOnly) && (
            <button onClick={() => { setActiveTags([]); setActiveCategoryId(null); setSearchQuery(''); setShowNoTagsOnly(false); }} className="p-1.5 text-gray-500" aria-label="Clear filters"><X size={16} /></button>
          )}
        </div>
        {isTagBrowserOpen && (
          <section className="rounded-xl border border-gray-200 dark:border-gray-700 p-3 bg-gray-50 dark:bg-gray-800/80">
            <div className="flex justify-between items-center mb-2">
              <div><h2 className="text-sm font-bold text-gray-800 dark:text-white">All tags</h2><p className="text-[11px] text-gray-500 dark:text-gray-400">Select multiple tags to combine filters.</p></div>
              <div className="flex items-center gap-2"><button onClick={createTag} className="text-xs font-bold text-purple-600 dark:text-purple-300">+ New tag</button><button onClick={() => setActiveTags([])} className="text-xs text-gray-500">Clear</button></div>
            </div>
            {tagCounts.length ? (
              <div className="flex flex-wrap gap-2 max-h-40 overflow-y-auto">
                {tagCounts.map(([tag, count]) => (
                  <div key={tag} className="flex items-center rounded-lg overflow-hidden border border-gray-200 dark:border-gray-600">
                    <button onClick={() => { setActiveTags(current => current.includes(tag) ? current.filter(item => item !== tag) : [...current, tag]); setShowNoTagsOnly(false); }} className={`px-2.5 py-1.5 text-xs font-semibold ${activeTags.includes(tag) ? 'bg-purple-600 text-white' : 'bg-white dark:bg-gray-700 text-gray-700 dark:text-gray-200'}`}>
                      {tag} <span className="opacity-70">({count})</span>
                    </button>
                    <button onClick={() => renameTag(tag)} className="px-1.5 py-1.5 text-[10px] bg-gray-100 dark:bg-gray-800 text-gray-500" title={`Rename ${tag}`}>✎</button>
                    <button onClick={() => deleteTag(tag)} className="px-1.5 py-1.5 text-[10px] bg-gray-100 dark:bg-gray-800 text-red-500" title={`Delete ${tag}`}>×</button>
                  </div>
                ))}
              </div>
            ) : <p className="text-xs text-gray-500 dark:text-gray-400">Create a tag or add tags while editing a coloring page.</p>}
            <div className="flex items-center gap-2 mt-3 text-xs text-gray-600 dark:text-gray-300"><span>Match:</span><button onClick={() => setTagMatchMode('all')} className={`px-2 py-1 rounded ${tagMatchMode === 'all' ? 'bg-purple-600 text-white' : 'bg-white dark:bg-gray-700'}`}>All selected</button><button onClick={() => setTagMatchMode('any')} className={`px-2 py-1 rounded ${tagMatchMode === 'any' ? 'bg-purple-600 text-white' : 'bg-white dark:bg-gray-700'}`}>Any selected</button></div>
          </section>
        )}
        {scanProgress?.active && (
          <div className="w-full pt-1" role="status" aria-live="polite">
            <div className="flex items-center justify-between gap-3 mb-1.5">
              <span className="text-[11px] font-semibold text-purple-700 dark:text-purple-300 truncate">{scanProgress.message}</span>
              <span className="text-[11px] tabular-nums text-gray-500 dark:text-gray-400">{scanProgress.percent}%</span>
            </div>
            <div className="h-1.5 w-full rounded-full bg-purple-100 dark:bg-gray-700 overflow-hidden">
              <div className="h-full rounded-full bg-purple-600 transition-all duration-300" style={{ width: `${scanProgress.percent}%` }} />
            </div>
          </div>
        )}
        {dropStatus && (
          <p className="text-[11px] font-semibold text-purple-700 dark:text-purple-300 truncate" role="status" aria-live="polite">{dropStatus}</p>
        )}
      </header>

      <div className="px-4 mt-4">        <div className="flex items-center justify-between gap-2">
          <span className="text-xs text-gray-500 dark:text-gray-400">{sortedPages.length} pages</span>
          <select value={sortBy} onChange={event => setSortBy(event.target.value)} aria-label="Sort coloring pages" className="text-xs rounded-lg px-2 py-1.5 bg-gray-100 dark:bg-gray-800 text-gray-700 dark:text-gray-200 border border-gray-200 dark:border-gray-700">
            <option value="recent">Recently updated</option>
            <option value="oldest">Oldest updated</option>
            <option value="title-asc">Title A–Z</option>
            <option value="title-desc">Title Z–A</option>
            <option value="favorites">Favorites first</option>
          </select>
        </div>
      </div>
      <main className="px-4 mt-3 grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-4">
        {sortedPages.map(page => (
          <article key={page.id} className="bg-white dark:bg-gray-800 rounded-[24px] p-2 pb-3 shadow-sm border border-gray-100 dark:border-gray-700 flex flex-col relative">
            <div className="relative h-40 bg-white dark:bg-gray-900 rounded-[18px] overflow-hidden">
              <img src={galleryFullImages ? (page.imageUrl || page.thumbnailUrl) : (page.thumbnailUrl || page.imageUrl)} alt={page.title} loading="lazy" className="coloring-image w-full h-full object-cover" />
              {page.isPreviewOnly === 1 && <span className="absolute top-2 left-2 text-[9px] font-bold bg-black/60 text-white px-1.5 py-0.5 rounded">Preview</span>}
              <button onClick={async () => { await db.pages.update(page.id, { isFavorite: page.isFavorite ? 0 : 1, updatedAt: Date.now() }); }} className="absolute top-2 right-2 w-7 h-7 bg-white dark:bg-gray-700 rounded-full flex items-center justify-center shadow-sm" aria-label="Toggle favorite">
                <Heart size={13} fill={page.isFavorite === 1 ? '#ec4899' : 'none'} color={page.isFavorite === 1 ? '#ec4899' : '#9ca3af'} />
              </button>
              <button onClick={() => setCategorizingPage(page)} className="absolute inset-0 bg-black/40 flex flex-col items-center justify-center text-white font-bold opacity-0 hover:opacity-100 focus:opacity-100 transition-opacity">Edit Details</button>
            </div>
            <div className="px-1 mt-2 flex flex-col flex-grow">
              <div className="flex justify-between items-start">
                <h3 className="font-bold text-gray-800 dark:text-gray-100 text-sm leading-tight mb-1 line-clamp-2 pr-2">{page.title}</h3>
                <button onClick={() => deletePageWithDrive(page)} className="text-gray-300 dark:text-gray-600 hover:text-red-500" aria-label="Delete page"><Trash2 size={14} /></button>
              </div>
              {!!page.tags?.length && <div className="flex flex-wrap gap-1 mb-2">{page.tags.map(tag => <button key={tag} onClick={() => setActiveTags(current => current.includes(tag) ? current.filter(item => item !== tag) : [...current, tag])} className="text-[9px] bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300 px-1.5 py-0.5 rounded">{tag}</button>)}</div>}
              <button onClick={() => openExternal(page)} className="mt-auto w-full bg-purple-50 dark:bg-purple-900/30 text-purple-600 dark:text-purple-300 py-1.5 rounded-lg text-xs font-bold flex items-center justify-center gap-1">
                <ExternalLink size={12} /> Open Page
              </button>
            </div>
          </article>
        ))}
        {filteredPages.length === 0 && <div className="col-span-full text-center py-16 text-gray-500 dark:text-gray-400"><p className="font-semibold">No coloring pages found</p><p className="text-sm mt-1">Try clearing a filter or add a new page.</p></div>}
      </main>

      {viewingPage && (
        <div className="fixed inset-0 z-[250] bg-white dark:bg-gray-900 flex flex-col" role="dialog" aria-modal="true" aria-label={viewingPage.title}>
          <div className="flex items-center justify-between gap-3 p-3 text-gray-800 dark:text-white">
            <h2 className="font-semibold text-sm truncate">{viewingPage.title || 'Coloring page'}</h2>
            <div className="flex items-center gap-2 shrink-0">
              {viewingPage.isPreviewOnly === 1 && <span className="text-[10px] font-bold bg-gray-100 dark:bg-white/15 text-gray-800 dark:text-white px-1.5 py-0.5 rounded">Preview only</span>}
              {viewingPage.isPreviewOnly === 1 && downloadedPdfPages === null && (
                <button onClick={() => handleDownloadPreview(viewingPage)} disabled={isDownloadingPreview} className="p-2 rounded-full bg-gray-100 dark:bg-white/15 disabled:opacity-50" aria-label="Download this file"><Download size={20} /></button>
              )}
              <button onClick={() => handleSharePage(viewingPage)} className="p-2 rounded-full bg-gray-100 dark:bg-white/15" aria-label="Share page"><Share2 size={20} /></button>
              <button onClick={() => { setViewingPage(null); setShareStatus(''); setDownloadStatus(''); setDownloadedPdfPages(null); }} className="p-2 rounded-full bg-gray-100 dark:bg-white/15" aria-label="Close page"><X size={22} /></button>
            </div>
          </div>
          {shareStatus && <p className="px-3 pb-2 text-xs text-gray-500 dark:text-gray-300" role="status">{shareStatus}</p>}
          {downloadStatus && <p className="px-3 pb-2 text-xs text-gray-500 dark:text-gray-300" role="status">{downloadStatus}</p>}
          <div className="flex-1 min-h-0 flex items-center justify-center overflow-auto p-2">
            {downloadedPdfPages !== null ? (
              <div className="text-center px-6">
                <CheckCircle2 size={40} className="mx-auto text-green-500 mb-3" />
                <p className="font-bold text-gray-800 dark:text-white">Added {downloadedPdfPages} page{downloadedPdfPages === 1 ? '' : 's'} from this PDF</p>
                <p className="text-xs text-gray-600 dark:text-gray-400 mt-1">They're in your library now, in the same category as the preview.</p>
                <button onClick={() => { setViewingPage(null); setShareStatus(''); setDownloadStatus(''); setDownloadedPdfPages(null); }} className="mt-4 bg-purple-600 text-white px-6 py-2 rounded-lg font-bold">Done</button>
              </div>
            ) : viewingPage.isPreviewOnly === 1 ? (
              isTempPreviewLoading ? (
                <p className="text-sm text-gray-500 dark:text-gray-400">Loading preview…</p>
              ) : (tempPreviewUrl || viewingPage.thumbnailUrl) ? (
                <img
                  src={tempPreviewUrl || viewingPage.thumbnailUrl}
                  alt={viewingPage.title || 'Coloring page'}
                  className="coloring-image max-w-full max-h-full object-contain"
                  onError={(event) => {
                    if (tempPreviewUrl && viewingPage.thumbnailUrl && event.currentTarget.src !== viewingPage.thumbnailUrl) {
                      event.currentTarget.src = viewingPage.thumbnailUrl;
                    } else {
                      event.currentTarget.alt = 'This preview could not be loaded.';
                      event.currentTarget.style.display = 'none';
                    }
                  }}
                />
              ) : (
                <p className="text-sm text-gray-500 dark:text-gray-400">No preview available for this file.</p>
              )
            ) : (
              <img
                src={viewingPage.imageUrl}
                alt={viewingPage.title || 'Coloring page'}
                className="coloring-image max-w-full max-h-full object-contain"
                onError={(event) => { event.currentTarget.alt = 'This image could not be loaded.'; event.currentTarget.style.display = 'none'; }}
              />
            )}
          </div>
          <p className="px-3 pb-3 pt-1 text-center text-[11px] text-gray-500 dark:text-gray-400">Background shown for viewing only — it won’t be in the actual file.</p>
        </div>
      )}

      {isDraggingFiles && (
        <div className="fixed inset-0 z-[400] bg-purple-600/20 flex items-center justify-center pointer-events-none p-6">
          <div className="w-full h-full border-4 border-dashed border-purple-600 rounded-3xl bg-white/80 dark:bg-gray-900/80 flex flex-col items-center justify-center gap-3">
            <Plus size={48} className="text-purple-600 dark:text-purple-300" />
            <p className="text-xl font-bold text-gray-800 dark:text-white">Drop to import</p>
            <p className="text-sm text-gray-600 dark:text-gray-400">Images and PDFs become coloring pages</p>
          </div>
        </div>
      )}

      <button onClick={() => setIsModalOpen(true)} className="fixed bottom-[max(1.5rem,env(safe-area-inset-bottom))] right-6 w-14 h-14 bg-purple-600 text-white rounded-full flex items-center justify-center shadow-lg hover:scale-105 transition-transform z-40" aria-label="Add coloring pages">
        <Plus size={28} />
      </button>
    </div>
  );
}

export default App;


