import { apiClient } from '@/lib/api/client';
import { localeRequestConfig, type LocaleRequestOptions } from '@/lib/api/localeRequestConfig';
import {
  batchScanResponseSchema,
  type BatchScanResponse,
} from '@/features/scanner/schemas/batchScanResponse';

export const BATCH_SCAN_ENDPOINT = '/api/v1/scans/batch';

/** Submits a bounded list for server-owned, per-item static URL analysis. */
export async function postBatchScan(
  urls: readonly string[],
  options?: LocaleRequestOptions,
): Promise<BatchScanResponse> {
  const response = await apiClient.post<unknown>(BATCH_SCAN_ENDPOINT, { urls }, localeRequestConfig(options));
  return batchScanResponseSchema.parse(response.data);
}
