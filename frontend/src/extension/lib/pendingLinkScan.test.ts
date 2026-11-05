import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  isScannableLink,
  parkPendingLink,
  PENDING_LINK_KEY,
  PENDING_LINK_TTL_MS,
  takePendingLink,
} from '@/extension/lib/pendingLinkScan';

function stubChrome(initial: Record<string, unknown> = {}) {
  const store = new Map<string, unknown>(Object.entries(initial));
  const session = {
    get: vi.fn(async (key: string) => (store.has(key) ? { [key]: store.get(key) } : {})),
    set: vi.fn(async (items: Record<string, unknown>) => {
      for (const [key, value] of Object.entries(items)) {
        store.set(key, value);
      }
    }),
    remove: vi.fn(async (key: string) => {
      store.delete(key);
    }),
  };
  const action = {
    setBadgeText: vi.fn().mockResolvedValue(undefined),
    setBadgeBackgroundColor: vi.fn().mockResolvedValue(undefined),
  };
  vi.stubGlobal('chrome', { storage: { session }, action });
  return { store, action };
}

describe('pending link hand-off', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('accepts only absolute http(s) links within the API length limit', () => {
    expect(isScannableLink('https://example.com/a')).toBe(true);
    expect(isScannableLink('http://example.com')).toBe(true);
    for (const value of [
      'javascript:alert(1)',
      'data:text/html,hello',
      'file:///etc/passwd',
      'chrome://settings',
      'ftp://example.com',
      'not a url',
      '',
      undefined,
      null,
      42,
      `https://example.com/${'a'.repeat(2048)}`,
    ]) {
      expect(isScannableLink(value)).toBe(false);
    }
  });

  it('parks a link with an expiry and hands it over exactly once', async () => {
    const { store, action } = stubChrome();

    expect(await parkPendingLink('https://example.com/a', 1_000)).toBe(true);
    expect(store.get(PENDING_LINK_KEY)).toEqual({
      url: 'https://example.com/a',
      expiresAt: 1_000 + PENDING_LINK_TTL_MS,
    });

    expect(await takePendingLink(2_000)).toBe('https://example.com/a');
    expect(store.has(PENDING_LINK_KEY)).toBe(false);
    expect(action.setBadgeText).toHaveBeenCalledWith({ text: '' });
    expect(await takePendingLink(2_000)).toBeNull();
  });

  it('refuses to park anything but an http(s) link', async () => {
    const { store } = stubChrome();

    expect(await parkPendingLink('javascript:alert(1)')).toBe(false);
    expect(await parkPendingLink(undefined)).toBe(false);
    expect(store.size).toBe(0);
  });

  it('discards an expired or malformed hand-off instead of scanning it', async () => {
    const expired = stubChrome({ [PENDING_LINK_KEY]: { url: 'https://example.com/a', expiresAt: 999 } });
    expect(await takePendingLink(1_000)).toBeNull();
    expect(expired.store.has(PENDING_LINK_KEY)).toBe(false);

    stubChrome({ [PENDING_LINK_KEY]: { url: 'javascript:alert(1)', expiresAt: 5_000 } });
    expect(await takePendingLink(1_000)).toBeNull();

    stubChrome({ [PENDING_LINK_KEY]: 'https://example.com/a' });
    expect(await takePendingLink(1_000)).toBeNull();
  });

  it('returns null when session storage is unavailable', async () => {
    vi.stubGlobal('chrome', { tabs: {} });
    expect(await takePendingLink()).toBeNull();
  });
});
