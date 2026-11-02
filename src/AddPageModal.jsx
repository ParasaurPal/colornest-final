import React, { useState } from 'react';
import imageCompression from 'browser-image-compression';
import { db } from './db';
import { X, Upload, Loader2 } from 'lucide-react';

export default function AddPageModal({ isOpen, onClose }) {
  const [isProcessing, setIsProcessing] = useState(false);

  if (!isOpen) return null;

  const handleImageUpload = async (event) => {
    const file = event.target.files[0];
    if (!file) return;

    setIsProcessing(true);
    try {
      const options = { maxSizeMB: 0.5, maxWidthOrHeight: 600, useWebWorker: true };
      const compressedFile = await imageCompression(file, options);
      
      const reader = new FileReader();
      reader.readAsDataURL(compressedFile);
      reader.onloadend = async () => {
        try {
          await db.pages.add({
            title: "Sorting...",
            category: "Uncategorized",
            categoryColor: "bg-gray-400",
            complexity: "Beginner",
            time: "TBD",
            tool: "TBD",
            imageUrl: reader.result,
            isFavorite: 0,      // <--- Changed to 0
            isCompleted: 0,     // <--- Changed to 0
            progress: 0,
            isUncategorized: 1, // <--- Changed to 1
            isProcessingAi: 0,  // <--- Changed to 0
            aiStatus: "Queued"
          });
          
          setIsProcessing(false);
          onClose();
        } catch (dbError) {
          alert("Database Error: " + dbError.message);
          setIsProcessing(false);
        }
      };
    } catch (error) {
      console.error("Error:", error);
      setIsProcessing(false);
      alert("Failed to process image.");
    }
  };

  return (
    <div className="fixed inset-0 bg-black/60 flex justify-center items-center z-[100] p-4">
      <div className="bg-white rounded-3xl w-full max-w-md p-6 relative">
        
        <button onClick={onClose} className="absolute top-4 right-4 text-gray-500 z-50 bg-white rounded-full p-1">
          <X size={24} />
        </button>

        <h2 className="text-2xl font-bold mb-6 text-center text-gray-800">Upload Page</h2>

        <div className="border-4 border-dashed border-purple-200 bg-purple-50 rounded-3xl p-8 text-center relative h-64 flex flex-col items-center justify-center overflow-hidden">
          
          {isProcessing ? (
            <div className="flex flex-col items-center text-purple-600">
              <Loader2 size={48} className="mb-4 animate-spin" />
              <p className="font-bold text-lg">Saving...</p>
            </div>
          ) : (
            <div className="flex flex-col items-center text-purple-500 pointer-events-none">
              <Upload size={64} className="mb-4" />
              <p className="font-bold text-xl">Tap to Upload</p>
              <p className="text-sm mt-2 opacity-70">AI will sort it in the background</p>
            </div>
          )}

          <input 
            type="file" 
            accept="image/*" 
            onChange={handleImageUpload} 
            disabled={isProcessing}
            className="absolute inset-0 w-full h-full opacity-0 cursor-pointer z-10" 
          />
        </div>
      </div>
    </div>
  );
}
