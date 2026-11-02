import React, { useState } from 'react';
import { Search, SlidersHorizontal, BarChart2, Plus, Heart, Clock, CheckCircle2 } from 'lucide-react';

// MOCK DATA
const mockPages = [
  { id: 1, title: "Playful Garden Bunny", category: "Animals", categoryColor: "bg-pink-400", complexity: "Beginner", stars: 1, time: "20m", tool: "Crayons", isFavorite: true, isCompleted: false, progress: 0, img: "https://placehold.co/400x400/fdf2f8/ec4899?text=Bunny" },
  { id: 2, title: "Sunburst Kaleidoscope", category: "Mandalas", categoryColor: "bg-purple-400", complexity: "Advanced", stars: 4, time: "150m", tool: "Dual Tip Pens", isFavorite: true, isCompleted: false, progress: 20, img: "https://placehold.co/400x400/f3e8ff/9333ea?text=Mandala" },
  { id: 3, title: "Enchanted Dragon's Peak", category: "Fantasy", categoryColor: "bg-rose-400", complexity: "Advanced", stars: 4, time: "180m", tool: "Watercolor Pencils", isFavorite: false, isCompleted: true, progress: 100, img: "https://placehold.co/400x400/fff1f2/e11d48?text=Dragon" },
  { id: 4, title: "Sacred Lotus", category: "Mandalas", categoryColor: "bg-purple-400", complexity: "Expert", stars: 5, time: "240m", tool: "Gel Pens", isFavorite: false, isCompleted: false, progress: 0, img: "https://placehold.co/400x400/f3e8ff/9333ea?text=Lotus" },
];

function App() {
  // State for the active tab and active category filter
  const [activeTab, setActiveTab] = useState('All Pages');
  const [activeCategory, setActiveCategory] = useState('All Subjects');

  // Logic to filter the mock data based on what is clicked
  const filteredPages = mockPages.filter(page => {
    // 1. Check Tab Filter
    let matchesTab = true;
    if (activeTab === 'In Progress') matchesTab = !page.isCompleted;
    if (activeTab === 'Completed') matchesTab = page.isCompleted;
    if (activeTab === 'Favorites') matchesTab = page.isFavorite;

    // 2. Check Category Filter
    let matchesCategory = activeCategory === 'All Subjects' || page.category === activeCategory;

    return matchesTab && matchesCategory;
  });

  return (
    <div className="min-h-screen bg-[#F8F9FA] font-sans pb-10">
      
      {/* 1. HEADER */}
      <header className="flex justify-between items-center p-4 pt-8 bg-white">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 bg-purple-100 rounded-full flex items-center justify-center text-purple-600">
            🎨
          </div>
          <div>
            <h1 className="text-xl font-bold text-gray-800 leading-tight">ColorNest</h1>
            <p className="text-xs text-gray-500">7 coloring pages</p>
          </div>
        </div>
        <div className="flex gap-4 text-gray-600">
          <Search size={20} />
          <SlidersHorizontal size={20} />
          <BarChart2 size={20} />
        </div>
      </header>

      {/* 2. STATS BAR */}
      <div className="mx-4 mt-4 bg-white rounded-2xl p-4 shadow-sm flex justify-between text-center">
        <div>
          <div className="font-bold text-purple-600 text-lg">7</div>
          <div className="text-[10px] text-gray-400 font-bold uppercase mt-1 tracking-wide">Total Pages</div>
        </div>
        <div>
          <div className="font-bold text-purple-600 text-lg">2</div>
          <div className="text-[10px] text-gray-400 font-bold uppercase mt-1 tracking-wide">In Progress</div>
        </div>
        <div>
          <div className="font-bold text-purple-600 text-lg">1</div>
          <div className="text-[10px] text-gray-400 font-bold uppercase mt-1 tracking-wide">Completed</div>
        </div>
        <div>
          <div className="font-bold text-purple-600 text-lg">3.1★</div>
          <div className="text-[10px] text-gray-400 font-bold uppercase mt-1 tracking-wide">Avg Level</div>
        </div>
      </div>

      {/* 3. SUBJECT FILTERS (Now clickable!) */}
      <div className="mt-6 px-4">
        <div className="flex gap-2 overflow-x-auto pb-2">
          <button 
            onClick={() => setActiveCategory('All Subjects')}
            className={`px-4 py-2 rounded-full text-sm font-medium whitespace-nowrap transition-colors ${activeCategory === 'All Subjects' ? 'bg-purple-600 text-white' : 'bg-white border border-gray-200 text-gray-700'}`}
          >
            All Subjects
          </button>
          <button 
            onClick={() => setActiveCategory('Animals')}
            className={`px-4 py-2 rounded-full text-sm font-medium whitespace-nowrap flex items-center gap-2 transition-colors ${activeCategory === 'Animals' ? 'bg-purple-600 text-white' : 'bg-white border border-gray-200 text-gray-700'}`}
          >
            🐾 Animals
          </button>
          <button 
            onClick={() => setActiveCategory('Mandalas')}
            className={`px-4 py-2 rounded-full text-sm font-medium whitespace-nowrap flex items-center gap-2 transition-colors ${activeCategory === 'Mandalas' ? 'bg-purple-600 text-white' : 'bg-white border border-gray-200 text-gray-700'}`}
          >
            🌀 Mandalas
          </button>
        </div>
      </div>

      {/* 4. TABS (Now clickable!) */}
      <div className="mt-2 px-4 border-b border-gray-200 flex gap-6">
        {['All Pages', 'In Progress', 'Completed', 'Favorites'].map(tab => (
          <button 
            key={tab}
            onClick={() => setActiveTab(tab)}
            className={`pb-3 transition-colors ${activeTab === tab ? 'text-purple-600 font-bold border-b-2 border-purple-600' : 'text-gray-400 font-medium'}`}
          >
            {tab}
          </button>
        ))}
      </div>

      {/* 5. THE GRID */}
      <div className="px-4 mt-4 grid grid-cols-2 gap-4">
        {filteredPages.map((page) => (
          <div key={page.id} className="bg-white rounded-[24px] p-2 pb-3 shadow-sm border border-gray-100 flex flex-col">
            
            <div className="relative h-40 bg-gray-50 rounded-[18px] overflow-hidden">
              <img src={page.img} alt={page.title} className="w-full h-full object-cover" />
              
              <span className={`absolute top-2 left-2 ${page.categoryColor} text-white text-[10px] px-2 py-1 rounded-full font-bold tracking-wide`}>
                {page.category}
              </span>
              
              <div className="absolute top-2 right-2 w-6 h-6 bg-white rounded-full flex items-center justify-center shadow-sm cursor-pointer hover:scale-110 transition-transform">
                <Heart size={12} fill={page.isFavorite ? "#ec4899" : "none"} color={page.isFavorite ? "#ec4899" : "#9ca3af"} />
              </div>

              {page.progress > 0 && page.progress < 100 && (
                <div className="absolute bottom-0 left-0 w-full bg-gray-200 h-1.5">
                  <div className="bg-purple-600 h-full" style={{ width: `${page.progress}%` }}></div>
                </div>
              )}

              {page.isCompleted && (
                <div className="absolute bottom-2 left-2 bg-green-500 text-white text-[10px] px-2 py-0.5 rounded-full flex items-center gap-1 font-bold">
                  <CheckCircle2 size={10} /> Completed
                </div>
              )}
            </div>

            <div className="px-1 mt-2 flex flex-col flex-grow">
              <h3 className="font-bold text-gray-800 text-sm leading-tight mb-2 line-clamp-2">{page.title}</h3>
              
              <div className="flex gap-0.5 mb-2">
                {[...Array(5)].map((_, i) => (
                  <span key={i} className={i < page.stars ? "text-orange-400" : "text-gray-200"}>★</span>
                ))}
                <span className="text-xs text-orange-400 font-bold ml-1">{page.complexity}</span>
              </div>

              <div className="mt-auto flex justify-between items-center text-[10px] text-gray-400 font-medium">
                <span className="flex items-center gap-1"><Clock size={10} /> {page.time}</span>
                <span className="truncate ml-2">{page.tool}</span>
              </div>
            </div>

          </div>
        ))}
      </div>

      {/* Empty State if no pages match filter */}
      {filteredPages.length === 0 && (
        <div className="text-center text-gray-400 mt-10">
          <p>No pages found in this category.</p>
        </div>
      )}

      {/* FAB */}
      <button className="fixed bottom-6 right-6 w-14 h-14 bg-purple-600 text-white rounded-full flex items-center justify-center shadow-lg shadow-purple-300 hover:scale-105 transition-transform">
        <Plus size={28} />
      </button>

    </div>
  );
}

export default App;
