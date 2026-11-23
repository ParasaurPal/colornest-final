import React, { useRef, useState } from 'react';
import { X, Upload, Loader2, FileText } from 'lucide-react';
import { convertPdfToImages } from './pdfConverter';
import { savePageToDb } from './pageImport';

export default function AddPageModal({ isOpen, onClose }) {
  const fileInputRef = useRef(null);
  const [isProcessing, setIsProcessing] = useState(false);
  const [progressText, setProgressText] = useState('');
  const [progressPercent, setProgressPercent] = useState(0);

  if (!isOpen) return null;

  const handleFileUpload = async (event) => {
    const files = Array.from(event.target.files || []);
    // Clear the input so selecting the same file again also triggers onChange.
    event.target.value = '';
    if (!files.length || isProcessing) return;

    setIsProcessing(true);
    setProgressPercent(0);
    let completedFiles = 0;

    try {
      for (const file of files) {
        if (file.type === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf')) {
          setProgressText('Reading PDF…');
          const arrayBuffer = await new Promise((resolve, reject) => {
            const reader = new FileReader();
            reader.onload = () => resolve(reader.result);
            reader.onerror = () => reject(new Error('Failed to read PDF file.'));
            reader.readAsArrayBuffer(file);
          });

          setProgressText('Converting PDF…');
          const images = await convertPdfToImages(arrayBuffer);
          for (let i = 0; i < images.length; i++) {
            setProgressText(`Saving page ${i + 1} of ${images.length}…`);
            setProgressPercent(Math.round(((i + 1) / images.length) * 100));
            await savePageToDb(images[i], `${file.name.replace(/\.pdf$/i, '')} - Page ${i + 1}`);
          }
        } else {
          // Import the file exactly as it is — never compressed or resized.
          setProgressText('Reading image…');
          setProgressPercent(50);
          const dataUrl = await new Promise((resolve, reject) => {
            const reader = new FileReader();
            reader.onload = () => resolve(reader.result);
            reader.onerror = () => reject(new Error('Failed to read image file.'));
            reader.readAsDataURL(file);
          });
          setProgressText('Saving image…');
          setProgressPercent(90);
          await savePageToDb(dataUrl);
        }

        completedFiles++;
        setProgressPercent(Math.round((completedFiles / files.length) * 100));
      }

      setProgressText('Upload complete');
      setProgressPercent(100);
      setTimeout(() => {
        setIsProcessing(false);
        onClose();
      }, 500);
    } catch (error) {
      console.error(error);
      alert('Upload failed: ' + (error?.message || 'Unknown error'));
      setIsProcessing(false);
      setProgressText('');
      setProgressPercent(0);
    }
  };

  return (
    <div className="fixed inset-0 bg-black/60 flex justify-center items-center z-[100] p-4">
      <div className="bg-white dark:bg-gray-800 rounded-3xl w-full max-w-md p-6 relative">
        <button
          type="button"
          onClick={onClose}
          disabled={isProcessing}
          aria-label="Close upload"
          className="absolute top-4 right-4 text-gray-500 z-50 p-1 disabled:opacity-40"
        >
          <X size={24} />
        </button>
        <h2 className="text-2xl font-bold mb-6 text-center dark:text-white">Upload Pages</h2>

        <button
          type="button"
          onClick={() => {
            if (!isProcessing) fileInputRef.current?.click();
          }}
          disabled={isProcessing}
          aria-label="Choose images or PDF files to upload"
          className={`w-full border-4 border-dashed border-purple-200 dark:border-purple-900 bg-purple-50 dark:bg-gray-900 rounded-3xl p-8 text-center h-64 flex flex-col items-center justify-center transition-colors disabled:opacity-70 disabled:cursor-wait ${isProcessing ? '' : 'cursor-pointer active:bg-purple-100 dark:active:bg-gray-800'}`}
        >
          {isProcessing ? (
            <div className="flex flex-col items-center w-full px-4 text-purple-600 dark:text-purple-400">
              <Loader2 size={40} className="mb-4 animate-spin" />
              <p className="font-bold text-lg mb-2 text-center">{progressText}</p>
              <div className="w-full bg-gray-200 dark:bg-gray-700 rounded-full h-4 overflow-hidden shadow-inner mt-2">
                <div
                  className="bg-purple-600 h-full rounded-full transition-all duration-300 ease-out flex items-center justify-center text-[10px] text-white font-bold"
                  style={{ width: `${progressPercent}%` }}
                >
                  {progressPercent > 10 && `${progressPercent}%`}
                </div>
              </div>
            </div>
          ) : (
            <div className="flex flex-col items-center text-purple-500 dark:text-purple-400 pointer-events-none">
              <div className="flex gap-4 mb-4"><Upload size={48} /><FileText size={48} /></div>
              <p className="font-bold text-xl">Tap to Upload</p>
              <p className="text-sm mt-2 opacity-70">Images or PDFs</p>
            </div>
          )}
        </button>
        <input
          ref={fileInputRef}
          id="colornest-page-upload"
          type="file"
          accept="image/*,.pdf,application/pdf"
          multiple
          onChange={handleFileUpload}
          disabled={isProcessing}
          tabIndex={-1}
          aria-hidden="true"
          className="hidden"
        />
      </div>
    </div>
  );
}
