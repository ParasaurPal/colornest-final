import { db } from './db';

export async function generateImageFingerprint(base64Image) {
  return new Promise((resolve) => {
    const img = new Image();
    img.src = base64Image;
    img.onload = () => {
      const canvas = document.createElement('canvas');
      canvas.width = 8; canvas.height = 8;
      const ctx = canvas.getContext('2d');
      ctx.drawImage(img, 0, 0, 8, 8);
      const imgData = ctx.getImageData(0, 0, 8, 8).data;
      const fingerprint = [];
      for (let i = 0; i < imgData.length; i += 4) {
        const brightness = (imgData[i] + imgData[i+1] + imgData[i+2]) / 3;
        fingerprint.push(brightness);
      }
      resolve(fingerprint);
    };
  });
}

export function compareFingerprints(fp1, fp2) {
  if (!fp1 || !fp2 || fp1.length !== fp2.length) return 0;
  let similarity = 0;
  for (let i = 0; i < fp1.length; i++) {
    if (Math.abs(fp1[i] - fp2[i]) < 20) similarity++;
  }
  return similarity / fp1.length;
}

export async function learnPattern(base64Image, categoryId) {
  if (!categoryId) return;
  const fingerprint = await generateImageFingerprint(base64Image);
  const existing = await db.learnedPatterns.toArray();
  const isDuplicate = existing.some(p => compareFingerprints(p.fingerprint, fingerprint) > 0.95);
  
  if (!isDuplicate) {
    await db.learnedPatterns.add({ fingerprint, categoryId });
    console.log(`🧠 Learned new pattern for Category ID: ${categoryId}`);
  }
}

export async function guessCategoryLocally(base64Image) {
  const fingerprint = await generateImageFingerprint(base64Image);
  const patterns = await db.learnedPatterns.toArray();
  
  let bestMatch = null;
  let highestScore = 0;

  for (const pattern of patterns) {
    const score = compareFingerprints(fingerprint, pattern.fingerprint);
    if (score > highestScore) {
      highestScore = score;
      bestMatch = pattern.categoryId;
    }
  }

  if (highestScore > 0.85) {
    return { categoryId: bestMatch, confidence: highestScore };
  }
  return null;
}

// THIS IS THE MISSING FUNCTION
export async function resetAIBrain() {
  await db.learnedPatterns.clear();
}
