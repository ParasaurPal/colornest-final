# 🎨 ColorNest (Android - Kotlin & Jetpack Compose)

**ColorNest** is an AI-driven Android catalog and organizer for physical coloring books, printed pages, line art, and PDF coloring books. Built with modern Android development standards using **Kotlin**, **Jetpack Compose**, and **Room Database**.

## ✨ Features
* **📸 1-Tap Media Import:** Native Photo Picker and document picker for single images and multi-page PDF coloring books.
* **📄 Built-in PDF Renderer:** Converts multi-page PDF books into high-resolution, compressed digital coloring page cards on-device using Android's `PdfRenderer`.
* **🧠 Local AI Memory (Perceptual Hashing):** Features a custom 8x8 downscaled grayscale perceptual hash algorithm (`LocalBrain`) that remembers visual patterns and auto-sorts pages based on learned user corrections without needing cloud connectivity.
* **🤖 Smart Auto-Categorization:** Background Coroutines worker periodically inspects uncategorized pages, consults local perceptual memory first, and matches category paths, complexity, and tools.
* **🗂️ Infinite Tree Categories:** Deeply nested category hierarchies (e.g., Animals > Mammals > Canines > Dogs) with expandable/collapsible nodes and recursive subtree filtering.
* **🌙 Dark Mode & Line Inversion:** Night viewing mode with a custom color filter that inverts coloring lines to crisp white against a dark canvas.
* **❤️ Favorites & Tags:** Mark favorites with instant heart toggles and filter by comma-separated tags or title search.
* **🚀 External Share / Open:** Secure `FileProvider` integration allows opening and sharing any coloring page directly with external digital painting or printing apps.
* **🛡️ Privacy First & Danger Zone:** 100% on-device local storage powered by Android Room Database with a 4-step secure data erase workflow.

## 🛠️ Architecture & Tech Stack
* **Language:** Kotlin 2.1
* **UI Framework:** Jetpack Compose with Material Design 3 (M3)
* **Architecture:** Clean Architecture & MVVM (StateFlow, Coroutines)
* **Local Persistence:** Android Jetpack Room Database (`ColorNestDatabase`)
* **Image Loading:** Coil Compose
* **Build System:** Gradle Kotlin DSL (`build.gradle.kts`, `settings.gradle.kts`)
