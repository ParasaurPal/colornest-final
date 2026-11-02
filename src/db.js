import Dexie from 'dexie';

export const db = new Dexie('ColorNestDB');

db.version(8).stores({
  pages: '++id, title, categoryId, complexity, isFavorite, isCompleted, isUncategorized, aiStatus, aiSuggestedCategoryId, *tags',
  categories: '++id, name, parentId',
  learnedPatterns: '++id, categoryId',
  settings: 'key' 
});

// Auto-create default categories on first load
db.on('populate', () => {
  db.categories.bulkAdd([
    { name: 'Animals', parentId: null },
    { name: 'Mandalas & Patterns', parentId: null },
    { name: 'Fantasy & Mythical', parentId: null },
    { name: 'Botanical & Nature', parentId: null },
    { name: 'Architecture', parentId: null }
  ]);
});
