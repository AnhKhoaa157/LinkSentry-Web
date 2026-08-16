import axios from 'axios';

import { env } from '@/lib/config/env';

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
