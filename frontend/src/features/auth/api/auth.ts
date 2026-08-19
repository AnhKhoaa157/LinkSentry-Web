import { z } from 'zod';

import { apiClient } from '@/lib/api/client';

const userSchema = z.object({ email: z.string().email() });

const authResponseSchema = z.object({
  accessToken: z.string().min(1),
  tokenType: z.literal('Bearer'),
  expiresAt: z.string(),
  user: userSchema,
});

const registrationStartedSchema = z.object({
  message: z.string().min(1),
  expiresAt: z.string(),
});

const sessionResponseSchema = z.object({
  expiresAt: z.string(),
  user: userSchema,
});

export type AuthUser = z.infer<typeof userSchema>;
export type AuthResponse = z.infer<typeof authResponseSchema>;
export type RegistrationStartedResponse = z.infer<typeof registrationStartedSchema>;
export type SessionResponse = z.infer<typeof sessionResponseSchema>;

export const AUTH_ENDPOINT = '/api/v1/auth';
export const REGISTRATION_ENDPOINT = '/api/v2/auth';

export async function register(email: string, password: string): Promise<RegistrationStartedResponse> {
  const response = await apiClient.post<unknown>(`${REGISTRATION_ENDPOINT}/register`, { email, password });
  return registrationStartedSchema.parse(response.data);
}

export async function verifyRegistration(email: string, code: string): Promise<AuthResponse> {
  const response = await apiClient.post<unknown>(`${REGISTRATION_ENDPOINT}/register/verify`, { email, code });
  return authResponseSchema.parse(response.data);
}

export async function resendRegistrationCode(email: string): Promise<RegistrationStartedResponse> {
  const response = await apiClient.post<unknown>(`${REGISTRATION_ENDPOINT}/register/resend`, { email });
  return registrationStartedSchema.parse(response.data);
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
