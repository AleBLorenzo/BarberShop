/* TheTankBarber — service worker mínimo (PWA).
   - Navegaciones: network-first con fallback offline a la home cacheada.
   - Assets con hash (/assets/*, immutable): cache-first.
   - Todo lo demás (API cross-origin, llamadas de datos): directo a red,
     sin cachear (evita servir datos de reservas obsoletos). */
const CACHE = 'tb-shell-v2';

self.addEventListener('install', () => self.skipWaiting());

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener('fetch', (event) => {
  const { request } = event;
  const url = new URL(request.url);

  // Solo GET del mismo origen; el resto (API, cross-origin) va directo a red.
  if (request.method !== 'GET' || url.origin !== self.location.origin) return;

  // Assets con hash: cache-first (inmutables por nombre).
  if (url.pathname.startsWith('/assets/')) {
    event.respondWith(
      caches.open(CACHE).then(async (cache) => {
        const cached = await cache.match(request);
        if (cached) return cached;
        const response = await fetch(request);
        if (response.ok) cache.put(request, response.clone());
        return response;
      }),
    );
    return;
  }

  // Navegaciones: network-first con fallback offline.
  if (request.mode === 'navigate') {
    event.respondWith(
      fetch(request)
        .then((response) => {
          const copy = response.clone();
          caches.open(CACHE).then((cache) => cache.put('/', copy));
          return response;
        })
        .catch(() => caches.match('/')),
    );
  }
});
