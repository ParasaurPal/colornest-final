import React, { useState, useEffect } from 'react';
import { db } from './db';
import { X, Trash2, ShieldAlert, RefreshCw, AlertTriangle, Cloud, CheckCircle2 } from 'lucide-react';
import { resetAIBrain } from './localBrain';
import { seedMassiveCategories } from './prepopulate';
import { Filesystem, Directory } from '@capacitor/filesystem';
import { Capacitor } from '@capacitor/core';

export default function SettingsModal({ onClose }) {
  const [aiEnabled, setAiEnabled] = useState(true);
  const [isSeeding, setIsSeeding] = useState(false);
  
  // Wipe App State
  const [wipeStep, setWipeStep] = useState(0); 
  const [confirmText, setConfirmText] = useState("");
  const [deleteCloud, setDeleteCloud] = useState(false);

  useEffect(() => {
    db.settings.get('aiCategorizationEnabled').then(s => { if (s) setAiEnabled(s.value); });
  }, []);

  const toggleAI = async () => {
    const newVal = !aiEnabled;
    setAiEnabled(newVal);
    await db.settings.put({ key: 'aiCategorizationEnabled', value: newVal });
  };

  const handleReset = async () => {
    if (window.confirm("Reset AI Learning?")) {
      await resetAIBrain();
      alert("AI Brain reset.");
    }
  };

  const handleSeedCategories = async () => {
    if (window.confirm("This will replace all current categories with the massive default list. Continue?")) {
      setIsSeeding(true);
      await seedMassiveCategories();
      setIsSeeding(false);
      alert("Categories successfully populated!");
      onClose();
    }
  };

  const handleWipeApp = async () => {
    if (confirmText !== "DELETE") return;

    try {
      if (Capacitor.isNativePlatform()) {
        try {
          await Filesystem.rmdir({ path: 'colornest', directory: Directory.Data, recursive: true });
        } catch (e) { console.log("Folder already empty."); }
      }

      await db.delete();
      alert("All data has been erased. The app will now restart.");
      window.location.reload();

    } catch (error) {
      alert("Error wiping app: " + error.message);
    }
  };

  // Always go to Step 3 after Step 2
  const handleNextWipeStep = () => {
    if (wipeStep === 2) {
      setWipeStep(3); 
    } else if (wipeStep === 3) {
      setWipeStep(4); 
    }
  };

  return (
    <div className="fixed inset-0 bg-black/60 flex justify-center items-center z-[200] p-4">
      <div className="bg-white dark:bg-gray-800 rounded-3xl w-full max-w-md p-6 relative max-h-[90vh] overflow-y-auto">
        <button onClick={onClose} className="absolute top-4 right-4 text-gray-500 dark:text-gray-300 z-50 p-1"><X size={24} /></button>
        <h2 className="text-2xl font-bold mb-6 text-gray-800 dark:text-white">Settings</h2>

        <div className="space-y-4">
          <div className="flex justify-between items-center p-4 bg-gray-50 dark:bg-gray-700 rounded-xl">
            <div>
              <h3 className="font-bold text-gray-800 dark:text-white">AI Categorization</h3>
              <p className="text-xs text-gray-500 dark:text-gray-300 mt-1">Allow AI to assign folders.</p>
            </div>
            <button onClick={toggleAI} className={`w-14 h-8 rounded-full transition-colors relative ${aiEnabled ? 'bg-purple-600' : 'bg-gray-300 dark:bg-gray-600'}`}>
              <div className={`w-6 h-6 bg-white rounded-full absolute top-1 transition-transform ${aiEnabled ? 'left-7' : 'left-1'}`}></div>
            </button>
          </div>

          <div className="p-4 bg-purple-50 dark:bg-purple-900/30 rounded-xl">
            <h3 className="font-bold text-purple-800 dark:text-purple-300 flex items-center gap-2 mb-2">
              <RefreshCw size={18} /> Setup Categories
            </h3>
            <button onClick={handleSeedCategories} disabled={isSeeding} className="w-full bg-purple-600 text-white py-2 rounded-lg font-bold flex items-center justify-center gap-2">
              {isSeeding ? "Building Tree..." : "Load Default Categories"}
            </button>
          </div>

          <div className="border border-red-200 dark:border-red-900 rounded-xl p-4">
            <h3 className="font-bold text-red-600 dark:text-red-400 flex items-center gap-2 mb-2"><ShieldAlert size={18} /> Danger Zone</h3>
            
            <button onClick={handleReset} className="w-full mb-2 bg-red-100 dark:bg-red-900/30 text-red-600 dark:text-red-400 py-2 rounded-lg font-bold flex items-center justify-center gap-2">
              <Trash2 size={16} /> Reset AI Learning
            </button>

            <button onClick={() => setWipeStep(1)} className="w-full bg-red-600 text-white py-2 rounded-lg font-bold flex items-center justify-center gap-2">
              <AlertTriangle size={16} /> Erase All Images & Data
            </button>
          </div>
        </div>
      </div>

      {/* WIPE MODAL OVERLAY */}
      {wipeStep > 0 && (
        <div className="fixed inset-0 bg-black/80 flex justify-center items-center z-[300] p-4">
          <div className="bg-white dark:bg-gray-800 rounded-2xl p-6 w-full max-w-sm">
            
            {/* Step 1: Main Warning & Checkbox */}
            {wipeStep === 1 && (
              <div className="text-center">
                <AlertTriangle size={48} className="mx-auto text-red-500 mb-4" />
                <h3 className="text-xl font-bold text-gray-800 dark:text-white mb-2">Erase App Data?</h3>
                <p className="text-sm text-gray-600 dark:text-gray-400 mb-4">This will permanently delete every uploaded image, PDF, tag, and category from your device.</p>
                
                <label className="flex items-center gap-3 p-3 bg-gray-50 dark:bg-gray-700 rounded-lg cursor-pointer mb-6 border border-gray-200 dark:border-gray-600">
                  <input type="checkbox" checked={deleteCloud} onChange={(e) => setDeleteCloud(e.target.checked)} className="w-5 h-5 accent-red-600" />
                  <div className="text-left">
                    <span className="text-sm font-bold text-gray-800 dark:text-white flex items-center gap-1"><Cloud size={14}/> Also wipe Cloud Folder</span>
                    <span className="text-xs text-gray-500 dark:text-gray-400">Syncs deletion to Google Drive / iCloud</span>
                  </div>
                </label>

                <div className="flex gap-2">
                  <button onClick={() => { setWipeStep(0); setDeleteCloud(false); }} className="flex-1 bg-gray-200 dark:bg-gray-700 py-2 rounded-lg font-bold text-gray-800 dark:text-white">Cancel</button>
                  <button onClick={() => setWipeStep(2)} className="flex-1 bg-red-500 text-white py-2 rounded-lg font-bold">Continue</button>
                </div>
              </div>
            )}

            {/* Step 2: Local Data Warning */}
            {wipeStep === 2 && (
              <div className="text-center">
                <ShieldAlert size={48} className="mx-auto text-red-600 mb-4" />
                <h3 className="text-xl font-bold text-red-600 mb-2">Final Warning!</h3>
                <p className="text-sm text-gray-600 dark:text-gray-400 mb-6">There is no undo. All images saved on this device will be permanently lost.</p>
                <div className="flex gap-2">
                  <button onClick={() => setWipeStep(0)} className="flex-1 bg-gray-200 dark:bg-gray-700 py-2 rounded-lg font-bold text-gray-800 dark:text-white">Cancel</button>
                  <button onClick={handleNextWipeStep} className="flex-1 bg-red-600 text-white py-2 rounded-lg font-bold">I Understand</button>
                </div>
              </div>
            )}

            {/* Step 3: Cloud Warning (ALWAYS SHOWN, Text changes based on checkbox) */}
            {wipeStep === 3 && (
              <div className="text-center">
                {deleteCloud ? (
                  <>
                    <Cloud size={48} className="mx-auto text-red-600 mb-4" />
                    <h3 className="text-xl font-bold text-red-600 mb-2">Cloud Data Loss!</h3>
                    <p className="text-sm text-gray-600 dark:text-gray-400 mb-6">You have chosen to wipe the cloud folder. This will permanently delete your entire database from your cloud storage.</p>
                    <div className="flex gap-2">
                      <button onClick={() => setWipeStep(0)} className="flex-1 bg-gray-200 dark:bg-gray-700 py-2 rounded-lg font-bold text-gray-800 dark:text-white">Cancel</button>
                      <button onClick={handleNextWipeStep} className="flex-1 bg-red-600 text-white py-2 rounded-lg font-bold">Wipe Cloud Too</button>
                    </div>
                  </>
                ) : (
                  <>
                    <CheckCircle2 size={48} className="mx-auto text-green-500 mb-4" />
                    <h3 className="text-xl font-bold text-gray-800 dark:text-white mb-2">Cloud Folder Safe</h3>
                    <p className="text-sm text-gray-600 dark:text-gray-400 mb-6">You did not select to wipe the cloud folder. Your database will remain intact in your cloud storage (Google Drive / iCloud).</p>
                    <div className="flex gap-2">
                      <button onClick={() => setWipeStep(0)} className="flex-1 bg-gray-200 dark:bg-gray-700 py-2 rounded-lg font-bold text-gray-800 dark:text-white">Cancel</button>
                      <button onClick={handleNextWipeStep} className="flex-1 bg-red-600 text-white py-2 rounded-lg font-bold">Keep Cloud Data</button>
                    </div>
                  </>
                )}
              </div>
            )}

            {/* Step 4: Type to Confirm */}
            {wipeStep === 4 && (
              <div className="text-center">
                <h3 className="text-lg font-bold text-gray-800 dark:text-white mb-2">Type "DELETE" to confirm</h3>
                <input 
                  type="text" 
                  value={confirmText} 
                  onChange={(e) => setConfirmText(e.target.value)}
                  className="w-full p-3 border-2 border-red-300 rounded-lg text-center font-bold text-lg mb-4 bg-red-50 dark:bg-gray-700 dark:text-white focus:outline-none focus:border-red-500"
                  placeholder="DELETE"
                />
                <div className="flex gap-2">
                  <button onClick={() => { setWipeStep(0); setConfirmText(""); }} className="flex-1 bg-gray-200 dark:bg-gray-700 py-2 rounded-lg font-bold text-gray-800 dark:text-white">Cancel</button>
                  <button 
                    onClick={handleWipeApp} 
                    disabled={confirmText !== "DELETE"}
                    className="flex-1 bg-red-600 text-white py-2 rounded-lg font-bold disabled:opacity-50"
                  >
                    {deleteCloud ? "Wipe App & Cloud" : "Wipe App Only"}
                  </button>
                </div>
              </div>
            )}

          </div>
        </div>
      )}
    </div>
  );
}
