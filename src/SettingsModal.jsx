import React, { useState, useEffect } from 'react';
import { db } from './db';
import { X, Trash2, ShieldAlert, ExternalLink } from 'lucide-react';
import { resetAIBrain } from './localBrain';

export default function SettingsModal({ onClose }) {
  const [aiEnabled, setAiEnabled] = useState(true);

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

  return (
    <div className="fixed inset-0 bg-black/60 flex justify-center items-center z-[200] p-4">
      <div className="bg-white dark:bg-gray-800 rounded-3xl w-full max-w-md p-6 relative">
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

          <div className="p-4 bg-gray-50 dark:bg-gray-700 rounded-xl">
            <h3 className="font-bold text-gray-800 dark:text-white flex items-center gap-2 mb-1">
              <ExternalLink size={16} /> External App
            </h3>
            <p className="text-xs text-gray-500 dark:text-gray-300">Tapping "Open Page" will prompt Android to choose an app (like Ibis Paint or Samsung Notes) to open your drawing.</p>
          </div>

          <div className="border border-red-200 dark:border-red-900 rounded-xl p-4">
            <h3 className="font-bold text-red-600 dark:text-red-400 flex items-center gap-2 mb-2"><ShieldAlert size={18} /> Danger Zone</h3>
            <button onClick={handleReset} className="w-full bg-red-100 dark:bg-red-900/30 text-red-600 dark:text-red-400 py-2 rounded-lg font-bold flex items-center justify-center gap-2">
              <Trash2 size={16} /> Reset AI Learning
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
