import { useMutation } from '@tanstack/react-query';

import { postScan } from '@/features/scanner/api/postScan';

/** Submits a URL for analysis. One scan per call — see queryClient's mutation defaults. */
export function useScanMutation() {
  return useMutation({
    mutationFn: (url: string) => postScan(url),
  });
}
