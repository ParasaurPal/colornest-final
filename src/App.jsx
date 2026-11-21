import React, { useState, useEffect, useMemo } from 'react';
import { Search, Heart, Plus, Trash2, Moon, Sun, Menu, Settings, ExternalLink, Tags, Maximize2, Minimize2, Download, X } from 'lucide-react';
import { useLiveQuery } from 'dexie-react-hooks';
import { db } from './db';
import AddPageModal from './AddPageModal';
import SideMenu from './SideMenu';
import CategorizeModal from './CategorizeModal';
import SettingsModal from './SettingsModal';
import Onboarding from './Onboarding';
import { triggerBackgroundSync, deletePageWithDrive, subscribeScanProgress } from './driveSync';

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
  const [scanProgress, setScanProgress] = useState(null);

  const pages = useLiveQuery(() => db.pages.toArray());
  const categories = useLiveQuery(() => db.categories.toArray());
  const savedTags = useLiveQuery(() => db.tags.toArray());

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
    const retryDriveSync = () => { if (navigator.onLine) triggerBackgroundSync(true); };
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

  const openExternal = (imageUrl) => {
    const opened = window.open(imageUrl, '_blank', 'noopener,noreferrer');
    if (!opened) {
      const link = document.createElement('a');
      link.href = imageUrl;
      link.download = 'coloring-page';
      link.click();
    }
  };

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
    triggerBackgroundSync(true);
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
    triggerBackgroundSync(true);
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
    <div className="min-h-screen bg-[#F8F9FA] dark:bg-[#121212] font-sans pb-10 transition-colors duration-300">
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
            <div className="relative h-40 bg-gray-50 dark:bg-gray-900 rounded-[18px] overflow-hidden">
              <img src={page.imageUrl} alt={page.title} loading="lazy" className="coloring-image w-full h-full object-cover" />
              <button onClick={async () => { await db.pages.update(page.id, { isFavorite: page.isFavorite ? 0 : 1, updatedAt: Date.now() }); triggerBackgroundSync(true); }} className="absolute top-2 right-2 w-7 h-7 bg-white dark:bg-gray-700 rounded-full flex items-center justify-center shadow-sm" aria-label="Toggle favorite">
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
              <button onClick={() => openExternal(page.imageUrl)} className="mt-auto w-full bg-purple-50 dark:bg-purple-900/30 text-purple-600 dark:text-purple-300 py-1.5 rounded-lg text-xs font-bold flex items-center justify-center gap-1">
                <ExternalLink size={12} /> Open Page
              </button>
            </div>
          </article>
        ))}
        {filteredPages.length === 0 && <div className="col-span-full text-center py-16 text-gray-500 dark:text-gray-400"><p className="font-semibold">No coloring pages found</p><p className="text-sm mt-1">Try clearing a filter or add a new page.</p></div>}
      </main>

      <button onClick={() => setIsModalOpen(true)} className="fixed bottom-[max(1.5rem,env(safe-area-inset-bottom))] right-6 w-14 h-14 bg-purple-600 text-white rounded-full flex items-center justify-center shadow-lg hover:scale-105 transition-transform z-40" aria-label="Add coloring pages">
        <Plus size={28} />
      </button>
    </div>
  );
}

export default App;
