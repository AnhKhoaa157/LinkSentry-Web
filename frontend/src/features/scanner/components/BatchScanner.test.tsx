import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AxiosError, AxiosHeaders } from 'axios';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { apiClient } from '@/lib/api/client';
import { BatchScanner } from '@/features/scanner/components/BatchScanner';
import { renderWithProviders } from '@/test/renderWithProviders';

function scanResponse(input: string) {
  return {
    data: {
      scanId: null,
      input,
      normalized: {
        scheme: 'https',
        host: 'redacted.invalid',
        asciiHost: 'redacted.invalid',
        registrableDomain: 'redacted.invalid',
        port: null,
        path: '/',
        queryPresent: false,
        fragmentPresent: false,
      },
      score: 10,
      riskLevel: 'MODERATE',
      findings: [],
      analyzedAt: '2026-11-06T00:00:00Z',
    },
    meta: { engineVersion: '0.1.0' },
  };
}

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

const batchInput = () => screen.getByLabelText(/URLs to analyze/i);
const startButton = () => screen.getByRole('button', { name: /scan URLs/i });

describe('BatchScanner', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('scans valid lines sequentially and renders only the redacted server values as inert text', async () => {
    let resolveFirst: ((value: { data: unknown }) => void) | undefined;
    const firstResponse = new Promise<{ data: unknown }>((resolve) => {
      resolveFirst = resolve;
    });
    const postSpy = vi
      .spyOn(apiClient, 'post')
      .mockReturnValueOnce(firstResponse as never)
      .mockResolvedValueOnce({ data: scanResponse('https://redacted.invalid/second') });
    const user = userEvent.setup();
    const { container } = renderWithProviders(<BatchScanner onPendingChange={vi.fn()} />);

    await user.type(
      batchInput(),
      'https://private.invalid/first?token=raw-secret\nhttps://private.invalid/second',
    );
    await waitFor(() => expect(startButton()).toBeEnabled());
    await user.click(startButton());

    await waitFor(() => expect(postSpy).toHaveBeenCalledOnce());
    expect(postSpy).toHaveBeenCalledWith(
      '/api/v1/scans',
      { url: 'https://private.invalid/first?token=raw-secret' },
      expect.anything(),
    );
    expect(screen.getByText('Scanning')).toBeInTheDocument();

    resolveFirst?.({ data: scanResponse('https://redacted.invalid/first?[redacted]') });

    expect(await screen.findByText('https://redacted.invalid/first?[redacted]')).toBeInTheDocument();
    expect(await screen.findByText('https://redacted.invalid/second')).toBeInTheDocument();
    expect(postSpy).toHaveBeenCalledTimes(2);
    expect(screen.getByRole('button', { name: /download CSV/i })).toBeEnabled();
    expect(container.querySelector('a[href*="private.invalid"]')).toBeNull();
    expect(container.querySelector('a[href*="redacted.invalid"]')).toBeNull();
    expect(batchInput()).toHaveValue(
      'https://private.invalid/first?token=raw-secret\nhttps://private.invalid/second',
    );
    expect(screen.getByRole('list', { name: 'Batch results' })).not.toHaveTextContent('raw-secret');
  });

  it('marks an invalid line without echoing it and continues with valid lines', async () => {
    const postSpy = vi
      .spyOn(apiClient, 'post')
      .mockResolvedValueOnce({ data: scanResponse('https://redacted.invalid/first') })
      .mockResolvedValueOnce({ data: scanResponse('https://redacted.invalid/third') });
    const user = userEvent.setup();

    renderWithProviders(<BatchScanner onPendingChange={vi.fn()} />);
    await user.type(batchInput(), 'https://private.invalid/first\nnot a URL\nhttps://private.invalid/third');
    await waitFor(() => expect(startButton()).toBeEnabled());
    await user.click(startButton());

    expect(await screen.findByText('https://redacted.invalid/third')).toBeInTheDocument();
    expect(screen.getByText('Line 2')).toBeInTheDocument();
    expect(screen.getByText('Enter a URL starting with http:// or https://')).toBeInTheDocument();
    expect(postSpy).toHaveBeenCalledTimes(2);
    expect(postSpy.mock.calls.map((call) => (call[1] as { url: string }).url)).toEqual([
      'https://private.invalid/first',
      'https://private.invalid/third',
    ]);
  });

  it('rejects more than ten URLs before making any API request', async () => {
    const postSpy = vi.spyOn(apiClient, 'post');
    const user = userEvent.setup();
    const tooMany = Array.from({ length: 11 }, (_, index) => 'https://example-' + index + '.invalid').join(
      '\n',
    );

    renderWithProviders(<BatchScanner onPendingChange={vi.fn()} />);
    await user.type(batchInput(), tooMany);
    await waitFor(() => expect(startButton()).toBeEnabled());
    await user.click(startButton());

    expect(await screen.findByRole('alert')).toHaveTextContent('Enter no more than 10 URLs at a time.');
    expect(postSpy).not.toHaveBeenCalled();
  });

  it('keeps completed results and stops the remaining queue when the trial quota is exhausted', async () => {
    const postSpy = vi
      .spyOn(apiClient, 'post')
      .mockResolvedValueOnce({ data: scanResponse('https://redacted.invalid/first') })
      .mockRejectedValueOnce(
        axiosErrorWithResponse(429, {
          code: 'ANONYMOUS_TRIAL_EXHAUSTED',
          message: 'Request a license to continue scanning.',
          traceId: 'trace-trial',
        }),
      );
    const user = userEvent.setup();

    renderWithProviders(<BatchScanner onPendingChange={vi.fn()} />);
    await user.type(
      batchInput(),
      'https://private.invalid/first\nhttps://private.invalid/second\nhttps://private.invalid/third',
    );
    await waitFor(() => expect(startButton()).toBeEnabled());
    await user.click(startButton());

    expect(await screen.findByText('https://redacted.invalid/first')).toBeInTheDocument();
    expect(
      await screen.findByText('The queue stopped after an API error. Completed results are still available.'),
    ).toBeInTheDocument();
    expect(screen.getByText('Skipped')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /get a license to save future scans/i })).toHaveAttribute(
      'href',
      '/license',
    );
    expect(postSpy).toHaveBeenCalledTimes(2);
  });
});
