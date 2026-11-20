import Dexie from 'dexie';

export const db = new Dexie('ColorNestDB');

db.version(10).stores({
  pages: '++id, title, categoryId, complexity, isFavorite, isCompleted, isUncategorized, driveFileId, *tags',
  categories: '++id, name, parentId, driveFolderId',
  settings: 'key'
});

// Auto-create default categories on first load
db.version(11).stores({
  pages: '++id, title, categoryId, complexity, isFavorite, isCompleted, isUncategorized, driveFileId, syncId, updatedAt, *tags',
  categories: '++id, name, parentId, driveFolderId',
  settings: 'key',
  tags: '++id, &name, createdAt'
}).upgrade(async (tx) => {
  const names = new Set();
  await tx.table('pages').toCollection().modify((page) => {
    (page.tags || []).forEach((tag) => {
      const name = String(tag).trim();
      if (name) names.add(name);
    });
    if (!page.updatedAt) page.updatedAt = Date.now();
  });
  if (names.size) await tx.table('tags').bulkAdd([...names].map((name) => ({ name, createdAt: Date.now() })));
});

db.on('populate', () => {
  db.categories.bulkAdd([
    { name: 'Animals', parentId: null },
    { name: 'Mandalas & Patterns', parentId: null },
    { name: 'Fantasy & Mythical', parentId: null },
    { name: 'Botanical & Nature', parentId: null },
    { name: 'Architecture', parentId: null }
  ]);
});
