import { QueryClientProvider } from '@tanstack/react-query';
import { render, type RenderOptions, type RenderResult } from '@testing-library/react';
import type { ReactElement, ReactNode } from 'react';
import { MemoryRouter } from 'react-router';

import { LicenseProvider } from '@/features/license/context/LicenseProvider';
import { createQueryClient } from '@/lib/api/queryClient';
import { LocaleProvider } from '@/lib/i18n/LocaleProvider';

interface Options extends Omit<RenderOptions, 'wrapper'> {
  /** Initial history entry. */
  readonly route?: string;
  /** Set to false to omit `LicenseProvider`, e.g. when testing it in isolation. */
  readonly withLicenseProvider?: boolean;
}

/**
 * Renders a component inside the providers the app supplies in production, using
 * `MemoryRouter` so tests can start on any route without touching the URL bar.
 *
 * A fresh `QueryClient` per render keeps one test's cache out of the next.
 */
export function renderWithProviders(
  ui: ReactElement,
  { route = '/', withLicenseProvider = true, ...options }: Options = {},
): RenderResult {
  const queryClient = createQueryClient();

  function Wrapper({ children }: { children: ReactNode }) {
    const routed = <MemoryRouter initialEntries={[route]}>{children}</MemoryRouter>;
    return (
      <LocaleProvider>
        <QueryClientProvider client={queryClient}>
          {withLicenseProvider ? <LicenseProvider clientLabel="web">{routed}</LicenseProvider> : routed}
        </QueryClientProvider>
      </LocaleProvider>
    );
  }

  return render(ui, { wrapper: Wrapper, ...options });
}
