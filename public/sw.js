const CACHE_NAME = "colornest-pwa-v4";
const BASE_URL = new URL(self.registration.scope).pathname;
const APP_SHELL = [
  BASE_URL,
  BASE_URL + "manifest.webmanifest",
  BASE_URL + "icons/colornest.svg",
];

self.addEventListener("install", (event) => {
  event.waitUntil(caches.open(CACHE_NAME).then((cache) => cache.addAll(APP_SHELL)));
  self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((key) => key.startsWith("colornest-") && key !== CACHE_NAME).map((key) => caches.delete(key)))
    )
  );
  self.clients.claim();
});

self.addEventListener("fetch", (event) => {
  const request = event.request;
  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return;

  // Android share target: a PDF shared to ColorNest is POSTed here. Stash
  // the files in a cache the app reads when it opens, then open the app.
  // The cache name deliberately does not start with "colornest-", so the
  // activate handler above never deletes a share that is still waiting.
  if (request.method === "POST" && url.pathname === BASE_URL + "share-target") {
    event.respondWith((async () => {
      try {
        const formData = await request.formData();
        const files = formData.getAll("shared-files").filter((entry) => entry instanceof File);
        if (files.length > 0) {
          const cache = await caches.open("share-target-pending");
          await Promise.all(files.map((file, index) => {
            const key = `${BASE_URL}share-target/${Date.now()}-${index}-${encodeURIComponent(file.name)}`;
            return cache.put(key, new Response(file, { headers: { "Content-Type": file.type } }));
          }));
        }
      } catch (error) {
        // If the share can't be stashed, still open the app — the file can
        // be imported manually from there.
      }
      return Response.redirect(self.registration.scope, 303);
    })());
    return;
  }

  if (request.method !== "GET") return;
  if (!url.pathname.startsWith(BASE_URL)) return;

  if (request.mode === "navigate") {
    event.respondWith((async () => {
      try {
        const response = await fetch(request);
        if (response.ok) {
          const cache = await caches.open(CACHE_NAME);
          await cache.put(BASE_URL, response.clone());
        }
        return response;
      } catch {
        return (await caches.match(request)) || (await caches.match(BASE_URL));
      }
    })());
    return;
  }

  const isAppAsset =
    url.pathname.startsWith(BASE_URL + "assets/") ||
    url.pathname.startsWith(BASE_URL + "icons/") ||
    url.pathname === BASE_URL + "manifest.webmanifest";

  if (isAppAsset) {
    event.respondWith((async () => {
      const cached = await caches.match(request);
      if (cached) return cached;
      const response = await fetch(request);
      if (response.ok) {
        const cache = await caches.open(CACHE_NAME);
        await cache.put(request, response.clone());
      }
      return response;
    })());
  }
});
