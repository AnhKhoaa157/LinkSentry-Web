import { useInfiniteQuery } from '@tanstack/react-query';

import { getScanHistory, SCAN_HISTORY_PAGE_SIZE } from '@/features/history/api/getScanHistory';
import {
  DEFAULT_SCAN_HISTORY_FILTERS,
  scanHistoryFiltersSchema,
  type ScanHistoryFilters,
} from '@/features/history/schemas/scanHistoryResponse';

export const scanHistoryQueryKey = ['scan-history'] as const;

/** Loads private history using the backend's opaque cursor without interpreting it. */
export function useScanHistoryQuery(filters: ScanHistoryFilters = DEFAULT_SCAN_HISTORY_FILTERS) {
  const validatedFilters = scanHistoryFiltersSchema.parse(filters);

  return useInfiniteQuery({
    queryKey: [...scanHistoryQueryKey, validatedFilters],
    queryFn: ({ pageParam, signal }) =>
      getScanHistory({
        limit: SCAN_HISTORY_PAGE_SIZE,
        cursor: pageParam,
        riskLevel: validatedFilters.riskLevel,
        ruleId: validatedFilters.ruleId,
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
