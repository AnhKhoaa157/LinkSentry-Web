import { afterEach, describe, expect, it, vi } from 'vitest';

const LINK = 'https://login.vietcombank.com.vn.evil-domain.xyz/account';

async function loadWith(options: { launch: boolean }) {
  vi.resetModules();
  const takeSharedLink = vi.fn().mockResolvedValue({ kind: 'link', url: LINK });
  vi.doMock('@/pwa/sharedLinkClient', () => ({
    isSharedLaunch: () => options.launch,
    takeSharedLink,
  }));
  const module = await import('@/pwa/sharedLaunch');
  return { ...module, takeSharedLink };
}

describe('shared launch', () => {
  afterEach(() => {
    vi.doUnmock('@/pwa/sharedLinkClient');
  });

  it('has nothing to take on an ordinary page load', async () => {
    const { hasPendingSharedLaunch, startSharedLaunch, takeSharedLink } = await loadWith({ launch: false });

    expect(hasPendingSharedLaunch()).toBe(false);
    expect(startSharedLaunch()).toBeNull();
    expect(takeSharedLink).not.toHaveBeenCalled();
  });

  it('takes the link once however many times it is started, and lets one consumer claim it', async () => {
    const { hasPendingSharedLaunch, startSharedLaunch, claimSharedLaunch, takeSharedLink } = await loadWith({
      launch: true,
    });

    expect(hasPendingSharedLaunch()).toBe(true);
    const first = startSharedLaunch();
    const second = startSharedLaunch();
    expect(second).toBe(first);
    expect(takeSharedLink).toHaveBeenCalledOnce();
    expect(await first).toEqual({ kind: 'link', url: LINK });

    expect(claimSharedLaunch()).toBe(true);
    expect(claimSharedLaunch()).toBe(false);
    expect(hasPendingSharedLaunch()).toBe(false);
    expect(startSharedLaunch()).toBeNull();
  });
});
