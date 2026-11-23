// Finds pages whose stored image is byte-for-byte identical. This is a plain
// content hash (SHA-256), not image recognition: the same picture imported
// at a different size or from a different source file will not match.

async function sha256Hex(text) {
  const bytes = new TextEncoder().encode(text);
  const digest = await globalThis.crypto.subtle.digest('SHA-256', bytes);
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, '0')).join('');
}

// Fallback when crypto.subtle is unavailable (non-secure contexts): a fast
// 64-bit string hash. Collisions are far more likely than with SHA-256, so it
// is only a fallback — the PWA is served over HTTPS, where subtle exists.
function cyrb53Hex(text, seed = 0) {
  let h1 = 0xdeadbeef ^ seed;
  let h2 = 0x41c6ce57 ^ seed;
  for (let i = 0, ch; i < text.length; i++) {
    ch = text.charCodeAt(i);
    h1 = Math.imul(h1 ^ ch, 2654435761);
    h2 = Math.imul(h2 ^ ch, 1597334677);
  }
  h1 = Math.imul(h1 ^ (h1 >>> 16), 2246822507) ^ Math.imul(h2 ^ (h2 >>> 13), 3266489909);
  h2 = Math.imul(h2 ^ (h2 >>> 16), 2246822507) ^ Math.imul(h1 ^ (h1 >>> 13), 3266489909);
  return (h2 >>> 0).toString(16).padStart(8, '0') + (h1 >>> 0).toString(16).padStart(8, '0');
}

export async function hashImageUrl(imageUrl) {
  if (globalThis.crypto?.subtle?.digest) {
    return sha256Hex(imageUrl);
  }
  return cyrb53Hex(imageUrl);
}

// Prefer keeping the copy that is filed in a category, then a favorite, then
// a completed page; ties go to the oldest page.
function keepScore(page) {
  return (page.categoryId ? 4 : 0) + (page.isFavorite ? 2 : 0) + (page.isCompleted ? 1 : 0);
}

function sortGroup(pages) {
  return [...pages].sort((a, b) => keepScore(b) - keepScore(a) || a.id - b.id);
}

// Groups pages with identical images. Pages without a stored image
// (preview-only Drive entries) are skipped — there is nothing to compare.
// onProgress(done, total) is called as the scan advances.
export async function findDuplicateGroups(pages, onProgress) {
  const hashable = pages.filter((page) => page.imageUrl);
  const byHash = new Map();
  for (let i = 0; i < hashable.length; i++) {
    const page = hashable[i];
    const hash = await hashImageUrl(page.imageUrl);
    if (!byHash.has(hash)) byHash.set(hash, []);
    byHash.get(hash).push(page);
    if (onProgress && (i % 10 === 9 || i === hashable.length - 1)) {
      onProgress(i + 1, hashable.length);
    }
  }
  const groups = [];
  for (const [hash, members] of byHash) {
    if (members.length < 2) continue;
    groups.push({ hash, pages: sortGroup(members) });
  }
  // Largest groups first.
  groups.sort((a, b) => b.pages.length - a.pages.length);
  return groups;
}
