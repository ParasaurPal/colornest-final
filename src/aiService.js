import { GoogleGenerativeAI } from "@google/generative-ai";

const apiKey = import.meta.env.VITE_GEMINI_KEY;
const genAI = new GoogleGenerativeAI(apiKey);

export async function analyzeColoringPage(base64Image, availableCategories) {
  const model = genAI.getGenerativeModel({ model: "gemini-3.8-flash" });
  const categoryList = availableCategories.map(c => c.name).join(', ');

  const prompt = `
    Analyze this coloring page. Return ONLY a valid JSON object.
    {
      "title": "Short descriptive title",
      "complexity": "Beginner, Easy, Medium, Advanced, or Expert",
      "time": "e.g. 45m",
      "tool": "Crayons, Colored Pencils, Gel Pens, Watercolor Pencils, or Markers",
      "tags": ["tag1", "tag2", "tag3"],
      "categoryName": "Choose from this list: ${categoryList || 'None'}. If none fit, return 'None'."
    }
  `;

  try {
    const result = await model.generateContent([prompt, { inlineData: { data: base64Image, mimeType: "image/jpeg" } }]);
    const cleanJson = result.response.text().replace(/```json/g, '').replace(/```/g, '').trim();
    return JSON.parse(cleanJson);
  } catch (error) {
    console.error("AI Error:", error.message);
    return null; 
  }
}
