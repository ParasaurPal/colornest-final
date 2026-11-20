# Google Drive sync setup

ColorNest keeps the working library on the current device and optionally synchronizes it with Google Drive. Connect the **same Google account** from every device that should share the library.

## What sync stores

ColorNest creates a `ColorNest` folder in Drive. Images are stored in category folders. Each page also has a hidden-style JSON sidecar file named `.colornest-page-<id>.json` in the ColorNest root. The sidecar carries the stable page identity, title, nested category path, tags, favorite/completion state, and last-edit timestamp. This is what lets another device reconstruct the same library rather than merely downloading anonymous images.

Sync is two-way:
- New local images are uploaded.
- New images placed in the ColorNest folders in Drive are imported.
- Page metadata is synchronized separately from the image.
- Category paths are recreated on another device when needed.
- Local deletes move the image to Drive trash; sync does not permanently delete Drive files.

Metadata conflict policy is last-write-wins using the page's update timestamp. Keep device clocks accurate and avoid simultaneous edits to the same page on multiple devices. This is not a collaborative real-time editor.

## Google Cloud setup

1. Open [Google Cloud Console](https://console.cloud.google.com/) and create/select a project.
2. Enable **Google Drive API**.
3. Configure the OAuth consent screen as External. Add your Google account as a test user while the app is in Testing mode.
4. Create an OAuth client ID with application type **Web application**.
5. Add authorized JavaScript origins for each place you run ColorNest, for example:
   - `http://localhost:5173` for local development.
   - `https://parasaurpal.github.io` for the GitHub Pages deployment (the origin does not include `/colornest-final/`).
   No redirect URI is required for Google Identity Services token flow.
6. Add these scopes to the consent configuration:
   - `https://www.googleapis.com/auth/drive.file`
   - `https://www.googleapis.com/auth/drive.readonly`
7. Copy the Web client ID. Do not create or embed a client secret.

## Configure the app

Create a local `.env` file from `.env.example` and set:

```text
VITE_GOOGLE_CLIENT_ID=your-web-client-id.apps.googleusercontent.com
```

Restart the dev server or rebuild after changing the value. For GitHub Pages, add a repository Actions variable named `VITE_GOOGLE_CLIENT_ID` under **Settings → Secrets and variables → Actions → Variables**. The PWA build workflow injects that value at build time.

## Hosted app

The PWA code is merged into `main`. To publish it, open repository **Settings → Pages**, choose **GitHub Actions** under Build and deployment, then open **Settings → Secrets and variables → Actions → Variables** and add `ENABLE_GITHUB_PAGES` with value `true`. The next push to `main` will deploy to `https://parasaurpal.github.io/colornest-final/`. Also add `VITE_GOOGLE_CLIENT_ID` there to enable Google Drive sync in the hosted build.

## Connect devices

1. Open ColorNest on the first device.
2. Go to **Settings → Google Drive → Connect Google Drive**.
3. Sign in and grant access.
4. Run **Sync now** and let the first upload finish.
5. On each additional device, open the same hosted PWA, connect the same Google account, and run **Sync now**.

The initial OAuth consent screen may show Google's unverified-app warning while your project remains in Testing mode. Only accounts listed as test users can connect in that mode.

## Important storage notes

- Local IndexedDB remains the offline working copy on each device.
- Drive is optional; disconnecting does not delete local data or Drive files.
- Use **Settings → This device & backups → Export backup** for a portable backup independent of Google Drive.
- Browser storage quotas vary. Export important libraries periodically.
- Access tokens are managed by Google Identity Services in the browser. Never share your OAuth client secret (the app does not need one).

## Troubleshooting

- **Drive setup missing:** the built app does not contain `VITE_GOOGLE_CLIENT_ID`. Configure the build environment and rebuild.
- **Access denied:** check that the signed-in Google account is listed as a test user.
- **403 from Drive:** verify that Drive API is enabled and the requested scopes are configured.
- **Origin not allowed:** add the exact scheme + hostname (and port, when present) to the Web OAuth client's authorized JavaScript origins.
- **Sync appears incomplete:** open Settings and use Sync now. Check the status text for uploaded/downloaded images and metadata updates.
