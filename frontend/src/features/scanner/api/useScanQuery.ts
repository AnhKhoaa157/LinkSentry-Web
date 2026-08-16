import { useQuery } from '@tanstack/react-query';

import { getScan } from '@/features/scanner/api/getScan';

/** Loads one retained scan result without retrying a not-found permalink. */
export function useScanQuery(scanId: string | undefined) {
  return useQuery({
    queryKey: ['scan', scanId],
    queryFn: ({ signal }) => getScan(scanId ?? '', signal),
    enabled: Boolean(scanId),
    retry: false,
  });
}
