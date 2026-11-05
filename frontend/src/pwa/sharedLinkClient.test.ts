import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { SHARED_LAUNCH_PARAM, TAKE_SHARED_LINK, takeSharedLink } from '@/pwa/sharedLinkClient';
import * as worker from '@/pwa/shareTarget';

const LINK = 'https://login.vietcombank.com.vn.evil-domain.xyz/account';
const pwaDir = dirname(fileURLToPath(import.meta.url));

/** Stands in for navigator.serviceWorker, answering a take request with `reply`. */
function stubServiceWorker(reply: unknown, { ready = true }: { ready?: boolean } = {}) {
  const postMessage = vi.fn((_message: unknown, transfer: MessagePort[]) => {
    const [port] = transfer;
    port?.postMessage(reply);
    port?.close();
  });
  const registration = { active: { postMessage } };
  Object.defineProperty(navigator, 'serviceWorker', {
    configurable: true,
    value: { ready: ready ? Promise.resolve(registration) : new Promise(() => undefined) },
  });
  return postMessage;
}

describe('takeSharedLink', () => {
  beforeEach(() => {
    window.history.replaceState(null, '', '/?shared=1');
  });

  afterEach(() => {
    Reflect.deleteProperty(navigator, 'serviceWorker');
    window.history.replaceState(null, '', '/');
  });

  it('does nothing on an ordinary page load', async () => {
    window.history.replaceState(null, '', '/?other=1');
    const postMessage = stubServiceWorker({ url: LINK });

    expect(await takeSharedLink()).toEqual({ kind: 'none' });
    expect(postMessage).not.toHaveBeenCalled();
    expect(window.location.search).toBe('?other=1');
  });

  it('removes the launch marker and takes the link from the service worker', async () => {
    const postMessage = stubServiceWorker({ url: LINK });

    expect(await takeSharedLink()).toEqual({ kind: 'link', url: LINK });
    expect(postMessage).toHaveBeenCalledWith({ type: TAKE_SHARED_LINK }, expect.any(Array));
    expect(window.location.search).toBe('');
    expect(window.location.href).not.toContain('evil-domain');
  });

  it('reports a missing link when the worker holds none', async () => {
    stubServiceWorker({ url: null });

    expect(await takeSharedLink()).toEqual({ kind: 'missing' });
    expect(window.location.search).toBe('');
  });

  it('reports a missing link when there is no service worker, or it never becomes ready', async () => {
    expect(await takeSharedLink()).toEqual({ kind: 'missing' });

    window.history.replaceState(null, '', '/?shared=1');
    stubServiceWorker({ url: LINK }, { ready: false });
    expect(await takeSharedLink(20)).toEqual({ kind: 'missing' });
  });
});

describe('share-target contract', () => {
  it('uses the same message and launch marker as the service worker', () => {
    expect(TAKE_SHARED_LINK).toBe(worker.TAKE_SHARED_LINK);
    expect(worker.SHARED_LAUNCH_PATH).toBe(`/?${SHARED_LAUNCH_PARAM}=1`);
  });

  it('keeps the service worker bundle self-contained', () => {
    // The worker is a second build entry. Importing anything the app also imports
    // would split it into a shared chunk that a classic service worker cannot load.
    const imports = (file: string) =>
      [...readFileSync(join(pwaDir, file), 'utf-8').matchAll(/^import .* from '([^']+)';$/gm)].map(
        (match) => match[1],
      );

    expect(imports('serviceWorker.ts')).toEqual(['@/pwa/shareTarget']);
    expect(imports('shareTarget.ts')).toEqual([]);
  });
});
