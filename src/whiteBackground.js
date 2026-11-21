// Converts white and near-white pixels to transparency, including soft anti-aliased
// edges. PNG output preserves the alpha channel.
export async function removeWhiteBackground(source, {
  fadeStart = 205,
  transparentAt = 245,
  colorTolerance = 65,
} = {}) {
  let image;
  let objectUrl = null;
  try {
    if (source instanceof Blob) objectUrl = URL.createObjectURL(source);
    const src = objectUrl || source;
    image = await new Promise((resolve, reject) => {
      const element = new Image();
      element.onload = () => resolve(element);
      element.onerror = () => reject(new Error('Could not load image for white-to-transparency conversion.'));
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
    const range = Math.max(1, transparentAt - fadeStart);

    for (let i = 0; i < pixels.length; i += 4) {
      const red = pixels[i];
      const green = pixels[i + 1];
      const blue = pixels[i + 2];
      const minChannel = Math.min(red, green, blue);
      const maxChannel = Math.max(red, green, blue);
      const colorSpread = maxChannel - minChannel;

      // Only remove neutral whites / light greys, not saturated colored artwork.
      if (colorSpread > colorTolerance || minChannel <= fadeStart) continue;

      const remainingAlpha = minChannel >= transparentAt
        ? 0
        : Math.round(255 * (1 - (minChannel - fadeStart) / range));
      pixels[i + 3] = Math.min(pixels[i + 3], remainingAlpha);
    }

    context.putImageData(frame, 0, 0);
    return canvas.toDataURL('image/png');
  } finally {
    if (objectUrl) URL.revokeObjectURL(objectUrl);
  }
}
