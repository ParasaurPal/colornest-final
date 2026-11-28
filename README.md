# ColorNest (Android)

ColorNest is a native Android **Kotlin & Jetpack Compose** coloring-page library and catalog manager. It keeps your coloring pages, multi-page PDF coloring books, nested categories, and tags organized locally in a Room database, with optional Google Drive integration.

## Features

- **Import Images & PDF Coloring Books**: Import multiple images via the Android Photo Picker, PDF books via the system document picker (`PdfRenderer`), or files shared directly from other apps via Android's Share sheet (`ACTION_SEND` / `ACTION_SEND_MULTIPLE`).
- **Transparent Black-Ink Paper Removal**: Automatically converts white/near-white paper and gray tones into crisp black line art on transparent PNG paper (with an option to keep white backgrounds, plus batch conversion for existing library pages).
- **Nested Categories & Multi-Tag Browser**: Organize pages in deeply nested folders (`Animals > Mammals > Canines`), create/rename/delete reusable tags, filter using multiple tags with **All** or **Any** matching, filter untagged pages, mark Favorites and Completed status, and sort by recency, title, or favorites.
- **Fullscreen Viewer & Dark Mode**: Tap any card to view full-resolution pages with pinch-to-zoom and double-tap zoom. Dark mode automatically inverts line art for comfortable nighttime browsing.
- **Drawing App & System Integration**:
  - Open pages directly as a new canvas in **Clip Studio Paint**, via `ACTION_VIEW` / `ACTION_EDIT`, or save to `Pictures/ColorNest`.
  - Expose saved local pages to external drawing apps via `PickPageActivity` (`GET_CONTENT` / `PICK`) and `ColorNestDocumentsProvider` (Android Storage Access Framework).
- **Backups & Duplicate Finder**: Export and restore portable JSON backups (`ColorNest backup` format) and detect/remove byte-identical duplicate images (SHA-256) while keeping preferred categorized/favorite copies.
- **Google Drive Sync & Browser**: Connect your Google account (`DriveConnectActivity`) to browse your `ColorNest` Drive folder, import lightweight previews on launch or on demand, process the `PDF Inbox`, and download full-resolution pages on demand.
## Architecture & Tech Stack

- **UI**: Jetpack Compose, Material Design 3, Edge-to-Edge (`enableEdgeToEdge()`), Coil
- **Persistence**: Room (`ColorNestDatabase` v4 with `Page`, `Category`, `TagEntity`, `Setting`) + app-private file storage (`filesDir/colornest`)
- **Cloud**: Google Drive REST API v3 + Google Play Services Auth
- **Requirements**: Java 21, Android SDK 36, Min SDK 26

## Building

No Firebase or AI configuration is required.

```sh
./gradlew :app:assembleDebug
```
