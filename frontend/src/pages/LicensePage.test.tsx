import { screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { LicensePage } from '@/pages/LicensePage';
import { renderWithProviders } from '@/test/renderWithProviders';

describe('LicensePage', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('explains the device-license model and shows the installation status card', async () => {
    renderWithProviders(<LicensePage />);

    expect(screen.getByRole('heading', { name: 'License', level: 1 })).toBeInTheDocument();
    expect(screen.getByText(/no accounts, passwords, or sign-in/i)).toBeInTheDocument();
    // The default bootstrap mock (test/setup.ts) returns this activation code.
    expect(await screen.findByText('TEST-CODE')).toBeInTheDocument();
  });

  it('links back to the scanner', () => {
    renderWithProviders(<LicensePage />);

    expect(screen.getByRole('link', { name: /back to scanner/i })).toHaveAttribute('href', '/');
  });
});
