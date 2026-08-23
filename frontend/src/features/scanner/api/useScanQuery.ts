import { useQuery } from '@tanstack/react-query';

import { getScan } from '@/features/scanner/api/getScan';
import type { Locale } from '@/lib/i18n/locale';

/** Loads one retained scan result without retrying a not-found permalink. */
export function useScanQuery(scanId: string | undefined, locale: Locale = 'en') {
  return useQuery({
    queryKey: ['scan', scanId, locale],
    queryFn: ({ signal }) => getScan(scanId ?? '', { signal, locale }),
    enabled: Boolean(scanId),
    retry: false,
  });
}
