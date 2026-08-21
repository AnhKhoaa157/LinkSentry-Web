import { hasExtensionStorage } from '@/lib/device/environment';

const STORAGE_KEY = 'linksentry.deviceCredential';

/**
 * Where the device credential lives: `chrome.storage.local` inside the extension popup,
 * `localStorage` in the web app. Both persist across restarts on purpose — see
 * `docs/adr/0008-device-license-authentication.md`: clearing that storage is the documented way
 * to reset an installation and requires a new admin activation, so the credential must outlive a
 * single tab session the way `sessionStorage` would not.
 */
export interface DeviceCredentialStorage {
  get(): Promise<string | null>;
  set(credential: string): Promise<void>;
  clear(): Promise<void>;
}

const chromeStorageBackend: DeviceCredentialStorage = {
  async get() {
    try {
      const result = await chrome.storage.local.get(STORAGE_KEY);
      const value: unknown = result[STORAGE_KEY];
      return typeof value === 'string' && value.length > 0 ? value : null;
    } catch {
      return null;
    }
  },
  async set(credential) {
    try {
      await chrome.storage.local.set({ [STORAGE_KEY]: credential });
    } catch {
      // A blocked storage implementation leaves the device unbootstrapped rather than
      // falling back to a less private mechanism.
    }
  },
  async clear() {
    try {
      await chrome.storage.local.remove(STORAGE_KEY);
    } catch {
      // No safer fallback.
    }
  },
};

const webStorageBackend: DeviceCredentialStorage = {
  async get() {
    try {
      return localStorage.getItem(STORAGE_KEY);
    } catch {
      return null;
    }
  },
  async set(credential) {
    try {
      localStorage.setItem(STORAGE_KEY, credential);
    } catch {
      // No safer fallback.
    }
  },
  async clear() {
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch {
      // No safer fallback.
    }
  },
};

export const deviceCredentialStorage: DeviceCredentialStorage = hasExtensionStorage()
  ? chromeStorageBackend
  : webStorageBackend;
