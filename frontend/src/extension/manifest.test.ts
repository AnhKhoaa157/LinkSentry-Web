import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

// Not `new URL('./public/manifest.json', import.meta.url)`: Vite statically
// rewrites that exact pattern into a bundled browser asset URL, which under
// Vitest's jsdom environment resolves against the fake `localhost:3000`
// origin instead of this file's real path on disk.
const manifestPath = join(dirname(fileURLToPath(import.meta.url)), 'public', 'manifest.json');
const manifest: Record<string, unknown> = JSON.parse(readFileSync(manifestPath, 'utf-8'));

/**
 * Asserts the manifest's actual permission surface, not just that it "has"
 * activeTab — `toEqual` fails equally on activeTab being removed and on any
 * extra permission (tabs, scripting, webRequest, cookies,
 * contextMenus, notifications, host wildcards, ...) being added. `storage` is
 * the one deliberate addition: it backs `chrome.storage.local`, where the
 * device credential lives — see docs/adr/0008-device-license-authentication.md.
 */
describe('extension manifest', () => {
  it('targets Manifest V3', () => {
    expect(manifest['manifest_version']).toBe(3);
  });

  it('requests exactly activeTab and storage, and nothing else', () => {
    expect(manifest['permissions']).toEqual(['activeTab', 'storage']);
  });

  it('scopes host_permissions to exactly the deployed LinkSentry API', () => {
    expect(manifest['host_permissions']).toEqual(['https://linksentry-web.onrender.com/*']);
  });

  it('never requests a broad or wildcard host pattern', () => {
    const hosts = manifest['host_permissions'] as string[];
    expect(hosts.length).toBeGreaterThan(0);
    for (const pattern of hosts) {
      expect(pattern).not.toBe('<all_urls>');
      expect(pattern).not.toMatch(/^\*:\/\//);
      expect(pattern).not.toMatch(/\/\/\*[./]/);
    }
  });

  it('declares no content scripts', () => {
    expect(manifest['content_scripts']).toBeUndefined();
  });

  it('declares no background service worker', () => {
    expect(manifest['background']).toBeUndefined();
  });

  it('never declares any permission beyond the reviewed least-privilege set', () => {
    // A locked allow-list, not a denylist: any newly-requested Chrome API
    // permission must be added here deliberately, with its own review, rather
    // than slipping in silently.
    const allowed = new Set(['activeTab', 'storage']);
    const requested = (manifest['permissions'] as string[] | undefined) ?? [];
    for (const permission of requested) {
      expect(allowed.has(permission)).toBe(true);
    }
  });

  it('opens a popup from the toolbar action', () => {
    const action = manifest['action'] as Record<string, unknown> | undefined;
    expect(action?.['default_popup']).toBe('popup.html');
  });
});
