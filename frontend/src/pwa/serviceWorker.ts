/**
 * The web app's service worker, built to /sw.js (see vite.config.ts).
 *
 * Its only job is the Web Share Target: it answers the share POST without the
 * network and hands the link to the page (see shareTarget.ts). It caches
 * nothing and leaves every other request to the network as usual.
 */
import { createShareInbox, handleShareRequest, SHARE_TARGET_PATH, TAKE_SHARED_LINK } from '@/pwa/shareTarget';

// Minimal service-worker types. The app's tsconfig uses the DOM library, and the
// WebWorker library cannot be loaded next to it.
interface ExtendableEvent extends Event {
  waitUntil(promise: Promise<unknown>): void;
}

interface FetchEvent extends ExtendableEvent {
  readonly request: Request;
  respondWith(response: Promise<Response> | Response): void;
}

interface ServiceWorkerScope {
  readonly location: Location;
  readonly clients: { claim(): Promise<void> };
  skipWaiting(): Promise<void>;
  addEventListener(type: 'install' | 'activate', listener: (event: ExtendableEvent) => void): void;
  addEventListener(type: 'fetch', listener: (event: FetchEvent) => void): void;
  addEventListener(type: 'message', listener: (event: MessageEvent<unknown>) => void): void;
}

const worker = self as unknown as ServiceWorkerScope;
const inbox = createShareInbox();

worker.addEventListener('install', (event) => {
  event.waitUntil(worker.skipWaiting());
});

worker.addEventListener('activate', (event) => {
  event.waitUntil(worker.clients.claim());
});

worker.addEventListener('fetch', (event) => {
  const { request } = event;
  if (request.method !== 'POST') {
    return;
  }
  const url = new URL(request.url);
  if (url.origin === worker.location.origin && url.pathname === SHARE_TARGET_PATH) {
    event.respondWith(handleShareRequest(request, inbox));
  }
});

worker.addEventListener('message', (event) => {
  const port = event.ports[0];
  const type = (event.data as { type?: unknown } | null)?.type;
  if (port !== undefined && type === TAKE_SHARED_LINK) {
    port.postMessage({ url: inbox.take() });
  }
});
