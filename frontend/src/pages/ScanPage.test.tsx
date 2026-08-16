import { screen } from '@testing-library/react';
import { AxiosError, AxiosHeaders } from 'axios';
import { Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ScanPage } from '@/pages/ScanPage';
import { apiClient } from '@/lib/api/client';
import type { ScanResponse } from '@/features/scanner/schemas/scanResponse';
import { renderWithProviders } from '@/test/renderWithProviders';

const scanId = '2ce16fb9-d52d-4310-8d45-a4e48f31889e';
const savedResponse: ScanResponse = {
  data: {
    scanId,
    input: 'https://login.example.com/account',
    normalized: {
      scheme: 'https',
      host: 'login.example.com',
      asciiHost: 'login.example.com',
      registrableDomain: 'example.com',
      port: null,
      path: '/account',
      queryPresent: false,
      fragmentPresent: false,
    },
    score: 20,
    riskLevel: 'MODERATE',
    findings: [],
    analyzedAt: '2026-08-16T12:00:00Z',
  },
  meta: { engineVersion: '0.1.0' },
};

function axiosErrorWithResponse(status: number, data: unknown) {
  const headers = new AxiosHeaders();
  return new AxiosError('Request failed', 'ERR_BAD_RESPONSE', { headers }, null, {
    status,
    statusText: 'Error',
    headers,
    config: { headers },
    data,
  });
}

describe('ScanPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('shows a loading state while retrieving a saved result', () => {
    vi.spyOn(apiClient, 'get').mockReturnValue(new Promise(() => {}) as never);

    renderScanPage();

    expect(screen.getByRole('status')).toHaveTextContent('Loading saved scan');
  });

  it('renders the saved result through the existing plain-text result UI', async () => {
    const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValue({ data: savedResponse });

    renderScanPage();

    expect(await screen.findByRole('heading', { name: 'Link analysis', level: 1 })).toBeInTheDocument();
    expect(screen.getByText('https://login.example.com/account').tagName).toBe('P');
    expect(screen.getByText('Moderate risk')).toBeInTheDocument();
    expect(getSpy).toHaveBeenCalledWith(
      `/api/v1/scans/${scanId}`,
      expect.objectContaining({ signal: expect.anything() }),
    );
  });

  it('explains that a missing or expired result is unavailable', async () => {
    vi.spyOn(apiClient, 'get').mockRejectedValue(
      axiosErrorWithResponse(404, {
        code: 'SCAN_NOT_FOUND',
        message: 'The requested scan could not be found.',
        traceId: 'trace-1',
      }),
    );

    renderScanPage();

    expect(await screen.findByRole('alert')).toHaveTextContent(/retention policy/i);
    expect(screen.getByRole('link', { name: /back to scanner/i })).toHaveAttribute('href', '/');
  });

  it('shows a generic message when retrieval fails unexpectedly', async () => {
    vi.spyOn(apiClient, 'get').mockRejectedValue(
      axiosErrorWithResponse(500, {
        code: 'INTERNAL_ERROR',
        message: 'The request could not be completed. Please try again later.',
        traceId: 'trace-2',
      }),
    );

    renderScanPage();

    expect(await screen.findByRole('alert')).toHaveTextContent(/could not load this saved scan/i);
    expect(screen.getByRole('alert')).not.toHaveTextContent('trace-2');
  });

  it('treats a malformed scan id in the URL the same as a missing one, accessibly', async () => {
    // The backend collapses missing, malformed, and expired IDs into the same
    // SCAN_NOT_FOUND response; a non-UUID route param must reach the same
    // accessible state as an out-of-range UUID, not a crash or a blank page.
    const getSpy = vi.spyOn(apiClient, 'get').mockRejectedValue(
      axiosErrorWithResponse(404, {
        code: 'SCAN_NOT_FOUND',
        message: 'The requested scan could not be found.',
        traceId: 'trace-3',
      }),
    );

    renderScanPage('not-a-uuid');

    expect(await screen.findByRole('alert')).toHaveTextContent(/retention policy/i);
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Saved scan unavailable');
    expect(screen.getByRole('link', { name: /back to scanner/i })).toHaveAttribute('href', '/');
    expect(getSpy).toHaveBeenCalledWith(
      '/api/v1/scans/not-a-uuid',
      expect.objectContaining({ signal: expect.anything() }),
    );
  });
});

function renderScanPage(id: string = scanId) {
  return renderWithProviders(
    <Routes>
      <Route path="scans/:scanId" element={<ScanPage />} />
    </Routes>,
    { route: `/scans/${id}` },
  );
}
