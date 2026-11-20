const CACHE_NAME = "colornest-pwa-v3";
const BASE_URL = new URL(self.registration.scope).pathname;
const APP_SHELL = [
  BASE_URL,
  BASE_URL + "manifest.webmanifest",
  BASE_URL + "icons/colornest.svg",
  BASE_URL + "pdf.worker.min.mjs",
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
  if (request.method !== "GET" || url.origin !== self.location.origin) return;
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
    url.pathname === BASE_URL + "manifest.webmanifest" ||
    url.pathname === BASE_URL + "pdf.worker.min.mjs";

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
