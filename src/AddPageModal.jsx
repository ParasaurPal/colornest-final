import React, { useState } from 'react';
import imageCompression from 'browser-image-compression';
import { db } from './db';
import { X, Upload, Loader2, FileText } from 'lucide-react';
import { convertPdfToImages } from './pdfConverter';
import { Filesystem, Directory } from '@capacitor/filesystem';
import { Capacitor } from '@capacitor/core';

export default function AddPageModal({ isOpen, onClose }) {
  const [isProcessing, setIsProcessing] = useState(false);
  const [progress, setProgress] = useState("");

  if (!isOpen) return null;

  const saveImageToDevice = async (base64Data, fileName) => {
    if (Capacitor.isNativePlatform()) {
      const savedFile = await Filesystem.writeFile({
        path: `colornest/${fileName}`,
        data: base64Data.split(',')[1],
        directory: Directory.Data,
        recursive: true
      });
      return Capacitor.convertFileSrc(savedFile.uri);
    }
    return base64Data; 
  };

  const savePageToDb = async (base64Data, title = "Sorting...") => {
    const fileName = `img_${Date.now()}.jpg`;
    const finalImageUrl = await saveImageToDevice(base64Data, fileName);
    
    await db.pages.add({
      title: title, categoryId: null, complexity: "Beginner", time: "TBD", tool: "TBD",
      imageUrl: finalImageUrl, isFavorite: 0, isCompleted: 0, progress: 0, isUncategorized: 1, 
      isProcessingAi: 0, aiStatus: "Queued", tags: []
    });
  };

  const handleFileUpload = async (event) => {
    const files = Array.from(event.target.files);
    if (!files.length) return;

    setIsProcessing(true);
    try {
      for (const file of files) {
        if (file.type === "application/pdf") {
          setProgress(`Reading PDF...`);
          
          // Read PDF as ArrayBuffer safely
          const arrayBuffer = await new Promise((resolve, reject) => {
            const reader = new FileReader();
            reader.onload = (e) => resolve(e.target.result);
            reader.onerror = (e) => reject(new Error("Failed to read PDF file"));
            reader.readAsArrayBuffer(file);
          });

          setProgress(`Converting PDF to Images...`);
          const images = await convertPdfToImages(arrayBuffer);
          
          for (let i = 0; i < images.length; i++) {
             setProgress(`Saving Page ${i + 1}/${images.length}...`);
             await savePageToDb(images[i], `${file.name.replace('.pdf', '')} - Page ${i+1}`);
          }
        } else {
          setProgress(`Compressing Image...`);
          const options = { maxSizeMB: 0.5, maxWidthOrHeight: 600, useWebWorker: true };
          const compressedFile = await imageCompression(file, options);
          
          await new Promise((resolve) => {
            const reader = new FileReader();
            reader.readAsDataURL(compressedFile);
            reader.onloadend = async () => {
              await savePageToDb(reader.result);
              resolve();
            };
          });
        }
      }
      setIsProcessing(false);
      onClose();
    } catch (error) {
      console.error(error);
      alert("Error: " + error.message);
      setIsProcessing(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-black/60 flex justify-center items-center z-[100] p-4">
      <div className="bg-white dark:bg-gray-800 rounded-3xl w-full max-w-md p-6 relative">
        <button onClick={onClose} className="absolute top-4 right-4 text-gray-500 z-50 p-1"><X size={24} /></button>
        <h2 className="text-2xl font-bold mb-6 text-center dark:text-white">Upload Pages</h2>
        <div className="border-4 border-dashed border-purple-200 dark:border-purple-900 bg-purple-50 dark:bg-gray-900 rounded-3xl p-8 text-center relative h-64 flex flex-col items-center justify-center overflow-hidden">
          {isProcessing ? (
            <div className="flex flex-col items-center text-purple-600 dark:text-purple-400">
              <Loader2 size={48} className="mb-4 animate-spin" />
              <p className="font-bold text-lg">{progress}</p>
            </div>
          ) : (
            <div className="flex flex-col items-center text-purple-500 dark:text-purple-400 pointer-events-none">
              <div className="flex gap-4 mb-4"><Upload size={48} /><FileText size={48} /></div>
              <p className="font-bold text-xl">Tap to Upload</p>
              <p className="text-sm mt-2 opacity-70">Images or PDFs</p>
            </div>
          )}
          <input type="file" accept="image/*,application/pdf" multiple onChange={handleFileUpload} disabled={isProcessing} className="absolute inset-0 w-full h-full opacity-0 cursor-pointer z-10" />
        </div>
      </div>
    </div>
  );
}
