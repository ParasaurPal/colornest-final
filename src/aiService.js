import { GoogleGenerativeAI } from "@google/generative-ai";

const apiKey = import.meta.env.VITE_GEMINI_KEY;
const genAI = new GoogleGenerativeAI(apiKey);

export async function analyzeColoringPage(base64Image) {
  const model = genAI.getGenerativeModel({ model: "gemini-3.8-flash" });

  const prompt = `
    Analyze this coloring page. Return ONLY a JSON object.
    {
      "title": "Short title",
      "category": "Animals, Mandalas & Patterns, Fantasy & Mythical, Botanical & Nature, or Architecture & Places",
      "complexity": "Beginner, Easy, Medium, Advanced, or Expert",
      "time": "e.g. 45m",
      "tool": "Crayons, Colored Pencils, Gel Pens, Watercolor Pencils, or Markers"
    }
  `;

  try {
    const result = await model.generateContent([
      prompt,
      { inlineData: { data: base64Image, mimeType: "image/jpeg" } }
    ]);
    
    const responseText = result.response.text();
    const cleanJson = responseText.replace(/```json/g, '').replace(/```/g, '').trim();
    return JSON.parse(cleanJson);
  } catch (error) {
    console.error("AI Error:", error);
    // If it fails, return an object with the error so the worker can display it
    return { error: error.message.substring(0, 30) + "..." }; 
  }
}
