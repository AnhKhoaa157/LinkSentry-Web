import { QueryClient } from '@tanstack/react-query';

/**
 * Builds the TanStack Query client.
 *
 * A factory rather than a module-level singleton so each test gets a clean cache;
 * a shared client would leak one test's data into the next.
 */
export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        // Analysis is deterministic, so a result never becomes stale on its own.
        // Refetching on window focus would be pure noise.
        refetchOnWindowFocus: false,
        retry: 1,
        staleTime: 30_000,
      },
    },
  });
}
