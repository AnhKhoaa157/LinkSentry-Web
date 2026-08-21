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

function structuredResponse(overrides?: {
  riskLevel?: string;
  keyFindings?: unknown[];
  summary?: string;
  recommendedActions?: string[];
}) {
  return {
    data: {
      data: {
        riskLevel: overrides?.riskLevel ?? 'HIGH',
        keyFindings: overrides?.keyFindings ?? [
          {
            title: 'Hostname names a brand it is not registered to',
            explanation: 'Generic rule explanation text.',
            severity: 'HIGH',
            points: 30,
          },
        ],
        summary: overrides?.summary ?? 'This link shows several risk signals worth a second look.',
        recommendedActions: overrides?.recommendedActions ?? [
          'Verify the sender through an official channel.',
          'Avoid entering credentials on this page.',
        ],
      },
    },
  };
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

  it('posts to the owner-scoped explanation endpoint and renders every section', async () => {
    const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValue(structuredResponse());
    const user = userEvent.setup();
    renderWithProviders(<ExplainResult scanId={SCAN_ID} />);

    await user.click(screen.getByRole('button', { name: 'Explain this result' }));

    expect(await screen.findByText('HIGH LEXICAL RISK')).toBeInTheDocument();
    expect(screen.getByText('What LinkSentry detected')).toBeInTheDocument();
    expect(screen.getByText('Hostname names a brand it is not registered to')).toBeInTheDocument();
    expect(screen.getByText('Generic rule explanation text.')).toBeInTheDocument();
    expect(screen.getByText('AI context (advisory)')).toBeInTheDocument();
    expect(screen.getByText('This link shows several risk signals worth a second look.')).toBeInTheDocument();
    expect(screen.getByText('What to do')).toBeInTheDocument();
    expect(screen.getByText('Verify the sender through an official channel.')).toBeInTheDocument();
    expect(screen.getByText('Avoid entering credentials on this page.')).toBeInTheDocument();

    expect(postSpy).toHaveBeenCalledWith(
      `/api/v1/scans/${SCAN_ID}/explanation`,
      undefined,
      expect.objectContaining({}),
    );
    // The control is replaced by the result, not left alongside it.
    expect(screen.queryByRole('button', { name: 'Explain this result' })).not.toBeInTheDocument();
  });

  it('safely handles an empty keyFindings array', async () => {
    vi.spyOn(apiClient, 'post').mockResolvedValue(structuredResponse({ keyFindings: [] }));
    const user = userEvent.setup();
    renderWithProviders(<ExplainResult scanId={SCAN_ID} />);

    await user.click(screen.getByRole('button', { name: 'Explain this result' }));

    expect(
      await screen.findByText(
        'No lexical signals were detected by the current rules. This does not mean the link is safe.',
      ),
    ).toBeInTheDocument();
  });

  it('renders recommended actions and the summary as inert plain text — never as HTML, a link, or executable markup', async () => {
    const hostile =
      '<img src=x onerror="window.__pwned = true">Click <a href="https://evil.example">here</a>';
    vi.spyOn(apiClient, 'post').mockResolvedValue(
      structuredResponse({ summary: hostile, recommendedActions: [hostile] }),
    );
    const user = userEvent.setup();
    renderWithProviders(<ExplainResult scanId={SCAN_ID} />);

    await user.click(screen.getByRole('button', { name: 'Explain this result' }));

    const renderedNodes = await screen.findAllByText(hostile);
    expect(renderedNodes).toHaveLength(2); // once for the summary, once for the action
    for (const node of renderedNodes) {
      expect(['P', 'LI']).toContain(node.tagName);
      expect(node.querySelector('img')).toBeNull();
      expect(node.querySelector('a')).toBeNull();
    }
    expect(screen.queryByRole('link', { name: /here/i })).not.toBeInTheDocument();
    expect((window as unknown as { __pwned?: boolean }).__pwned).toBeUndefined();
  });

  it('renders each key finding title and explanation as inert plain text', async () => {
    const hostileTitle = '<script>window.__pwned2 = true</script>';
    vi.spyOn(apiClient, 'post').mockResolvedValue(
      structuredResponse({
        keyFindings: [
          { title: hostileTitle, explanation: 'Generic explanation.', severity: 'MEDIUM', points: 10 },
        ],
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<ExplainResult scanId={SCAN_ID} />);

    await user.click(screen.getByRole('button', { name: 'Explain this result' }));

    const rendered = await screen.findByText(hostileTitle);
    expect(rendered.querySelector('script')).toBeNull();
    expect((window as unknown as { __pwned2?: boolean }).__pwned2).toBeUndefined();
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
      .mockResolvedValueOnce(structuredResponse({ summary: 'A short advisory sentence.' }));
    const user = userEvent.setup();
    renderWithProviders(<ExplainResult scanId={SCAN_ID} />);

    await user.click(screen.getByRole('button', { name: 'Explain this result' }));
    await screen.findByRole('alert');
    await user.click(screen.getByRole('button', { name: 'Try again' }));

    expect(await screen.findByText('A short advisory sentence.')).toBeInTheDocument();
    await waitFor(() => expect(postSpy).toHaveBeenCalledTimes(2));
  });
});
