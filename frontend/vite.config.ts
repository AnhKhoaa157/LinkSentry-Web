import { fileURLToPath, URL } from 'node:url';

import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
// From 'vitest/config' rather than 'vite': Vite's own `defineConfig` does not
// accept the `test` block.
import { defineConfig } from 'vitest/config';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      // Absolute imports (`@/features/health/...`) so a moved file does not
      // produce a trail of `../../..` edits. Mirrored in tsconfig.json paths.
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    strictPort: true,
  },
  build: {
    outDir: 'dist',
    sourcemap: true,
    rollupOptions: {
      // The service worker is a second entry, emitted as a fixed /sw.js at the
      // site root so its scope covers the whole app. It imports only
      // src/pwa/shareTarget.ts, so it builds into one self-contained file.
      input: {
        index: fileURLToPath(new URL('./index.html', import.meta.url)),
        sw: fileURLToPath(new URL('./src/pwa/serviceWorker.ts', import.meta.url)),
      },
      output: {
        entryFileNames: (chunk) => (chunk.name === 'sw' ? 'sw.js' : 'assets/[name]-[hash].js'),
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
  },
});
