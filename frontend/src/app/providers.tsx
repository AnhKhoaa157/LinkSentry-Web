import { QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { useState } from 'react';
import { BrowserRouter } from 'react-router';

import { createQueryClient } from '@/lib/api/queryClient';
import { LocaleProvider } from '@/lib/i18n/LocaleProvider';

interface AppProvidersProps {
  readonly children: ReactNode;
}

/**
 * Router, locale, and server-state providers wrapped around the application shell.
 *
 * The device-license `LicenseProvider` deliberately lives in `AppLayout`, not here: it must wrap
 * only the public shell, never `/admin`'s separate console and its own auth provider. `LocaleProvider`
 * lives here instead, since display language isn't a licensing concern and the shared, translated
 * scanner/explanation components must work under `/admin` too if ever reused there.
 */
export function AppProviders({ children }: AppProvidersProps) {
  const [queryClient] = useState(createQueryClient);

  return (
    <LocaleProvider>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>{children}</BrowserRouter>
      </QueryClientProvider>
    </LocaleProvider>
  );
}
