// Converts white to transparency (like GIMP's "Color to Alpha" with white):
// every pixel is split into a white component, which becomes transparency,
// and the remaining color. Pure white becomes fully transparent, black is
// untouched, and light colors keep their hue at lower opacity, so the
// result composites identically over a white background.
// Output is PNG so transparency is preserved.

// Bump this when the filter algorithm changes so Settings can offer to
// reprocess pages done with an older version. Version 1 was the old
// threshold/ramp filter (pages processed before versioning have no stamp).
export const WHITE_BACKGROUND_VERSION = 2;

// Pixels ending up below this alpha are set fully transparent, so faint
// JPEG halos and paper specks vanish instead of lingering as
// near-invisible smudges.
const MIN_ALPHA = 10;

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
      // Already fully transparent: stays transparent, so re-running the
      // filter never resurrects a pixel as opaque black.
      if (oldAlpha === 0) continue;
      const white = Math.min(pixels[i], pixels[i + 1], pixels[i + 2]);
      if (white === 255) {
        // Pure white: fully transparent (this also avoids dividing by zero below).
        pixels[i] = 0; pixels[i + 1] = 0; pixels[i + 2] = 0; pixels[i + 3] = 0;
        continue;
      }
      const newAlpha = Math.round(oldAlpha * (1 - white / 255));
      if (newAlpha < MIN_ALPHA) {
        pixels[i] = 0; pixels[i + 1] = 0; pixels[i + 2] = 0; pixels[i + 3] = 0;
        continue;
      }
      // Un-multiply the color so hues survive: remove the white component
      // and rescale the remainder to full strength.
      const scale = 255 / (255 - white);
      pixels[i] = Math.min(255, Math.round((pixels[i] - white) * scale));
      pixels[i + 1] = Math.min(255, Math.round((pixels[i + 1] - white) * scale));
      pixels[i + 2] = Math.min(255, Math.round((pixels[i + 2] - white) * scale));
      pixels[i + 3] = newAlpha;
    }

    context.putImageData(frame, 0, 0);
    return canvas.toDataURL('image/png');
  } finally {
    if (objectUrl) URL.revokeObjectURL(objectUrl);
  }
}
