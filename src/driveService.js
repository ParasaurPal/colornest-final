// Google Drive API access for the ColorNest PWA.
// Google Identity Services supplies short-lived browser access tokens; no
// backend server or embedded client secret is required. Configure the exact
// HTTPS PWA origin as an authorized JavaScript origin in Google Cloud.
import { GOOGLE_CLIENT_ID, DRIVE_SCOPES, isDriveConfigured } from './driveConfig';

const DRIVE_API = 'https://www.googleapis.com/drive/v3';
const DRIVE_UPLOAD_API = 'https://www.googleapis.com/upload/drive/v3';
const GIS_SCRIPT = 'https://accounts.google.com/gsi/client';

const LS_TOKEN = 'colornest_drive_token';
const LS_TOKEN_EXPIRY = 'colornest_drive_token_expiry';
const LS_ACCOUNT_EMAIL = 'colornest_drive_account';
const LS_CONNECTED = 'colornest_drive_connected';

const FOLDER_MIME_TYPE = 'application/vnd.google-apps.folder';

let gisLoadPromise = null;
let tokenClient = null;
let tokenRequest = null;

function loadGisScript() {
  if (window.google?.accounts?.oauth2) return Promise.resolve();
  if (gisLoadPromise) return gisLoadPromise;
  gisLoadPromise = new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = GIS_SCRIPT;
    script.async = true;
    script.defer = true;
    script.onload = () => resolve();
    script.onerror = () => reject(new Error('Could not load Google sign-in. Check your connection and try again.'));
    document.head.appendChild(script);
  });
  return gisLoadPromise;
}

function getTokenClient() {
  if (tokenClient) return tokenClient;
  tokenClient = window.google.accounts.oauth2.initTokenClient({
    client_id: GOOGLE_CLIENT_ID,
    scope: DRIVE_SCOPES,
    callback: (response) => {
      if (tokenRequest) {
        const { resolve, reject } = tokenRequest;
        tokenRequest = null;
        if (response.error) {
          reject(new Error(response.error_description || response.error));
        } else {
          resolve(response);
        }
      }
    },
    error_callback: (error) => {
      if (tokenRequest) {
        const { reject } = tokenRequest;
        tokenRequest = null;
        reject(new Error(error.message || 'Google sign-in failed'));
      }
    },
  });
  return tokenClient;
}

function storeToken(response) {
  const expiresAt = Date.now() + (response.expires_in || 3600) * 1000;
  localStorage.setItem(LS_TOKEN, response.access_token);
  localStorage.setItem(LS_TOKEN_EXPIRY, String(expiresAt));
  localStorage.setItem(LS_CONNECTED, '1');
}

function clearStoredToken() {
  localStorage.removeItem(LS_TOKEN);
  localStorage.removeItem(LS_TOKEN_EXPIRY);
}

function getStoredToken() {
  const token = localStorage.getItem(LS_TOKEN);
  const expiry = Number(localStorage.getItem(LS_TOKEN_EXPIRY) || 0);
  // Treat tokens expiring within the next minute as expired.
  if (!token || Date.now() > expiry - 60 * 1000) return null;
  return token;
}

async function requestNewToken(prompt) {
  if (!isDriveConfigured()) {
    throw new Error('Google Drive is not set up yet — add your client ID first (see DRIVE_SETUP.md).');
  }
  await loadGisScript();
  const client = getTokenClient();
  const response = await new Promise((resolve, reject) => {
    tokenRequest = { resolve, reject };
    client.requestAccessToken({ prompt });
  });
  storeToken(response);
  return response.access_token;
}

/** Returns a valid access token, refreshing silently when possible. */
export async function getAccessToken() {
  const cached = getStoredToken();
  if (cached) return cached;
  // Empty prompt: with an active Google session this completes without
  // asking the user again.
  return requestNewToken('');
}

/** Interactive sign-in: always shows the account chooser / consent screen. */
export async function connectDrive() {
  const token = await requestNewToken('consent');
  // Look up the account email for display in Settings.
  try {
    const res = await fetch('https://www.googleapis.com/oauth2/v3/userinfo', {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (res.ok) {
      const info = await res.json();
      if (info.email) localStorage.setItem(LS_ACCOUNT_EMAIL, info.email);
    }
  } catch (e) {
    // Non-fatal: the connection still works without the email.
    console.log('Could not read account email', e);
  }
  return token;
}

export async function disconnectDrive() {
  const token = localStorage.getItem(LS_TOKEN);
  clearStoredToken();
  localStorage.removeItem(LS_ACCOUNT_EMAIL);
  localStorage.removeItem(LS_CONNECTED);
  if (token) {
    try {
      await fetch(`https://oauth2.googleapis.com/revoke?token=${token}`, { method: 'POST' });
    } catch {
      // Non-fatal: the token is already forgotten locally.
    }
  }
}

export function isDriveConnected() {
  return localStorage.getItem(LS_CONNECTED) === '1' || getStoredToken() !== null;
}

export function getDriveAccountEmail() {
  return localStorage.getItem(LS_ACCOUNT_EMAIL);
}

// ---------- Drive API helpers ----------

async function driveFetch(path, options = {}) {
  const token = await getAccessToken();
  const res = await fetch(`${DRIVE_API}${path}`, {
    ...options,
    headers: { Authorization: `Bearer ${token}`, ...(options.headers || {}) },
  });
  if (res.status === 401) {
    clearStoredToken();
    throw new Error('Drive session expired — please reconnect Google Drive in Settings.');
  }
  if (!res.ok) {
    const detail = await res.text().catch(() => '');
    throw new Error(`Drive request failed (${res.status}): ${detail.slice(0, 200)}`);
  }
  return res;
}

function escapeQueryValue(value) {
  return value.replace(/'/g, "\\'");
}

export async function findFolder(name, parentId) {
  const q = `name = '${escapeQueryValue(name)}' and mimeType = '${FOLDER_MIME_TYPE}' and trashed = false and '${parentId}' in parents`;
  const res = await driveFetch(`/files?q=${encodeURIComponent(q)}&fields=files(id)&pageSize=10`);
  const data = await res.json();
  return data.files?.[0]?.id || null;
}

export async function createFolder(name, parentId) {
  const res = await driveFetch('/files?fields=id', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, mimeType: FOLDER_MIME_TYPE, parents: [parentId] }),
  });
  const data = await res.json();
  return data.id;
}

export async function getOrCreateFolder(name, parentId) {
  const existing = await findFolder(name, parentId);
  if (existing) return existing;
  return createFolder(name, parentId);
}

export async function listChildren(folderId) {
  const out = [];
  let pageToken = null;
  do {
    const q = `'${folderId}' in parents and trashed = false`;
    let url = `/files?q=${encodeURIComponent(q)}&fields=nextPageToken,files(id,name,mimeType,parents,thumbnailLink)&pageSize=1000`;
    if (pageToken) url += `&pageToken=${pageToken}`;
    const res = await driveFetch(url);
    const data = await res.json();
    for (const file of data.files || []) {
      out.push({
        id: file.id,
        name: file.name,
        mimeType: file.mimeType,
        parentIds: file.parents || [],
        thumbnailLink: file.thumbnailLink || null,
      });
    }
    pageToken = data.nextPageToken;
  } while (pageToken);
  return out;
}

/** Lists the subfolders of a folder, for the Drive folder picker. */
export async function listFolders(parentId) {
  const q = `'${parentId}' in parents and mimeType = '${FOLDER_MIME_TYPE}' and trashed = false`;
  const res = await driveFetch(`/files?q=${encodeURIComponent(q)}&fields=files(id,name)&orderBy=name&pageSize=1000`);
  const data = await res.json();
  return (data.files || []).map((file) => ({ id: file.id, name: file.name }));
}

/** Recursively lists every non-folder file under a folder. */
export async function listTree(rootId) {
  const out = [];
  const queue = [rootId];
  while (queue.length > 0) {
    const folderId = queue.shift();
    for (const child of await listChildren(folderId)) {
      if (child.mimeType === FOLDER_MIME_TYPE) {
        queue.push(child.id);
      } else {
        out.push(child);
      }
    }
  }
  return out;
}

/**
 * Uploads a file with a multipart/related body (Drive's uploadType=multipart
 * needs multipart/related, which FormData cannot produce).
 */
export async function uploadFile(blob, name, folderId, mimeType) {
  const token = await getAccessToken();
  const boundary = 'colornest-' + Math.random().toString(36).slice(2);
  const metadata = JSON.stringify({ name, parents: [folderId] });
  const body = new Blob(
    [
      `--${boundary}\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n${metadata}\r\n` +
        `--${boundary}\r\nContent-Type: ${mimeType}\r\n\r\n`,
      blob,
      `\r\n--${boundary}--`,
    ],
    { type: `multipart/related; boundary=${boundary}` }
  );
  const res = await fetch(
    `${DRIVE_UPLOAD_API}/files?uploadType=multipart&fields=id`,
    {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': `multipart/related; boundary=${boundary}`,
      },
      body,
    }
  );
  if (res.status === 401) {
    clearStoredToken();
    throw new Error('Drive session expired — please reconnect Google Drive in Settings.');
  }
  if (!res.ok) {
    const detail = await res.text().catch(() => '');
    throw new Error(`Drive upload failed (${res.status}): ${detail.slice(0, 200)}`);
  }
  const data = await res.json();
  return data.id;
}

export async function downloadFile(fileId) {
  const res = await driveFetch(`/files/${fileId}?alt=media`);
  return res.blob();
}

/** Reads a file's name, type and parents without downloading its contents. */
export async function getFileMetadata(fileId) {
  const res = await driveFetch(`/files/${fileId}?fields=id,name,mimeType,parents`);
  const data = await res.json();
  return { id: data.id, name: data.name, mimeType: data.mimeType, parents: data.parents || [] };
}

// Drive thumbnail links end with a size suffix like =s220; ask for a
// larger preview when one is needed.
export function largeThumbnailUrl(link) {
  if (!link) return null;
  return link.replace(/=s\d+$/, '=s1600');
}

/**
 * Downloads a Drive thumbnail and returns it as a data URL, so previews
 * work offline and don't depend on short-lived thumbnail links. Returns
 * null when the file has no thumbnail or the download fails (callers fall
 * back to using the thumbnail link directly).
 */
export async function fetchThumbnailDataUrl(thumbnailLink) {
  if (!thumbnailLink) return null;
  try {
    const token = await getAccessToken();
    const res = await fetch(thumbnailLink, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!res.ok) return null;
    const blob = await res.blob();
    return await new Promise((resolve) => {
      const reader = new FileReader();
      reader.onloadend = () => resolve(reader.result);
      reader.onerror = () => resolve(null);
      reader.readAsDataURL(blob);
    });
  } catch {
    return null;
  }
}

export async function moveFile(fileId, currentParentIds, newParentId) {
  const params = new URLSearchParams({
    addParents: newParentId,
    removeParents: currentParentIds.join(','),
  });
  await driveFetch(`/files/${fileId}?${params.toString()}`, { method: 'PATCH' });
}

export async function renameFile(fileId, newName) {
  await driveFetch(`/files/${fileId}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name: newName }),
  });
}

/** Moves a file to the Drive trash. Never permanently deletes. */
export async function trashFile(fileId) {
  await driveFetch(`/files/${fileId}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ trashed: true }),
  });
}

/**
 * Checks a file that is no longer in the ColorNest tree: was it trashed,
 * permanently deleted, or just moved somewhere else by the user?
 */
export async function checkRemoteState(fileId) {
  try {
    const res = await driveFetch(`/files/${fileId}?fields=id,trashed`);
    const data = await res.json();
    return data.trashed ? 'trashed' : 'exists-elsewhere';
  } catch (e) {
    if (String(e.message).includes('(404)')) return 'gone';
    // Network blip — do not delete anything on a maybe.
    return 'exists-elsewhere';
  }
}


/** Find an app-owned file by name within a Drive folder. */
export async function findFileByName(name, parentId) {
  const q = `name = '${escapeQueryValue(name)}' and trashed = false and '${parentId}' in parents`;
  const res = await driveFetch(`/files?q=${encodeURIComponent(q)}&fields=files(id,name,mimeType,parents,modifiedTime)&pageSize=10`);
  const data = await res.json();
  return data.files?.[0] || null;
}

/** Replace the content of an existing Drive file without changing its ID. */
export async function updateFileContent(fileId, blob, mimeType) {
  const token = await getAccessToken();
  const res = await fetch(`${DRIVE_UPLOAD_API}/files/${encodeURIComponent(fileId)}?uploadType=media&fields=id`, {
    method: 'PATCH',
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': mimeType },
    body: blob,
  });
  if (res.status === 401) {
    clearStoredToken();
    throw new Error('Drive session expired — please reconnect Google Drive in Settings.');
  }
  if (!res.ok) {
    const detail = await res.text().catch(() => '');
    throw new Error(`Drive update failed (${res.status}): ${detail.slice(0, 200)}`);
  }
  return res.json();
}


