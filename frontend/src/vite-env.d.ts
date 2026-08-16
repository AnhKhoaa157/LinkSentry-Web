/// <reference types="vite/client" />

/**
 * Typed build-time environment. Access it through `@/lib/config/env`, which
 * validates these values instead of trusting them.
 */
interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
