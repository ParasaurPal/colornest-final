import React, { useState } from 'react';
import { ChevronRight, ChevronDown, X, Plus, Settings } from 'lucide-react';
import { db } from './db';

export default function SideMenu({ isOpen, onClose, categories, activeCategoryId, setActiveCategoryId, onOpenSettings }) {
  const [expanded, setExpanded] = useState({});

  if (!isOpen) return null;

  const toggleExpand = (id, e) => {
    e.stopPropagation();
    setExpanded(prev => ({ ...prev, [id]: !prev[id] }));
  };

  const handleAddCategory = async (parentId = null) => {
    const name = window.prompt("Enter new category name:");
    if (name) {
      await db.categories.add({ name, parentId: parentId });
      if (parentId) setExpanded(prev => ({ ...prev, [parentId]: true }));
    }
  };

  const renderTree = (parentId = null, depth = 0) => {
    const nodes = categories.filter(c => c.parentId === parentId);
    
    return nodes.map(node => {
      const children = categories.filter(c => c.parentId === node.id);
      const hasChildren = children.length > 0;
      const isExpanded = expanded[node.id];
      const isActive = activeCategoryId === node.id;

      return (
        <div key={node.id}>
          <div 
            onClick={() => setActiveCategoryId(node.id)}
            className={`flex items-center py-3 px-3 rounded-xl cursor-pointer transition-colors ${isActive ? 'bg-purple-100 dark:bg-purple-900 text-purple-700 dark:text-purple-300 font-bold' : 'hover:bg-gray-100 dark:hover:bg-gray-800 text-gray-700 dark:text-gray-300'}`}
            style={{ paddingLeft: `${depth * 16 + 12}px` }}
          >
            {/* Expand/Collapse Arrow */}
            <div onClick={(e) => toggleExpand(node.id, e)} className="w-6 h-6 flex items-center justify-center mr-2">
              {hasChildren ? (
                isExpanded ? <ChevronDown size={18} /> : <ChevronRight size={18} />
              ) : <div className="w-4" />}
            </div>
            
            <span className="flex-grow text-base truncate">{node.name}</span>
          </div>
          
          {isExpanded && hasChildren && (
            <div>
              {renderTree(node.id, depth + 1)}
            </div>
          )}
        </div>
      );
    });
  };

  return (
    <div className="fixed inset-0 bg-black/60 z-[200] flex">
      <div className="w-80 bg-white dark:bg-gray-900 h-full shadow-2xl flex flex-col">
        <div className="p-4 flex justify-between items-center border-b dark:border-gray-800">
          <h2 className="font-bold text-lg dark:text-white">Categories</h2>
          <button onClick={onClose} className="p-1 text-gray-500 hover:text-red-500">
            <X size={24} />
          </button>
        </div>
        
        <div className="flex-grow overflow-y-auto p-2">
          {/* All Pages Option */}
          <div 
            onClick={() => setActiveCategoryId(null)}
            className={`flex items-center py-3 px-3 rounded-xl cursor-pointer mb-2 ${activeCategoryId === null ? 'bg-purple-100 dark:bg-purple-900 text-purple-700 dark:text-purple-300 font-bold' : 'hover:bg-gray-100 dark:hover:bg-gray-800 text-gray-700 dark:text-gray-300'}`}
          >
            <span className="flex-grow text-base ml-8">All Pages</span>
          </div>
          
          {/* The Tree */}
          {renderTree(null)}
        </div>

        <div className="p-4 border-t dark:border-gray-800 flex flex-col gap-2">
          <button onClick={() => handleAddCategory(null)} className="w-full bg-purple-600 text-white py-2.5 rounded-xl font-bold flex items-center justify-center gap-2 hover:bg-purple-700">
            <Plus size={18} /> Add Root Category
          </button>
          <button onClick={onOpenSettings} className="w-full bg-gray-100 dark:bg-gray-800 text-gray-700 dark:text-gray-300 py-2.5 rounded-xl font-bold flex items-center justify-center gap-2 hover:bg-gray-200 dark:hover:bg-gray-700">
            <Settings size={18} /> Settings
          </button>
        </div>
      </div>
      <div className="flex-grow" onClick={onClose}></div>
    </div>
  );
}
