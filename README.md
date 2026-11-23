# ColorNest

ColorNest is a **PWA-first coloring-page library**. Install it from a supported browser on Android, iOS, Windows, macOS, or Linux. It runs as a standalone web app and keeps the library available offline.

## What it does

- Import individual images and multi-page PDF coloring books.
- Store originals in the browser's local IndexedDB on the phone or computer.
- Organize pages with nested collections and reusable tags.
- Browse and filter by multiple tags, using all-selected or any-selected matching.
- Create, rename, merge, and delete tags.
- Mark favorites, search titles and tags, and view pages in a responsive gallery.
- Install as a PWA and use fullscreen / standalone display modes where supported.
- Export and restore a complete portable JSON library backup.
- Optionally synchronize page images and page metadata with Google Drive.

## Install and fullscreen

- **Android:** open the hosted PWA in Chrome, then use the browser menu → **Install app** or **Add to Home screen**.
- **Desktop:** use the install icon in Chrome/Edge when it appears.
- **iPhone/iPad:** open in Safari → Share → **Add to Home Screen**. iOS opens the installed site in its standalone app window; the explicit Fullscreen button is available only in browsers that support the Fullscreen API.

The manifest requests fullscreen display mode first and falls back to standalone/minimal UI where the platform does not support fullscreen PWAs.

## Storage model

Local browser storage is the working library and is available offline. Each browser/device has its own local copy. Use **Settings → This device & backups** to export a complete backup and restore it on another device. Browser storage quotas and eviction policies vary by platform, so keep a separate backup for important libraries.

Google Drive is optional cloud synchronization. Connect the same Google account on each device. ColorNest stores images in a `ColorNest` folder and one JSON sidecar per page containing the title, nested category path, tags, favorite/completion state, and update timestamp. Sync uses last-write-wins timestamps for metadata. Keep device clocks reasonably accurate and avoid editing the same page simultaneously on multiple devices.

## Development

Requirements: Node.js 22.12+ (or 20.19+) and npm.

```sh
npm ci
npm run dev
```

Production build:

```sh
npm run build
npm run preview
```

The production site must be served over HTTPS (localhost is also treated as a secure context by browsers) for PWA installation, service workers, and Google sign-in.

## Hosted PWA

The repository builds a production PWA artifact on every push and pull request. To publish it at `https://parasaurpal.github.io/colornest-final/`, enable **Settings → Pages → Build and deployment → GitHub Actions**, then add the Actions variable `ENABLE_GITHUB_PAGES=true`. Pull requests run the production build without publishing it. The PWA uses the repository subpath as its install scope.

## Google Drive setup

See [DRIVE_SETUP.md](DRIVE_SETUP.md). Set `VITE_GOOGLE_CLIENT_ID` to a Google OAuth **Web application** client ID before building. It is a public client identifier, not a secret. Never put a client secret in the browser app.

## Android app

The repository also contains the native Kotlin/Jetpack Compose Android app in `app/`. It uses a local Room database and app-private image storage. The Android and PWA clients are built separately and have separate local libraries; connecting both to the same Google account does not currently synchronize every metadata field between clients.

- Android builds use Java 21, Android SDK 36, and the Gradle wrapper.
- GitHub Actions runs `:app:assembleDebug` on relevant pushes and pull requests.
- The debug APK is published as the `ColorNest-Android-debug` workflow artifact.
- The Android client has its own Google Drive setup described in [DRIVE_SETUP.md](DRIVE_SETUP.md).

To build locally:

```sh
./gradlew :app:assembleDebug
```

The PWA build does not compile or package the Android app.
