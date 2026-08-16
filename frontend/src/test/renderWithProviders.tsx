import { QueryClientProvider } from '@tanstack/react-query';
import { render, type RenderOptions, type RenderResult } from '@testing-library/react';
import type { ReactElement, ReactNode } from 'react';
import { MemoryRouter } from 'react-router';

import { createQueryClient } from '@/lib/api/queryClient';

interface Options extends Omit<RenderOptions, 'wrapper'> {
  /** Initial history entry. */
  readonly route?: string;
}

/**
 * Renders a component inside the providers the app supplies in production, using
 * `MemoryRouter` so tests can start on any route without touching the URL bar.
 *
 * A fresh `QueryClient` per render keeps one test's cache out of the next.
 */
export function renderWithProviders(
  ui: ReactElement,
  { route = '/', ...options }: Options = {},
): RenderResult {
  const queryClient = createQueryClient();

  function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[route]}>{children}</MemoryRouter>
      </QueryClientProvider>
    );
  }

  return render(ui, { wrapper: Wrapper, ...options });
}
