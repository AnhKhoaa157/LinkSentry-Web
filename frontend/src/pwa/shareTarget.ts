/**
 * Service-worker side of the Web Share Target ("Share → LinkSentry" on Android).
 *
 * The manifest sends a share as a POST to SHARE_TARGET_PATH, so the shared text
 * travels in the request body rather than a query string. The service worker
 * answers that POST itself: the request never reaches Vercel, so the link never
 * appears in a server access log. The link is held in memory only, for at most a
 * minute, until the page asks for it over a MessageChannel. It is never put in a
 * URL, so it never reaches browser history either.
 *
 * Imported only by serviceWorker.ts. It must stay free of imports, so that the
 * service-worker bundle is a single self-contained file.
 */
export const SHARE_TARGET_PATH = '/share-target';
/** Where the share navigation lands. The marker carries no data. */
export const SHARED_LAUNCH_PATH = '/?shared=1';
export const TAKE_SHARED_LINK = 'linksentry/take-shared-link';
export const SHARED_LINK_TTL_MS = 60_000;

/** Same ceiling the API enforces. */
const MAX_LINK_LENGTH = 2048;
const LINK_IN_TEXT = /https?:\/\/[^\s<>"'`]+/i;
/** Sentence punctuation that commonly follows a pasted link. */
const TRAILING_PUNCTUATION = /[.,;:!?)\]}'"]+$/;

export interface SharedFields {
  url?: unknown;
  text?: unknown;
  title?: unknown;
}

/**
 * The first http(s) link in a share: the url field if it is one, otherwise the
 * first link inside the text or title. Many Android apps put the link in text,
 * often after a message such as "Look at this: https://...".
 */
export function pickSharedLink(fields: SharedFields): string | null {
  for (const candidate of [fields.url, fields.text, fields.title]) {
    if (typeof candidate !== 'string') {
      continue;
    }
    const value = candidate.trim();
    const direct = asScannableLink(value);
    if (direct !== null) {
      return direct;
    }
    const embedded = LINK_IN_TEXT.exec(value)?.[0];
    if (embedded !== undefined) {
      const cleaned = asScannableLink(embedded.replace(TRAILING_PUNCTUATION, ''));
      if (cleaned !== null) {
        return cleaned;
      }
    }
  }
  return null;
}

function asScannableLink(value: string): string | null {
  if (value.length === 0 || value.length > MAX_LINK_LENGTH) {
    return null;
  }
  try {
    const { protocol } = new URL(value);
    return protocol === 'http:' || protocol === 'https:' ? value : null;
  } catch {
    return null;
  }
}

export interface ShareInbox {
  put(url: string): void;
  /** Returns the held link once, or null if there is none or it expired. */
  take(): string | null;
}

/** Holds at most one shared link, in memory, until the page takes it or it expires. */
export function createShareInbox(now: () => number = Date.now): ShareInbox {
  let pending: { url: string; expiresAt: number } | null = null;
  return {
    put(url) {
      pending = { url, expiresAt: now() + SHARED_LINK_TTL_MS };
    },
    take() {
      const entry = pending;
      pending = null;
      return entry !== null && entry.expiresAt >= now() ? entry.url : null;
    },
  };
}

/**
 * Answers the share-target POST: keeps the link, if the share had one, and
 * redirects the navigation into the app. A share with no usable link still lands
 * in the app, which then tells the user to paste the link instead.
 */
export async function handleShareRequest(request: Request, inbox: ShareInbox): Promise<Response> {
  try {
    const form = await request.formData();
    const link = pickSharedLink({ url: form.get('url'), text: form.get('text'), title: form.get('title') });
    if (link !== null) {
      inbox.put(link);
    }
  } catch {
    // A malformed body is treated like a share without a link.
  }
  return Response.redirect(new URL(SHARED_LAUNCH_PATH, request.url).href, 303);
}
