import * as pdfjsLib from 'pdfjs-dist';
import pdfWorkerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url';

// Let Vite bundle and version the matching PDF.js worker with the app.
pdfjsLib.GlobalWorkerOptions.workerSrc = pdfWorkerUrl;

// Pages render at the embedded image's native resolution, so converted
// images keep the original quality and size of the PDF image — never
// downscaled. Pure vector pages (no embedded image) and any page whose
// native size can't be read render at print quality (300 DPI), and the
// render never goes below 300 DPI or above 600 DPI: below would shrink
// low-resolution originals further than today's behavior, above would
// risk running the browser out of memory on a phone.
const PDF_FALLBACK_DPI = 300;
const PDF_MAX_DPI = 600;

// Compose two affine matrices [a b c d e f]: returns the matrix for
// applying `first` and then `second`.
function multiplyMatrices(first, second) {
  return [
    second[0] * first[0] + second[2] * first[1],
    second[1] * first[0] + second[3] * first[1],
    second[0] * first[2] + second[2] * first[3],
    second[1] * first[2] + second[3] * first[3],
    second[0] * first[4] + second[2] * first[5] + second[4],
    second[1] * first[4] + second[3] * first[5] + second[5],
  ];
}

// Finds the largest embedded image on a page and returns the DPI it is
// printed at (its native pixels over its displayed size), or null when
// the page has no embedded raster image or its size can't be read. The
// paint op already carries the image's pixel size; the displayed size
// comes from the current transformation matrix, tracked through the
// save/restore/transform ops (an image is drawn into the unit square).
async function nativeImageDpi(page) {
  try {
    const { fnArray, argsArray } = await page.getOperatorList();
    let ctm = [1, 0, 0, 1, 0, 0];
    const stack = [];
    let best = null;
    for (let i = 0; i < fnArray.length; i++) {
      const fn = fnArray[i];
      const args = argsArray[i];
      if (fn === pdfjsLib.OPS.save) {
        stack.push(ctm);
      } else if (fn === pdfjsLib.OPS.restore) {
        if (stack.length) ctm = stack.pop();
      } else if (fn === pdfjsLib.OPS.transform) {
        ctm = multiplyMatrices(args, ctm);
      } else if (fn === pdfjsLib.OPS.paintImageXObject) {
        const width = args[1];
        const height = args[2];
        if (typeof width !== 'number' || typeof height !== 'number' || width <= 0 || height <= 0) continue;
        const displayedWidthIn = Math.hypot(ctm[0], ctm[1]) / 72;
        const displayedHeightIn = Math.hypot(ctm[2], ctm[3]) / 72;
        if (!(displayedWidthIn > 0) || !(displayedHeightIn > 0)) continue;
        const dpi = Math.min(width / displayedWidthIn, height / displayedHeightIn);
        if (!Number.isFinite(dpi) || dpi <= 0) continue;
        if (!best || width * height > best.pixels) best = { dpi, pixels: width * height };
      }
    }
    return best ? best.dpi : null;
  } catch {
    return null;
  }
}

async function renderPage(page, scale) {
  const viewport = page.getViewport({ scale });
  const canvas = document.createElement('canvas');
  const context = canvas.getContext('2d');
  if (!context) throw new Error('Could not create a canvas for PDF conversion.');
  canvas.height = viewport.height;
  canvas.width = viewport.width;
  await page.render({ canvasContext: context, viewport }).promise;
  return canvas.toDataURL('image/png');
}

// Accept an ArrayBuffer from the browser file picker.
export async function convertPdfToImages(arrayBuffer) {
  const pdf = await pdfjsLib.getDocument({ data: arrayBuffer }).promise;
  const images = [];

  for (let i = 1; i <= pdf.numPages; i++) {
    const page = await pdf.getPage(i);
    const nativeDpi = await nativeImageDpi(page);
    const dpi = nativeDpi === null
      ? PDF_FALLBACK_DPI
      : Math.min(PDF_MAX_DPI, Math.max(PDF_FALLBACK_DPI, nativeDpi));
    try {
      images.push(await renderPage(page, dpi / 72));
    } catch (error) {
      // A very large native render can fail (canvas limits, memory) — retry
      // once at the 300 DPI fallback instead of failing the whole import.
      if (dpi === PDF_FALLBACK_DPI) throw error;
      images.push(await renderPage(page, PDF_FALLBACK_DPI / 72));
    }
  }

  return images;
}
