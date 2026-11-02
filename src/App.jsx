import React, { useState, useEffect } from 'react';
import { Search, Heart, Plus, Loader2, AlertCircle, Trash2, Moon, Sun, Menu, Settings, ExternalLink } from 'lucide-react';
import { useLiveQuery } from 'dexie-react-hooks';
import { db } from './db';
import AddPageModal from './AddPageModal';
import SideMenu from './SideMenu';
import CategorizeModal from './CategorizeModal';
import SettingsModal from './SettingsModal';
import { startBackgroundWorker } from './backgroundWorker';
import { Capacitor } from '@capacitor/core';
import { Share } from '@capacitor/share';
import { Filesystem, Directory } from '@capacitor/filesystem';

function App() {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);
  const [categorizingPage, setCategorizingPage] = useState(null);
  const [isDarkMode, setIsDarkMode] = useState(false);
  const [activeCategoryId, setActiveCategoryId] = useState(null);
  const [searchQuery, setSearchQuery] = useState("");

  const pages = useLiveQuery(() => db.pages.toArray());
  const categories = useLiveQuery(() => db.categories.toArray());

  useEffect(() => {
    startBackgroundWorker();
    if (localStorage.getItem('theme') === 'dark') {
      setIsDarkMode(true); document.documentElement.classList.add('dark');
    }
  }, []);

  const toggleTheme = () => {
    const newTheme = !isDarkMode;
    setIsDarkMode(newTheme);
    document.documentElement.classList.toggle('dark');
    localStorage.setItem('theme', newTheme ? 'dark' : 'light');
  };

  const openExternal = async (base64Image) => {
    if (!Capacitor.isNativePlatform()) {
      alert("This feature works in the installed APK!");
      return;
    }
    try {
      // Save base64 to a temporary file so Android can read it
      const base64Data = base64Image.split(',')[1];
      const fileName = `coloring_page_${Date.now()}.jpg`;
      
      const result = await Filesystem.writeFile({
        path: fileName,
        data: base64Data,
        directory: Directory.Cache
      });

      // Open the Android Share/Open With menu
      await Share.share({
        title: 'Open Coloring Page',
        url: result.uri,
        dialogTitle: 'Open with...'
      });
    } catch (e) {
      alert("Error opening file: " + e.message);
    }
  };

  const getCategoryAndChildren = (catId, allCats) => {
    let ids = [catId];
    const children = allCats.filter(c => c.parentId === catId);
    children.forEach(child => { ids = [...ids, ...getCategoryAndChildren(child.id, allCats)]; });
    return ids;
  };

  const filteredPages = (pages || []).filter(page => {
    // 1. Category Filter
    if (activeCategoryId !== null) {
      const validIds = getCategoryAndChildren(activeCategoryId, categories || []);
      if (!validIds.includes(page.categoryId)) return false;
    }
    // 2. Search Filter (Title or Tags)
    if (searchQuery) {
      const q = searchQuery.toLowerCase();
      const matchTitle = page.title.toLowerCase().includes(q);
      const matchTags = page.tags && page.tags.some(t => t.toLowerCase().includes(q));
      if (!matchTitle && !matchTags) return false;
    }
    return true;
  });

  return (
    <div className="min-h-screen bg-[#F8F9FA] dark:bg-[#121212] font-sans pb-10 transition-colors duration-300">
      
      <AddPageModal isOpen={isModalOpen} onClose={() => setIsModalOpen(false)} />
      <SideMenu isOpen={isMenuOpen} onClose={() => setIsMenuOpen(false)} categories={categories || []} activeCategoryId={activeCategoryId} setActiveCategoryId={(id) => { setActiveCategoryId(id); setIsMenuOpen(false); }} onOpenSettings={() => { setIsMenuOpen(false); setIsSettingsOpen(true); }} />
      {categorizingPage && <CategorizeModal page={categorizingPage} categories={categories || []} onClose={() => setCategorizingPage(null)} />}
      {isSettingsOpen && <SettingsModal onClose={() => setIsSettingsOpen(false)} />}

      <header className="flex flex-col p-4 pt-8 bg-white dark:bg-gray-900 gap-4">
        <div className="flex justify-between items-center">
          <div className="flex items-center gap-3">
            <button onClick={() => setIsMenuOpen(true)} className="p-2 -ml-2 text-gray-600 dark:text-gray-300"><Menu size={24} /></button>
            <div>
              <h1 className="text-xl font-bold text-gray-800 dark:text-white leading-tight">ColorNest</h1>
              <p className="text-xs text-gray-500 dark:text-gray-400">{activeCategoryId ? categories?.find(c => c.id === activeCategoryId)?.name : "All Pages"}</p>
            </div>
          </div>
          <div className="flex items-center gap-2 text-gray-600 dark:text-gray-300">
            <button onClick={() => setIsSettingsOpen(true)} className="p-2"><Settings size={20} /></button>
            <button onClick={toggleTheme} className="p-2">{isDarkMode ? <Sun size={20} className="text-yellow-400" /> : <Moon size={20} />}</button>
          </div>
        </div>
        {/* Search Bar */}
        <div className="relative">
          <Search size={18} className="absolute left-3 top-2.5 text-gray-400" />
          <input type="text" placeholder="Search titles or tags..." value={searchQuery} onChange={(e) => setSearchQuery(e.target.value)} className="w-full bg-gray-100 dark:bg-gray-800 text-gray-800 dark:text-white pl-10 pr-4 py-2 rounded-xl outline-none" />
        </div>
      </header>

      <div className="px-4 mt-4 grid grid-cols-2 gap-4">
        {filteredPages.map((page) => (
          <div key={page.id} className="bg-white dark:bg-gray-800 rounded-[24px] p-2 pb-3 shadow-sm border border-gray-100 dark:border-gray-700 flex flex-col relative transition-colors duration-300">
            <div className="relative h-40 bg-gray-50 dark:bg-gray-900 rounded-[18px] overflow-hidden">
              <img src={page.imageUrl} alt={page.title} className="coloring-image w-full h-full object-cover" />
              
              <button onClick={() => db.pages.update(page.id, { isFavorite: page.isFavorite ? 0 : 1 })} className="absolute top-2 right-2 w-6 h-6 bg-white dark:bg-gray-700 rounded-full flex items-center justify-center shadow-sm">
                <Heart size={12} fill={page.isFavorite === 1 ? "#ec4899" : "none"} color={page.isFavorite === 1 ? "#ec4899" : "#9ca3af"} />
              </button>

              {page.isUncategorized === 1 && (
                <button onClick={() => setCategorizingPage(page)} className="absolute inset-0 bg-black/40 flex flex-col items-center justify-center text-white font-bold opacity-0 hover:opacity-100 transition-opacity">
                  Edit Details
                </button>
              )}
            </div>

            <div className="px-1 mt-2 flex flex-col flex-grow">
              <div className="flex justify-between items-start">
                <h3 className="font-bold text-gray-800 dark:text-gray-100 text-sm leading-tight mb-1 line-clamp-2 pr-2">{page.title}</h3>
                <button onClick={() => db.pages.delete(page.id)} className="text-gray-300 dark:text-gray-600 hover:text-red-500"><Trash2 size={14} /></button>
              </div>
              
              {/* Tags Display */}
              {page.tags && page.tags.length > 0 && (
                <div className="flex flex-wrap gap-1 mb-2">
                  {page.tags.map(tag => <span key={tag} className="text-[9px] bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300 px-1.5 py-0.5 rounded">{tag}</span>)}
                </div>
              )}

              {/* Open Button */}
              <button onClick={() => openExternal(page.imageUrl)} className="mt-auto w-full bg-purple-50 dark:bg-purple-900/30 text-purple-600 dark:text-purple-300 py-1.5 rounded-lg text-xs font-bold flex items-center justify-center gap-1">
                <ExternalLink size={12} /> Open Page
              </button>
              
              {page.aiStatus && (
                <div className={`mt-2 text-[10px] px-2 py-1 rounded-md flex items-center gap-1 font-bold ${page.aiStatus.includes("Error") ? 'bg-red-100 text-red-700' : 'bg-purple-100 text-purple-700 dark:bg-purple-900 dark:text-purple-200'}`}>
                  {page.aiStatus.includes("Error") ? <AlertCircle size={10} /> : <Loader2 size={10} className="animate-spin" />}
                  {page.aiStatus}
                </div>
              )}
            </div>
          </div>
        ))}
      </div>

      <button onClick={() => setIsModalOpen(true)} className="fixed bottom-6 right-6 w-14 h-14 bg-purple-600 text-white rounded-full flex items-center justify-center shadow-lg shadow-purple-300 dark:shadow-none hover:scale-105 transition-transform z-50">
        <Plus size={28} />
      </button>
    </div>
  );
}

export default App;
