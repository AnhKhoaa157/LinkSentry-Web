import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AxiosError, AxiosHeaders } from 'axios';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ExplainResult } from '@/features/explanation/components/ExplainResult';
import { apiClient } from '@/lib/api/client';
import { renderWithProviders } from '@/test/renderWithProviders';

const SCAN_ID = '2ce16fb9-d52d-4310-8d45-a4e48f31889e';

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

describe('ExplainResult', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('renders nothing for a result with no persisted scan ID (an anonymous result)', () => {
    const postSpy = vi.spyOn(apiClient, 'post');
    const { container } = renderWithProviders(<ExplainResult scanId={null} />);

    expect(container).toBeEmptyDOMElement();
    expect(postSpy).not.toHaveBeenCalled();
  });

  it('shows the "Explain this result" control for an owned, retained scan', () => {
    renderWithProviders(<ExplainResult scanId={SCAN_ID} />);

    expect(screen.getByRole('button', { name: 'Explain this result' })).toBeInTheDocument();
  });

  it('shows a loading state while the request is in flight', async () => {
    vi.spyOn(apiClient, 'post').mockReturnValue(new Promise(() => {}) as never);
    const user = userEvent.setup();
    renderWithProviders(<ExplainResult scanId={SCAN_ID} />);

    await user.click(screen.getByRole('button', { name: 'Explain this result' }));

    expect(screen.getByRole('status')).toHaveTextContent('Generating explanation…');
  });

  it('posts to the owner-scoped explanation endpoint and renders the returned text', async () => {
    const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValue({
      data: { data: { explanation: 'This link shows several risk signals worth a second look.' } },
    });
    const user = userEvent.setup();
    renderWithProviders(<ExplainResult scanId={SCAN_ID} />);

    await user.click(screen.getByRole('button', { name: 'Explain this result' }));

    expect(
      await screen.findByText('This link shows several risk signals worth a second look.'),
    ).toBeInTheDocument();
    expect(postSpy).toHaveBeenCalledWith(
      `/api/v1/scans/${SCAN_ID}/explanation`,
      undefined,
      expect.objectContaining({}),
    );
    // The control is replaced by the result, not left alongside it.
    expect(screen.queryByRole('button', { name: 'Explain this result' })).not.toBeInTheDocument();
  });

  it('renders the AI text as inert plain text — never as HTML, a link, or executable markup', async () => {
    const hostile =
      '<img src=x onerror="window.__pwned = true">Click <a href="https://evil.example">here</a>';
    vi.spyOn(apiClient, 'post').mockResolvedValue({ data: { data: { explanation: hostile } } });
    const user = userEvent.setup();
    renderWithProviders(<ExplainResult scanId={SCAN_ID} />);

    await user.click(screen.getByRole('button', { name: 'Explain this result' }));

    const rendered = await screen.findByText(hostile);
    expect(rendered.tagName).toBe('P');
    // Rendered as one plain-text node, not parsed into a link or an image tag.
    expect(screen.queryByRole('link', { name: /here/i })).not.toBeInTheDocument();
    expect(rendered.querySelector('img')).toBeNull();
    expect(rendered.querySelector('a')).toBeNull();
    expect((window as unknown as { __pwned?: boolean }).__pwned).toBeUndefined();
  });

  it('shows a safe error message and lets the user retry when the feature is unavailable', async () => {
    vi.spyOn(apiClient, 'post').mockRejectedValue(
      axiosErrorWithResponse(503, {
        code: 'AI_EXPLANATION_UNAVAILABLE',
        message: 'AI explanation is not available right now.',
        traceId: 'trace-1',
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<ExplainResult scanId={SCAN_ID} />);

    await user.click(screen.getByRole('button', { name: 'Explain this result' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('AI explanation is not available right now.');
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument();
  });

  it('never shows vendor or trace detail in the error state', async () => {
    vi.spyOn(apiClient, 'post').mockRejectedValue(
      axiosErrorWithResponse(500, {
        code: 'INTERNAL_ERROR',
        message: 'The request could not be completed. Please try again later.',
        traceId: 'trace-2',
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<ExplainResult scanId={SCAN_ID} />);

    await user.click(screen.getByRole('button', { name: 'Explain this result' }));

    const alert = await screen.findByRole('alert');
    expect(alert).not.toHaveTextContent('trace-2');
    expect(alert).not.toHaveTextContent(/anthropic|deepseek/i);
  });

  it('lets the user retry after a failure', async () => {
    const postSpy = vi
      .spyOn(apiClient, 'post')
      .mockRejectedValueOnce(
        axiosErrorWithResponse(503, {
          code: 'AI_EXPLANATION_UNAVAILABLE',
          message: 'AI explanation is not available right now.',
        }),
      )
      .mockResolvedValueOnce({ data: { data: { explanation: 'A short advisory explanation.' } } });
    const user = userEvent.setup();
    renderWithProviders(<ExplainResult scanId={SCAN_ID} />);

    await user.click(screen.getByRole('button', { name: 'Explain this result' }));
    await screen.findByRole('alert');
    await user.click(screen.getByRole('button', { name: 'Try again' }));

    expect(await screen.findByText('A short advisory explanation.')).toBeInTheDocument();
    await waitFor(() => expect(postSpy).toHaveBeenCalledTimes(2));
  });
});
