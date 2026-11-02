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
        
        // 1. BUILD THE CATEGORY TREE WITH FULL PATHS
        const rawCategories = await db.categories.toArray();
        
        const buildPath = (cat, allCats) => {
          let path = cat.name;
          let current = cat;
          while (current.parentId) {
            const parent = allCats.find(c => c.id === current.parentId);
            if (parent) {
              path = `${parent.name} > ${path}`;
              current = parent;
            } else break;
          }
          return path;
        };

        const structuredCategories = rawCategories.map(c => ({
          id: c.id,
          pathString: buildPath(c, rawCategories)
        }));

        let finalCategoryId = null;
        
        // 2. LOCAL AI MEMORY FIRST
        const localGuess = await guessCategoryLocally(pageToProcess.imageUrl);
        if (localGuess) {
          finalCategoryId = localGuess.categoryId;
          await db.pages.update(pageToProcess.id, { aiStatus: "Sorted by Local Memory" });
        } 
        // 3. GOOGLE GEMINI FALLBACK
        else if (aiEnabled) {
          await db.pages.update(pageToProcess.id, { aiStatus: "Asking Google..." });
          const timeoutPromise = new Promise((_, reject) => setTimeout(() => reject(new Error("Timeout")), 10000));
          
          const aiData = await Promise.race([
            analyzeColoringPage(pageToProcess.imageUrl.split(',')[1], structuredCategories),
            timeoutPromise
          ]).catch(() => ({ error: "Timed out" }));

          if (aiData && !aiData.error && aiData.matchedCategoryPath !== 'None') {
            const matchedCategory = structuredCategories.find(c => c.pathString === aiData.matchedCategoryPath);
            
            if (matchedCategory) {
              finalCategoryId = matchedCategory.id;
              await db.pages.update(pageToProcess.id, {
                title: aiData.title, 
                complexity: aiData.complexity,
                time: aiData.time, 
                tool: aiData.tool
              });
            }
          }
        }

        // 4. SAVE RESULTS AND TEACH LOCAL AI
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
