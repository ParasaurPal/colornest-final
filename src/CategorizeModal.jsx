import React, { useState } from 'react';
import { db } from './db';
import { learnPattern } from './localBrain';

export default function CategorizeModal({ page, categories, onClose }) {
  const [title, setTitle] = useState(page.title);
  const [selectedCat, setSelectedCat] = useState("");
  const [tags, setTags] = useState(page.tags ? page.tags.join(', ') : "");

  const handleSave = async () => {
    const tagArray = tags.split(',').map(t => t.trim()).filter(t => t !== "");
    
    await db.pages.update(page.id, { 
      title: title,
      categoryId: selectedCat ? parseInt(selectedCat) : null, 
      isUncategorized: 0, 
      aiStatus: null,
      tags: tagArray
    });

    if (selectedCat) await learnPattern(page.imageUrl, parseInt(selectedCat));
    onClose();
  };

  const renderOptions = (parentId = null, depth = 0) => {
    const nodes = categories.filter(c => c.parentId === parentId);
    return nodes.map(node => (
      <React.Fragment key={node.id}>
        <option value={node.id}>{'—'.repeat(depth)} {node.name}</option>
        {renderOptions(node.id, depth + 1)}
      </React.Fragment>
    ));
  };

  return (
    <div className="fixed inset-0 bg-black/60 flex justify-center items-center z-[150] p-4">
      <div className="bg-white dark:bg-gray-800 rounded-2xl p-6 w-80 shadow-2xl max-h-[90vh] overflow-y-auto">
        <h3 className="font-bold text-lg mb-4 text-center dark:text-white">Edit Details</h3>
        
        <label className="text-xs font-bold text-gray-600 dark:text-gray-400">Title</label>
        <input type="text" value={title} onChange={e => setTitle(e.target.value)} className="w-full p-2 border rounded-lg mb-4 dark:bg-gray-700 dark:border-gray-600 dark:text-white" />

        <label className="text-xs font-bold text-gray-600 dark:text-gray-400">Category</label>
        <select className="w-full p-2 border rounded-lg mb-4 dark:bg-gray-700 dark:border-gray-600 dark:text-white" onChange={(e) => setSelectedCat(e.target.value)} value={selectedCat}>
          <option value="" disabled>Select a category...</option>
          {renderOptions(null)}
        </select>

        <label className="text-xs font-bold text-gray-600 dark:text-gray-400">Tags (comma separated)</label>
        <input type="text" placeholder="e.g. easy, flowers, gift" value={tags} onChange={e => setTags(e.target.value)} className="w-full p-2 border rounded-lg mb-4 dark:bg-gray-700 dark:border-gray-600 dark:text-white" />
        
        <div className="flex gap-2">
          <button onClick={onClose} className="flex-1 bg-gray-200 dark:bg-gray-700 dark:text-white py-2 rounded-lg font-bold">Cancel</button>
          <button onClick={handleSave} className="flex-1 bg-purple-600 text-white py-2 rounded-lg font-bold">Save</button>
        </div>
      </div>
    </div>
  );
}
