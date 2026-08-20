import { AxiosError, AxiosHeaders, type AxiosAdapter } from 'axios';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { adminApiClient } from '@/features/admin/api/adminClient';

describe('adminApiClient authentication boundary', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    sessionStorage.clear();
  });

  it('attaches the admin sessionStorage bearer, never the end-user key, and never localStorage', async () => {
    sessionStorage.setItem('linksentry.admin.accessToken', 'test-only-admin-token');
    sessionStorage.setItem('linksentry.accessToken', 'end-user-token-should-not-be-sent');
    let observedAuthorization: string | undefined;
    const adapter: AxiosAdapter = async (config) => {
      const authorization = config.headers.get('Authorization');
      observedAuthorization = typeof authorization === 'string' ? authorization : undefined;
      return {
        data: { ok: true },
        status: 200,
        statusText: 'OK',
        headers: new AxiosHeaders(),
        config,
      };
    };

    await adminApiClient.get('/api/v1/admin-auth/session', { adapter });

    expect(observedAuthorization).toBe('Bearer test-only-admin-token');
    expect(localStorage.getItem('linksentry.admin.accessToken')).toBeNull();
  });

  it('clears only the admin bearer and emits the admin session event on a 401 response', async () => {
    sessionStorage.setItem('linksentry.admin.accessToken', 'test-only-admin-token');
    sessionStorage.setItem('linksentry.accessToken', 'unrelated-end-user-token');
    const unauthorized = vi.fn();
    window.addEventListener('linksentry:admin:unauthorized', unauthorized);
    const adapter: AxiosAdapter = async (config) => {
      throw new AxiosError('Unauthorized', 'ERR_BAD_REQUEST', config, null, {
        status: 401,
        statusText: 'Unauthorized',
        headers: new AxiosHeaders(),
        config,
        data: { code: 'UNAUTHORIZED', message: 'Authentication is required.' },
      });
    };

    await expect(adminApiClient.get('/api/v1/admin-auth/session', { adapter })).rejects.toMatchObject({
      code: 'UNAUTHORIZED',
      isNetworkError: false,
    });

    expect(sessionStorage.getItem('linksentry.admin.accessToken')).toBeNull();
    expect(sessionStorage.getItem('linksentry.accessToken')).toBe('unrelated-end-user-token');
    expect(unauthorized).toHaveBeenCalledTimes(1);
    window.removeEventListener('linksentry:admin:unauthorized', unauthorized);
  });
});
