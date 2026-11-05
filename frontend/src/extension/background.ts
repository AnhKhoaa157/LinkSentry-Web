/**
 * Service worker for the "Check this link" context-menu item.
 *
 * Right-clicking an http(s) link offers the item; choosing it parks the link
 * for the popup (see `pendingLinkScan.ts`) and opens the popup, which scans it.
 * When the browser will not open the popup programmatically, a badge on the
 * toolbar icon asks the user to click it instead.
 *
 * The worker never calls the API, never logs, and never keeps the link beyond
 * the session-storage hand-off. Listeners are registered synchronously at the
 * top level, as Manifest V3 requires for events to wake the worker.
 */
import { isScannableLink, parkPendingLink, showPendingBadge } from '@/extension/lib/pendingLinkScan';
import { getBrowserLocale, isLocale, type Locale } from '@/lib/i18n/locale';
import { LOCALE_STORAGE_KEY } from '@/lib/i18n/localeStorage';
import { dictionaries } from '@/lib/i18n/translations';

export const CHECK_LINK_MENU_ID = 'linksentry-check-link';

function menuTitle(locale: Locale): string {
  return dictionaries[locale]['contextMenu.checkLink'];
}

async function preferredLocale(): Promise<Locale> {
  try {
    const value: unknown = (await chrome.storage.local.get(LOCALE_STORAGE_KEY))[LOCALE_STORAGE_KEY];
    if (typeof value === 'string' && isLocale(value)) {
      return value;
    }
  } catch {
    // Fall back to the browser language below.
  }
  return getBrowserLocale();
}

async function createMenu(): Promise<void> {
  const locale = await preferredLocale();
  await chrome.contextMenus.removeAll();
  chrome.contextMenus.create({
    id: CHECK_LINK_MENU_ID,
    title: menuTitle(locale),
    contexts: ['link'],
    targetUrlPatterns: ['http://*/*', 'https://*/*'],
  });
}

function openPopup(): Promise<void> {
  try {
    return chrome.action.openPopup();
  } catch (error) {
    return Promise.reject(error);
  }
}

chrome.runtime.onInstalled.addListener(() => {
  void createMenu();
});

chrome.storage.onChanged.addListener((changes, areaName) => {
  const next: unknown = changes[LOCALE_STORAGE_KEY]?.newValue;
  if (areaName === 'local' && typeof next === 'string' && isLocale(next)) {
    void chrome.contextMenus.update(CHECK_LINK_MENU_ID, { title: menuTitle(next) }).catch(() => undefined);
  }
});

chrome.contextMenus.onClicked.addListener((info) => {
  if (info.menuItemId !== CHECK_LINK_MENU_ID || !isScannableLink(info.linkUrl)) {
    return;
  }
  // Start the hand-off first, then open the popup while still inside the click's
  // user gesture. The storage write is sent first, so it lands before the popup
  // has even loaded.
  const parked = parkPendingLink(info.linkUrl);
  const opened = openPopup();
  void Promise.allSettled([parked, opened]).then(([parkResult, openResult]) => {
    if (parkResult.status === 'fulfilled' && parkResult.value && openResult.status === 'rejected') {
      void showPendingBadge();
    }
  });
});
