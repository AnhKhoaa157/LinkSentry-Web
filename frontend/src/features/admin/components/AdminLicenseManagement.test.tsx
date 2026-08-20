import { QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AdminLicenseManagement } from '@/features/admin/components/AdminLicenseManagement';
import * as adminLicenseApi from '@/features/admin/api/adminLicenses';
import { createQueryClient } from '@/lib/api/queryClient';

vi.mock('@/features/admin/api/adminLicenses', () => ({
  createAdminLicense: vi.fn(),
  extendAdminLicense: vi.fn(),
  getAdminDeviceByActivationCode: vi.fn(),
  getAdminLicense: vi.fn(),
  grantAdminDevice: vi.fn(),
  listAdminLicenses: vi.fn(),
  revokeAdminDevice: vi.fn(),
  revokeAdminLicense: vi.fn(),
}));

const licenseSummary = {
  licenseId: 'license-1',
  label: 'Acme operations',
  expiresAt: '2027-08-20T23:59:59Z',
  maxDevices: 2,
  revoked: false,
  createdAt: '2026-08-20T00:00:00Z',
  activeDeviceCount: 0,
};

const licenseDetail = {
  licenseId: 'license-1',
  label: 'Acme operations',
  expiresAt: '2027-08-20T23:59:59Z',
  maxDevices: 2,
  revoked: false,
  createdAt: '2026-08-20T00:00:00Z',
  devices: [],
};

const deviceLookup = {
  deviceId: 'device-1',
  activationCode: 'K7H9-QX3P',
  clientLabel: 'web',
  state: 'LICENSED' as const,
  licenseId: 'license-1',
  createdAt: '2026-08-20T00:05:00Z',
};

function renderManagement() {
  return render(
    <QueryClientProvider client={createQueryClient()}>
      <AdminLicenseManagement />
    </QueryClientProvider>,
  );
}

describe('AdminLicenseManagement', () => {
  beforeEach(() => {
    vi.mocked(adminLicenseApi.listAdminLicenses).mockResolvedValue([licenseSummary]);
    vi.mocked(adminLicenseApi.getAdminLicense).mockResolvedValue(licenseDetail);
    vi.mocked(adminLicenseApi.createAdminLicense).mockResolvedValue(licenseDetail);
    vi.mocked(adminLicenseApi.grantAdminDevice).mockResolvedValue(licenseDetail);
    vi.mocked(adminLicenseApi.extendAdminLicense).mockResolvedValue(licenseDetail);
    vi.mocked(adminLicenseApi.revokeAdminLicense).mockResolvedValue(undefined);
    vi.mocked(adminLicenseApi.getAdminDeviceByActivationCode).mockResolvedValue(deviceLookup);
    vi.mocked(adminLicenseApi.revokeAdminDevice).mockResolvedValue(undefined);
  });

  it('supports creating, inspecting, granting, extending, and revoking licenses and devices', async () => {
    const user = userEvent.setup();
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);
    renderManagement();

    expect(await screen.findByText('Acme operations')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Inspect license Acme operations' }));
    expect(await screen.findByText('Attached devices')).toBeInTheDocument();

    await user.type(screen.getByLabelText('Label'), 'New customer');
    fireEvent.change(screen.getByLabelText(/Expiry \(optional\)/), { target: { value: '2028-08-20' } });
    await user.type(screen.getByLabelText(/Maximum devices \(optional\)/), '3');
    await user.click(screen.getByRole('button', { name: 'Create license' }));

    await waitFor(() =>
      expect(adminLicenseApi.createAdminLicense).toHaveBeenCalledWith(
        {
          label: 'New customer',
          expiresAt: '2028-08-20T23:59:59Z',
          maxDevices: 3,
        },
        expect.anything(),
      ),
    );

    await user.type(screen.getByLabelText('Pasted activation code'), 'K7H9-QX3P');
    await user.click(screen.getByRole('button', { name: 'Grant device' }));
    await waitFor(() =>
      expect(adminLicenseApi.grantAdminDevice).toHaveBeenCalledWith('license-1', {
        activationCode: 'K7H9-QX3P',
      }),
    );

    fireEvent.change(screen.getByLabelText('New expiry date'), { target: { value: '2028-08-20' } });
    await user.click(screen.getByRole('button', { name: 'Save expiry' }));
    await waitFor(() =>
      expect(adminLicenseApi.extendAdminLicense).toHaveBeenCalledWith('license-1', {
        expiresAt: '2028-08-20T23:59:59Z',
      }),
    );

    await user.click(screen.getByLabelText('No expiry'));
    await user.click(screen.getByRole('button', { name: 'Save expiry' }));
    await waitFor(() =>
      expect(adminLicenseApi.extendAdminLicense).toHaveBeenLastCalledWith('license-1', { expiresAt: null }),
    );

    await user.click(screen.getByRole('button', { name: 'Revoke license' }));
    await waitFor(() =>
      expect(adminLicenseApi.revokeAdminLicense).toHaveBeenCalledWith('license-1', expect.anything()),
    );

    await user.type(screen.getByLabelText('Activation code'), 'K7H9-QX3P');
    await user.click(screen.getByRole('button', { name: 'Inspect device' }));
    expect(await screen.findByText('K7H9-QX3P')).toBeInTheDocument();
    expect(adminLicenseApi.getAdminDeviceByActivationCode).toHaveBeenCalledWith(
      'K7H9-QX3P',
      expect.anything(),
    );

    await user.click(screen.getByRole('button', { name: 'Revoke device' }));
    await waitFor(() =>
      expect(adminLicenseApi.revokeAdminDevice).toHaveBeenCalledWith('device-1', expect.anything()),
    );
    expect(confirm).toHaveBeenCalledTimes(2);
    expect(document.body).not.toHaveTextContent('device-secret');
  });

  it('shows the empty state when no licenses exist', async () => {
    vi.mocked(adminLicenseApi.listAdminLicenses).mockResolvedValue([]);

    renderManagement();

    expect(await screen.findByText('No licenses yet. Create one above to get started.')).toBeInTheDocument();
  });
});
