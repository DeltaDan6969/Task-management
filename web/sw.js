/**
 * ShiftTask Service Worker
 * Enables PWA installation, offline shell caching, and Web Push / Lock Screen Notifications for iPhone (iOS 16.4+) & Android.
 */

const CACHE_NAME = 'shifttask-cache-v1.6';
const ASSETS_TO_CACHE = [
  './',
  './tasks.html',
  './index.html',
  './manifest.json',
  './icon-192.png',
  './icon-512.png',
  './apple-touch-icon.png',
  './favicon.png'
];

// Install: Cache offline assets and immediately skip waiting
self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => {
      return cache.addAll(ASSETS_TO_CACHE).catch((err) => {
        console.warn('Cache addAll non-critical error:', err);
      });
    }).then(() => self.skipWaiting())
  );
});

// Activate: Clean old caches and claim clients
self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) => {
      return Promise.all(
        keys.map((key) => {
          if (key !== CACHE_NAME) {
            return caches.delete(key);
          }
        })
      );
    }).then(() => self.clients.claim())
  );
});

// Fetch: Network first with cache fallback
self.addEventListener('fetch', (event) => {
  // Let Firebase / external API calls go directly to network
  if (event.request.url.includes('firestore') || 
      event.request.url.includes('firebase') || 
      event.request.url.includes('/api/')) {
    return;
  }

  event.respondWith(
    fetch(event.request).catch(() => {
      return caches.match(event.request).then((res) => {
        if (res) return res;
        if (event.request.destination === 'document') {
          return caches.match('./tasks.html');
        }
      });
    })
  );
});

// Client Message: Trigger notifications directly from page
self.addEventListener('message', (event) => {
  if (event.data && event.data.type === 'SHOW_NOTIFICATION') {
    const { title, options } = event.data;
    const notifOptions = {
      body: options?.body || 'A new task has been added to your shift checklist.',
      icon: options?.icon || 'apple-touch-icon.png',
      badge: options?.badge || 'icon-192.png',
      tag: options?.tag || ('task-notif-' + Date.now()),
      data: options?.data || { url: './tasks.html' },
      vibrate: options?.vibrate || [200, 100, 200],
      renotify: true,
      requireInteraction: false
    };
    self.registration.showNotification(title || '📋 New Task Added', notifOptions);
  }
});

// Push Event: Handle server push notifications
self.addEventListener('push', (event) => {
  let data = {};
  if (event.data) {
    try {
      data = event.data.json();
    } catch (e) {
      data = { title: 'New Task Added', body: event.data.text() };
    }
  }

  const title = data.title || '📋 New Task Added - One Stop';
  const options = {
    body: data.body || 'A new task was added to your shift checklist.',
    icon: data.icon || 'apple-touch-icon.png',
    badge: data.badge || 'icon-192.png',
    tag: data.tag || ('new-task-' + Date.now()),
    data: data.data || { url: './tasks.html' },
    vibrate: [200, 100, 200],
    renotify: true
  };

  event.waitUntil(self.registration.showNotification(title, options));
});

// Notification Click: Focus existing window or open task
self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const urlToOpen = (event.notification.data && event.notification.data.url) 
    ? event.notification.data.url 
    : './tasks.html';
  const taskId = event.notification.data?.taskId;

  event.waitUntil(
    clients.matchAll({ type: 'window', includeUncontrolled: true }).then((windowClients) => {
      for (const client of windowClients) {
        if (client.url.includes('tasks.html') && 'focus' in client) {
          if (taskId) {
            client.postMessage({ type: 'FOCUS_TASK', taskId: taskId });
          }
          return client.focus();
        }
      }
      if (clients.openWindow) {
        return clients.openWindow(urlToOpen);
      }
    })
  );
});
