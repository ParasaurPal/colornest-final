// Google Drive configuration for the web / Capacitor app.
//
// The Web OAuth client ID comes from a `.env` file (see `.env.example` and
// DRIVE_SETUP.md). A client ID is a public identifier, not a secret, but it
// is kept out of the repo so each install can use its own Google Cloud
// project. With no client ID configured, Drive simply shows "not connected".
export const GOOGLE_CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID || '';

// DRIVE_FILE lets the app create and manage the files it uploads.
// DRIVE_READONLY lets it see images the user drops into the folders
// themselves — DRIVE_FILE alone cannot see those.
export const DRIVE_SCOPES =
  'https://www.googleapis.com/auth/drive.file https://www.googleapis.com/auth/drive.readonly';

export const DRIVE_ROOT_FOLDER_NAME = 'ColorNest';
export const DRIVE_UNTAGGED_FOLDER_NAME = 'Untagged';

export function isDriveConfigured() {
  return GOOGLE_CLIENT_ID !== '';
}
