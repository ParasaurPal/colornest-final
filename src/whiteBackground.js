// Removes near-white, low-saturation pixels while retaining dark line art
// and colored details. Output is PNG so transparency is preserved.
export async function removeWhiteBackground(source, {
  threshold = 238,
  softness = 17,
  colorTolerance = 20,
} = {}) {
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
      const red = pixels[i];
      const green = pixels[i + 1];
      const blue = pixels[i + 2];
      const lightest = Math.min(red, green, blue);
      const darkest = Math.max(red, green, blue);
      const neutral = darkest - lightest <= colorTolerance;
      if (!neutral || lightest < threshold) continue;

      const alpha = Math.max(0, Math.min(255,
        Math.round(255 * (1 - (lightest - threshold) / softness))
      ));
      pixels[i + 3] = Math.min(pixels[i + 3], alpha);
    }

    context.putImageData(frame, 0, 0);
    return canvas.toDataURL('image/png');
  } finally {
    if (objectUrl) URL.revokeObjectURL(objectUrl);
  }
}
