import { z } from 'zod';

import { adminApiClient } from '@/features/admin/api/adminClient';

const adminIdentitySchema = z.object({ username: z.string().min(1) });

const adminAuthResponseSchema = z.object({
  accessToken: z.string().min(1),
  tokenType: z.literal('Bearer'),
  expiresAt: z.string(),
  admin: adminIdentitySchema,
});

const adminSessionResponseSchema = z.object({
  expiresAt: z.string(),
  admin: adminIdentitySchema,
});

export type AdminIdentity = z.infer<typeof adminIdentitySchema>;
export type AdminAuthResponse = z.infer<typeof adminAuthResponseSchema>;
export type AdminSessionResponse = z.infer<typeof adminSessionResponseSchema>;

export const ADMIN_AUTH_ENDPOINT = '/api/v1/admin-auth';

export async function adminLogin(username: string, password: string): Promise<AdminAuthResponse> {
  const response = await adminApiClient.post<unknown>(`${ADMIN_AUTH_ENDPOINT}/login`, { username, password });
  return adminAuthResponseSchema.parse(response.data);
}

export async function getAdminCurrentSession(): Promise<AdminSessionResponse> {
  const response = await adminApiClient.get<unknown>(`${ADMIN_AUTH_ENDPOINT}/session`);
  return adminSessionResponseSchema.parse(response.data);
}

export async function adminLogout(): Promise<void> {
  await adminApiClient.post(`${ADMIN_AUTH_ENDPOINT}/logout`);
}
