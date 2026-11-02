import Dexie from 'dexie';

export const db = new Dexie('ColorNestDB');

// Added a new table for our Local AI "Memory"
db.version(5).stores({
  pages: '++id, title, category, complexity, isFavorite, isCompleted, isUncategorized, aiStatus',
  learnedPatterns: '++id, category' // Stores image fingerprints for local matching
});
