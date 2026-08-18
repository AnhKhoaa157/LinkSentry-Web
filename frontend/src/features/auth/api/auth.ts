import { z } from 'zod';

import { apiClient } from '@/lib/api/client';

const userSchema = z.object({ email: z.string().email() });

const authResponseSchema = z.object({
  accessToken: z.string().min(1),
  tokenType: z.literal('Bearer'),
  expiresAt: z.string(),
  user: userSchema,
});

const sessionResponseSchema = z.object({
  expiresAt: z.string(),
  user: userSchema,
});

export type AuthUser = z.infer<typeof userSchema>;
export type AuthResponse = z.infer<typeof authResponseSchema>;
export type SessionResponse = z.infer<typeof sessionResponseSchema>;

export const AUTH_ENDPOINT = '/api/v1/auth';

export async function register(email: string, password: string): Promise<AuthResponse> {
  const response = await apiClient.post<unknown>(`${AUTH_ENDPOINT}/register`, { email, password });
  return authResponseSchema.parse(response.data);
}

export async function login(email: string, password: string): Promise<AuthResponse> {
  const response = await apiClient.post<unknown>(`${AUTH_ENDPOINT}/login`, { email, password });
  return authResponseSchema.parse(response.data);
}

export async function getCurrentSession(): Promise<SessionResponse> {
  const response = await apiClient.get<unknown>(`${AUTH_ENDPOINT}/session`);
  return sessionResponseSchema.parse(response.data);
}

export async function logout(): Promise<void> {
  await apiClient.post(`${AUTH_ENDPOINT}/logout`);
}
