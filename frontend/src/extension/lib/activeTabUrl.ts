export type UnsupportedReason = 'no-tab' | 'no-url' | 'unsupported-scheme' | 'malformed';

export type ActiveTabLookup =
  | { readonly scannable: true; readonly url: string }
  | { readonly scannable: false; readonly reason: UnsupportedReason };

const SUPPORTED_SCHEMES = new Set(['http:', 'https:']);

/**
 * Reads the current active tab's URL through the `activeTab` permission and
 * classifies whether it is safe to submit for analysis.
 *
 * Never logs, stores, or returns anything on the unsupported path beyond a
 * machine-readable reason: internal browser pages, new-tab pages, `file:`
 * URLs, and any other non-http(s) scheme are indistinguishable from a missing
 * or malformed URL to every caller. The raw URL is returned only in the
 * `scannable: true` branch, for an explicit scan handler to submit immediately;
 * callers must not retain it.
 */
export async function getActiveTabUrl(): Promise<ActiveTabLookup> {
  let tabs: chrome.tabs.Tab[];
  try {
    tabs = await chrome.tabs.query({ active: true, currentWindow: true });
  } catch {
    return { scannable: false, reason: 'no-tab' };
  }

  const tab = tabs[0];
  if (!tab) {
    return { scannable: false, reason: 'no-tab' };
  }

  const rawUrl = tab.url;
  if (!rawUrl) {
    return { scannable: false, reason: 'no-url' };
  }

  let parsed: URL;
  try {
    parsed = new URL(rawUrl);
  } catch {
    return { scannable: false, reason: 'malformed' };
  }

  if (!SUPPORTED_SCHEMES.has(parsed.protocol)) {
    return { scannable: false, reason: 'unsupported-scheme' };
  }

  return { scannable: true, url: rawUrl };
}
