/* Minimal service worker for Abeona PWA, and the Web Share Target's receiver */

const CACHE_PREFIX = "abeona-v1";
const PRECACHE_ASSETS = ["./favicon.ico", "./manifest.webmanifest"];

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

// Web Share Target (manifest.webmanifest, share_target): Android's share
// sheet POSTs a DB Navigator text or a ticket PDF to ./share-target. Pages
// are static, so the worker keeps what was shared in its cache and opens the
// search page, which reads it on the device (src/lib/intake/shared.ts — keep
// the names in step).
const SHARE_TEXT = "share/text";
const SHARE_FILE = "share/file";

self.addEventListener("fetch", (event) => {
  const url = new URL(event.request.url);
  if (event.request.method !== "POST" || url.pathname !== new URL("./share-target", self.registration.scope).pathname) return;
  event.respondWith(
    (async () => {
      const scope = self.registration.scope;
      try {
        const form = await event.request.formData();
        const cache = await caches.open(CACHE_PREFIX);
        const shared = {
          title: String(form.get("title") ?? ""),
          text: String(form.get("text") ?? ""),
          url: String(form.get("url") ?? ""),
        };
        await cache.put(new URL(SHARE_TEXT, scope).href, new Response(JSON.stringify(shared), { headers: { "Content-Type": "application/json" } }));
        const file = form.get("ticket");
        if (file && typeof file !== "string" && file.size > 0) {
          await cache.put(
            new URL(SHARE_FILE, scope).href,
            new Response(file, {
              headers: { "Content-Type": file.type || "application/pdf", "X-File-Name": encodeURIComponent(file.name || "ticket.pdf") },
            }),
          );
        }
      } catch (err) {
        console.warn("sw: could not keep the shared data", err);
      }
      return Response.redirect(new URL("search?intake=shared", scope).href, 303);
    })(),
  );
});
