import { db } from './db';
import { analyzeColoringPage } from './aiService';
import { guessCategoryLocally, learnPattern } from './localBrain';

export function startBackgroundWorker() {
  setInterval(async () => {
    try {
      const setting = await db.settings.get('aiCategorizationEnabled');
      const aiEnabled = setting ? setting.value : true; 

      const pendingPages = await db.pages.where('isUncategorized').equals(1).toArray();
      const pageToProcess = pendingPages.find(p => p.isProcessingAi === 0);

      if (pageToProcess) {
        await db.pages.update(pageToProcess.id, { isProcessingAi: 1, aiStatus: "Analyzing..." });
        
        const categories = await db.categories.toArray();
        let finalCategoryId = null;
        
        // 1. LOCAL AI
        const localGuess = await guessCategoryLocally(pageToProcess.imageUrl);
        if (localGuess) {
          finalCategoryId = localGuess.categoryId;
          await db.pages.update(pageToProcess.id, { aiStatus: "Sorted by Local AI" });
        } 
        // 2. GOOGLE AI (Generates Title, Tags, and Guesses Category)
        else if (aiEnabled) {
          await db.pages.update(pageToProcess.id, { aiStatus: "Asking Google..." });
          const timeoutPromise = new Promise((_, reject) => setTimeout(() => reject(new Error("Timeout")), 10000));
          
          const aiData = await Promise.race([
            analyzeColoringPage(pageToProcess.imageUrl.split(',')[1], categories),
            timeoutPromise
          ]).catch(() => ({ error: "Timed out" }));

          if (aiData && !aiData.error) {
            const matchedCat = categories.find(c => c.name.toLowerCase() === aiData.categoryName?.toLowerCase());
            if (matchedCat) finalCategoryId = matchedCat.id;

            await db.pages.update(pageToProcess.id, {
              title: aiData.title, 
              complexity: aiData.complexity,
              time: aiData.time, 
              tool: aiData.tool,
              tags: aiData.tags || [] // Save AI generated tags
            });
          }
        }

        // 3. APPLY CATEGORY
        if (finalCategoryId) {
          await db.pages.update(pageToProcess.id, {
            categoryId: finalCategoryId, isUncategorized: 0,
            aiSuggestedCategoryId: finalCategoryId, isProcessingAi: 0, aiStatus: null
          });
          await learnPattern(pageToProcess.imageUrl, finalCategoryId);
        } else {
          await db.pages.update(pageToProcess.id, { isProcessingAi: 0, aiStatus: "Ready to categorize" });
        }
      }
    } catch (error) {
      console.error("Worker error:", error);
    }
  }, 5000);
}
