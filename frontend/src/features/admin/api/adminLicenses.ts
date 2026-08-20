import { adminApiClient } from '@/features/admin/api/adminClient';
import {
  createLicenseRequestSchema,
  deviceLookupResponseSchema,
  extendLicenseRequestSchema,
  grantDeviceRequestSchema,
  licenseResponseSchema,
  licenseSummarySchema,
  type CreateLicenseRequest,
  type DeviceLookupResponse,
  type ExtendLicenseRequest,
  type GrantDeviceRequest,
  type LicenseResponse,
  type LicenseSummary,
} from '@/features/admin/schemas/adminLicense';

export const ADMIN_LICENSES_ENDPOINT = '/api/v1/admin/licenses';
export const ADMIN_DEVICES_ENDPOINT = '/api/v1/admin/devices';

/** Fetches and validates the administrator's license summaries. */
export async function listAdminLicenses(signal?: AbortSignal): Promise<LicenseSummary[]> {
  const response = await adminApiClient.get<unknown>(ADMIN_LICENSES_ENDPOINT, signal ? { signal } : {});
  return licenseSummarySchema.array().parse(response.data);
}

/** Fetches one license with its currently active device assignments. */
export async function getAdminLicense(licenseId: string, signal?: AbortSignal): Promise<LicenseResponse> {
  const response = await adminApiClient.get<unknown>(
    `${ADMIN_LICENSES_ENDPOINT}/${encodeURIComponent(licenseId)}`,
    signal ? { signal } : {},
  );
  return licenseResponseSchema.parse(response.data);
}

/** Creates a license after validating the explicit request shape. */
export async function createAdminLicense(request: CreateLicenseRequest): Promise<LicenseResponse> {
  const payload = createLicenseRequestSchema.parse(request);
  const response = await adminApiClient.post<unknown>(ADMIN_LICENSES_ENDPOINT, payload);
  return licenseResponseSchema.parse(response.data);
}

/** Grants a public activation code to a license. */
export async function grantAdminDevice(
  licenseId: string,
  request: GrantDeviceRequest,
): Promise<LicenseResponse> {
  const payload = grantDeviceRequestSchema.parse(request);
  const response = await adminApiClient.post<unknown>(
    `${ADMIN_LICENSES_ENDPOINT}/${encodeURIComponent(licenseId)}/devices`,
    payload,
  );
  return licenseResponseSchema.parse(response.data);
}

/** Changes a license expiry; `null` explicitly means no expiry. */
export async function extendAdminLicense(
  licenseId: string,
  request: ExtendLicenseRequest,
): Promise<LicenseResponse> {
  const payload = extendLicenseRequestSchema.parse(request);
  const response = await adminApiClient.post<unknown>(
    `${ADMIN_LICENSES_ENDPOINT}/${encodeURIComponent(licenseId)}/extend`,
    payload,
  );
  return licenseResponseSchema.parse(response.data);
}

/** Revokes a license and all of its current device access. */
export async function revokeAdminLicense(licenseId: string): Promise<void> {
  await adminApiClient.post(`${ADMIN_LICENSES_ENDPOINT}/${encodeURIComponent(licenseId)}/revoke`);
}

/** Looks up a device by its public activation code. */
export async function getAdminDeviceByActivationCode(
  activationCode: string,
  signal?: AbortSignal,
): Promise<DeviceLookupResponse> {
  const response = await adminApiClient.get<unknown>(
    `${ADMIN_DEVICES_ENDPOINT}/by-code/${encodeURIComponent(activationCode)}`,
    signal ? { signal } : {},
  );
  return deviceLookupResponseSchema.parse(response.data);
}

/** Revokes one device's active license assignment. */
export async function revokeAdminDevice(deviceId: string): Promise<void> {
  await adminApiClient.post(`${ADMIN_DEVICES_ENDPOINT}/${encodeURIComponent(deviceId)}/revoke`);
}
