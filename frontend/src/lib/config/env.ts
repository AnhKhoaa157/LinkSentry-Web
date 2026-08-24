import { z } from 'zod';

/**
 * Validated access to build-time environment variables.
 *
 * Parsing at module load means a missing or malformed `VITE_API_BASE_URL` fails
 * immediately with a precise message, rather than surfacing later as requests to
 * `undefined/api/v1/health`. The optional web-app URL is handled separately so
 * an extension without an official destination fails closed at the click site.
 */
const apiBaseUrlSchema = z
  .string()
  .url('VITE_API_BASE_URL must be an absolute URL, e.g. http://localhost:8080')
  .refine((value) => !value.endsWith('/'), 'VITE_API_BASE_URL must not end with a trailing slash');

const officialWebAppUrlSchema = z
  .string()
  .url('VITE_WEB_APP_URL must be an absolute URL, e.g. https://link-sentry-web.vercel.app')
  .refine((value) => /^https?:\/\//i.test(value), 'VITE_WEB_APP_URL must use HTTP or HTTPS')
  .refine((value) => !value.endsWith('/'), 'VITE_WEB_APP_URL must not end with a trailing slash');

const parsedApiBaseUrl = apiBaseUrlSchema.safeParse(
  import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080',
);

if (!parsedApiBaseUrl.success) {
  throw new Error(`Invalid frontend environment configuration:\n${z.prettifyError(parsedApiBaseUrl.error)}`);
}

/**
 * Returns only a safe official web-app base URL. An absent or malformed value
 * is deliberately represented as null so callers can show a localized error
 * instead of opening an arbitrary fallback destination.
 */
export function parseOfficialWebAppUrl(value: string | null | undefined): string | null {
  if (value === null || value === undefined || value.length === 0 || value.trim() !== value) {
    return null;
  }

  const parsed = officialWebAppUrlSchema.safeParse(value);
  if (!parsed.success) {
    return null;
  }

  try {
    const url = new URL(value);
    if (url.username || url.password || url.search || url.hash) {
      return null;
    }
  } catch {
    return null;
  }

  return value;
}

/**
 * Builds one of the extension's static web-app destinations. Keeping the
 * pathname closed over known values prevents submitted scan data from being
 * carried into a new tab.
 */
export function buildOfficialWebAppUrl(
  baseUrl: string | null | undefined,
  pathname: '' | '/history',
): string | null {
  const safeBaseUrl = parseOfficialWebAppUrl(baseUrl);
  return safeBaseUrl === null ? null : `${safeBaseUrl}${pathname}`;
}

export const env = {
  apiBaseUrl: parsedApiBaseUrl.data,
  webAppUrl: parseOfficialWebAppUrl(import.meta.env.VITE_WEB_APP_URL),
} as const;
