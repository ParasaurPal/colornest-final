import React, { useState, useEffect, useRef } from 'react';
import { X, Copy, CheckCircle2, Trash2, Loader2 } from 'lucide-react';
import { db } from './db';
import { findDuplicateGroups } from './duplicates';
import { deletePageWithDrive } from './driveSync';

export default function DuplicatesModal({ onClose }) {
  const [status, setStatus] = useState('scanning'); // scanning | ready | deleting | done
  const [groups, setGroups] = useState([]);
  const [keepByGroup, setKeepByGroup] = useState({});
  const [progress, setProgress] = useState({ done: 0, total: 0 });
  const [deleteProgress, setDeleteProgress] = useState({ done: 0, total: 0 });
  const [deletedCount, setDeletedCount] = useState(0);
  const [categoryNames, setCategoryNames] = useState({});
  const pagesRef = useRef([]);
  const cancelledRef = useRef(false);

  useEffect(() => {
    cancelledRef.current = false;
    (async () => {
      const [pages, categories] = await Promise.all([db.pages.toArray(), db.categories.toArray()]);
      if (cancelledRef.current) return;
      pagesRef.current = pages;
      setCategoryNames(Object.fromEntries(categories.map((c) => [c.id, c.name])));
      const found = await findDuplicateGroups(pages, (done, total) => {
        if (!cancelledRef.current) setProgress({ done, total });
      });
      if (cancelledRef.current) return;
      setGroups(found);
      setKeepByGroup(Object.fromEntries(found.map((g) => [g.hash, g.pages[0].id])));
      setStatus('ready');
    })();
    return () => { cancelledRef.current = true; };
  }, []);

  const totalDuplicates = groups.reduce((sum, g) => sum + g.pages.length - 1, 0);

  const handleDelete = async () => {
    setStatus('deleting');
    const toDelete = [];
    for (const group of groups) {
      const keepId = keepByGroup[group.hash];
      for (const page of group.pages) {
        if (page.id !== keepId) toDelete.push(page);
      }
    }
    // If several pages point at the same Drive file, only trash the Drive
    // file when the last referencing page is deleted; the others just lose
    // their local copy so the kept page's Drive link stays intact.
    const driveRefCounts = new Map();
    for (const page of pagesRef.current) {
      if (page.driveFileId) driveRefCounts.set(page.driveFileId, (driveRefCounts.get(page.driveFileId) || 0) + 1);
    }
    let deleted = 0;
    for (const page of toDelete) {
      if (cancelledRef.current) break;
      if (page.driveFileId && driveRefCounts.get(page.driveFileId) > 1) {
        await db.pages.delete(page.id);
        driveRefCounts.set(page.driveFileId, driveRefCounts.get(page.driveFileId) - 1);
      } else {
        await deletePageWithDrive(page);
      }
      deleted++;
      setDeleteProgress({ done: deleted, total: toDelete.length });
    }
    setDeletedCount(deleted);
    setStatus('done');
  };

  return (
    <div className="fixed inset-0 bg-black/60 flex justify-center items-center z-[300] p-4">
      <div className="bg-white dark:bg-gray-800 rounded-3xl w-full max-w-md p-6 relative max-h-[90vh] overflow-y-auto">
        <button onClick={onClose} className="absolute top-4 right-4 text-gray-500 dark:text-gray-300 z-50 p-1"><X size={24} /></button>
        <h2 className="text-xl font-bold mb-1 text-gray-800 dark:text-white flex items-center gap-2"><Copy size={20} /> Duplicate pages</h2>
        <p className="text-xs text-gray-600 dark:text-gray-400 mb-4">Pages with the exact same image are grouped together. The copy in a category is kept by default — tap another copy to keep that one instead. Preview-only Drive entries are skipped.</p>

        {status === 'scanning' && (
          <div>
            <p className="text-sm text-gray-600 dark:text-gray-300 mb-2">Checking {progress.done} of {progress.total} pages…</p>
            <div className="h-2 rounded-full bg-purple-100 dark:bg-gray-700 overflow-hidden"><div className="h-full bg-purple-600 transition-all" style={{ width: `${(progress.done / Math.max(progress.total, 1)) * 100}%` }} /></div>
          </div>
        )}

        {status === 'ready' && groups.length === 0 && (
          <div className="text-center py-8">
            <CheckCircle2 size={40} className="mx-auto text-green-500 mb-2" />
            <p className="font-bold text-gray-800 dark:text-white">No duplicates found</p>
            <p className="text-xs text-gray-600 dark:text-gray-400 mt-1">Every page in your library has a different image.</p>
          </div>
        )}

        {status === 'ready' && groups.length > 0 && (
          <>
            <div className="space-y-4">
              {groups.map((group) => (
                <div key={group.hash} className="p-3 bg-gray-50 dark:bg-gray-900 rounded-xl">
                  <p className="text-xs font-bold text-gray-600 dark:text-gray-400 mb-2">{group.pages.length} copies</p>
                  <div className="flex gap-2 overflow-x-auto pb-1">
                    {group.pages.map((page) => {
                      const isKept = keepByGroup[group.hash] === page.id;
                      return (
                        <button key={page.id} onClick={() => setKeepByGroup((prev) => ({ ...prev, [group.hash]: page.id }))} className={`shrink-0 w-24 text-left rounded-lg overflow-hidden border-2 ${isKept ? 'border-purple-600' : 'border-transparent'}`}>
                          <div className="bg-white aspect-square flex items-center justify-center overflow-hidden">
                            <img src={page.thumbnailUrl || page.imageUrl} alt="" className="coloring-image max-w-full max-h-full object-contain" />
                          </div>
                          <div className="p-1.5 bg-white dark:bg-gray-800">
                            <p className="text-[11px] font-bold text-gray-800 dark:text-white truncate">{page.title}</p>
                            <p className="text-[10px] text-gray-500 dark:text-gray-400 truncate">{page.categoryId ? categoryNames[page.categoryId] || 'Category' : 'Untagged'}</p>
                            <p className={`text-[10px] font-bold mt-0.5 ${isKept ? 'text-purple-600 dark:text-purple-400' : 'text-gray-400'}`}>{isKept ? 'Kept' : 'Tap to keep'}</p>
                          </div>
                        </button>
                      );
                    })}
                  </div>
                </div>
              ))}
            </div>
            <button onClick={handleDelete} className="w-full mt-4 bg-red-600 text-white py-2.5 rounded-lg font-bold flex items-center justify-center gap-2">
              <Trash2 size={18} /> Delete {totalDuplicates} duplicate{totalDuplicates === 1 ? '' : 's'}
            </button>
            <p className="text-[11px] text-gray-500 dark:text-gray-400 mt-2 text-center">Deleted pages are removed from this device. Drive copies go to the trash, never permanently deleted.</p>
          </>
        )}

        {status === 'deleting' && (
          <div>
            <p className="text-sm text-gray-600 dark:text-gray-300 mb-2 flex items-center gap-2"><Loader2 size={16} className="animate-spin" /> Deleting {deleteProgress.done} of {deleteProgress.total}…</p>
            <div className="h-2 rounded-full bg-red-100 dark:bg-gray-700 overflow-hidden"><div className="h-full bg-red-600 transition-all" style={{ width: `${(deleteProgress.done / Math.max(deleteProgress.total, 1)) * 100}%` }} /></div>
          </div>
        )}

        {status === 'done' && (
          <div className="text-center py-8">
            <CheckCircle2 size={40} className="mx-auto text-green-500 mb-2" />
            <p className="font-bold text-gray-800 dark:text-white">Deleted {deletedCount} duplicate page{deletedCount === 1 ? '' : 's'}</p>
            <button onClick={onClose} className="mt-4 bg-purple-600 text-white px-6 py-2 rounded-lg font-bold">Done</button>
          </div>
        )}
      </div>
    </div>
  );
}
