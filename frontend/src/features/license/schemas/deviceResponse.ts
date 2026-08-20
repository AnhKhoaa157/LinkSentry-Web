import { z } from 'zod';

export const deviceStateSchema = z.enum(['PENDING', 'LICENSED', 'EXPIRED', 'REVOKED']);
export type DeviceState = z.infer<typeof deviceStateSchema>;

export const deviceBootstrapResponseSchema = z.object({
  deviceId: z.string(),
  activationCode: z.string().min(1),
  credential: z.string().min(1),
});
export type DeviceBootstrapResponse = z.infer<typeof deviceBootstrapResponseSchema>;

export const deviceStatusResponseSchema = z.object({
  state: deviceStateSchema,
  activationCode: z.string().min(1),
  licenseExpiresAt: z.string().nullable(),
});
export type DeviceStatusResponse = z.infer<typeof deviceStatusResponseSchema>;
