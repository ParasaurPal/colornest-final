import React, { useState, useEffect } from 'react';
import { Search, Heart, Plus, Loader2, AlertCircle, Trash2, Brain } from 'lucide-react';
import { useLiveQuery } from 'dexie-react-hooks';
import { db } from './db';
import AddPageModal from './AddPageModal';
import { startBackgroundWorker } from './backgroundWorker';
import { learnPattern } from './localBrain';

function App() {
  const [activeTab, setActiveTab] = useState('All Pages');
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingPage, setEditingPage] = useState(null);

  useEffect(() => {
    startBackgroundWorker();
  }, []);

  const pages = useLiveQuery(() => db.pages.toArray());

  const handleDelete = async (id) => {
    if (window.confirm("Delete this page?")) await db.pages.delete(id);
  };

  const handleEditCategory = async (id, newCategory, imageUrl) => {
    // 1. Update the page
    await db.pages.update(id, { category: newCategory, isUncategorized: 0, aiStatus: null });
    
    // 2. TEACH THE LOCAL AI
    await learnPattern(imageUrl, newCategory);
    
    setEditingPage(null);
  };

  const filteredPages = (pages || []).filter(page => {
    if (activeTab === 'In Progress') return !page.isCompleted;
    if (activeTab === 'Completed') return page.isCompleted;
    if (activeTab === 'Favorites') return page.isFavorite;
    if (activeTab === 'Uncategorized') return page.isUncategorized === 1;
    return true;
  });

  return (
    <div className="min-h-screen bg-[#F8F9FA] font-sans pb-10">
      
      <AddPageModal isOpen={isModalOpen} onClose={() => setIsModalOpen(false)} />

      {editingPage && (
        <div className="fixed inset-0 bg-black/60 flex justify-center items-center z-[100] p-4">
          <div className="bg-white rounded-2xl p-6 w-80">
            <h3 className="font-bold text-lg mb-4 text-center">Train Local AI</h3>
            <p className="text-xs text-gray-500 mb-4 text-center">The app will remember this pattern for next time.</p>
            <select 
              className="w-full p-2 border rounded-lg mb-4"
              onChange={(e) => handleEditCategory(editingPage.id, e.target.value, editingPage.imageUrl)}
              defaultValue=""
            >
              <option value="" disabled>Select a category...</option>
              <option>Animals</option>
              <option>Mandalas & Patterns</option>
              <option>Fantasy & Mythical</option>
              <option>Botanical & Nature</option>
              <option>Architecture & Places</option>
            </select>
            <button onClick={() => setEditingPage(null)} className="w-full bg-gray-200 py-2 rounded-lg font-bold">Cancel</button>
          </div>
        </div>
      )}

      <header className="flex justify-between items-center p-4 pt-8 bg-white">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 bg-purple-100 rounded-full flex items-center justify-center text-purple-600">🎨</div>
          <div>
            <h1 className="text-xl font-bold text-gray-800 leading-tight">ColorNest</h1>
            <p className="text-xs text-gray-500">{pages ? pages.length : 0} coloring pages</p>
          </div>
        </div>
        <Search size={20} className="text-gray-600" />
      </header>

      <div className="mt-2 px-4 border-b border-gray-200 flex gap-6 overflow-x-auto">
        {['All Pages', 'Uncategorized', 'In Progress', 'Completed', 'Favorites'].map(tab => (
          <button key={tab} onClick={() => setActiveTab(tab)} className={`pb-3 whitespace-nowrap transition-colors ${activeTab === tab ? 'text-purple-600 font-bold border-b-2 border-purple-600' : 'text-gray-400 font-medium'}`}>{tab}</button>
        ))}
      </div>

      <div className="px-4 mt-4 grid grid-cols-2 gap-4">
        {filteredPages.map((page) => (
          <div key={page.id} className="bg-white rounded-[24px] p-2 pb-3 shadow-sm border border-gray-100 flex flex-col relative">
            
            <div className="relative h-40 bg-gray-50 rounded-[18px] overflow-hidden">
              <img src={page.imageUrl} alt={page.title} className="w-full h-full object-cover" />
              <span className={`absolute top-2 left-2 ${page.categoryColor} text-white text-[10px] px-2 py-1 rounded-full font-bold tracking-wide`}>{page.category}</span>
              
              <button onClick={() => db.pages.update(page.id, { isFavorite: page.isFavorite ? 0 : 1 })} className="absolute top-2 right-2 w-6 h-6 bg-white rounded-full flex items-center justify-center shadow-sm">
                <Heart size={12} fill={page.isFavorite === 1 ? "#ec4899" : "none"} color={page.isFavorite === 1 ? "#ec4899" : "#9ca3af"} />
              </button>

              {page.isUncategorized === 1 && (
                <button 
                  onClick={() => setEditingPage(page)}
                  className="absolute inset-0 bg-black/40 flex flex-col items-center justify-center text-white font-bold opacity-0 hover:opacity-100 transition-opacity"
                >
                  <Brain size={24} className="mb-2" />
                  Train & Categorize
                </button>
              )}
            </div>

            <div className="px-1 mt-2 flex flex-col flex-grow">
              <div className="flex justify-between items-start">
                <h3 className="font-bold text-gray-800 text-sm leading-tight mb-2 line-clamp-2 pr-2">{page.title}</h3>
                <button onClick={() => handleDelete(page.id)} className="text-gray-300 hover:text-red-500">
                  <Trash2 size={14} />
                </button>
              </div>
              
              {page.aiStatus && (
                <div className={`mt-1 text-[10px] px-2 py-1 rounded-md flex items-center gap-1 font-bold ${page.aiStatus.includes("Error") || page.aiStatus.includes("Timeout") ? 'bg-red-100 text-red-700' : 'bg-purple-100 text-purple-700'}`}>
                  {page.aiStatus.includes("Error") || page.aiStatus.includes("Timeout") ? <AlertCircle size={10} /> : <Loader2 size={10} className="animate-spin" />}
                  {page.aiStatus}
                </div>
              )}
            </div>
          </div>
        ))}
      </div>

      <button onClick={() => setIsModalOpen(true)} className="fixed bottom-6 right-6 w-14 h-14 bg-purple-600 text-white rounded-full flex items-center justify-center shadow-lg shadow-purple-300 hover:scale-105 transition-transform z-50">
        <Plus size={28} />
      </button>
    </div>
  );
}

export default App;
