import { db } from './db';
import { removeWhiteBackground, WHITE_BACKGROUND_VERSION } from './whiteBackground';
import { tryCreateThumbnail } from './thumbnails';

// Stores one coloring page in the library: the white background is removed
// (the stored file keeps a transparent background), a thumbnail is made for
// the gallery, and the page lands in Untagged for hand filing. Shared by
// the upload modal and the Android share-target import.
export async function savePageToDb(base64Data, title = 'Untitled page') {
  const transparentImage = await removeWhiteBackground(base64Data);
  const thumbnailUrl = await tryCreateThumbnail(transparentImage);
  const pageId = await db.pages.add({
    title,
    categoryId: null,
    complexity: 'Beginner',
    time: 'TBD',
    tool: 'TBD',
    imageUrl: transparentImage,
    ...(thumbnailUrl ? { thumbnailUrl } : {}),
    isFavorite: 0,
    isCompleted: 0,
    progress: 0,
    isUncategorized: 1,
    tags: [],
    whiteBackgroundProcessed: true,
    whiteBackgroundVersion: WHITE_BACKGROUND_VERSION,
    syncId: globalThis.crypto?.randomUUID?.() || `page-${Date.now()}-${Math.random().toString(36).slice(2)}`,
    updatedAt: Date.now(),
  });
  return pageId;
}
