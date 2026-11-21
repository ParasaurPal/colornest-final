// Builds small gallery thumbnails, stored on the page row in IndexedDB so
// the gallery never has to re-decode full-size images on every open.
// PNG output preserves the transparency left by white-background removal.
// Thumbnails are local-only: they are never uploaded to Drive or included
// in metadata sidecars.
export async function createThumbnail(source, { maxSize = 320 } = {}) {
  let image;
  let objectUrl = null;
  try {
    if (source instanceof Blob) {
      objectUrl = URL.createObjectURL(source);
    }
    const src = objectUrl || source;
    image = await new Promise((resolve, reject) => {
      const element = new Image();
      element.onload = () => resolve(element);
      element.onerror = () => reject(new Error('Could not load image for thumbnail.'));
      element.src = src;
    });

    const width = image.naturalWidth || image.width;
    const height = image.naturalHeight || image.height;
    if (!width || !height) throw new Error('Image has invalid dimensions.');

    // Scale to fit maxSize on the long side, keep aspect ratio, and never
    // upscale a small image.
    const scale = Math.min(1, maxSize / Math.max(width, height));
    const thumbWidth = Math.max(1, Math.round(width * scale));
    const thumbHeight = Math.max(1, Math.round(height * scale));

    const canvas = document.createElement('canvas');
    canvas.width = thumbWidth;
    canvas.height = thumbHeight;
    const context = canvas.getContext('2d');
    if (!context) throw new Error('Could not create thumbnail canvas.');
    context.drawImage(image, 0, 0, thumbWidth, thumbHeight);
    return canvas.toDataURL('image/png');
  } finally {
    if (objectUrl) URL.revokeObjectURL(objectUrl);
  }
}

// Best-effort wrapper: returns the thumbnail data URL, or null when the
// image can't be thumbnailed. Callers save the page without a thumbnail
// and the gallery falls back to the full image.
export async function tryCreateThumbnail(source, options) {
  try {
    return await createThumbnail(source, options);
  } catch (error) {
    console.warn('Could not create thumbnail:', error);
    return null;
  }
}
