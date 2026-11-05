import { z } from 'zod';

// Zod compiles object schemas with `new Function` unless told otherwise, and it
// probes for that ability on first use. The web app's Content-Security-Policy
// (vercel.json) and the extension's Manifest V3 policy both forbid eval, so the
// probe would report a CSP violation on every page load. Validation is exactly
// as strict without the JIT. Imported first by every entry point.
z.config({ jitless: true });
