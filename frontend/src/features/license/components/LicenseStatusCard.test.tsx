import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { getDeviceStatus } from '@/features/license/api/device';
import { LicenseStatusCard } from '@/features/license/components/LicenseStatusCard';
import { deviceCredentialStorage } from '@/lib/device/deviceCredentialStorage';
import { renderWithProviders } from '@/test/renderWithProviders';

async function renderWithState(
  state: 'PENDING' | 'LICENSED' | 'EXPIRED' | 'REVOKED',
  licenseExpiresAt: string | null = null,
) {
  await deviceCredentialStorage.set('existing-credential');
  vi.mocked(getDeviceStatus).mockResolvedValue({ state, activationCode: 'K7H9-QX3P', licenseExpiresAt });
  return renderWithProviders(<LicenseStatusCard />);
}

describe('LicenseStatusCard', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('shows a safe Copy activation code action while pending, and never the device credential', async () => {
    await renderWithState('PENDING');

    expect(await screen.findByText('K7H9-QX3P')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /copy activation code/i })).toBeInTheDocument();
    expect(document.body).not.toHaveTextContent('existing-credential');
  });

  it('copies the activation code to the clipboard on click', async () => {
    await renderWithState('PENDING');
    // `userEvent.setup()` installs its own clipboard handling, so the manual
    // stub must be defined after it (and immediately before the click) to be
    // the one `navigator.clipboard.writeText` actually resolves to — the same
    // ordering constraint noted in NextSteps.test.tsx's `fireEvent`-based
    // equivalent, which sidesteps it by never calling `userEvent.setup()`.
    const user = userEvent.setup();
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText },
      configurable: true,
      writable: true,
    });

    await user.click(await screen.findByRole('button', { name: /copy activation code/i }));

    await waitFor(() => expect(writeText).toHaveBeenCalledWith('K7H9-QX3P'));
    expect(await screen.findByText(/copied/i)).toBeInTheDocument();
  });

  it('shows expiry and hides the activation-code action once licensed', async () => {
    await renderWithState('LICENSED', '2027-01-01T00:00:00Z');

    await waitFor(() => expect(screen.getByText('Licensed')).toBeInTheDocument());
    expect(screen.queryByRole('button', { name: /copy activation code/i })).not.toBeInTheDocument();
  });

  it('shows the activation-code action again for an expired license, alongside when it expired', async () => {
    await renderWithState('EXPIRED', '2020-01-01T00:00:00Z');

    expect(await screen.findByRole('button', { name: /copy activation code/i })).toBeInTheDocument();
    expect(screen.getByText('Expired')).toBeInTheDocument();
  });

  it('shows the activation-code action again for a revoked device', async () => {
    await renderWithState('REVOKED');

    expect(await screen.findByRole('button', { name: /copy activation code/i })).toBeInTheDocument();
    expect(screen.getByText('Revoked')).toBeInTheDocument();
  });
});
