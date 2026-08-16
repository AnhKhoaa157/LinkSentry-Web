import { useQuery } from '@tanstack/react-query';

import { getHealth } from '@/features/health/api/getHealth';

export const healthQueryKey = ['health'] as const;

/**
 * Polls the API health endpoint.
 *
 * Polling rather than one-shot so the indicator recovers on its own when the
 * developer starts the backend after the frontend — which is the normal order.
 */
export function useHealth() {
  return useQuery({
    queryKey: healthQueryKey,
    queryFn: ({ signal }) => getHealth(signal),
    refetchInterval: 30_000,
    retry: 0,
    staleTime: 10_000,
  });
}
