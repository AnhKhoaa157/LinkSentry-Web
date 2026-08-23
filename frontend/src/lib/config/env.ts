import { z } from 'zod';

/**
 * Validated access to build-time environment variables.
 *
 * Parsing at module load means a missing or malformed `VITE_API_BASE_URL` fails
 * immediately with a precise message, rather than surfacing later as requests to
 * `undefined/api/v1/health`.
 */
const envSchema = z.object({
  VITE_API_BASE_URL: z
    .string()
    .url('VITE_API_BASE_URL must be an absolute URL, e.g. http://localhost:8080')
    .refine((value) => !value.endsWith('/'), 'VITE_API_BASE_URL must not end with a trailing slash'),
  VITE_WEB_APP_URL: z
    .string()
    .url('VITE_WEB_APP_URL must be an absolute URL, e.g. https://link-sentry-web.vercel.app')
    .refine((value) => !value.endsWith('/'), 'VITE_WEB_APP_URL must not end with a trailing slash'),
});

const parsed = envSchema.safeParse({
  VITE_API_BASE_URL: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080',
  VITE_WEB_APP_URL: import.meta.env.VITE_WEB_APP_URL ?? 'https://link-sentry-web.vercel.app',
});

if (!parsed.success) {
  throw new Error(`Invalid frontend environment configuration:\n${z.prettifyError(parsed.error)}`);
}

export const env = {
  apiBaseUrl: parsed.data.VITE_API_BASE_URL,
  webAppUrl: parsed.data.VITE_WEB_APP_URL,
} as const;
