// Converts coloring-page artwork into black line art with transparency.
// Neutral black/gray pixels become black ink with alpha based on darkness;
// colored pixels are discarded. This preserves antialiased edges while
// removing white paper and colored backgrounds. Output is PNG to retain alpha.

export const WHITE_BACKGROUND_VERSION = 3;

// Allow small RGB differences from scanner/JPEG noise while rejecting
// visibly colored pixels. Very dark pixels are accepted even with slight hue.
const MAX_NEUTRAL_CHANNEL_SPREAD = 24;
const VERY_DARK_MAX_CHANNEL = 24;
const MIN_ALPHA = 12;

export async function removeWhiteBackground(source) {
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
      element.onerror = () => reject(new Error('Could not load image for background removal.'));
      element.src = src;
    });

    const width = image.naturalWidth || image.width;
    const height = image.naturalHeight || image.height;
    if (!width || !height) throw new Error('Image has invalid dimensions.');

    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const context = canvas.getContext('2d', { willReadFrequently: true });
    if (!context) throw new Error('Could not process image pixels.');
    context.drawImage(image, 0, 0);
    const frame = context.getImageData(0, 0, width, height);
    const pixels = frame.data;

    for (let i = 0; i < pixels.length; i += 4) {
      const oldAlpha = pixels[i + 3];
      if (oldAlpha === 0) continue;

      const red = pixels[i];
      const green = pixels[i + 1];
      const blue = pixels[i + 2];
      const maxChannel = Math.max(red, green, blue);
      const minChannel = Math.min(red, green, blue);
      const channelSpread = maxChannel - minChannel;

      // Discard colored pixels instead of preserving their hue.
      // Near-black pixels are accepted because tiny channel differences
      // can be introduced by image compression and scanning.
      if (channelSpread > MAX_NEUTRAL_CHANNEL_SPREAD && maxChannel > VERY_DARK_MAX_CHANNEL) {
        pixels[i] = 0;
        pixels[i + 1] = 0;
        pixels[i + 2] = 0;
        pixels[i + 3] = 0;
        continue;
      }

      // Convert grayscale darkness to opacity: black is fully opaque,
      // white is fully transparent, and gray antialiased edges stay smooth.
      const luminance = 0.2126 * red + 0.7152 * green + 0.0722 * blue;
      const newAlpha = Math.round(oldAlpha * (1 - luminance / 255));
      pixels[i] = 0;
      pixels[i + 1] = 0;
      pixels[i + 2] = 0;
      pixels[i + 3] = newAlpha < MIN_ALPHA ? 0 : newAlpha;
    }

    context.putImageData(frame, 0, 0);
    return canvas.toDataURL('image/png');
  } finally {
    if (objectUrl) URL.revokeObjectURL(objectUrl);
  }
}
