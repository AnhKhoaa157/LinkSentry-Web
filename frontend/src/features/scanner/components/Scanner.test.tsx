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

const HINT_TEXT = 'Paste a link to analyse its structure. LinkSentry never visits it.';

const urlInput = () => screen.getByLabelText(/suspicious url/i);
const analyzeButton = () => screen.getByRole('button', { name: /analyze/i });

/**
 * Resolves `aria-describedby` into the text a screen reader would actually
 * announce, failing on a repeated ID or one that points at nothing. Asserting on
 * the rendered text rather than the raw ID list keeps the tests honest if the
 * IDs are ever renamed.
 */
function describedByText(element: HTMLElement): string[] {
  const ids = (element.getAttribute('aria-describedby') ?? '').split(' ').filter(Boolean);
  expect(new Set(ids).size, 'aria-describedby repeats an ID').toBe(ids.length);

  return ids.map((id) => {
    const target = document.getElementById(id);
    expect(target, `aria-describedby points at missing element #${id}`).not.toBeNull();
    return target?.textContent ?? '';
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

  it('describes the URL input with only the hint while nothing has failed', () => {
    renderWithProviders(<Scanner />);

    expect(urlInput()).toHaveAttribute('aria-invalid', 'false');
    expect(describedByText(urlInput())).toEqual([HINT_TEXT]);
  });

  it('shows the analysis result after a successful scan', async () => {
    vi.spyOn(apiClient, 'post').mockResolvedValue({ data: validScanResponse });
    const user = userEvent.setup();

    renderWithProviders(<Scanner />);
    await user.type(urlInput(), 'http://login.example.com.security-check.invalid/account');
    await user.click(analyzeButton());

    expect(await screen.findByText('25')).toBeInTheDocument();
    expect(screen.getByText('Moderate risk')).toBeInTheDocument();
    expect(screen.getByText('Connection is not encrypted')).toBeInTheDocument();
    expect(screen.getByText('security-check.invalid')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /open your private result/i })).not.toBeInTheDocument();
    const signInLink = screen.getByRole('link', { name: /sign in to save future scans/i });
    expect(signInLink).toHaveAttribute('href', '/auth');
    // Anonymous scans expose only the auth route, never a URL-derived target.
    expect(signInLink.getAttribute('href')).not.toMatch(/login|example|security-check|http/i);

    // A success is not a field error: the input stays valid and plainly described.
    expect(urlInput()).toHaveAttribute('aria-invalid', 'false');
    expect(describedByText(urlInput())).toEqual([HINT_TEXT]);
  });

  it('shows a private result link only after a signed-in session is established', async () => {
    sessionStorage.setItem('linksentry.accessToken', 'test-only-token');
    vi.spyOn(apiClient, 'get').mockResolvedValue({
      data: { expiresAt: '2026-08-19T12:00:00Z', user: { email: 'person@example.com' } },
    });
    vi.spyOn(apiClient, 'post').mockResolvedValue({ data: validScanResponse });
    const user = userEvent.setup();

    renderWithProviders(<Scanner />);
    await user.type(urlInput(), 'https://example.com/account');
    await user.click(analyzeButton());

    const privateLink = await screen.findByRole('link', { name: /open your private result/i });
    expect(privateLink).toHaveAttribute('href', '/scans/2ce16fb9-d52d-4310-8d45-a4e48f31889e');
    expect(document.body).not.toHaveTextContent('test-only-token');
  });

  it('renders the analysed URL as inert text, never as a target or markup', async () => {
    vi.spyOn(apiClient, 'post').mockResolvedValue({ data: validScanResponse });
    const user = userEvent.setup();

    const { container } = renderWithProviders(<Scanner />);
    await user.type(urlInput(), 'http://login.example.com.security-check.invalid/account');
    await user.click(analyzeButton());

    const analysed = await screen.findByText('http://login.example.com.security-check.invalid/account');
    expect(analysed.tagName).toBe('P');
    expect(analysed).not.toHaveAttribute('href');
    // Text content only: nothing in the redacted value became live markup.
    expect(analysed.innerHTML).not.toMatch(/[<>]/);

    expect(container.querySelector('iframe')).toBeNull();
    for (const anchor of Array.from(container.querySelectorAll('a'))) {
      expect(anchor.getAttribute('href')).not.toMatch(/security-check|login\.example/i);
    }

    // The result region stays a polite live region, not an assertive alert.
    expect(analysed.closest('[aria-live="polite"]')).not.toBeNull();
  });

  it('rejects an invalid URL client-side without calling the API', async () => {
    const postSpy = vi.spyOn(apiClient, 'post');
    const user = userEvent.setup();

    renderWithProviders(<Scanner />);
    await user.type(urlInput(), 'not-a-url');
    await user.click(analyzeButton());

    expect(await screen.findByRole('alert')).toHaveTextContent(/http:\/\/ or https:\/\//i);
    expect(postSpy).not.toHaveBeenCalled();
  });

  it('focuses and marks the URL input invalid when client validation fails', async () => {
    vi.spyOn(apiClient, 'post');
    const user = userEvent.setup();

    renderWithProviders(<Scanner />);
    await user.type(urlInput(), 'not-a-url');
    // Submitting by button click parks focus on the button, so focus landing back
    // on the input can only be the component moving it.
    await user.click(analyzeButton());

    expect(urlInput()).toHaveFocus();
    expect(urlInput()).toHaveAttribute('aria-invalid', 'true');
    expect(describedByText(urlInput())).toEqual([HINT_TEXT, 'Enter a URL starting with http:// or https://']);
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
    await user.type(urlInput(), 'https://example.com');
    await user.click(analyzeButton());

    expect(
      await screen.findByText('The submitted value is not a supported HTTP or HTTPS URL.'),
    ).toBeInTheDocument();
    expect(screen.getByText('Enter a valid HTTP or HTTPS URL.')).toBeInTheDocument();

    // A server-reported `url` error is a field error: same ARIA and focus
    // treatment as a client-side one, so the actionable control is reachable.
    await waitFor(() => expect(urlInput()).toHaveFocus());
    expect(urlInput()).toHaveAttribute('aria-invalid', 'true');
    expect(describedByText(urlInput())).toEqual([HINT_TEXT, 'Enter a valid HTTP or HTTPS URL.']);
  });

  it('clears the field error as the user edits and does not re-grab focus afterwards', async () => {
    vi.spyOn(apiClient, 'post').mockRejectedValue(
      axiosErrorWithResponse(400, {
        code: 'VALIDATION_ERROR',
        message: 'The request contains invalid values.',
        fieldErrors: { url: 'Enter a valid HTTP or HTTPS URL.' },
      }),
    );
    const user = userEvent.setup();

    renderWithProviders(<Scanner />);
    await user.type(urlInput(), 'https://example.com');
    await user.click(analyzeButton());
    await waitFor(() => expect(urlInput()).toHaveAttribute('aria-invalid', 'true'));

    await user.clear(urlInput());
    await user.type(urlInput(), 'https://example.org');

    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(urlInput()).toHaveAttribute('aria-invalid', 'false');
    expect(describedByText(urlInput())).toEqual([HINT_TEXT]);

    // Leaving the field must stick: no stale error re-runs the focus handler.
    await user.tab();
    expect(analyzeButton()).toHaveFocus();
  });

  it('shows scanner-specific rate-limit copy in an alert, then recovers on retry', async () => {
    const postSpy = vi
      .spyOn(apiClient, 'post')
      .mockRejectedValueOnce(
        axiosErrorWithResponse(429, {
          code: 'RATE_LIMITED',
          message: 'Too many requests. Please slow down and try again shortly.',
          traceId: 'trace-429',
        }),
      )
      .mockResolvedValueOnce({ data: validScanResponse });
    const user = userEvent.setup();

    renderWithProviders(<Scanner />);
    await user.type(urlInput(), 'http://login.example.com.security-check.invalid/account');
    await user.click(analyzeButton());

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Too many scan requests. Wait a moment before trying again.');
    // No countdown, no seconds, no quota: the backend publishes none of it.
    expect(alert).not.toHaveTextContent(/\d+\s*(second|minute|s\b)/i);
    // A quota failure says nothing about the URL, so the field stays valid.
    expect(urlInput()).toHaveAttribute('aria-invalid', 'false');
    expect(describedByText(urlInput())).toEqual([HINT_TEXT]);

    // Recovery: retrying (without needing to edit the URL) clears the error and
    // renders the normal result, exactly as a successful first attempt would.
    await user.click(analyzeButton());

    expect(await screen.findByText('25')).toBeInTheDocument();
    expect(
      screen.queryByText('Too many scan requests. Wait a moment before trying again.'),
    ).not.toBeInTheDocument();
    expect(postSpy).toHaveBeenCalledTimes(2);
  });

  it('shows a generic message for a 500 response without leaking detail or blaming the field', async () => {
    vi.spyOn(apiClient, 'post').mockRejectedValue(
      axiosErrorWithResponse(500, {
        code: 'INTERNAL_ERROR',
        message: 'The request could not be completed. Please try again later.',
        traceId: 'trace-2',
      }),
    );
    const user = userEvent.setup();

    renderWithProviders(<Scanner />);
    await user.type(urlInput(), 'https://example.com');
    await user.click(analyzeButton());

    expect(
      await screen.findByText('The request could not be completed. Please try again later.'),
    ).toBeInTheDocument();
    expect(screen.queryByText(/trace-2/)).not.toBeInTheDocument();

    // A server fault is not the user's input problem: no invalid marking, and no
    // focus yanked away from the control the user was actually on.
    expect(urlInput()).toHaveAttribute('aria-invalid', 'false');
    expect(describedByText(urlInput())).toEqual([HINT_TEXT]);
    expect(analyzeButton()).toHaveFocus();
  });

  it('keeps the URL field valid when the request never reaches the server', async () => {
    vi.spyOn(apiClient, 'post').mockRejectedValue(new AxiosError('Network Error', AxiosError.ERR_NETWORK));
    const user = userEvent.setup();

    renderWithProviders(<Scanner />);
    await user.type(urlInput(), 'https://example.com');
    await user.click(analyzeButton());

    expect(await screen.findByRole('alert')).toHaveTextContent(/could not reach the linksentry api/i);
    expect(urlInput()).toHaveAttribute('aria-invalid', 'false');
    expect(describedByText(urlInput())).toEqual([HINT_TEXT]);
    expect(analyzeButton()).toHaveFocus();
  });

  it('treats a response that does not match the contract as a failure', async () => {
    vi.spyOn(apiClient, 'post').mockResolvedValue({ data: { unexpected: true } });
    const user = userEvent.setup();

    renderWithProviders(<Scanner />);
    await user.type(urlInput(), 'https://example.com');
    await user.click(analyzeButton());

    await waitFor(() => {
      expect(screen.getByText('Something went wrong. Please try again.')).toBeInTheDocument();
    });
    expect(urlInput()).toHaveAttribute('aria-invalid', 'false');
    expect(describedByText(urlInput())).toEqual([HINT_TEXT]);
  });
});
