import { apiClient } from '@/lib/api/client';
import {
  deviceBootstrapResponseSchema,
  deviceStatusResponseSchema,
  type DeviceBootstrapResponse,
  type DeviceStatusResponse,
} from '@/features/license/schemas/deviceResponse';

export const DEVICES_ENDPOINT = '/api/v1/devices';

/**
 * Creates one new independent device installation. The credential in the response is the only
 * time the server ever discloses it; callers must persist it immediately through
 * `deviceCredentialStorage` and never render it.
 */
export async function bootstrapDevice(clientLabel: string): Promise<DeviceBootstrapResponse> {
  const response = await apiClient.post<unknown>(DEVICES_ENDPOINT, { clientLabel });
  return deviceBootstrapResponseSchema.parse(response.data);
}

/**
 * Reports this device's current state. Relies on `apiClient`'s request interceptor to attach the
 * stored credential as `Authorization: Device <credential>` — this function never touches the
 * credential itself.
 */
export async function getDeviceStatus(): Promise<DeviceStatusResponse> {
  const response = await apiClient.get<unknown>(`${DEVICES_ENDPOINT}/me`);
  return deviceStatusResponseSchema.parse(response.data);
}
