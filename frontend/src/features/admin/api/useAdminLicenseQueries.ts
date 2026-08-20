import { useQuery } from '@tanstack/react-query';

import {
  getAdminDeviceByActivationCode,
  getAdminLicense,
  listAdminLicenses,
} from '@/features/admin/api/adminLicenses';

export const adminLicensesQueryKey = ['admin', 'licenses'] as const;

export function useAdminLicensesQuery() {
  return useQuery({
    queryKey: adminLicensesQueryKey,
    queryFn: ({ signal }) => listAdminLicenses(signal),
    retry: false,
  });
}

export function useAdminLicenseQuery(licenseId: string | null) {
  return useQuery({
    queryKey: [...adminLicensesQueryKey, licenseId] as const,
    queryFn: ({ signal }) => getAdminLicense(licenseId ?? '', signal),
    enabled: licenseId !== null,
    retry: false,
  });
}

export function useAdminDeviceQuery(activationCode: string | null) {
  return useQuery({
    queryKey: ['admin', 'device', activationCode] as const,
    queryFn: ({ signal }) => getAdminDeviceByActivationCode(activationCode ?? '', signal),
    enabled: activationCode !== null,
    retry: false,
  });
}
