import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AxiosError, AxiosHeaders } from 'axios';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { apiClient } from '@/lib/api/client';
import { BatchScanner } from '@/features/scanner/components/BatchScanner';
import { renderWithProviders } from '@/test/renderWithProviders';

function scanData(input: string) {
  return {
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
  };
}

function batchResponse(results: unknown[]) {
  return { data: { results }, meta: { engineVersion: '0.1.0' } };
}

function completeItem(index: number, input: string) {
  return { index, status: 'COMPLETE', result: scanData(input) };
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

  it('submits one batch request and renders only the redacted server values as inert text', async () => {
    let resolveBatch: ((value: { data: unknown }) => void) | undefined;
    const batchPromise = new Promise<{ data: unknown }>((resolve) => {
      resolveBatch = resolve;
    });
    const postSpy = vi.spyOn(apiClient, 'post').mockReturnValueOnce(batchPromise as never);
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
      '/api/v1/scans/batch',
      {
        urls: ['https://private.invalid/first?token=raw-secret', 'https://private.invalid/second'],
      },
      expect.anything(),
    );
    expect(screen.getAllByText('Scanning')).toHaveLength(2);

    resolveBatch?.({
      data: batchResponse([
        completeItem(0, 'https://redacted.invalid/first?[redacted]'),
        completeItem(1, 'https://redacted.invalid/second'),
      ]),
    });

    expect(await screen.findByText('https://redacted.invalid/first?[redacted]')).toBeInTheDocument();
    expect(await screen.findByText('https://redacted.invalid/second')).toBeInTheDocument();
    expect(postSpy).toHaveBeenCalledOnce();
    expect(screen.getByRole('button', { name: /download CSV/i })).toBeEnabled();
    expect(container.querySelector('a[href*="private.invalid"]')).toBeNull();
    expect(container.querySelector('a[href*="redacted.invalid"]')).toBeNull();
    expect(batchInput()).toHaveValue(
      'https://private.invalid/first?token=raw-secret\nhttps://private.invalid/second',
    );
    expect(screen.getByRole('list', { name: 'Batch results' })).not.toHaveTextContent('raw-secret');
  });

  it('marks a client-invalid line and maps batch outcomes back to their original lines', async () => {
    const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValueOnce({
      data: batchResponse([
        completeItem(0, 'https://redacted.invalid/first'),
        completeItem(1, 'https://redacted.invalid/third'),
      ]),
    });
    const user = userEvent.setup();

    renderWithProviders(<BatchScanner onPendingChange={vi.fn()} />);
    await user.type(batchInput(), 'https://private.invalid/first\nnot a URL\nhttps://private.invalid/third');
    await waitFor(() => expect(startButton()).toBeEnabled());
    await user.click(startButton());

    expect(await screen.findByText('https://redacted.invalid/third')).toBeInTheDocument();
    expect(screen.getByText('Line 2')).toBeInTheDocument();
    expect(screen.getByText('Enter a URL starting with http:// or https://')).toBeInTheDocument();
    expect(postSpy).toHaveBeenCalledOnce();
    expect(postSpy).toHaveBeenCalledWith(
      '/api/v1/scans/batch',
      { urls: ['https://private.invalid/first', 'https://private.invalid/third'] },
      expect.anything(),
    );
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

  it('keeps completed results and marks quota-exhausted and remaining items', async () => {
    const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValueOnce({
      data: batchResponse([
        completeItem(0, 'https://redacted.invalid/first'),
        {
          index: 1,
          status: 'FAILED',
          error: {
            code: 'ANONYMOUS_TRIAL_EXHAUSTED',
            message: 'Request a license to continue scanning.',
            traceId: 'trace-trial',
          },
        },
        { index: 2, status: 'SKIPPED' },
      ]),
    });
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
      await screen.findByText(
        'Batch processing stopped at a service limit or error. Completed results are still available.',
      ),
    ).toBeInTheDocument();
    expect(screen.getByText('Skipped')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /get a license to save future scans/i })).toHaveAttribute(
      'href',
      '/license',
    );
    expect(postSpy).toHaveBeenCalledOnce();
  });

  it('marks every valid line failed when the batch request itself fails', async () => {
    const postSpy = vi.spyOn(apiClient, 'post').mockRejectedValueOnce(
      axiosErrorWithResponse(503, {
        code: 'TRIAL_QUOTA_UNAVAILABLE',
        message: 'The trial scan quota is temporarily unavailable. Please try again shortly.',
        traceId: 'trace-quota',
      }),
    );
    const user = userEvent.setup();

    renderWithProviders(<BatchScanner onPendingChange={vi.fn()} />);
    await user.type(batchInput(), 'https://private.invalid/first\nhttps://private.invalid/second');
    await waitFor(() => expect(startButton()).toBeEnabled());
    await user.click(startButton());

    expect(
      await screen.findAllByText('The trial scan limit is temporarily unavailable. Please try again later.'),
    ).toHaveLength(2);
    expect(screen.getAllByText('Failed')).toHaveLength(2);
    expect(postSpy).toHaveBeenCalledOnce();
  });
});
