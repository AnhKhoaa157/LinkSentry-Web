import { apiClient } from '@/lib/api/client';
import {
  scanHistoryResponseSchema,
  type ScanHistoryResponse,
} from '@/features/history/schemas/scanHistoryResponse';

export const SCAN_HISTORY_ENDPOINT = '/api/v1/scans';
export const SCAN_HISTORY_PAGE_SIZE = 20;

export interface GetScanHistoryOptions {
  readonly cursor?: string | null;
  readonly limit?: number;
  readonly signal?: AbortSignal;
}

/** Retrieves and validates one private, license-scoped history page. */
export async function getScanHistory(options: GetScanHistoryOptions = {}): Promise<ScanHistoryResponse> {
  const params: { limit: number; cursor?: string } = {
    limit: options.limit ?? SCAN_HISTORY_PAGE_SIZE,
  };
  if (options.cursor !== undefined && options.cursor !== null) {
    params.cursor = options.cursor;
  }

  const response = await apiClient.get<unknown>(SCAN_HISTORY_ENDPOINT, {
    params,
    ...(options.signal ? { signal: options.signal } : {}),
  });
  return scanHistoryResponseSchema.parse(response.data);
}
