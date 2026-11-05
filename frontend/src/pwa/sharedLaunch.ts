import { isSharedLaunch, takeSharedLink, type SharedLinkResult } from '@/pwa/sharedLinkClient';

// At most one share launch per page load. The request starts once and is shared
// by every caller (React StrictMode mounts twice in development). Its result is
// claimed once, so returning to the scanner later never replays the shared link.
let launch: Promise<SharedLinkResult> | undefined;
let claimed = false;

/** True while this page load is a share launch whose result nobody has claimed yet. */
export function hasPendingSharedLaunch(): boolean {
  return !claimed && (launch !== undefined || isSharedLaunch());
}

/**
 * Starts taking the shared link, if this load is an unclaimed share launch, and
 * returns the pending result. Returns null when there is nothing to take.
 */
export function startSharedLaunch(): Promise<SharedLinkResult> | null {
  if (!hasPendingSharedLaunch()) {
    return null;
  }
  launch ??= takeSharedLink();
  return launch;
}

/** Claims the result for one consumer. Only the first call returns true. */
export function claimSharedLaunch(): boolean {
  if (claimed) {
    return false;
  }
  claimed = true;
  return true;
}
