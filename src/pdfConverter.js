import * as pdfjsLib from 'pdfjs-dist';
import pdfWorkerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url';

// Let Vite bundle and version the matching PDF.js worker with the app.
pdfjsLib.GlobalWorkerOptions.workerSrc = pdfWorkerUrl;

// Render each PDF page at print quality (300 DPI) and keep it lossless:
// PDF points are 1/72 inch, so 300 DPI means a scale of 300/72. Pages are
// never downscaled or JPEG-compressed on import.
const PDF_RENDER_DPI = 300;
const PDF_RENDER_SCALE = PDF_RENDER_DPI / 72;

// Accept an ArrayBuffer from the browser file picker.
export async function convertPdfToImages(arrayBuffer) {
  const pdf = await pdfjsLib.getDocument({ data: arrayBuffer }).promise;
  const images = [];

  for (let i = 1; i <= pdf.numPages; i++) {
    const page = await pdf.getPage(i);
    const viewport = page.getViewport({ scale: PDF_RENDER_SCALE });

    const canvas = document.createElement('canvas');
    const context = canvas.getContext('2d');
    if (!context) throw new Error('Could not create a canvas for PDF conversion.');

    canvas.height = viewport.height;
    canvas.width = viewport.width;

    await page.render({ canvasContext: context, viewport }).promise;
    images.push(canvas.toDataURL('image/png'));
  }

  return images;
}
