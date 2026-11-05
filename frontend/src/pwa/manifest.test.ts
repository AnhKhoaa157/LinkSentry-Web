import { existsSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

import { SHARE_TARGET_PATH } from '@/pwa/shareTarget';

const frontendRoot = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
const publicDir = join(frontendRoot, 'public');

interface ManifestIcon {
  src: string;
  sizes: string;
  purpose?: string;
}

const manifest = JSON.parse(readFileSync(join(publicDir, 'manifest.webmanifest'), 'utf-8')) as {
  name: string;
  start_url: string;
  scope: string;
  display: string;
  icons: ManifestIcon[];
  share_target: unknown;
};

describe('web app manifest', () => {
  it('meets the install requirements: name, standalone display, and root start URL and scope', () => {
    expect(manifest.name).toBe('LinkSentry');
    expect(manifest.display).toBe('standalone');
    expect(manifest.start_url).toBe('/');
    expect(manifest.scope).toBe('/');
  });

  it('lists 192px and 512px icons plus a maskable one, and every file exists', () => {
    expect(manifest.icons.map((icon) => icon.sizes)).toEqual(expect.arrayContaining(['192x192', '512x512']));
    expect(manifest.icons.some((icon) => icon.purpose === 'maskable')).toBe(true);
    for (const icon of manifest.icons) {
      expect(existsSync(join(publicDir, icon.src)), icon.src).toBe(true);
    }
  });

  it('receives shares as a POST to the path the service worker answers, never as a query string', () => {
    expect(manifest.share_target).toEqual({
      action: SHARE_TARGET_PATH,
      method: 'POST',
      enctype: 'application/x-www-form-urlencoded',
      params: { title: 'title', text: 'text', url: 'url' },
    });
  });

  it('is linked from index.html', () => {
    expect(readFileSync(join(frontendRoot, 'index.html'), 'utf-8')).toContain(
      '<link rel="manifest" href="/manifest.webmanifest" />',
    );
  });
});
