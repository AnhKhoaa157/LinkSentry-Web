import { screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { SiteHeader } from '@/components/layout/SiteHeader';
import { getDeviceStatus } from '@/features/license/api/device';
import { deviceCredentialStorage } from '@/lib/device/deviceCredentialStorage';
import { renderWithProviders } from '@/test/renderWithProviders';

describe('SiteHeader license status', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('shows a checking state before the device check settles', () => {
    vi.mocked(getDeviceStatus).mockReturnValue(new Promise(() => {}));

    renderWithProviders(<SiteHeader />);

    expect(screen.getByText(/checking/i)).toBeInTheDocument();
  });

  it('uses the single-colour PNG brand mark', () => {
    renderWithProviders(<SiteHeader />);

    const homeLink = screen.getByRole('link', { name: /linksentry home/i });
    const mark = homeLink.querySelector('img');

    expect(homeLink).toHaveTextContent('LinkSentry');
    expect(mark).toHaveAttribute('src', '/brand/linksentry-mark.png');
    expect(mark).toHaveClass('brand-mark');
  });

  it('links an unlicensed (trial) device to the License page', async () => {
    renderWithProviders(<SiteHeader />);

    const link = await screen.findByRole('link', { name: /installation status: trial/i });
    expect(link).toHaveAttribute('href', '/license');
    expect(link).toHaveTextContent('Trial');
    expect(screen.queryByRole('link', { name: 'History' })).not.toBeInTheDocument();
  });

  it('shows Licensed for a device with an active license', async () => {
    await deviceCredentialStorage.set('existing-credential');
    vi.mocked(getDeviceStatus).mockResolvedValue({
      state: 'LICENSED',
      activationCode: 'AAAA-BBBB',
      licenseExpiresAt: null,
    });

    renderWithProviders(<SiteHeader />);

    expect(await screen.findByRole('link', { name: /installation status: licensed/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'History' })).toHaveAttribute('href', '/history');
  });

  it('shows Revoked for a revoked device, distinct from Trial or Expired', async () => {
    await deviceCredentialStorage.set('existing-credential');
    vi.mocked(getDeviceStatus).mockResolvedValue({
      state: 'REVOKED',
      activationCode: 'AAAA-BBBB',
      licenseExpiresAt: null,
    });

    renderWithProviders(<SiteHeader />);

    await waitFor(() =>
      expect(screen.getByRole('link', { name: /installation status: revoked/i })).toBeInTheDocument(),
    );
  });

  it('never renders the device credential anywhere in the header', async () => {
    await deviceCredentialStorage.set('super-secret-device-credential');
    vi.mocked(getDeviceStatus).mockResolvedValue({
      state: 'LICENSED',
      activationCode: 'AAAA-BBBB',
      licenseExpiresAt: null,
    });

    renderWithProviders(<SiteHeader />);
    await screen.findByRole('link', { name: /installation status: licensed/i });

    expect(document.body).not.toHaveTextContent('super-secret-device-credential');
  });
});
