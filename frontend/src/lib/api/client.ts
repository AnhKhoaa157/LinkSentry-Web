import axios from 'axios';

import { deviceCredentialStorage } from '@/lib/device/deviceCredentialStorage';
import { env } from '@/lib/config/env';
import { normalizeApiError } from '@/lib/api/errors';

/**
 * The single Axios instance every request goes through, so the base URL, the
 * timeout and the error shape are defined once.
 *
 * Deliberately absent: any interceptor that would log a request URL. A submitted
 * URL is exactly the kind of value that must not reach a log or a console —
 * see docs/SECURITY_BOUNDARY.md.
 */
export const apiClient = axios.create({
  baseURL: env.apiBaseUrl,
  timeout: 10_000,
  headers: {
    Accept: 'application/json',
    'Content-Type': 'application/json',
  },
  // The API is stateless and cookie-free.
  withCredentials: false,
});

/**
 * Attaches the device credential, when one is stored, as `Authorization: Device <credential>`.
 * Async because `chrome.storage.local` (the extension's backend) is itself async — Axios awaits a
 * Promise returned from a request interceptor before sending the request.
 *
 * Unlike the removed bearer-token interceptor, a `401` response here never clears the stored
 * credential: a device that is merely unlicensed, expired, or revoked still has a valid identity
 * and must keep presenting it. Only `LicenseProvider`'s own `INVALID_DEVICE_CREDENTIAL` handling
 * ever discards it.
 */
apiClient.interceptors.request.use(async (config) => {
  const credential = await deviceCredentialStorage.get();
  if (credential) {
    config.headers.set('Authorization', `Device ${credential}`);
  }
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  // Do not let an AxiosError carrying Authorization or a submitted request
  // body enter a query cache or a component state holder.
  (error: unknown) => Promise.reject(normalizeApiError(error)),
);
