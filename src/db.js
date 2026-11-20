import Dexie from 'dexie';

export const db = new Dexie('ColorNestDB');

db.version(9).stores({
  pages: '++id, title, categoryId, complexity, isFavorite, isCompleted, isUncategorized, *tags',
  categories: '++id, name, parentId',
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
