import { describe, expect, it } from 'vitest';

import {
  createShareInbox,
  handleShareRequest,
  pickSharedLink,
  SHARED_LINK_TTL_MS,
  SHARED_LAUNCH_PATH,
} from '@/pwa/shareTarget';

const LINK = 'https://login.vietcombank.com.vn.evil-domain.xyz/account?token=query-sentinel';

function sharePost(fields: Record<string, string>): Request {
  return new Request('https://link-sentry-web.vercel.app/share-target', {
    method: 'POST',
    body: new URLSearchParams(fields),
  });
}

describe('pickSharedLink', () => {
  it('takes the url field when it holds an http(s) link', () => {
    expect(pickSharedLink({ url: ` ${LINK} `, text: 'https://other.example/' })).toBe(LINK);
  });

  it('finds a link inside the shared text, without trailing sentence punctuation', () => {
    expect(pickSharedLink({ text: `Xem cái này nè: ${LINK}.` })).toBe(LINK);
    expect(pickSharedLink({ text: `(${LINK})` })).toBe(LINK);
    expect(pickSharedLink({ url: '', text: 'no link here', title: `Title ${LINK}` })).toBe(LINK);
  });

  it('accepts only http and https links within the API length limit', () => {
    for (const value of [
      'javascript:alert(1)',
      'data:text/html,hello',
      'file:///etc/passwd',
      'intent://scan/#Intent;scheme=zxing;end',
      'ftp://files.example/',
      `https://example.com/${'a'.repeat(2048)}`,
    ]) {
      expect(pickSharedLink({ url: value, text: value })).toBeNull();
    }
  });

  it('returns null for a share without any link, or with non-text fields', () => {
    expect(pickSharedLink({ text: 'Only words', title: 'Hello' })).toBeNull();
    expect(pickSharedLink({ url: null, text: undefined, title: 42 })).toBeNull();
  });
});

describe('share inbox', () => {
  it('hands the link over exactly once', () => {
    const inbox = createShareInbox(() => 1_000);
    inbox.put(LINK);

    expect(inbox.take()).toBe(LINK);
    expect(inbox.take()).toBeNull();
  });

  it('drops a link the page did not take within the time limit', () => {
    let now = 1_000;
    const inbox = createShareInbox(() => now);
    inbox.put(LINK);

    now += SHARED_LINK_TTL_MS + 1;
    expect(inbox.take()).toBeNull();
  });
});

describe('handleShareRequest', () => {
  it('keeps the shared link and redirects into the app without it in the URL', async () => {
    const inbox = createShareInbox();

    const response = await handleShareRequest(sharePost({ title: 'Chat', text: `Xem nè ${LINK}` }), inbox);

    expect(response.status).toBe(303);
    expect(response.headers.get('Location')).toBe(`https://link-sentry-web.vercel.app${SHARED_LAUNCH_PATH}`);
    expect(response.headers.get('Location')).not.toContain('evil-domain');
    expect(inbox.take()).toBe(LINK);
  });

  it('still lands in the app when the share had no link or a malformed body', async () => {
    const noLink = createShareInbox();
    const withoutLink = await handleShareRequest(sharePost({ text: 'just words' }), noLink);
    expect(withoutLink.status).toBe(303);
    expect(noLink.take()).toBeNull();

    const malformed = createShareInbox();
    const garbled = await handleShareRequest(
      new Request('https://link-sentry-web.vercel.app/share-target', {
        method: 'POST',
        headers: { 'Content-Type': 'multipart/form-data; boundary=missing' },
        body: 'not multipart',
      }),
      malformed,
    );
    expect(garbled.status).toBe(303);
    expect(malformed.take()).toBeNull();
  });
});
