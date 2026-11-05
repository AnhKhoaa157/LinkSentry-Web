import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

// Read from disk rather than imported, for the same reason as manifest.test.ts:
// Vite would otherwise rewrite the paths into bundled asset URLs.
const frontendRoot = join(dirname(fileURLToPath(import.meta.url)), '..', '..');

interface VercelHeader {
  key: string;
  value: string;
}

interface VercelConfig {
  headers?: { source: string; headers: VercelHeader[] }[];
}

function readText(...segments: string[]): string {
  return readFileSync(join(frontendRoot, ...segments), 'utf-8');
}

const vercelConfig = JSON.parse(readText('vercel.json')) as VercelConfig;

function headerValue(key: string): string | undefined {
  const allPaths = vercelConfig.headers?.find((entry) => entry.source === '/(.*)');
  return allPaths?.headers.find((header) => header.key.toLowerCase() === key.toLowerCase())?.value;
}

function cspDirectives(): Map<string, string[]> {
  const directives = new Map<string, string[]>();
  for (const directive of (headerValue('Content-Security-Policy') ?? '').split(';')) {
    const [name, ...sources] = directive.trim().split(/\s+/);
    if (name) {
      directives.set(name, sources);
    }
  }
  return directives;
}

describe('Vercel deployment config', () => {
  it('is identical to the repository-root copy, whichever root directory the Vercel project uses', () => {
    expect(JSON.parse(readText('..', 'vercel.json'))).toEqual(vercelConfig);
  });

  it('forbids inline script, eval, plugins, and framing', () => {
    const csp = cspDirectives();

    expect(csp.get('default-src')).toEqual(["'self'"]);
    expect(csp.get('script-src')).toEqual(["'self'"]);
    expect(csp.get('object-src')).toEqual(["'none'"]);
    expect(csp.get('base-uri')).toEqual(["'none'"]);
    expect(csp.get('frame-ancestors')).toEqual(["'none'"]);
    expect(headerValue('Content-Security-Policy')).not.toMatch(/unsafe-inline|unsafe-eval/);
  });

  it('lets the app call the deployed API origin that the extension is also built against', () => {
    const apiOrigin = /^VITE_API_BASE_URL=(\S+)$/m.exec(readText('.env.extension'))?.[1];

    expect(apiOrigin).toMatch(/^https:\/\//);
    expect(cspDirectives().get('connect-src')).toContain(apiOrigin);
  });

  it('sends the remaining hardening headers', () => {
    expect(headerValue('X-Content-Type-Options')).toBe('nosniff');
    expect(headerValue('X-Frame-Options')).toBe('DENY');
    expect(headerValue('Referrer-Policy')).toBe('no-referrer');
    expect(headerValue('Permissions-Policy')).toContain('camera=()');
  });

  it('keeps index.html free of inline scripts, which the CSP would block', () => {
    const scripts = [...readText('index.html').matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/gi)];

    expect(scripts.length).toBeGreaterThan(0);
    for (const [, attributes, body] of scripts) {
      expect(attributes).toMatch(/\ssrc="/);
      expect(body?.trim()).toBe('');
    }
  });
});
