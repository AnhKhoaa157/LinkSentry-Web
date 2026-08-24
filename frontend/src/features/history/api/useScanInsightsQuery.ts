import { useQuery } from '@tanstack/react-query';

import { DEFAULT_SCAN_INSIGHTS_WINDOW_DAYS, getScanInsights } from '@/features/history/api/getScanInsights';
import {
  scanInsightsWindowDaysSchema,
  type ScanInsightsWindowDays,
} from '@/features/history/schemas/scanHistoryResponse';

export const scanInsightsQueryKey = ['scan-insights'] as const;

/** Loads private personal insights only while the licensed history subtree is mounted. */
export function useScanInsightsQuery(windowDays: ScanInsightsWindowDays = DEFAULT_SCAN_INSIGHTS_WINDOW_DAYS) {
  const validatedWindowDays = scanInsightsWindowDaysSchema.parse(windowDays);

  return useQuery({
    queryKey: [...scanInsightsQueryKey, validatedWindowDays],
    queryFn: ({ signal }) => getScanInsights({ windowDays: validatedWindowDays, signal }),
    retry: false,
    gcTime: 0,
  });
}
