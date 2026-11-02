import { GoogleGenerativeAI } from "@google/generative-ai";

const apiKey = import.meta.env.VITE_GEMINI_KEY;
const genAI = new GoogleGenerativeAI(apiKey);

export async function analyzeColoringPage(base64Image, availableCategories) {
  const model = genAI.getGenerativeModel({ model: "gemini-3.8-flash" });

  // Create a formatted string of all your custom folders for the AI to read
  const categoryList = availableCategories.map(c => c.pathString).join("\n");

  const prompt = `
    Analyze this coloring page. 
    First, generate a short descriptive Title, Complexity (Beginner, Easy, Medium, Advanced, Expert), estimated Time (e.g. 45m), and Tool (Crayons, Colored Pencils, etc).
    Second, look at the list of available categories below and choose the EXACT best fit for this image.
    
    Available Categories:
    ${categoryList || 'None'}
    
    Return ONLY a valid JSON object with this schema:
    {
      "title": "Short descriptive title",
      "complexity": "Beginner",
      "time": "45m",
      "tool": "Crayons",
      "matchedCategoryPath": "The exact string from the list above that best fits, or 'None' if nothing fits."
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
    console.error("AI Error:", error.message);
    return null; 
  }
}
