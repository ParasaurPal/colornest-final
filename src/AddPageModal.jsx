import React, { useState } from 'react';
import imageCompression from 'browser-image-compression';
import { db } from './db';
import { X, Upload, Loader2, FileText } from 'lucide-react';
import { convertPdfToImages } from './pdfConverter';
import { Filesystem, Directory } from '@capacitor/filesystem';
import { Capacitor } from '@capacitor/core';

export default function AddPageModal({ isOpen, onClose }) {
  const [isProcessing, setIsProcessing] = useState(false);
  const [progressText, setProgressText] = useState("");
  const [progressPercent, setProgressPercent] = useState(0);

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
    const fileName = `img_${Date.now()}_${Math.floor(Math.random()*1000)}.jpg`;
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
    setProgressPercent(0);
    let totalFiles = files.length;
    let completedFiles = 0;

    try {
      for (const file of files) {
        if (file.type === "application/pdf") {
          setProgressText(`Reading PDF...`);
          
          const arrayBuffer = await new Promise((resolve, reject) => {
            const reader = new FileReader();
            reader.onload = (e) => resolve(e.target.result);
            reader.onerror = (e) => reject(new Error("Failed to read PDF file"));
            reader.readAsArrayBuffer(file);
          });

          setProgressText(`Converting PDF...`);
          const images = await convertPdfToImages(arrayBuffer);
          
          for (let i = 0; i < images.length; i++) {
             // Calculate overall progress based on pages processed
             let pagePercent = Math.round(((i + 1) / images.length) * 100);
             setProgressText(`Saving Page ${i + 1} of ${images.length}...`);
             setProgressPercent(pagePercent);
             
             await savePageToDb(images[i], `${file.name.replace('.pdf', '')} - Page ${i+1}`);
          }
        } else {
          setProgressText(`Compressing Image...`);
          setProgressPercent(50); // Halfway there while compressing
          
          const options = { maxSizeMB: 0.5, maxWidthOrHeight: 600, useWebWorker: true };
          const compressedFile = await imageCompression(file, options);
          
          await new Promise((resolve) => {
            const reader = new FileReader();
            reader.readAsDataURL(compressedFile);
            reader.onloadend = async () => {
              setProgressText(`Saving Image...`);
              setProgressPercent(90);
              await savePageToDb(reader.result);
              resolve();
            };
          });
        }
        
        completedFiles++;
        // Update overall progress if multiple files were selected
        setProgressPercent(Math.round((completedFiles / totalFiles) * 100));
      }
      
      setProgressPercent(100);
      setTimeout(() => {
        setIsProcessing(false);
        onClose();
      }, 500); // Small delay so the user sees 100%
      
    } catch (error) {
      console.error(error);
      alert("Error: " + error.message);
      setIsProcessing(false);
      setProgressPercent(0);
    }
  };

  return (
    <div className="fixed inset-0 bg-black/60 flex justify-center items-center z-[100] p-4">
      <div className="bg-white dark:bg-gray-800 rounded-3xl w-full max-w-md p-6 relative">
        <button onClick={onClose} className="absolute top-4 right-4 text-gray-500 z-50 p-1"><X size={24} /></button>
        <h2 className="text-2xl font-bold mb-6 text-center dark:text-white">Upload Pages</h2>
        
        <div className="border-4 border-dashed border-purple-200 dark:border-purple-900 bg-purple-50 dark:bg-gray-900 rounded-3xl p-8 text-center relative h-64 flex flex-col items-center justify-center overflow-hidden">
          {isProcessing ? (
            <div className="flex flex-col items-center w-full px-4 text-purple-600 dark:text-purple-400">
              <Loader2 size={40} className="mb-4 animate-spin" />
              <p className="font-bold text-lg mb-2 text-center">{progressText}</p>
              
              {/* Progress Bar */}
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
          <input type="file" accept="image/*,application/pdf" multiple onChange={handleFileUpload} disabled={isProcessing} className="absolute inset-0 w-full h-full opacity-0 cursor-pointer z-10" />
        </div>
      </div>
    </div>
  );
}
