/**
 * Hand-off for the "Check this link" context-menu action.
 *
 * The service worker cannot show a result, so it parks the link the user
 * right-clicked in `chrome.storage.session` and opens the popup, which takes it
 * exactly once. Session storage stays in memory: it is never written to disk,
 * it is cleared when the browser closes, and only the extension's own pages and
 * service worker can read it. The entry also expires after a minute, so a link
 * the user never got to is not scanned later by surprise.
 *
 * Nothing here logs or renders the link.
 */
export const PENDING_LINK_KEY = 'linksentry.pendingLinkScan';
export const PENDING_LINK_TTL_MS = 60_000;

/** Same ceiling the API enforces, so an oversized link is dropped before it is parked. */
const MAX_LINK_LENGTH = 2048;

interface PendingLink {
  url: string;
  expiresAt: number;
}

/** True only for an absolute http(s) URL within the API's length limit. */
export function isScannableLink(value: unknown): value is string {
  if (typeof value !== 'string' || value.length === 0 || value.length > MAX_LINK_LENGTH) {
    return false;
  }
  try {
    const { protocol } = new URL(value);
    return protocol === 'http:' || protocol === 'https:';
  } catch {
    return false;
  }
}

/** Parks {@link url} for the popup. Resolves false, storing nothing, when it is not scannable. */
export async function parkPendingLink(url: unknown, now: number = Date.now()): Promise<boolean> {
  if (!isScannableLink(url)) {
    return false;
  }
  const entry: PendingLink = { url, expiresAt: now + PENDING_LINK_TTL_MS };
  await chrome.storage.session.set({ [PENDING_LINK_KEY]: entry });
  return true;
}

/**
 * Removes and returns the parked link. Returns null when there is none, it has
 * expired, it is malformed, or session storage is unavailable. Also clears the
 * toolbar badge that marks a waiting link.
 */
export async function takePendingLink(now: number = Date.now()): Promise<string | null> {
  const session = typeof chrome === 'undefined' ? undefined : chrome.storage?.session;
  if (!session) {
    return null;
  }

  let stored: unknown;
  try {
    stored = (await session.get(PENDING_LINK_KEY))[PENDING_LINK_KEY];
    if (stored === undefined) {
      return null;
    }
    await session.remove(PENDING_LINK_KEY);
  } catch {
    return null;
  }
  void clearPendingBadge();

  if (typeof stored !== 'object' || stored === null) {
    return null;
  }
  const { url, expiresAt } = stored as Partial<PendingLink>;
  if (typeof expiresAt !== 'number' || expiresAt < now || !isScannableLink(url)) {
    return null;
  }
  return url;
}

/** Marks the toolbar icon so the user knows a link is waiting when the popup could not open by itself. */
export async function showPendingBadge(): Promise<void> {
  try {
    await chrome.action.setBadgeBackgroundColor({ color: '#0b5d95' });
    await chrome.action.setBadgeText({ text: '1' });
  } catch {
    // A missing badge only costs the visual hint; the parked link still expires on schedule.
  }
}

async function clearPendingBadge(): Promise<void> {
  try {
    await chrome.action?.setBadgeText({ text: '' });
  } catch {
    // Nothing to undo if the badge API is unavailable.
  }
}
