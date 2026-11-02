import { GoogleGenerativeAI } from "@google/generative-ai";

// We will bypass the service file to see the exact error
const apiKey = process.env.VITE_GEMINI_KEY;
console.log("Using API Key starting with:", apiKey ? apiKey.substring(0, 5) : "NO KEY FOUND");

const genAI = new GoogleGenerativeAI(apiKey);
const model = genAI.getGenerativeModel({ model: "gemini-3.8-flash" });

const fakeImage = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=";

async function test() {
  try {
    console.log("Sending request to Google...");
    const result = await model.generateContent([
      "Analyze this image. Return JSON.",
      { inlineData: { data: fakeImage, mimeType: "image/jpeg" } }
    ]);
    console.log("SUCCESS!", result.response.text());
  } catch (error) {
    console.error("CRITICAL ERROR:", error.message);
  }
}

test();
