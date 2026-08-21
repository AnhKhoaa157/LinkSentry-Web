import { beforeEach, describe, expect, it, vi } from 'vitest';

/**
 * The module picks its backend once, at import time, based on whether `chrome.storage.local`
 * exists — so each scenario here needs a fresh module instance via `resetModules` + a dynamic
 * `import()` taken *after* stubbing (or not stubbing) `chrome`. Mirrors
 * `lib/device/deviceCredentialStorage.test.ts`.
 */
describe('localeStorage', () => {
  beforeEach(() => {
    vi.resetModules();
    vi.unstubAllGlobals();
    localStorage.clear();
  });

  it('uses localStorage when chrome.storage is unavailable (the web app)', async () => {
    const { localeStorage } = await import('@/lib/i18n/localeStorage');

    await expect(localeStorage.get()).resolves.toBeNull();
    await localeStorage.set('vi');
    await expect(localeStorage.get()).resolves.toBe('vi');
    expect(localStorage.getItem('linksentry.locale')).toBe('vi');
  });

  it('uses chrome.storage.local, never localStorage, when it is available (the extension popup)', async () => {
    const store = new Map<string, unknown>();
    vi.stubGlobal('chrome', {
      storage: {
        local: {
          get: vi.fn(async (key: string) => ({ [key]: store.get(key) })),
          set: vi.fn(async (items: Record<string, unknown>) => {
            for (const [k, v] of Object.entries(items)) {
              store.set(k, v);
            }
          }),
        },
      },
    });

    const { localeStorage } = await import('@/lib/i18n/localeStorage');

    await expect(localeStorage.get()).resolves.toBeNull();
    await localeStorage.set('vi');
    await expect(localeStorage.get()).resolves.toBe('vi');
    expect(localStorage.getItem('linksentry.locale')).toBeNull();
  });

  it('rejects a stored value that is not a known locale', async () => {
    vi.stubGlobal('chrome', {});
    localStorage.setItem('linksentry.locale', 'fr');

    const { localeStorage } = await import('@/lib/i18n/localeStorage');

    await expect(localeStorage.get()).resolves.toBeNull();
  });

  it('never throws when the underlying storage rejects; get resolves null and set resolves void', async () => {
    vi.stubGlobal('chrome', {
      storage: {
        local: {
          get: vi.fn().mockRejectedValue(new Error('blocked')),
          set: vi.fn().mockRejectedValue(new Error('blocked')),
        },
      },
    });

    const { localeStorage } = await import('@/lib/i18n/localeStorage');

    await expect(localeStorage.get()).resolves.toBeNull();
    await expect(localeStorage.set('vi')).resolves.toBeUndefined();
  });
});
