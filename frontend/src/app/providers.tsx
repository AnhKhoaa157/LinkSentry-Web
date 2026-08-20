import { QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { useState } from 'react';
import { BrowserRouter } from 'react-router';

import { createQueryClient } from '@/lib/api/queryClient';

interface AppProvidersProps {
  readonly children: ReactNode;
}

/**
 * Router and server-state providers wrapped around the application shell.
 *
 * The device-license `LicenseProvider` deliberately lives in `AppLayout`, not here: it must wrap
 * only the public shell, never `/admin`'s separate console and its own auth provider.
 */
export function AppProviders({ children }: AppProvidersProps) {
  const [queryClient] = useState(createQueryClient);

  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>{children}</BrowserRouter>
    </QueryClientProvider>
  );
}
