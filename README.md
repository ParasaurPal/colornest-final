cat > README.md << 'EOF'
# 🎨 ColorNest

**ColorNest** is a powerful, AI-driven mobile catalog for organizing physical and digital coloring books. Built as a mobile-first application using React, Capacitor, and Local AI.

## ✨ Features
*   **📸 1-Tap Uploads:** Instantly snap or upload photos of coloring pages.
*   **📄 PDF Support:** Upload PDF books and convert them into individual images.
*   **🤖 Hybrid AI:** Uses Google Gemini to guess titles/categories, and learns from your manual corrections locally.
*   **🗂️ Infinite Folders:** Deeply nested categories (e.g., Animals > Mammals > Dogs).
*   **🌙 Dark Mode:** Inverts coloring lines to white for night viewing.
*   **🔍 Search & Tags:** Find any page instantly.
*   **🛡️ Privacy First:** All data is stored locally on your device.

## 🛠️ Tech Stack
*   React 18, Vite, Tailwind CSS
*   Capacitor (Mobile Wrapper)
*   Dexie.js (IndexedDB)
*   Google Gemini API

## 🚀 Local Development
1. Clone: `git clone https://github.com/ParasaurPal/colornest-final.git`
2. Install: `npm install`
3. Key: `export VITE_GEMINI_KEY="your_key"`
4. Run: `npm run dev -- --force`

## 📱 Building the APK
This repository uses GitHub Actions. Push to `main`, go to the **Actions** tab, download the APK from **Artifacts**, and install it!
EOF
