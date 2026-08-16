import { apiClient } from '@/lib/api/client';
import { scanResponseSchema, type ScanResponse } from '@/features/scanner/schemas/scanResponse';

export const SCAN_BY_ID_ENDPOINT = '/api/v1/scans';

/** Retrieves and validates one persisted scan by its opaque UUID. */
export async function getScan(scanId: string, signal?: AbortSignal): Promise<ScanResponse> {
  const response = await apiClient.get<unknown>(
    `${SCAN_BY_ID_ENDPOINT}/${encodeURIComponent(scanId)}`,
    signal ? { signal } : {},
  );
  return scanResponseSchema.parse(response.data);
}
