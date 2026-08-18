import { QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { useState } from 'react';
import { BrowserRouter } from 'react-router';

import { AuthProvider } from '@/features/auth/context/AuthProvider';
import { createQueryClient } from '@/lib/api/queryClient';

interface AppProvidersProps {
  readonly children: ReactNode;
}

/** Router and server-state providers wrapped around the application shell. */
export function AppProviders({ children }: AppProvidersProps) {
  const [queryClient] = useState(createQueryClient);

  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <BrowserRouter>{children}</BrowserRouter>
      </AuthProvider>
    </QueryClientProvider>
  );
}
