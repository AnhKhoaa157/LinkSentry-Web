import { apiClient } from '@/lib/api/client';
import {
  scanInsightsResponseSchema,
  scanInsightsWindowDaysSchema,
  type ScanInsightsResponse,
  type ScanInsightsWindowDays,
} from '@/features/history/schemas/scanHistoryResponse';

export const SCAN_INSIGHTS_ENDPOINT = '/api/v1/scans/insights';
export const DEFAULT_SCAN_INSIGHTS_WINDOW_DAYS: ScanInsightsWindowDays = 30;

export interface GetScanInsightsOptions {
  readonly windowDays?: ScanInsightsWindowDays;
  readonly signal?: AbortSignal;
}

/** Retrieves and validates only private, license-scoped aggregate insight data. */
export async function getScanInsights(options: GetScanInsightsOptions = {}): Promise<ScanInsightsResponse> {
  const windowDays = scanInsightsWindowDaysSchema.parse(
    options.windowDays ?? DEFAULT_SCAN_INSIGHTS_WINDOW_DAYS,
  );
  const response = await apiClient.get<unknown>(SCAN_INSIGHTS_ENDPOINT, {
    params: { windowDays },
    ...(options.signal ? { signal: options.signal } : {}),
  });
  return scanInsightsResponseSchema.parse(response.data);
}
