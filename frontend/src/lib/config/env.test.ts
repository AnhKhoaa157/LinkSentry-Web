import { describe, expect, it } from 'vitest';

import { buildOfficialWebAppUrl, parseOfficialWebAppUrl } from '@/lib/config/env';

describe('official web-app URL configuration', () => {
  it.each(['https://link-sentry-web.vercel.app', 'http://localhost:5173'])(
    'accepts a safe base URL: %s',
    (value) => {
      expect(parseOfficialWebAppUrl(value)).toBe(value);
    },
  );

  it.each([
    undefined,
    null,
    '',
    'not-a-url',
    'ftp://link-sentry-web.vercel.app',
    'https://user:password@example.com',
    'https://example.com?scanId=secret',
    'https://example.com#fragment',
    'https://example.com/',
    ' https://example.com',
  ])('rejects an unsafe or missing base URL: %s', (value) => {
    expect(parseOfficialWebAppUrl(value)).toBeNull();
  });

  it('constructs only the static history path', () => {
    expect(buildOfficialWebAppUrl('https://link-sentry-web.vercel.app', '/history')).toBe(
      'https://link-sentry-web.vercel.app/history',
    );
    expect(buildOfficialWebAppUrl('https://link-sentry-web.vercel.app', '')).toBe(
      'https://link-sentry-web.vercel.app',
    );
    expect(buildOfficialWebAppUrl('https://example.com?scanId=secret', '/history')).toBeNull();
  });
});
