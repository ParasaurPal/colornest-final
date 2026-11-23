import React, { useState, useEffect, useCallback } from 'react';
import { X, Folder, ChevronRight, Plus, Loader2, Check } from 'lucide-react';
import { listFolders, createFolder, findFolder } from './driveService';
import { getDriveRootFolder, setDriveRootFolder, triggerBackgroundSync } from './driveSync';
import { DRIVE_ROOT_FOLDER_NAME } from './driveConfig';

export default function DriveFolderPickerModal({ onClose, onSelect }) {
  const [path, setPath] = useState([]); // [{ id, name }] from My Drive down
  const [folders, setFolders] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [currentRootId, setCurrentRootId] = useState(null);
  const [showNewFolder, setShowNewFolder] = useState(false);
  const [newFolderName, setNewFolderName] = useState('');
  const [isCreating, setIsCreating] = useState(false);
  const [isSaving, setIsSaving] = useState(false);

  const parentId = path.length === 0 ? 'root' : path[path.length - 1].id;
  const viewingIsCurrent = path.length > 0 && path[path.length - 1].id === currentRootId;

  const loadFolders = useCallback(async (folderId) => {
    setIsLoading(true);
    setLoadError('');
    try {
      setFolders(await listFolders(folderId));
    } catch (error) {
      setLoadError(error.message || 'Could not list folders.');
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => { loadFolders(parentId); }, [parentId, loadFolders]);

  // Resolve the current root to a folder id so it can be badged, including
  // the default "ColorNest" folder when no custom folder is chosen.
  useEffect(() => {
    (async () => {
      try {
        const root = await getDriveRootFolder();
        if (root.id) {
          setCurrentRootId(root.id);
        } else {
          setCurrentRootId(await findFolder(DRIVE_ROOT_FOLDER_NAME, 'root'));
        }
      } catch (error) {
        console.warn('Could not resolve the current Drive folder:', error);
      }
    })();
  }, []);

  const handleUseThisFolder = async () => {
    if (path.length === 0 || viewingIsCurrent || isSaving) return;
    const folder = path[path.length - 1];
    setIsSaving(true);
    try {
      await setDriveRootFolder(folder.id, folder.name);
      // Import previews from the new folder right away.
      triggerBackgroundSync();
      onSelect(folder);
    } catch (error) {
      setLoadError(error.message || 'Could not save the folder choice.');
      setIsSaving(false);
    }
  };

  const handleCreateFolder = async () => {
    const name = newFolderName.trim();
    if (!name || isCreating) return;
    setIsCreating(true);
    try {
      const id = await createFolder(name, parentId);
      setNewFolderName('');
      setShowNewFolder(false);
      setPath((current) => [...current, { id, name }]);
    } catch (error) {
      setLoadError(error.message || 'Could not create the folder.');
    } finally {
      setIsCreating(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-black/60 flex justify-center items-center z-[300] p-4">
      <div className="bg-white dark:bg-gray-800 rounded-3xl w-full max-w-md p-6 relative max-h-[90vh] overflow-y-auto">
        <button onClick={onClose} className="absolute top-4 right-4 text-gray-500 dark:text-gray-300 z-50 p-1" aria-label="Close folder picker"><X size={24} /></button>
        <h2 className="text-xl font-bold mb-1 text-gray-800 dark:text-white flex items-center gap-2"><Folder size={20} /> Choose Drive folder</h2>
        <p className="text-xs text-gray-600 dark:text-gray-400 mb-4">ColorNest will sync with this folder. Your library stays as it is — only new imports come from the new folder.</p>

        <div className="flex items-center gap-1 flex-wrap text-sm mb-3">
          <button onClick={() => setPath([])} className={`font-bold ${path.length === 0 ? 'text-gray-800 dark:text-white' : 'text-purple-600 dark:text-purple-300'}`}>My Drive</button>
          {path.map((folder, index) => (
            <span key={folder.id} className="flex items-center gap-1">
              <ChevronRight size={14} className="text-gray-400" />
              <button
                onClick={() => setPath(path.slice(0, index + 1))}
                className={`font-bold ${index === path.length - 1 ? 'text-gray-800 dark:text-white' : 'text-purple-600 dark:text-purple-300'}`}
              >
                {folder.name}
              </button>
            </span>
          ))}
        </div>

        {isLoading ? (
          <p className="text-sm text-gray-500 dark:text-gray-400 py-8 text-center flex items-center justify-center gap-2"><Loader2 size={16} className="animate-spin" /> Loading folders…</p>
        ) : loadError ? (
          <div className="py-8 text-center">
            <p className="text-sm text-red-600 dark:text-red-400 mb-3">{loadError}</p>
            <button onClick={() => loadFolders(parentId)} className="bg-purple-600 text-white px-4 py-2 rounded-lg font-bold text-sm">Try again</button>
          </div>
        ) : folders.length === 0 ? (
          <p className="text-sm text-gray-500 dark:text-gray-400 py-8 text-center">No folders here.</p>
        ) : (
          <div className="space-y-1 max-h-64 overflow-y-auto">
            {folders.map((folder) => (
              <button
                key={folder.id}
                onClick={() => setPath((current) => [...current, folder])}
                className="w-full flex items-center gap-2 p-2 rounded-lg hover:bg-purple-50 dark:hover:bg-gray-700 text-left"
              >
                <Folder size={18} className="text-purple-600 dark:text-purple-300 shrink-0" />
                <span className="flex-1 text-sm font-bold text-gray-800 dark:text-white truncate">{folder.name}</span>
                {folder.id === currentRootId && <span className="text-[10px] font-bold bg-green-600 text-white px-1.5 py-0.5 rounded flex items-center gap-0.5"><Check size={10} /> Current</span>}
                <ChevronRight size={16} className="text-gray-400 shrink-0" />
              </button>
            ))}
          </div>
        )}

        {showNewFolder ? (
          <div className="flex gap-2 mt-3">
            <input
              type="text"
              value={newFolderName}
              onChange={(e) => setNewFolderName(e.target.value)}
              onKeyDown={(e) => { if (e.key === 'Enter') handleCreateFolder(); }}
              placeholder="New folder name"
              className="flex-1 min-w-0 border border-gray-300 dark:border-gray-600 rounded-lg px-3 py-2 text-sm bg-white dark:bg-gray-700 text-gray-800 dark:text-white"
              autoFocus
            />
            <button onClick={handleCreateFolder} disabled={!newFolderName.trim() || isCreating} className="bg-purple-600 text-white px-3 py-2 rounded-lg font-bold text-sm disabled:opacity-50">
              {isCreating ? 'Creating…' : 'Create'}
            </button>
            <button onClick={() => { setShowNewFolder(false); setNewFolderName(''); }} className="bg-gray-200 dark:bg-gray-700 text-gray-800 dark:text-white px-3 py-2 rounded-lg font-bold text-sm">Cancel</button>
          </div>
        ) : (
          <button onClick={() => setShowNewFolder(true)} className="mt-3 text-sm font-bold text-purple-600 dark:text-purple-300 flex items-center gap-1">
            <Plus size={14} /> New folder here
          </button>
        )}

        <button
          onClick={handleUseThisFolder}
          disabled={path.length === 0 || viewingIsCurrent || isSaving}
          className="w-full mt-4 bg-purple-600 text-white py-2.5 rounded-lg font-bold disabled:opacity-50"
        >
          {isSaving ? 'Saving…' : viewingIsCurrent ? 'This is the current folder' : path.length === 0 ? 'Open a folder to choose it' : `Use “${path[path.length - 1].name}”`}
        </button>
      </div>
    </div>
  );
}
