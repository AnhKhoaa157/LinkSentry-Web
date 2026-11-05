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
 *
 * The service worker is a second entry. It is emitted as a fixed `background.js`
 * at the output root, because manifest.json names it; modules it shares with
 * the popup become ordinary chunks under assets/ that it imports as an ES module.
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
      input: {
        popup: fileURLToPath(new URL('./src/extension/popup.html', import.meta.url)),
        background: fileURLToPath(new URL('./src/extension/background.ts', import.meta.url)),
      },
      output: {
        entryFileNames: (chunk) =>
          chunk.name === 'background' ? 'background.js' : 'assets/[name]-[hash].js',
      },
    },
  },
});
