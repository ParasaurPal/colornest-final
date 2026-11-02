import Dexie from 'dexie';

export const db = new Dexie('ColorNestDB');

db.version(8).stores({
  pages: '++id, title, categoryId, complexity, isFavorite, isCompleted, isUncategorized, aiStatus, aiSuggestedCategoryId, *tags',
  categories: '++id, name, parentId',
  learnedPatterns: '++id, categoryId',
  settings: 'key' 
});
