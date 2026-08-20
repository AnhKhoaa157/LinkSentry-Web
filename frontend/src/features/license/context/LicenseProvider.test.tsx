import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { bootstrapDevice, getDeviceStatus } from '@/features/license/api/device';
import { LicenseProvider } from '@/features/license/context/LicenseProvider';
import { useLicense } from '@/features/license/context/useLicense';
import { deviceCredentialStorage } from '@/lib/device/deviceCredentialStorage';

function Consumer() {
  const { state, activationCode, licenseExpiresAt, isLoading, isLicensed, refresh } = useLicense();
  return (
    <div>
      <p data-testid="state">{isLoading ? 'loading' : (state ?? 'unknown')}</p>
      <p data-testid="code">{activationCode ?? ''}</p>
      <p data-testid="expiry">{licenseExpiresAt ?? ''}</p>
      <p data-testid="licensed">{String(isLicensed)}</p>
      <button type="button" onClick={() => void refresh()}>
        refresh
      </button>
    </div>
  );
}

describe('LicenseProvider', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('bootstraps a new installation when no credential is stored, and persists only the credential', async () => {
    vi.mocked(bootstrapDevice).mockResolvedValue({
      deviceId: 'device-1',
      activationCode: 'K7H9-QX3P',
      credential: 'brand-new-credential',
    });

    render(
      <LicenseProvider clientLabel="web">
        <Consumer />
      </LicenseProvider>,
    );

    await waitFor(() => expect(screen.getByTestId('state')).toHaveTextContent('PENDING'));
    expect(screen.getByTestId('code')).toHaveTextContent('K7H9-QX3P');
    expect(screen.getByTestId('licensed')).toHaveTextContent('false');
    await expect(deviceCredentialStorage.get()).resolves.toBe('brand-new-credential');
    expect(document.body).not.toHaveTextContent('brand-new-credential');
  });

  it('checks status instead of bootstrapping when a credential is already stored', async () => {
    await deviceCredentialStorage.set('existing-credential');
    vi.mocked(getDeviceStatus).mockResolvedValue({
      state: 'LICENSED',
      activationCode: 'AAAA-BBBB',
      licenseExpiresAt: '2027-01-01T00:00:00Z',
    });

    render(
      <LicenseProvider clientLabel="web">
        <Consumer />
      </LicenseProvider>,
    );

    await waitFor(() => expect(screen.getByTestId('state')).toHaveTextContent('LICENSED'));
    expect(screen.getByTestId('licensed')).toHaveTextContent('true');
    expect(screen.getByTestId('expiry')).toHaveTextContent('2027-01-01T00:00:00Z');
    expect(bootstrapDevice).not.toHaveBeenCalled();
  });

  it('reports EXPIRED and REVOKED as not licensed', async () => {
    await deviceCredentialStorage.set('existing-credential');
    vi.mocked(getDeviceStatus).mockResolvedValue({
      state: 'EXPIRED',
      activationCode: 'AAAA-BBBB',
      licenseExpiresAt: '2020-01-01T00:00:00Z',
    });

    render(
      <LicenseProvider clientLabel="web">
        <Consumer />
      </LicenseProvider>,
    );

    await waitFor(() => expect(screen.getByTestId('state')).toHaveTextContent('EXPIRED'));
    expect(screen.getByTestId('licensed')).toHaveTextContent('false');
  });

  it('clears the stored credential and bootstraps a fresh one when the server no longer recognises it', async () => {
    await deviceCredentialStorage.set('stale-credential');
    const notRecognised = Object.assign(new Error('invalid'), {
      code: 'INVALID_DEVICE_CREDENTIAL',
      isNetworkError: false,
    });
    vi.mocked(getDeviceStatus).mockRejectedValue(notRecognised);
    vi.mocked(bootstrapDevice).mockResolvedValue({
      deviceId: 'device-2',
      activationCode: 'FRESH-CODE',
      credential: 'fresh-credential',
    });

    render(
      <LicenseProvider clientLabel="web">
        <Consumer />
      </LicenseProvider>,
    );

    await waitFor(() => expect(screen.getByTestId('code')).toHaveTextContent('FRESH-CODE'));
    expect(screen.getByTestId('state')).toHaveTextContent('PENDING');
    await expect(deviceCredentialStorage.get()).resolves.toBe('fresh-credential');
  });

  it('leaves the stored credential and prior state untouched on a transient failure (e.g. network error)', async () => {
    await deviceCredentialStorage.set('existing-credential');
    const networkFailure = Object.assign(new Error('network down'), {
      code: 'NETWORK_ERROR',
      isNetworkError: true,
    });
    vi.mocked(getDeviceStatus).mockRejectedValue(networkFailure);

    render(
      <LicenseProvider clientLabel="web">
        <Consumer />
      </LicenseProvider>,
    );

    await waitFor(() => expect(screen.getByTestId('state')).toHaveTextContent('unknown'));
    expect(bootstrapDevice).not.toHaveBeenCalled();
    await expect(deviceCredentialStorage.get()).resolves.toBe('existing-credential');
  });

  it('refresh re-checks status against the server on demand', async () => {
    await deviceCredentialStorage.set('existing-credential');
    vi.mocked(getDeviceStatus)
      .mockResolvedValueOnce({ state: 'PENDING', activationCode: 'AAAA-BBBB', licenseExpiresAt: null })
      .mockResolvedValueOnce({ state: 'LICENSED', activationCode: 'AAAA-BBBB', licenseExpiresAt: null });
    const user = userEvent.setup();

    render(
      <LicenseProvider clientLabel="web">
        <Consumer />
      </LicenseProvider>,
    );
    await waitFor(() => expect(screen.getByTestId('state')).toHaveTextContent('PENDING'));

    await user.click(screen.getByRole('button', { name: 'refresh' }));

    await waitFor(() => expect(screen.getByTestId('state')).toHaveTextContent('LICENSED'));
    expect(getDeviceStatus).toHaveBeenCalledTimes(2);
  });

  it('sends the provided client label on bootstrap', async () => {
    vi.mocked(bootstrapDevice).mockResolvedValue({
      deviceId: 'device-3',
      activationCode: 'AAAA-BBBB',
      credential: 'credential-3',
    });

    render(
      <LicenseProvider clientLabel="extension">
        <Consumer />
      </LicenseProvider>,
    );

    await waitFor(() => expect(bootstrapDevice).toHaveBeenCalledWith('extension'));
  });
});
