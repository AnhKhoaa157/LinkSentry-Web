import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AxiosError, AxiosHeaders } from 'axios';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { Scanner } from '@/features/scanner/components/Scanner';
import { apiClient } from '@/lib/api/client';
import { renderWithProviders } from '@/test/renderWithProviders';

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

const validScanResponse = {
  data: {
    scanId: '2ce16fb9-d52d-4310-8d45-a4e48f31889e',
    input: 'http://login.example.com.security-check.invalid/account',
    normalized: {
      scheme: 'http',
      host: 'login.example.com.security-check.invalid',
      asciiHost: 'login.example.com.security-check.invalid',
      registrableDomain: 'security-check.invalid',
      port: null,
      path: '/account',
      queryPresent: false,
      fragmentPresent: false,
    },
    score: 25,
    riskLevel: 'MODERATE',
    findings: [
      {
        ruleId: 'MISSING_HTTPS',
        severity: 'LOW',
        points: 5,
        title: 'Connection is not encrypted',
        explanation: 'This link uses HTTP instead of HTTPS.',
        evidence: null,
      },
    ],
    analyzedAt: '2026-08-12T00:00:00Z',
  },
  meta: { engineVersion: '0.1.0' },
};

describe('Scanner', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('shows the analysis result after a successful scan', async () => {
    vi.spyOn(apiClient, 'post').mockResolvedValue({ data: validScanResponse });
    const user = userEvent.setup();

    renderWithProviders(<Scanner />);
    await user.type(
      screen.getByLabelText(/suspicious url/i),
      'http://login.example.com.security-check.invalid/account',
    );
    await user.click(screen.getByRole('button', { name: /analyze/i }));

    expect(await screen.findByText('25')).toBeInTheDocument();
    expect(screen.getByText('Moderate risk')).toBeInTheDocument();
    expect(screen.getByText('Connection is not encrypted')).toBeInTheDocument();
    expect(screen.getByText('security-check.invalid')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /open shareable result/i })).toHaveAttribute(
      'href',
      '/scans/2ce16fb9-d52d-4310-8d45-a4e48f31889e',
    );
  });

  it('rejects an invalid URL client-side without calling the API', async () => {
    const postSpy = vi.spyOn(apiClient, 'post');
    const user = userEvent.setup();

    renderWithProviders(<Scanner />);
    await user.type(screen.getByLabelText(/suspicious url/i), 'not-a-url');
    await user.click(screen.getByRole('button', { name: /analyze/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/http:\/\/ or https:\/\//i);
    expect(postSpy).not.toHaveBeenCalled();
  });

  it('shows the backend message and field error for a 400 INVALID_URL response', async () => {
    vi.spyOn(apiClient, 'post').mockRejectedValue(
      axiosErrorWithResponse(400, {
        code: 'INVALID_URL',
        message: 'The submitted value is not a supported HTTP or HTTPS URL.',
        fieldErrors: { url: 'Enter a valid HTTP or HTTPS URL.' },
        traceId: 'trace-1',
      }),
    );
    const user = userEvent.setup();

    renderWithProviders(<Scanner />);
    await user.type(screen.getByLabelText(/suspicious url/i), 'https://example.com');
    await user.click(screen.getByRole('button', { name: /analyze/i }));

    expect(
      await screen.findByText('The submitted value is not a supported HTTP or HTTPS URL.'),
    ).toBeInTheDocument();
    expect(screen.getByText('Enter a valid HTTP or HTTPS URL.')).toBeInTheDocument();
  });

  it('shows a generic message for a 500 response without leaking detail', async () => {
    vi.spyOn(apiClient, 'post').mockRejectedValue(
      axiosErrorWithResponse(500, {
        code: 'INTERNAL_ERROR',
        message: 'The request could not be completed. Please try again later.',
        traceId: 'trace-2',
      }),
    );
    const user = userEvent.setup();

    renderWithProviders(<Scanner />);
    await user.type(screen.getByLabelText(/suspicious url/i), 'https://example.com');
    await user.click(screen.getByRole('button', { name: /analyze/i }));

    expect(
      await screen.findByText('The request could not be completed. Please try again later.'),
    ).toBeInTheDocument();
  });

  it('treats a response that does not match the contract as a failure', async () => {
    vi.spyOn(apiClient, 'post').mockResolvedValue({ data: { unexpected: true } });
    const user = userEvent.setup();

    renderWithProviders(<Scanner />);
    await user.type(screen.getByLabelText(/suspicious url/i), 'https://example.com');
    await user.click(screen.getByRole('button', { name: /analyze/i }));

    await waitFor(() => {
      expect(screen.getByText('Something went wrong. Please try again.')).toBeInTheDocument();
    });
  });
});
