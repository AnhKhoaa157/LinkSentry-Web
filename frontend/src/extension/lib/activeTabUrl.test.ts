import { describe, expect, it, vi } from 'vitest';

import { getActiveTabUrl } from '@/extension/lib/activeTabUrl';

function stubTabsQuery(tabs: ReadonlyArray<{ url?: string }>) {
  const query = vi.fn().mockResolvedValue(tabs);
  vi.stubGlobal('chrome', { tabs: { query } });
  return query;
}

describe('getActiveTabUrl', () => {
  it('reports an https tab as scannable and returns its exact URL', async () => {
    stubTabsQuery([{ url: 'https://example.com/path?x=1#frag' }]);

    await expect(getActiveTabUrl()).resolves.toEqual({
      scannable: true,
      url: 'https://example.com/path?x=1#frag',
    });
  });

  it('reports an http tab as scannable', async () => {
    stubTabsQuery([{ url: 'http://example.com/' }]);

    await expect(getActiveTabUrl()).resolves.toEqual({ scannable: true, url: 'http://example.com/' });
  });

  it.each([
    'chrome://newtab/',
    'chrome://extensions/',
    'edge://settings/',
    'about:blank',
    'file:///C:/secrets.txt',
    'javascript:alert(1)',
    'data:text/html,hi',
    'chrome-extension://abcdefghijklmnopqrstuvwxyzabcdef/options.html',
    'ftp://example.com/file',
    'view-source:http://example.com/',
  ])('reports %s as unsupported without throwing', async (url) => {
    stubTabsQuery([{ url }]);

    const result = await getActiveTabUrl();

    expect(result.scannable).toBe(false);
  });

  it('reports no active tab as unsupported with reason no-tab', async () => {
    stubTabsQuery([]);

    await expect(getActiveTabUrl()).resolves.toEqual({ scannable: false, reason: 'no-tab' });
  });

  it('reports a tab with no url as unsupported with reason no-url', async () => {
    stubTabsQuery([{}]);

    await expect(getActiveTabUrl()).resolves.toEqual({ scannable: false, reason: 'no-url' });
  });

  it('reports an empty-string url as unsupported', async () => {
    stubTabsQuery([{ url: '' }]);

    const result = await getActiveTabUrl();

    expect(result.scannable).toBe(false);
  });

  it('reports a malformed url string as unsupported instead of throwing', async () => {
    stubTabsQuery([{ url: 'not a url' }]);

    await expect(getActiveTabUrl()).resolves.toEqual({ scannable: false, reason: 'malformed' });
  });

  it('reports a query failure as unsupported instead of throwing', async () => {
    vi.stubGlobal('chrome', { tabs: { query: vi.fn().mockRejectedValue(new Error('boom')) } });

    await expect(getActiveTabUrl()).resolves.toEqual({ scannable: false, reason: 'no-tab' });
  });
});
