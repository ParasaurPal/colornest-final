# Google Drive Sync — Setup Guide

ColorNest can back up your coloring pages to Google Drive and keep them in
sync. This guide walks through the one-time Google Cloud setup. It is free,
takes about 15 minutes, and you only do it once.

## How sync works

- The app keeps a **`ColorNest`** folder in your Google Drive.
- Every category becomes a folder inside it, mirroring your category tree
  (for example `ColorNest/Animals/Mammals/Dogs`). Pages without a category go
  in `ColorNest/Untagged`.
- Every page image is uploaded into its category's folder. The first time you
  connect, all of your existing pages are uploaded.
- When the app opens, it scans those folders in the background for images
  **you** added yourself (for example by dragging files into Drive on your
  computer) and imports them into the matching category. You can also tap
  **Sync now** in Settings at any time.
- Moving a page to another category moves its Drive file; renaming a page
  renames the file.
- **Deletion rule: sync never permanently deletes anything from Drive.**
  Deleting a page in the app moves its Drive file to the trash, and a page
  whose Drive file was trashed is removed from the app. Trashed files stay
  recoverable from the Drive trash for 30 days.

## What you need

A Google account and access to
[Google Cloud Console](https://console.cloud.google.com/). No Google app
verification is needed: the app runs in testing mode and only your own
account (added as a test user below) can use it.

## Step 1 — Create a Google Cloud project

1. Go to [Google Cloud Console](https://console.cloud.google.com/).
2. Click the project picker at the top (it may say "Select a project") →
   **New Project**.
3. Name it something like `ColorNest`, then click **Create**.
4. Make sure the new project is selected in the picker.

## Step 2 — Enable the Google Drive API

1. In the left menu, go to **APIs & Services → Library**.
2. Search for **Google Drive API**, open it, and click **Enable**.

## Step 3 — Set up the OAuth consent screen

1. Go to **APIs & Services → OAuth consent screen**.
2. Choose **External** and click **Create**.
3. Fill in the app name (`ColorNest`), your email as user support email, and
   your email as developer contact. Click **Save and Continue**.
4. On **Scopes**, click **Add or Remove Scopes** and add these two:
   - `https://www.googleapis.com/auth/drive.file`
     (create and manage the files the app uploads)
   - `https://www.googleapis.com/auth/drive.readonly`
     (see images you drop into the folders yourself — `drive.file` alone
     cannot see those)
5. Click **Update**, then **Save and Continue**.
6. On **Test users**, click **Add Users** and add your own Google account
   email, then **Save and Continue**.
7. Review the summary and click **Back to Dashboard**. Leave the app in
   **Testing** mode — that is correct for personal use.

## Step 4 — Create the OAuth clients

Go to **APIs & Services → Credentials → Create Credentials → OAuth client ID**.
You need two clients (one per app). Create them one at a time.

### 4a. Android client (for the Kotlin app)

1. Application type: **Android**.
2. Name: `ColorNest Android`.
3. Package name: `com.aistudio.colornest.app`
4. SHA-1 certificate fingerprint: for debug builds this comes from the
   debug keystore. Run this on a machine with Java installed:

   ```sh
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
   ```

   Copy the **SHA1** value (a line of hex pairs like
   `AA:BB:CC:...`) into the form. If you later sign a release build with
   your own key, add a second Android client with that key's SHA-1.
5. Click **Create**. You do **not** need to paste this client ID anywhere:
   Google identifies the Android app by its package name + SHA-1.

### 4b. Web client (for the web / Capacitor app)

1. Application type: **Web application**.
2. Name: `ColorNest Web`.
3. Under **Authorized JavaScript origins**, add:
   - `http://localhost:5173` (for `npm run dev`)
   - `https://localhost` (for the Capacitor-installed build)
4. You can leave **Authorized redirect URIs** empty.
5. Click **Create** and copy the **Client ID** (it ends in
   `.apps.googleusercontent.com`).

## Step 5 — Put the Web client ID in the web app

1. In the project, copy `.env.example` to a new file named `.env`.
2. Replace the placeholder with the Web client ID from step 4b:

   ```text
   VITE_GOOGLE_CLIENT_ID=123456789012-abcdefghijklmnopqrstuvwxyz.apps.googleusercontent.com
   ```

3. Restart the dev server / rebuild the app so the new value is picked up.
   (`.env` is git-ignored — it will never be committed.)

The Android app needs no code change: rebuild/install it as usual.

## Step 6 — Connect inside the app

1. Open ColorNest → **Settings → Google Drive**.
2. Tap **Connect Google Drive** and pick your Google account.
3. Grant access when Google asks. (Google will show an "unverified app"
   warning because the app is in testing mode — tap **Advanced → Go to
   ColorNest** to continue. That warning is expected for personal projects.)
4. The first sync starts right away and uploads your existing pages. You can
   watch progress in Settings, or just keep using the app.

## Settings

- **Sync now** — runs a full sync immediately.
- **Scan Drive on open** (on by default) — checks the Drive folders for
  images you added yourself each time the app opens.
- **Disconnect** — stops syncing. Your Drive files stay where they are, and
  reconnecting later picks up where it left off.
- **Danger Zone → Erase All Images & Data** has a checkbox to also move the
  whole `ColorNest` folder in Drive to trash. As always, that is trash-only,
  never a permanent delete.

## Troubleshooting

- **"Google Drive is not set up yet"** (web app) — the `.env` file is missing
  or `VITE_GOOGLE_CLIENT_ID` is empty. Copy `.env.example` to `.env` and
  fill it in, then restart/rebuild.
- **"Access blocked" / "Error 403: access_denied"** when signing in — your
  Google account is not on the test users list. Go back to step 3 and add it
  under **OAuth consent screen → Test users**.
- **"Sync failed" with a 403** — the Drive API may not be enabled (step 2),
  or the consent screen is missing one of the two scopes (step 3).
- **Sign-in pop-up does nothing in the installed web app** — the
  `https://localhost` origin must be on the Web client's authorized JavaScript
  origins list (step 4b). If the pop-up is still blocked, use the web version
  of the app for the first sign-in, or use the Kotlin app, whose Drive
  sign-in uses the native account picker.
- **A deleted page keeps coming back** — the app records deleted Drive files
  so a scan never re-downloads them. If a page reappears, it was probably
  re-uploaded from another device before the deletion synced; delete it once
  more after a sync.
- **Two folders with the same category name** — categories with identical
  names under the same parent share one Drive folder. Rename one of them in
  the app to separate them.
