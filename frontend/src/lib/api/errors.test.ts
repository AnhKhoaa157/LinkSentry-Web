import { AxiosError, AxiosHeaders } from 'axios';
import { describe, expect, it } from 'vitest';

import { normalizeApiError } from '@/lib/api/errors';

describe('normalizeApiError', () => {
  it('identifies a request that never reached the server', () => {
    const result = normalizeApiError(new AxiosError('Network Error', AxiosError.ERR_NETWORK));

    expect(result.isNetworkError).toBe(true);
    expect(result.code).toBe('NETWORK_ERROR');
    expect(result.message).toMatch(/could not reach the linksentry api/i);
  });

  it('passes through the backend error envelope, including field errors', () => {
    const result = normalizeApiError(
      errorWithBody(400, {
        code: 'VALIDATION_ERROR',
        message: 'The request contains invalid values.',
        fieldErrors: { url: 'Enter a valid HTTP or HTTPS URL.' },
        traceId: 'trace-1',
      }),
    );

    expect(result.isNetworkError).toBe(false);
    expect(result.code).toBe('VALIDATION_ERROR');
    expect(result.fieldErrors).toEqual({ url: 'Enter a valid HTTP or HTTPS URL.' });
  });

  it('passes through a 429 RATE_LIMITED envelope without inventing quota detail', () => {
    const result = normalizeApiError(
      errorWithBody(429, {
        code: 'RATE_LIMITED',
        message: 'Too many requests. Please slow down and try again shortly.',
        traceId: 'trace-429',
      }),
    );

    expect(result.isNetworkError).toBe(false);
    expect(result.code).toBe('RATE_LIMITED');
    expect(result.message).toBe('Too many requests. Please slow down and try again shortly.');
    expect(result.fieldErrors).toBeUndefined();
  });

  it('falls back to a generic message when the body is not a known envelope', () => {
    const result = normalizeApiError(errorWithBody(502, '<html>gateway error</html>'));

    // Never surface an unrecognised payload to the user: it could be anything.
    expect(result.message).toBe('Something went wrong. Please try again.');
    expect(result.code).toBeUndefined();
  });

  it('handles a thrown value that is not an Axios error at all', () => {
    expect(normalizeApiError(new TypeError('boom')).message).toBe('Something went wrong. Please try again.');
    expect(normalizeApiError('a string').message).toBe('Something went wrong. Please try again.');
  });
});

function errorWithBody(status: number, data: unknown): AxiosError {
  const headers = new AxiosHeaders();
  return new AxiosError('Request failed', 'ERR_BAD_RESPONSE', { headers }, null, {
    status,
    statusText: 'Error',
    headers,
    config: { headers },
    data,
  });
}
