/**
 * Page side of the Web Share Target: takes the link the service worker is
 * holding (see shareTarget.ts) when the app was opened from the share sheet.
 *
 * Deliberately does not import shareTarget.ts, so the service-worker bundle
 * stays self-contained. sharedLinkClient.test.ts checks that the constants
 * below match.
 */
export const SHARED_LAUNCH_PARAM = 'shared';
export const TAKE_SHARED_LINK = 'linksentry/take-shared-link';
const WAIT_MS = 3_000;

export type SharedLinkResult = { kind: 'none' } | { kind: 'missing' } | { kind: 'link'; url: string };

/** True when this page load is a share-target launch (`/?shared=1`). */
export function isSharedLaunch(): boolean {
  return new URLSearchParams(window.location.search).get(SHARED_LAUNCH_PARAM) === '1';
}

/**
 * Removes the launch marker from the address bar, then asks the service worker
 * for the shared link. `missing` means this was a share launch but no link
 * arrived: the share had none, or the worker could not be reached in time.
 */
export async function takeSharedLink(waitMs: number = WAIT_MS): Promise<SharedLinkResult> {
  if (!isSharedLaunch()) {
    return { kind: 'none' };
  }

  const address = new URL(window.location.href);
  address.searchParams.delete(SHARED_LAUNCH_PARAM);
  window.history.replaceState(window.history.state, '', address.pathname + address.search + address.hash);

  const worker = await activeServiceWorker(waitMs);
  if (worker === null) {
    return { kind: 'missing' };
  }
  const url = await askWorker(worker, waitMs);
  return url === null ? { kind: 'missing' } : { kind: 'link', url };
}

async function activeServiceWorker(waitMs: number): Promise<ServiceWorker | null> {
  if (!('serviceWorker' in navigator)) {
    return null;
  }
  const timeout = new Promise<null>((resolve) => window.setTimeout(() => resolve(null), waitMs));
  const registration = await Promise.race([navigator.serviceWorker.ready, timeout]);
  return registration?.active ?? null;
}

function askWorker(worker: ServiceWorker, waitMs: number): Promise<string | null> {
  return new Promise((resolve) => {
    const channel = new MessageChannel();
    const finish = (url: string | null) => {
      window.clearTimeout(timer);
      channel.port1.close();
      resolve(url);
    };
    const timer = window.setTimeout(() => finish(null), waitMs);
    channel.port1.onmessage = (event: MessageEvent<unknown>) => {
      const url = (event.data as { url?: unknown } | null)?.url;
      finish(typeof url === 'string' && url.length > 0 ? url : null);
    };
    worker.postMessage({ type: TAKE_SHARED_LINK }, [channel.port2]);
  });
}
