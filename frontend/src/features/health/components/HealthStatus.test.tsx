import { screen, waitFor } from '@testing-library/react';
import { AxiosError, AxiosHeaders } from 'axios';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { HealthStatus } from '@/features/health/components/HealthStatus';
import { apiClient } from '@/lib/api/client';
import { renderWithProviders } from '@/test/renderWithProviders';

/**
 * The shared Axios instance is mocked rather than the fetch layer: it is the single
 * seam every request passes through, so these tests exercise the real query hook,
 * the real Zod parsing and the real error normalisation.
 */
describe('HealthStatus', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('reports the API as online when the request succeeds', async () => {
    vi.spyOn(apiClient, 'get').mockResolvedValue({ data: { status: 'UP', service: 'linksentry-api' } });

    renderWithProviders(<HealthStatus />);

    expect(await screen.findByText('UP')).toBeInTheDocument();
    expect(screen.getByText('linksentry-api')).toBeInTheDocument();
    // The state is in text, not only in the colour of the dot.
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('reports offline and explains why when the API is unreachable', async () => {
    vi.spyOn(apiClient, 'get').mockRejectedValue(new AxiosError('Network Error', AxiosError.ERR_NETWORK));

    renderWithProviders(<HealthStatus />);

    expect(await screen.findByText('Offline')).toBeInTheDocument();
    expect(await screen.findByRole('alert')).toHaveTextContent(/could not reach the linksentry api/i);
    expect(screen.getByRole('button', { name: /retry/i })).toBeEnabled();
  });

  it('surfaces the backend message when the API answers with an error envelope', async () => {
    const headers = new AxiosHeaders();
    vi.spyOn(apiClient, 'get').mockRejectedValue(
      new AxiosError('Request failed', 'ERR_BAD_RESPONSE', { headers }, null, {
        status: 500,
        statusText: 'Internal Server Error',
        headers,
        config: { headers },
        data: {
          code: 'INTERNAL_ERROR',
          message: 'The request could not be completed. Please try again later.',
          traceId: 'trace-1',
        },
      }),
    );

    renderWithProviders(<HealthStatus />);

    expect(await screen.findByText('Offline')).toBeInTheDocument();
    expect(await screen.findByRole('alert')).toHaveTextContent(/the request could not be completed/i);
  });

  it('treats a response that does not match the contract as a failure', async () => {
    // A silently-changed contract must surface as an error, not as a blank label.
    vi.spyOn(apiClient, 'get').mockResolvedValue({ data: { unexpected: true } });

    renderWithProviders(<HealthStatus />);

    await waitFor(() => {
      expect(screen.getByText('Offline')).toBeInTheDocument();
    });
  });

  it('shows a pending state before the first response arrives', () => {
    vi.spyOn(apiClient, 'get').mockReturnValue(new Promise(() => {}));

    renderWithProviders(<HealthStatus />);

    expect(screen.getByText('Checking…')).toBeInTheDocument();
  });
});
