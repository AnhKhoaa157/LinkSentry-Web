import { isLocale, type Locale } from '@/lib/i18n/locale';
import { hasExtensionStorage } from '@/lib/device/environment';

const STORAGE_KEY = 'linksentry.locale';

/**
 * Where the chosen display language lives: `chrome.storage.local` inside the extension popup,
 * `localStorage` in the web app — same split as `lib/device/deviceCredentialStorage.ts`, since a
 * popup's `localStorage` is scoped to the extension's own origin and would not persist the way a
 * user expects across the popup closing and reopening.
 */
export interface LocaleStorage {
  get(): Promise<Locale | null>;
  set(locale: Locale): Promise<void>;
}

const chromeStorageBackend: LocaleStorage = {
  async get() {
    try {
      const result = await chrome.storage.local.get(STORAGE_KEY);
      const value: unknown = result[STORAGE_KEY];
      return typeof value === 'string' && isLocale(value) ? value : null;
    } catch {
      return null;
    }
  },
  async set(locale) {
    try {
      await chrome.storage.local.set({ [STORAGE_KEY]: locale });
    } catch {
      // A blocked storage implementation leaves the choice unpersisted for this session only.
    }
  },
};

const webStorageBackend: LocaleStorage = {
  async get() {
    try {
      const value = localStorage.getItem(STORAGE_KEY);
      return value !== null && isLocale(value) ? value : null;
    } catch {
      return null;
    }
  },
  async set(locale) {
    try {
      localStorage.setItem(STORAGE_KEY, locale);
    } catch {
      // No safer fallback.
    }
  },
};

export const localeStorage: LocaleStorage = hasExtensionStorage() ? chromeStorageBackend : webStorageBackend;
