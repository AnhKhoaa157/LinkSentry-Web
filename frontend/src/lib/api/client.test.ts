import { AxiosError, AxiosHeaders, type AxiosAdapter } from 'axios';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { apiClient } from '@/lib/api/client';

describe('apiClient authentication boundary', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    sessionStorage.clear();
  });

  it('attaches the sessionStorage bearer centrally and never needs localStorage', async () => {
    sessionStorage.setItem('linksentry.accessToken', 'test-only-token');
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

    await apiClient.get('/api/v1/auth/session', { adapter });

    expect(observedAuthorization).toBe('Bearer test-only-token');
    expect(localStorage.getItem('linksentry.accessToken')).toBeNull();
  });

  it('clears the bearer and emits the session event on a 401 response', async () => {
    sessionStorage.setItem('linksentry.accessToken', 'test-only-token');
    const unauthorized = vi.fn();
    window.addEventListener('linksentry:unauthorized', unauthorized);
    const adapter: AxiosAdapter = async (config) => {
      throw new AxiosError('Unauthorized', 'ERR_BAD_REQUEST', config, null, {
        status: 401,
        statusText: 'Unauthorized',
        headers: new AxiosHeaders(),
        config,
        data: { code: 'UNAUTHORIZED', message: 'Authentication is required.' },
      });
    };

    await expect(apiClient.get('/api/v1/auth/session', { adapter })).rejects.toMatchObject({
      code: 'UNAUTHORIZED',
      isNetworkError: false,
    });

    expect(sessionStorage.getItem('linksentry.accessToken')).toBeNull();
    expect(unauthorized).toHaveBeenCalledTimes(1);
    window.removeEventListener('linksentry:unauthorized', unauthorized);
  });

  it('rejects with a safe error instead of retaining the bearer or submitted body', async () => {
    const token = 'test-only-token';
    const rawUrl = 'https://user:password@example.com/account?token=query-sentinel#fragment-sentinel';
    sessionStorage.setItem('linksentry.accessToken', token);
    const adapter: AxiosAdapter = async (config) => {
      throw new AxiosError('Request failed', 'ERR_BAD_RESPONSE', config, null, {
        status: 500,
        statusText: 'Server Error',
        headers: new AxiosHeaders(),
        config,
        data: { code: 'INTERNAL_ERROR', message: 'safe message' },
      });
    };

    let caught: unknown;
    try {
      await apiClient.post('/api/v1/scans', { url: rawUrl }, { adapter });
    } catch (error) {
      caught = error;
    }

    expect(caught).toMatchObject({ code: 'INTERNAL_ERROR', isNetworkError: false });
    const serialized = JSON.stringify(caught);
    expect(serialized).not.toContain(token);
    expect(serialized).not.toContain(rawUrl);
    expect(serialized).not.toContain('query-sentinel');
    expect(serialized).not.toContain('fragment-sentinel');
  });
});
