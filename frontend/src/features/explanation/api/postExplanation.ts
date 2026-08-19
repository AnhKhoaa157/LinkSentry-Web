import { apiClient } from '@/lib/api/client';
import {
  explanationResponseSchema,
  type ExplanationResponse,
} from '@/features/explanation/schemas/explanationResponse';

/** Never logs or renders `scanId` as a URL — it is an opaque path segment only. */
export function explanationEndpoint(scanId: string): string {
  return `/api/v1/scans/${encodeURIComponent(scanId)}/explanation`;
}

/**
 * Requests an optional, advisory AI explanation of one owned, retained scan.
 * There is no request body: everything the provider is allowed to see is built
 * entirely server-side from the already-persisted, already-safe scan snapshot.
 */
export async function postExplanation(scanId: string, signal?: AbortSignal): Promise<ExplanationResponse> {
  const response = await apiClient.post<unknown>(
    explanationEndpoint(scanId),
    undefined,
    signal ? { signal } : {},
  );
  return explanationResponseSchema.parse(response.data);
}
