/**
 * `chrome` exists as a global in some ordinary browser tabs too (not just extension contexts),
 * but `chrome.storage` is populated only inside an actual extension page — so this check
 * correctly selects the extension backend even when `typeof chrome !== 'undefined'` alone would
 * not. Shared by every module that picks a `chrome.storage.local`-vs-web-storage backend.
 */
export function hasExtensionStorage(): boolean {
  return typeof chrome !== 'undefined' && typeof chrome.storage !== 'undefined' && !!chrome.storage.local;
}
