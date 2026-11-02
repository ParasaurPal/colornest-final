import { db } from './db';
import { analyzeColoringPage } from './aiService';
import { guessCategoryLocally } from './localBrain';

export function startBackgroundWorker() {
  setInterval(async () => {
    try {
      const pendingPages = await db.pages.where('isUncategorized').equals(1).toArray();
      const pageToProcess = pendingPages.find(p => p.isProcessingAi === 0);

      if (pageToProcess) {
        await db.pages.update(pageToProcess.id, { isProcessingAi: 1, aiStatus: "Checking local memory..." });

        // 1. TRY LOCAL MEMORY FIRST
        const localGuess = await guessCategoryLocally(pageToProcess.imageUrl);
        
        if (localGuess) {
          // Local AI was confident!
          let categoryColor = 'bg-purple-400';
          if (localGuess.category === 'Animals') categoryColor = 'bg-pink-400';
          if (localGuess.category === 'Fantasy & Mythical') categoryColor = 'bg-rose-400';
          
          await db.pages.update(pageToProcess.id, {
            category: localGuess.category,
            categoryColor: categoryColor,
            title: pageToProcess.title === "Sorting..." ? "Locally Sorted" : pageToProcess.title,
            isUncategorized: 0,
            isProcessingAi: 0,
            aiStatus: null
          });
          return; // Done! Don't call Google.
        }

        // 2. IF LOCAL FAILS, CALL GOOGLE
        await db.pages.update(pageToProcess.id, { aiStatus: "Asking Google..." });
        
        const timeoutPromise = new Promise((_, reject) => setTimeout(() => reject(new Error("Timeout")), 10000));
        const aiData = await Promise.race([
          analyzeColoringPage(pageToProcess.imageUrl.split(',')[1]),
          timeoutPromise
        ]).catch(err => ({ error: "Timed out" }));

        if (aiData && !aiData.error) {
          let categoryColor = 'bg-pink-400';
          if (aiData.category === 'Mandalas & Patterns') categoryColor = 'bg-purple-400';
          if (aiData.category === 'Fantasy & Mythical') categoryColor = 'bg-rose-400';
          if (aiData.category === 'Botanical & Nature') categoryColor = 'bg-green-400';
          if (aiData.category === 'Architecture & Places') categoryColor = 'bg-blue-400';

          await db.pages.update(pageToProcess.id, {
            title: aiData.title,
            category: aiData.category,
            categoryColor: categoryColor,
            complexity: aiData.complexity,
            time: aiData.time,
            tool: aiData.tool,
            isUncategorized: 0, 
            isProcessingAi: 0,
            aiStatus: null
          });
        } else {
          await db.pages.update(pageToProcess.id, { isProcessingAi: 0, aiStatus: "Waiting for AI..." });
        }
      }
    } catch (error) {
      console.error("Worker error:", error);
    }
  }, 5000);
}
