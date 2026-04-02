/* Minimal service worker for Vibilia PWA
   - Precache core icon assets
   - No offline app shell caching
*/

const CACHE_PREFIX = "vibilia-v2";
const PRECACHE_ASSETS = [
  "./favicon.ico",
  "./favicon-16x16.png",
  "./favicon-32x32.png",
  "./apple-touch-icon.png",
  "./android-chrome-192x192.png",
  "./android-chrome-512x512.png",
  "./manifest.webmanifest",
];

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches.open(CACHE_PREFIX).then(async (cache) => {
      for (const asset of PRECACHE_ASSETS) {
        try {
          const res = await fetch(asset, { cache: "no-store" });
          if (!res.ok) throw new Error(`Request failed: ${res.status}`);
          await cache.put(asset, res.clone());
        } catch (err) {
          console.warn("sw: precache failed for", asset, err);
        }
      }
    }),
  );
  self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) =>
        Promise.all(
          keys.filter((k) => k !== CACHE_PREFIX).map((k) => caches.delete(k)),
        ),
      ),
  );
  self.clients.claim();
});
