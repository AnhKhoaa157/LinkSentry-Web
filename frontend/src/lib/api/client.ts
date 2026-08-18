import axios from 'axios';

import { clearAccessToken, getAccessToken, notifyUnauthorized } from '@/features/auth/sessionStorage';
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

apiClient.interceptors.request.use((config) => {
  const token = getAccessToken();
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`);
  }
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      clearAccessToken();
      notifyUnauthorized();
    }
    // Do not let an AxiosError carrying Authorization or a submitted request
    // body enter a query cache or a component state holder.
    return Promise.reject(normalizeApiError(error));
  },
);
