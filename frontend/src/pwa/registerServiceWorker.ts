/**
 * Registers the share-target service worker (built to /sw.js) once the page has
 * loaded. A failure only disables sharing into the app, so it is ignored.
 */
export function registerServiceWorker(): void {
  if (!('serviceWorker' in navigator)) {
    return;
  }
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js', { scope: '/' }).catch(() => undefined);
  });
}
