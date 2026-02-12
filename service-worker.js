/**
 * RideMate Service Worker
 * Provides offline support and caching for PWA functionality
 */

const STATIC_CACHE = 'ridemate-static-v1';
const DYNAMIC_CACHE = 'ridemate-dynamic-v1';

// Static assets to cache on install
const STATIC_ASSETS = [
    '/',
    '/index.html',
    '/login.html',
    '/signup.html',
    '/driver-confirmation.html',
    '/passenger-confirmation.html',
    '/style.css',
    '/auth.css',
    '/auth.js',
    '/global-notifications.js',
    '/manifest.json',
    '/icons/icon-192x192.png',
    '/icons/icon-512x512.png'
];

// URLs that should always fetch from network
const NETWORK_ONLY = [
    '/api/',
    '/socket.io/'
];

// Install event - cache static assets
self.addEventListener('install', (event) => {
    console.log('[ServiceWorker] Installing...');

    event.waitUntil(
        caches.open(STATIC_CACHE)
            .then((cache) => {
                console.log('[ServiceWorker] Caching static assets');
                return cache.addAll(STATIC_ASSETS);
            })
            .then(() => {
                console.log('[ServiceWorker] Static assets cached');
                return self.skipWaiting();
            })
            .catch((error) => {
                console.error('[ServiceWorker] Failed to cache:', error);
            })
    );
});

// Activate event - clean up old caches
self.addEventListener('activate', (event) => {
    console.log('[ServiceWorker] Activating...');

    event.waitUntil(
        caches.keys()
            .then((cacheNames) => {
                return Promise.all(
                    cacheNames
                        .filter((cacheName) => {
                            return cacheName !== STATIC_CACHE &&
                                cacheName !== DYNAMIC_CACHE;
                        })
                        .map((cacheName) => {
                            console.log('[ServiceWorker] Deleting old cache:', cacheName);
                            return caches.delete(cacheName);
                        })
                );
            })
            .then(() => {
                console.log('[ServiceWorker] Activated');
                return self.clients.claim();
            })
    );
});

// Fetch event - serve from cache, fall back to network
self.addEventListener('fetch', (event) => {
    const requestUrl = new URL(event.request.url);

    // Skip non-GET requests
    if (event.request.method !== 'GET') {
        return;
    }

    // Network-only for API calls and WebSocket
    if (NETWORK_ONLY.some(path => requestUrl.pathname.startsWith(path))) {
        event.respondWith(
            fetch(event.request)
                .catch(() => {
                    // Return offline response for API calls
                    return new Response(
                        JSON.stringify({ error: 'You are offline' }),
                        {
                            status: 503,
                            headers: { 'Content-Type': 'application/json' }
                        }
                    );
                })
        );
        return;
    }

    // For Ola Maps and external resources - network first with cache fallback
    if (requestUrl.origin !== location.origin) {
        event.respondWith(
            fetch(event.request)
                .then((response) => {
                    // Don't cache non-successful responses
                    if (!response || response.status !== 200) {
                        return response;
                    }

                    // Clone and cache the response
                    const responseClone = response.clone();
                    caches.open(DYNAMIC_CACHE)
                        .then((cache) => cache.put(event.request, responseClone));

                    return response;
                })
                .catch(() => caches.match(event.request))
        );
        return;
    }

    // For same-origin requests - cache first, then network
    event.respondWith(
        caches.match(event.request)
            .then((cachedResponse) => {
                if (cachedResponse) {
                    // Return cached version and update cache in background
                    fetch(event.request)
                        .then((networkResponse) => {
                            if (networkResponse && networkResponse.status === 200) {
                                caches.open(STATIC_CACHE)
                                    .then((cache) => cache.put(event.request, networkResponse));
                            }
                        })
                        .catch(() => { });

                    return cachedResponse;
                }

                // Not in cache - fetch from network
                return fetch(event.request)
                    .then((networkResponse) => {
                        // Cache the response for future
                        if (networkResponse && networkResponse.status === 200) {
                            const responseClone = networkResponse.clone();
                            caches.open(DYNAMIC_CACHE)
                                .then((cache) => cache.put(event.request, responseClone));
                        }
                        return networkResponse;
                    })
                    .catch(() => {
                        // Offline fallback for HTML pages
                        if (event.request.headers.get('accept').includes('text/html')) {
                            return caches.match('/index.html');
                        }
                    });
            })
    );
});

// Push notification event (for future implementation)
self.addEventListener('push', (event) => {
    console.log('[ServiceWorker] Push received');

    const options = {
        body: event.data ? event.data.text() : 'New notification from RideMate',
        icon: '/icons/icon-192x192.png',
        badge: '/icons/icon-72x72.png',
        vibrate: [100, 50, 100],
        data: {
            dateOfArrival: Date.now(),
            primaryKey: 1
        },
        actions: [
            { action: 'view', title: 'View' },
            { action: 'dismiss', title: 'Dismiss' }
        ]
    };

    event.waitUntil(
        self.registration.showNotification('RideMate', options)
    );
});

// Notification click event
self.addEventListener('notificationclick', (event) => {
    console.log('[ServiceWorker] Notification clicked');
    event.notification.close();

    if (event.action === 'view') {
        event.waitUntil(
            clients.openWindow('/')
        );
    }
});

// Background sync (for offline ride requests)
self.addEventListener('sync', (event) => {
    console.log('[ServiceWorker] Sync event:', event.tag);

    if (event.tag === 'sync-ride-requests') {
        event.waitUntil(syncRideRequests());
    }
});

// Sync pending ride requests when back online
async function syncRideRequests() {
    try {
        const pendingRequests = await getPendingRequests();

        for (const request of pendingRequests) {
            await fetch('/api/ride-requests', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(request)
            });
        }

        await clearPendingRequests();
    } catch (error) {
        console.error('[ServiceWorker] Sync failed:', error);
    }
}

// IndexedDB helpers for offline storage (placeholder)
async function getPendingRequests() {
    // TODO: Implement IndexedDB storage
    return [];
}

async function clearPendingRequests() {
    // TODO: Implement IndexedDB storage
}
