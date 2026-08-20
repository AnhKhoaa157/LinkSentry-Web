const ADMIN_ACCESS_TOKEN_KEY = 'linksentry.admin.accessToken';
export const ADMIN_UNAUTHORIZED_EVENT = 'linksentry:admin:unauthorized';

/**
 * A key deliberately distinct from `features/auth/sessionStorage`'s end-user token: an
 * administrator session and an end-user session are unrelated identities and must never share
 * storage, an Axios instance, or a 401 handler.
 */

/** Reads only the browser session token; callers must never put it in React state or markup. */
export function getAdminAccessToken(): string | null {
  try {
    return sessionStorage.getItem(ADMIN_ACCESS_TOKEN_KEY);
  } catch {
    return null;
  }
}

export function setAdminAccessToken(token: string): void {
  try {
    sessionStorage.setItem(ADMIN_ACCESS_TOKEN_KEY, token);
  } catch {
    // A blocked storage implementation leaves the request unauthenticated rather than
    // falling back to localStorage or a cookie.
  }
}

export function clearAdminAccessToken(): void {
  try {
    sessionStorage.removeItem(ADMIN_ACCESS_TOKEN_KEY);
  } catch {
    // There is no safer persistent fallback.
  }
}

export function notifyAdminUnauthorized(): void {
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new Event(ADMIN_UNAUTHORIZED_EVENT));
  }
}
