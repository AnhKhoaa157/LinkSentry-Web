import { z } from 'zod';

const instantSchema = z.string().min(1);

export const createLicenseRequestSchema = z.object({
  label: z.string().trim().min(1, 'Enter a label for this license.').max(200),
  expiresAt: instantSchema.nullable(),
  maxDevices: z.number().int().min(1).optional(),
});

export const grantDeviceRequestSchema = z.object({
  activationCode: z.string().trim().min(1, 'Enter an activation code.').max(32),
});

export const extendLicenseRequestSchema = z.object({
  expiresAt: instantSchema.nullable(),
});

const deviceSummarySchema = z.object({
  deviceId: z.string().min(1),
  activationCode: z.string().min(1),
  clientLabel: z.string().nullable(),
  grantedAt: instantSchema,
});

export const licenseSummarySchema = z.object({
  licenseId: z.string().min(1),
  label: z.string(),
  expiresAt: instantSchema.nullable(),
  maxDevices: z.number().int().min(1),
  revoked: z.boolean(),
  createdAt: instantSchema,
  activeDeviceCount: z.number().int().min(0),
});

export const licenseResponseSchema = z.object({
  licenseId: z.string().min(1),
  label: z.string(),
  expiresAt: instantSchema.nullable(),
  maxDevices: z.number().int().min(1),
  revoked: z.boolean(),
  createdAt: instantSchema,
  devices: z.array(deviceSummarySchema),
});

export const deviceStateSchema = z.enum(['PENDING', 'LICENSED', 'EXPIRED', 'REVOKED']);

export const deviceLookupResponseSchema = z.object({
  deviceId: z.string().min(1),
  activationCode: z.string().min(1),
  clientLabel: z.string().nullable(),
  state: deviceStateSchema,
  licenseId: z.string().min(1).nullable(),
  createdAt: instantSchema,
});

export type CreateLicenseRequest = z.infer<typeof createLicenseRequestSchema>;
export type GrantDeviceRequest = z.infer<typeof grantDeviceRequestSchema>;
export type ExtendLicenseRequest = z.infer<typeof extendLicenseRequestSchema>;
export type LicenseSummary = z.infer<typeof licenseSummarySchema>;
export type LicenseResponse = z.infer<typeof licenseResponseSchema>;
export type DeviceLookupResponse = z.infer<typeof deviceLookupResponseSchema>;
