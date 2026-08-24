import { useQuery, useQueryClient, type UseQueryResult } from '@tanstack/react-query';
import { useEffect } from 'react';

import { postExplanation } from '@/features/explanation/api/postExplanation';
import type { ExplanationResponse } from '@/features/explanation/schemas/explanationResponse';
import type { Locale } from '@/lib/i18n/locale';

/**
 * How long a validated advisory response is reused for the same scan and
 * locale before the next request calls the backend again.
 */
export const EXPLANATION_CACHE_TTL_MS = 10 * 60 * 1000;

/** Root key covering every cached explanation, across every scan and locale. */
export const EXPLANATION_QUERY_ROOT_KEY = ['explanation'] as const;

export function explanationQueryKey(scanId: string, locale: Locale) {
  return [...EXPLANATION_QUERY_ROOT_KEY, scanId, locale] as const;
}

interface UseExplanationQueryArgs {
  /** `null` renders nothing upstream; passed straight through so hook order never depends on it. */
  readonly scanId: string | null;
  readonly locale: Locale;
  /** True once the user has explicitly requested an explanation this mount — never on mount alone. */
  readonly requested: boolean;
  /** From `useLicense().isLicensed` — see the module doc for why the cache is purged, not just gated, when this is false. */
  readonly isLicensed: boolean;
}

/**
 * Memory-only, per-session cache for one scan+locale's AI advisory, built
 * entirely on the shared TanStack Query client (`lib/api/queryClient.ts`): a
 * successful, Zod-validated response (`postExplanation` throws on anything
 * else, so only a validated success is ever cached) is reused for
 * {@link EXPLANATION_CACHE_TTL_MS} instead of calling the backend again for
 * the same `(scanId, locale)` pair — a different locale for the same scan is
 * a different cache entry. Nothing here touches `localStorage`,
 * `sessionStorage`, `IndexedDB`, a URL parameter, a service worker, or a
 * backend store: the cache lives only in the `QueryClient` instance created
 * fresh per page load / extension popup open (`createQueryClient`), so it
 * cannot survive a reload or a new session, and a request the user cancels
 * (unmount, or a superseded call) or a `503 AI_EXPLANATION_UNAVAILABLE`/
 * malformed response never becomes cached `data` — only `queryFn` resolving
 * does that, and both of those cases reject instead.
 *
 * `isLicensed` is a plain argument rather than read via `useLicense()` here,
 * so this hook stays independently testable. The instant it is `false` the
 * *entire* explanation cache — every scan, every locale, not just the current
 * one — is removed from the query client outright (not merely marked stale):
 * a disabled query still returns its last-known `data` to the caller, so
 * gating only `enabled` would leave a previously cached advisory readable
 * after the device stops reporting `LICENSED`. Deleting the cache entries is
 * what actually makes that impossible.
 */
export function useExplanationQuery({
  scanId,
  locale,
  requested,
  isLicensed,
}: UseExplanationQueryArgs): UseQueryResult<ExplanationResponse> {
  const queryClient = useQueryClient();

  useEffect(() => {
    if (!isLicensed) {
      queryClient.removeQueries({ queryKey: EXPLANATION_QUERY_ROOT_KEY });
    }
  }, [isLicensed, queryClient]);

  return useQuery({
    queryKey: explanationQueryKey(scanId ?? '', locale),
    queryFn: ({ signal }) => postExplanation(scanId ?? '', { locale, signal }),
    // `isLicensed` gates the fetch itself, not just the purge above: a disabled
    // query alone would still hand back its last-known `data` to the caller, so
    // gating is what actually stops a stale advisory from staying readable the
    // instant the license is lost — the removeQueries call above deletes the
    // cache entry, and this is what makes the still-mounted observer notice.
    enabled: requested && isLicensed && Boolean(scanId),
    staleTime: EXPLANATION_CACHE_TTL_MS,
    gcTime: EXPLANATION_CACHE_TTL_MS,
    retry: false,
  });
}
