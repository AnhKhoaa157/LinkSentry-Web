import { AxiosError, AxiosHeaders, type AxiosAdapter } from 'axios';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { apiClient } from '@/lib/api/client';
import { deviceCredentialStorage } from '@/lib/device/deviceCredentialStorage';

describe('apiClient device credential boundary', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
  });

  it('attaches the stored device credential as Authorization: Device <credential>', async () => {
    await deviceCredentialStorage.set('test-only-credential');
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

    await apiClient.get('/api/v1/devices/me', { adapter });

    expect(observedAuthorization).toBe('Device test-only-credential');
  });

  it('sends no Authorization header at all when no credential is stored', async () => {
    let observedAuthorization: string | undefined;
    const adapter: AxiosAdapter = async (config) => {
      observedAuthorization = config.headers.get('Authorization') as string | undefined;
      return { data: {}, status: 200, statusText: 'OK', headers: new AxiosHeaders(), config };
    };

    await apiClient.post('/api/v1/scans', { url: 'https://example.com/' }, { adapter });

    expect(observedAuthorization).toBeUndefined();
  });

  it('never clears the stored credential on a 401 — an unlicensed device is a normal, not an error, state', async () => {
    await deviceCredentialStorage.set('test-only-credential');
    const adapter: AxiosAdapter = async (config) => {
      throw new AxiosError('Unauthorized', 'ERR_BAD_REQUEST', config, null, {
        status: 401,
        statusText: 'Unauthorized',
        headers: new AxiosHeaders(),
        config,
        data: { code: 'UNAUTHORIZED', message: 'Authentication is required.' },
      });
    };

    await expect(
      apiClient.get('/api/v1/scans/2ce16fb9-d52d-4310-8d45-a4e48f31889e', { adapter }),
    ).rejects.toMatchObject({
      code: 'UNAUTHORIZED',
      isNetworkError: false,
    });

    await expect(deviceCredentialStorage.get()).resolves.toBe('test-only-credential');
  });

  it('rejects with a safe error instead of retaining the credential or submitted body', async () => {
    const credential = 'test-only-credential';
    const rawUrl = 'https://user:password@example.com/account?token=query-sentinel#fragment-sentinel';
    await deviceCredentialStorage.set(credential);
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
    expect(serialized).not.toContain(credential);
    expect(serialized).not.toContain(rawUrl);
    expect(serialized).not.toContain('query-sentinel');
    expect(serialized).not.toContain('fragment-sentinel');
  });
});
