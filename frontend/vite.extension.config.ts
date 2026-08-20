import { fileURLToPath, URL } from 'node:url';

import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

/**
 * Separate build for the Manifest V3 popup: its own HTML entry, its own output
 * directory, and no dev server. `manifest.json` lives in src/extension/public,
 * so Vite's default publicDir copy places it in the output unmodified.
 *
 * `modulePreload: false` matters beyond bundle size: Vite's default modulepreload
 * polyfill is an inline <script>, and MV3's default extension-page CSP
 * (script-src 'self') blocks inline scripts outright.
 */
export default defineConfig({
  root: fileURLToPath(new URL('./src/extension', import.meta.url)),
  // Keep extension deployment configuration in frontend/.env.extension rather
  // than inheriting the web app's local-development API default.
  envDir: fileURLToPath(new URL('./', import.meta.url)),
  base: './',
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  build: {
    outDir: fileURLToPath(new URL('../linksentry', import.meta.url)),
    emptyOutDir: true,
    modulePreload: false,
    rollupOptions: {
      input: fileURLToPath(new URL('./src/extension/popup.html', import.meta.url)),
    },
  },
});
