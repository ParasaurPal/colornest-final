import * as pdfjsLib from 'pdfjs-dist';

// Point to the local worker file in the public folder
pdfjsLib.GlobalWorkerOptions.workerSrc = '/pdf.worker.min.mjs';

// Accept the arrayBuffer directly to avoid Android file reading issues
export async function convertPdfToImages(arrayBuffer) {
  const pdf = await pdfjsLib.getDocument({ data: arrayBuffer }).promise;
  const images = [];

  for (let i = 1; i <= pdf.numPages; i++) {
    const page = await pdf.getPage(i);
    const viewport = page.getViewport({ scale: 2.0 }); 
    
    const canvas = document.createElement('canvas');
    const context = canvas.getContext('2d');
    canvas.height = viewport.height;
    canvas.width = viewport.width;

    await page.render({ canvasContext: context, viewport: viewport }).promise;
    
    images.push(canvas.toDataURL('image/jpeg', 0.8));
  }
  
  return images;
}
