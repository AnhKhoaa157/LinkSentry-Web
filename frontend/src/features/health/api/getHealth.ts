import { apiClient } from '@/lib/api/client';
import { healthResponseSchema, type HealthResponse } from '@/features/health/types/health';

export const HEALTH_ENDPOINT = '/api/v1/health';

/** Fetches and validates the API health response. */
export async function getHealth(signal?: AbortSignal): Promise<HealthResponse> {
  const response = await apiClient.get<unknown>(HEALTH_ENDPOINT, signal ? { signal } : {});
  return healthResponseSchema.parse(response.data);
}
