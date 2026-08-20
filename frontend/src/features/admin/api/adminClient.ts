import axios from 'axios';

import {
  clearAdminAccessToken,
  getAdminAccessToken,
  notifyAdminUnauthorized,
} from '@/features/admin/sessionStorage';
import { normalizeApiError } from '@/lib/api/errors';
import { env } from '@/lib/config/env';

/**
 * A dedicated Axios instance, separate from `lib/api/client.ts`'s `apiClient`: that instance
 * attaches the end-user bearer and clears the end-user session on a 401. Reusing it here would
 * send the wrong identity to `/api/v1/admin-auth/**` and would let an admin-route 401 sign the
 * end user out (or vice versa). The two sessions never share storage, a client, or a 401 handler.
 */
export const adminApiClient = axios.create({
  baseURL: env.apiBaseUrl,
  timeout: 10_000,
  headers: {
    Accept: 'application/json',
    'Content-Type': 'application/json',
  },
  withCredentials: false,
});

adminApiClient.interceptors.request.use((config) => {
  const token = getAdminAccessToken();
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`);
  }
  return config;
});

adminApiClient.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      clearAdminAccessToken();
      notifyAdminUnauthorized();
    }
    return Promise.reject(normalizeApiError(error));
  },
);
