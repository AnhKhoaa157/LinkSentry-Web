import { beforeEach, describe, expect, it, vi } from 'vitest';

/**
 * The module picks its backend once, at import time, based on whether `chrome.storage.local`
 * exists — so each scenario here needs a fresh module instance via `resetModules` + a dynamic
 * `import()` taken *after* stubbing (or not stubbing) `chrome`.
 */
describe('deviceCredentialStorage', () => {
  beforeEach(() => {
    vi.resetModules();
    vi.unstubAllGlobals();
    localStorage.clear();
  });

  it('uses localStorage when chrome.storage is unavailable (the web app)', async () => {
    const { deviceCredentialStorage } = await import('@/lib/device/deviceCredentialStorage');

    await expect(deviceCredentialStorage.get()).resolves.toBeNull();
    await deviceCredentialStorage.set('web-credential');
    await expect(deviceCredentialStorage.get()).resolves.toBe('web-credential');
    expect(localStorage.getItem('linksentry.deviceCredential')).toBe('web-credential');

    await deviceCredentialStorage.clear();
    await expect(deviceCredentialStorage.get()).resolves.toBeNull();
    expect(localStorage.getItem('linksentry.deviceCredential')).toBeNull();
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
          remove: vi.fn(async (key: string) => {
            store.delete(key);
          }),
        },
      },
    });

    const { deviceCredentialStorage } = await import('@/lib/device/deviceCredentialStorage');

    await expect(deviceCredentialStorage.get()).resolves.toBeNull();
    await deviceCredentialStorage.set('extension-credential');
    await expect(deviceCredentialStorage.get()).resolves.toBe('extension-credential');
    expect(localStorage.getItem('linksentry.deviceCredential')).toBeNull();

    await deviceCredentialStorage.clear();
    await expect(deviceCredentialStorage.get()).resolves.toBeNull();
  });

  it('falls back to the web backend when chrome exists but chrome.storage does not (an ordinary browser tab)', async () => {
    vi.stubGlobal('chrome', {});

    const { deviceCredentialStorage } = await import('@/lib/device/deviceCredentialStorage');
    await deviceCredentialStorage.set('web-credential-2');

    expect(localStorage.getItem('linksentry.deviceCredential')).toBe('web-credential-2');
  });

  it('never throws when the underlying storage rejects; get resolves null and set/clear resolve void', async () => {
    vi.stubGlobal('chrome', {
      storage: {
        local: {
          get: vi.fn().mockRejectedValue(new Error('blocked')),
          set: vi.fn().mockRejectedValue(new Error('blocked')),
          remove: vi.fn().mockRejectedValue(new Error('blocked')),
        },
      },
    });

    const { deviceCredentialStorage } = await import('@/lib/device/deviceCredentialStorage');

    await expect(deviceCredentialStorage.get()).resolves.toBeNull();
    await expect(deviceCredentialStorage.set('x')).resolves.toBeUndefined();
    await expect(deviceCredentialStorage.clear()).resolves.toBeUndefined();
  });
});
