import React, { useState, useEffect, useMemo } from 'react';
import { Search, Heart, Plus, Trash2, Moon, Sun, Menu, Settings, ExternalLink, Tags, Maximize2, Minimize2, Download, X } from 'lucide-react';
import { useLiveQuery } from 'dexie-react-hooks';
import { db } from './db';
import AddPageModal from './AddPageModal';
import SideMenu from './SideMenu';
import CategorizeModal from './CategorizeModal';
import SettingsModal from './SettingsModal';
import Onboarding from './Onboarding';
import { Capacitor } from '@capacitor/core';
import { Share } from '@capacitor/share';
import { Filesystem, Directory } from '@capacitor/filesystem';
import { triggerBackgroundSync, deletePageWithDrive } from './driveSync';

function App() {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);
  const [categorizingPage, setCategorizingPage] = useState(null);
  const [isDarkMode, setIsDarkMode] = useState(false);
  const [activeCategoryId, setActiveCategoryId] = useState(null);
  const [searchQuery, setSearchQuery] = useState("");
  const [showNoTagsOnly, setShowNoTagsOnly] = useState(false);
  const [activeTag, setActiveTag] = useState(null);
  const [isTagBrowserOpen, setIsTagBrowserOpen] = useState(false);
  const [showTutorial, setShowTutorial] = useState(false);
  const [installPrompt, setInstallPrompt] = useState(null);
  const [isFullscreen, setIsFullscreen] = useState(Boolean(document.fullscreenElement));

  const pages = useLiveQuery(() => db.pages.toArray());
  const categories = useLiveQuery(() => db.categories.toArray());

  useEffect(() => {
    if (localStorage.getItem('theme') === 'dark') {
      setIsDarkMode(true);
      document.documentElement.classList.add('dark');
    }
    db.settings.get('hasSeenTutorial').then(setting => {
      if (!setting || !setting.value) setShowTutorial(true);
    });
    triggerBackgroundSync();

    const captureInstall = (event) => {
      event.preventDefault();
      setInstallPrompt(event);
    };
    const updateFullscreen = () => setIsFullscreen(Boolean(document.fullscreenElement));
    window.addEventListener('beforeinstallprompt', captureInstall);
    document.addEventListener('fullscreenchange', updateFullscreen);
    return () => {
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

  const openExternal = async (base64Image) => {
    if (!Capacitor.isNativePlatform()) {
      const link = document.createElement('a');
      link.href = base64Image;
      link.target = '_blank';
      link.rel = 'noopener';
      link.click();
      return;
    }
    try {
      const base64Data = base64Image.split(',')[1];
      const fileName = `coloring_page_${Date.now()}.jpg`;
      const result = await Filesystem.writeFile({ path: fileName, data: base64Data, directory: Directory.Cache });
      await Share.share({ title: 'Open Coloring Page', url: result.uri, dialogTitle: 'Choose an app to open this image' });
    } catch (error) {
      alert('Error opening file: ' + error.message);
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
    const counts = new Map();
    (pages || []).forEach(page => (page.tags || []).forEach(tag => {
      const normalized = String(tag).trim();
      if (normalized) counts.set(normalized, (counts.get(normalized) || 0) + 1);
    }));
    return [...counts.entries()].sort((a, b) => a[0].localeCompare(b[0]));
  }, [pages]);

  const filteredPages = (pages || []).filter(page => {
    if (activeCategoryId !== null) {
      const validIds = getCategoryAndChildren(activeCategoryId, categories || []);
      if (!validIds.includes(page.categoryId)) return false;
    }
    if (activeTag && !(page.tags || []).includes(activeTag)) return false;
    if (searchQuery) {
      const q = searchQuery.toLowerCase();
      const matchTitle = (page.title || '').toLowerCase().includes(q);
      const matchTags = (page.tags || []).some(tag => tag.toLowerCase().includes(q));
      if (!matchTitle && !matchTags) return false;
    }
    if (showNoTagsOnly && (page.tags || []).length > 0) return false;
    return true;
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
              <p className="text-xs text-gray-500 dark:text-gray-400">{activeTag ? `Tag: ${activeTag}` : activeCategoryId ? categories?.find(c => c.id === activeCategoryId)?.name : 'All Pages'}</p>
            </div>
          </div>
          <div className="flex items-center gap-1 text-gray-600 dark:text-gray-300">
            {installPrompt && <button onClick={installApp} className="p-2 text-purple-600" aria-label="Install ColorNest" title="Install ColorNest"><Download size={19} /></button>}
            <button onClick={toggleFullscreen} className="p-2" aria-label={isFullscreen ? 'Exit fullscreen' : 'Enter fullscreen'} title="Fullscreen">{isFullscreen ? <Minimize2 size={19} /> : <Maximize2 size={19} />}</button>
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
          <button onClick={() => { setShowNoTagsOnly(value => !value); setActiveTag(null); }} className={`px-3 py-1.5 rounded-full text-xs font-bold ${showNoTagsOnly ? 'bg-purple-600 text-white' : 'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-300'}`}>
            Untagged
          </button>
          {(activeTag || activeCategoryId !== null || searchQuery || showNoTagsOnly) && (
            <button onClick={() => { setActiveTag(null); setActiveCategoryId(null); setSearchQuery(''); setShowNoTagsOnly(false); }} className="p-1.5 text-gray-500" aria-label="Clear filters"><X size={16} /></button>
          )}
        </div>
        {isTagBrowserOpen && (
          <section className="rounded-xl border border-gray-200 dark:border-gray-700 p-3 bg-gray-50 dark:bg-gray-800/80">
            <div className="flex justify-between items-center mb-2">
              <h2 className="text-sm font-bold text-gray-800 dark:text-white">All tags</h2>
              <button onClick={() => setActiveTag(null)} className="text-xs text-purple-600 dark:text-purple-300">Clear tag</button>
            </div>
            {tagCounts.length ? (
              <div className="flex flex-wrap gap-2 max-h-40 overflow-y-auto">
                {tagCounts.map(([tag, count]) => (
                  <button key={tag} onClick={() => { setActiveTag(activeTag === tag ? null : tag); setShowNoTagsOnly(false); }} className={`px-2.5 py-1.5 rounded-lg text-xs font-semibold ${activeTag === tag ? 'bg-purple-600 text-white' : 'bg-white dark:bg-gray-700 text-gray-700 dark:text-gray-200 border border-gray-200 dark:border-gray-600'}`}>
                    {tag} <span className="opacity-70">({count})</span>
                  </button>
                ))}
              </div>
            ) : <p className="text-xs text-gray-500 dark:text-gray-400">Tags you add to pages will appear here.</p>}
          </section>
        )}
      </header>

      <main className="px-4 mt-4 grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-4">
        {filteredPages.map(page => (
          <article key={page.id} className="bg-white dark:bg-gray-800 rounded-[24px] p-2 pb-3 shadow-sm border border-gray-100 dark:border-gray-700 flex flex-col relative">
            <div className="relative h-40 bg-gray-50 dark:bg-gray-900 rounded-[18px] overflow-hidden">
              <img src={page.imageUrl} alt={page.title} loading="lazy" className="coloring-image w-full h-full object-cover" />
              <button onClick={() => db.pages.update(page.id, { isFavorite: page.isFavorite ? 0 : 1 })} className="absolute top-2 right-2 w-7 h-7 bg-white dark:bg-gray-700 rounded-full flex items-center justify-center shadow-sm" aria-label="Toggle favorite">
                <Heart size={13} fill={page.isFavorite === 1 ? '#ec4899' : 'none'} color={page.isFavorite === 1 ? '#ec4899' : '#9ca3af'} />
              </button>
              {page.isUncategorized === 1 && <button onClick={() => setCategorizingPage(page)} className="absolute inset-0 bg-black/40 flex flex-col items-center justify-center text-white font-bold opacity-0 hover:opacity-100 transition-opacity">Edit Details</button>}
            </div>
            <div className="px-1 mt-2 flex flex-col flex-grow">
              <div className="flex justify-between items-start">
                <h3 className="font-bold text-gray-800 dark:text-gray-100 text-sm leading-tight mb-1 line-clamp-2 pr-2">{page.title}</h3>
                <button onClick={() => deletePageWithDrive(page)} className="text-gray-300 dark:text-gray-600 hover:text-red-500" aria-label="Delete page"><Trash2 size={14} /></button>
              </div>
              {!!page.tags?.length && <div className="flex flex-wrap gap-1 mb-2">{page.tags.map(tag => <button key={tag} onClick={() => setActiveTag(tag)} className="text-[9px] bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300 px-1.5 py-0.5 rounded">{tag}</button>)}</div>}
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
