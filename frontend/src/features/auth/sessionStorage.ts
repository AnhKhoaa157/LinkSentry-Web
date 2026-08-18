const ACCESS_TOKEN_KEY = 'linksentry.accessToken';
export const AUTH_UNAUTHORIZED_EVENT = 'linksentry:unauthorized';

/** Reads only the browser session token; callers must never put it in React state or markup. */
export function getAccessToken(): string | null {
  try {
    return sessionStorage.getItem(ACCESS_TOKEN_KEY);
  } catch {
    return null;
  }
}

export function setAccessToken(token: string): void {
  try {
    sessionStorage.setItem(ACCESS_TOKEN_KEY, token);
  } catch {
    // A blocked storage implementation leaves the request unauthenticated rather
    // than falling back to localStorage or a cookie.
  }
}

export function clearAccessToken(): void {
  try {
    sessionStorage.removeItem(ACCESS_TOKEN_KEY);
  } catch {
    // There is no safer persistent fallback.
  }
}

export function notifyUnauthorized(): void {
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new Event(AUTH_UNAUTHORIZED_EVENT));
  }
}
