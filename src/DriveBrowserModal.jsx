import React, { useState, useEffect, useCallback } from 'react';
import { X, Download, FileText, RefreshCw, Check, Cloud } from 'lucide-react';
import { listDriveFiles, downloadDriveFile } from './driveSync';

// Drive thumbnail links end with a size suffix like =s220; ask for a
// larger preview in the detail view.
function largeThumbnailUrl(link) {
  if (!link) return null;
  return link.replace(/=s\d+$/, '=s1600');
}

// Shows the Drive thumbnail over a fallback icon, so a file without a
// thumbnail (or one that fails to load) still renders as a tile.
function FileIcon({ entry }) {
  return (
    <div className="w-full h-full relative flex items-center justify-center text-gray-400 bg-gray-100 dark:bg-gray-800">
      <FileText size={28} />
      {entry.thumbnailLink && (
        <img
          src={entry.thumbnailLink}
          alt=""
          loading="lazy"
          className="absolute inset-0 w-full h-full object-cover"
          onError={(event) => { event.currentTarget.style.display = 'none'; }}
        />
      )}
    </div>
  );
}

export default function DriveBrowserModal({ onClose }) {
  const [files, setFiles] = useState(null);
  const [notConnected, setNotConnected] = useState(false);
  const [loadError, setLoadError] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [selected, setSelected] = useState(null);
  const [downloadingId, setDownloadingId] = useState(null);
  const [status, setStatus] = useState('');

  const loadFiles = useCallback(async () => {
    setIsLoading(true);
    setLoadError('');
    setStatus('');
    try {
      const result = await listDriveFiles();
      if (result.notConnected) {
        setNotConnected(true);
        setFiles([]);
      } else {
        setFiles(result.files);
      }
    } catch (error) {
      setLoadError(error.message || 'Could not list Drive files.');
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => { loadFiles(); }, [loadFiles]);

  const handleDownload = async (entry) => {
    if (downloadingId) return;
    setDownloadingId(entry.id);
    setStatus('');
    try {
      const result = await downloadDriveFile(entry);
      const markSaved = (file) => file.id === entry.id
        ? { ...file, inLibrary: true, importedPdf: file.isPdf ? true : file.importedPdf }
        : file;
      setFiles((current) => (current || []).map(markSaved));
      setSelected((current) => (current && current.id === entry.id ? markSaved(current) : current));
      setStatus(result.pagesAdded === 1
        ? `Downloaded \u201c${entry.name}\u201d.`
        : `Downloaded \u201c${entry.name}\u201d — ${result.pagesAdded} pages added.`);
    } catch (error) {
      setStatus('Download failed: ' + (error.message || 'Unknown error'));
    } finally {
      setDownloadingId(null);
    }
  };

  const alreadyHave = (entry) => entry.inLibrary || entry.importedPdf;

  return (
    <div className="fixed inset-0 bg-black/60 flex justify-center items-center z-[300] p-4">
      <div className="bg-white dark:bg-gray-800 rounded-3xl w-full max-w-md p-6 relative max-h-[90vh] overflow-y-auto">
        <button onClick={onClose} className="absolute top-4 right-4 text-gray-500 dark:text-gray-300 z-50 p-1" aria-label="Close Drive browser"><X size={24} /></button>
        <h2 className="text-2xl font-bold mb-1 text-gray-800 dark:text-white">Google Drive</h2>
        <p className="text-xs text-gray-600 dark:text-gray-400 mb-4">Previews only — nothing is downloaded to this device until you download it.</p>

        {isLoading ? (
          <p className="text-sm text-gray-500 dark:text-gray-400 py-8 text-center">Loading Drive files…</p>
        ) : notConnected ? (
          <p className="text-sm text-gray-500 dark:text-gray-400 py-8 text-center">Not connected to Google Drive. Connect from Settings first.</p>
        ) : loadError ? (
          <div className="py-8 text-center">
            <p className="text-sm text-red-600 dark:text-red-400 mb-3">{loadError}</p>
            <button onClick={loadFiles} className="bg-purple-600 text-white px-4 py-2 rounded-lg font-bold text-sm">Try again</button>
          </div>
        ) : selected ? (
          <div>
            <button onClick={() => setSelected(null)} className="text-sm font-bold text-purple-600 dark:text-purple-300 mb-3">← All Drive files</button>
            <div className="bg-gray-50 dark:bg-gray-900 rounded-2xl overflow-hidden mb-3 flex items-center justify-center min-h-[200px] relative">
              <FileText size={48} className="text-gray-300 dark:text-gray-600" />
              {selected.thumbnailLink && (
                <img
                  src={largeThumbnailUrl(selected.thumbnailLink)}
                  alt={selected.name}
                  className="absolute inset-0 w-full h-full object-contain"
                  onError={(event) => { event.currentTarget.style.display = 'none'; }}
                />
              )}
            </div>
            <h3 className="font-bold text-gray-800 dark:text-white mb-1 break-words">{selected.name}</h3>
            <p className="text-xs text-gray-500 dark:text-gray-400 mb-4">{selected.isPdf ? 'PDF' : 'Image'}{alreadyHave(selected) ? ' · Already in your library' : ''}</p>
            <button onClick={() => handleDownload(selected)} disabled={downloadingId || alreadyHave(selected)} className="w-full bg-purple-600 text-white py-2 rounded-lg font-bold disabled:opacity-50 flex items-center justify-center gap-2">
              {downloadingId === selected.id ? 'Downloading…' : alreadyHave(selected) ? (<><Check size={16} /> In your library</>) : (<><Download size={16} /> Download</>)}
            </button>
            {status && <p className="text-xs text-gray-600 dark:text-gray-300 mt-2" role="status">{status}</p>}
          </div>
        ) : files.length === 0 ? (
          <div className="py-8 text-center">
            <Cloud size={40} className="mx-auto text-gray-300 dark:text-gray-600 mb-3" />
            <p className="text-sm text-gray-500 dark:text-gray-400">No images or PDFs in your ColorNest folder yet.</p>
          </div>
        ) : (
          <div>
            <div className="flex items-center justify-between mb-3">
              <span className="text-xs text-gray-500 dark:text-gray-400">{files.length} files</span>
              <button onClick={loadFiles} className="text-xs font-bold text-purple-600 dark:text-purple-300 flex items-center gap-1"><RefreshCw size={12} /> Refresh</button>
            </div>
            {status && <p className="text-xs text-gray-600 dark:text-gray-300 mb-3" role="status">{status}</p>}
            <div className="grid grid-cols-2 gap-3">
              {files.map(entry => (
                <div key={entry.id} className="bg-gray-50 dark:bg-gray-900 rounded-2xl overflow-hidden border border-gray-100 dark:border-gray-700">
                  <button onClick={() => setSelected(entry)} className="block w-full h-28 relative" aria-label={`Preview ${entry.name}`}>
                    <FileIcon entry={entry} />
                    {entry.isPdf && <span className="absolute bottom-1 left-1 text-[9px] font-bold bg-black/60 text-white px-1.5 py-0.5 rounded">PDF</span>}
                    {alreadyHave(entry) && <span className="absolute top-1 right-1 text-[9px] font-bold bg-green-600 text-white px-1.5 py-0.5 rounded flex items-center gap-0.5"><Check size={10} /> Saved</span>}
                  </button>
                  <div className="p-2">
                    <p className="text-xs font-bold text-gray-800 dark:text-white truncate mb-2" title={entry.name}>{entry.name}</p>
                    <button onClick={() => handleDownload(entry)} disabled={downloadingId || alreadyHave(entry)} className="w-full bg-purple-600 text-white py-1.5 rounded-lg text-xs font-bold disabled:opacity-50 flex items-center justify-center gap-1">
                      {downloadingId === entry.id ? 'Downloading…' : alreadyHave(entry) ? 'In library' : (<><Download size={12} /> Download</>)}
                    </button>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
