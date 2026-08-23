import { useInfiniteQuery } from '@tanstack/react-query';

import { getScanHistory, SCAN_HISTORY_PAGE_SIZE } from '@/features/history/api/getScanHistory';

export const scanHistoryQueryKey = ['scan-history'] as const;

/** Loads private history using the backend's opaque cursor without interpreting it. */
export function useScanHistoryQuery() {
  return useInfiniteQuery({
    queryKey: scanHistoryQueryKey,
    queryFn: ({ pageParam, signal }) =>
      getScanHistory({
        limit: SCAN_HISTORY_PAGE_SIZE,
        cursor: pageParam,
        signal,
      }),
    initialPageParam: null as string | null,
    getNextPageParam: (lastPage) => lastPage.data.nextCursor ?? undefined,
    retry: false,
    // A history observer exists only while the page reports LICENSED. Drop its
    // cache as soon as that observer unmounts so a later license assignment can
    // never inherit another license's previously rendered page.
    gcTime: 0,
  });
}
