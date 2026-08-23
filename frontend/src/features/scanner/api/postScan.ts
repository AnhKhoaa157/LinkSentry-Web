import { apiClient } from '@/lib/api/client';
import { scanResponseSchema, type ScanResponse } from '@/features/scanner/schemas/scanResponse';
import { localeRequestConfig, type LocaleRequestOptions } from '@/lib/api/localeRequestConfig';

export const SCAN_ENDPOINT = '/api/v1/scans';

/**
 * Submits a URL for analysis and validates the response.
 *
 * Never logs `url`, including on failure: the caller's `catch` sees whatever
 * Axios throws, and nothing here writes the value anywhere first.
 */
export async function postScan(url: string, options?: LocaleRequestOptions): Promise<ScanResponse> {
  const response = await apiClient.post<unknown>(SCAN_ENDPOINT, { url }, localeRequestConfig(options));
  return scanResponseSchema.parse(response.data);
}
