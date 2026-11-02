import { db } from './db';

// A simple function to create a "fingerprint" of the image based on brightness
export async function generateImageFingerprint(base64Image) {
  return new Promise((resolve) => {
    const img = new Image();
    img.src = base64Image;
    img.onload = () => {
      const canvas = document.createElement('canvas');
      // Shrink image to 8x8 pixels to get average brightness
      canvas.width = 8;
      canvas.height = 8;
      const ctx = canvas.getContext('2d');
      ctx.drawImage(img, 0, 0, 8, 8);
      
      const imgData = ctx.getImageData(0, 0, 8, 8).data;
      const fingerprint = [];
      for (let i = 0; i < imgData.length; i += 4) {
        // Convert to grayscale average
        const brightness = (imgData[i] + imgData[i+1] + imgData[i+2]) / 3;
        fingerprint.push(brightness);
      }
      resolve(fingerprint);
    };
  });
}

// Compare two fingerprints (returns a score from 0 to 1, where 1 is identical)
export function compareFingerprints(fp1, fp2) {
  if (!fp1 || !fp2 || fp1.length !== fp2.length) return 0;
  let similarity = 0;
  for (let i = 0; i < fp1.length; i++) {
    // Check how close the brightness is
    if (Math.abs(fp1[i] - fp2[i]) < 20) similarity++;
  }
  return similarity / fp1.length;
}

// Save a new pattern to memory when the user corrects the AI
export async function learnPattern(base64Image, category) {
  const fingerprint = await generateImageFingerprint(base64Image);
  await db.learnedPatterns.add({ fingerprint, category });
  console.log(`🧠 Learned new pattern for: ${category}`);
}

// Try to guess the category based on past memory
export async function guessCategoryLocally(base64Image) {
  const fingerprint = await generateImageFingerprint(base64Image);
  const patterns = await db.learnedPatterns.toArray();
  
  let bestMatch = null;
  let highestScore = 0;

  for (const pattern of patterns) {
    const score = compareFingerprints(fingerprint, pattern.fingerprint);
    if (score > highestScore) {
      highestScore = score;
      bestMatch = pattern.category;
    }
  }

  // If we are 85% confident, use the local guess
  if (highestScore > 0.85) {
    console.log(`🧠 Local AI guessed: ${bestMatch} (Confidence: ${Math.round(highestScore * 100)}%)`);
    return { category: bestMatch, confidence: highestScore };
  }
  
  return null; // Not confident enough, fall back to Google
}
