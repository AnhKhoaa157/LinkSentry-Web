import { screen, waitFor, render } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClientProvider } from '@tanstack/react-query';
import { AxiosError, AxiosHeaders } from 'axios';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { Popup } from '@/extension/components/Popup';
import { LicenseProvider } from '@/features/license/context/LicenseProvider';
import { apiClient } from '@/lib/api/client';
import { createQueryClient } from '@/lib/api/queryClient';
import { renderWithProviders } from '@/test/renderWithProviders';

function axiosErrorWithResponse(status: number, data: unknown, requestBody?: unknown) {
  const headers = new AxiosHeaders();
  return new AxiosError('Request failed', 'ERR_BAD_RESPONSE', { headers }, null, {
    status,
    statusText: 'Error',
    headers,
    config: { headers, data: requestBody },
    data,
  });
}

function stubActiveTab(url: string | undefined) {
  const tabs = url === undefined ? [{}] : [{ url }];
  const query = vi.fn().mockResolvedValue(tabs);
  vi.stubGlobal('chrome', { tabs: { query } });
  return query;
}

const SCAN_URL =
  'https://scan-user:scan-password@login.example.com.security-check.invalid/account?token=query-sentinel#fragment-sentinel';
const RAW_URL_SENTINELS = [
  'scan-user:scan-password@',
  'login.example.com.security-check.invalid',
  'token=query-sentinel',
  'fragment-sentinel',
  SCAN_URL,
];

const validScanResponse = {
  data: {
    scanId: '2ce16fb9-d52d-4310-8d45-a4e48f31889e',
    input: 'https://login.example.com.security-check.invalid/account',
    normalized: {
      scheme: 'https',
      host: 'login.example.com.security-check.invalid',
      asciiHost: 'login.example.com.security-check.invalid',
      registrableDomain: 'security-check.invalid',
      port: null,
      path: '/account',
      queryPresent: true,
      fragmentPresent: true,
    },
    score: 55,
    riskLevel: 'HIGH',
    findings: [
      {
        ruleId: 'SUSPICIOUS_KEYWORDS',
        severity: 'MEDIUM',
        points: 20,
        title: 'Subdomain uses a sensitive-sounding word',
        explanation: 'A subdomain contains a word commonly used in credential phishing.',
        evidence: null,
      },
    ],
    analyzedAt: '2026-08-18T00:00:00Z',
  },
  meta: { engineVersion: '0.1.0' },
};

const scanButton = () => screen.getByRole('button', { name: /scan this tab|scan again|scanning/i });

function renderPopupWithQueryClient() {
  const queryClient = createQueryClient();
  const rendered = render(
    <QueryClientProvider client={queryClient}>
      <LicenseProvider clientLabel="extension">
        <Popup />
      </LicenseProvider>
    </QueryClientProvider>,
  );

  return { ...rendered, queryClient };
}

describe('Popup', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('shows a ready state and enables scanning for a normal https tab, without revealing the URL', async () => {
    stubActiveTab(SCAN_URL);

    const { queryClient } = renderPopupWithQueryClient();

    await waitFor(() => expect(scanButton()).toBeEnabled());
    expect(screen.getByText('Ready to scan the current tab.')).toBeInTheDocument();
    for (const sentinel of RAW_URL_SENTINELS) {
      expect(document.body.innerHTML).not.toContain(sentinel);
    }
    expect(queryClient.getMutationCache().getAll()).toHaveLength(0);
  });

  it.each([
    'chrome://newtab/',
    'chrome://extensions/',
    'edge://settings/',
    'file:///C:/secrets.txt',
    'about:blank',
  ])('shows an unsupported state and makes no request for %s', async (url) => {
    stubActiveTab(url);
    const postSpy = vi.spyOn(apiClient, 'post');
    const user = userEvent.setup();

    renderWithProviders(<Popup />);

    await waitFor(() => expect(screen.getByText(/cannot be scanned/i)).toBeInTheDocument());
    expect(scanButton()).toBeDisabled();

    // Even an explicit click on the disabled control must never reach the API.
    await user.click(scanButton());
    expect(postSpy).not.toHaveBeenCalled();
  });

  it('shows an unsupported state and makes no request when the tab has no URL', async () => {
    stubActiveTab(undefined);
    const postSpy = vi.spyOn(apiClient, 'post');

    renderWithProviders(<Popup />);

    await waitFor(() => expect(scanButton()).toBeDisabled());
    expect(postSpy).not.toHaveBeenCalled();
  });

  it('submits exactly the active-tab URL as the request body and renders the result', async () => {
    const query = stubActiveTab(SCAN_URL);
    const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValue({ data: validScanResponse });
    const user = userEvent.setup();
    const { queryClient } = renderPopupWithQueryClient();

    await waitFor(() => expect(scanButton()).toBeEnabled());
    await user.click(scanButton());

    expect(query).toHaveBeenCalledTimes(2);
    expect(postSpy).toHaveBeenCalledTimes(1);
    expect(postSpy).toHaveBeenCalledWith('/api/v1/scans', { url: SCAN_URL }, {});
    expect(await screen.findByText('55')).toBeInTheDocument();
    expect(screen.getByText('High risk')).toBeInTheDocument();
    expect(screen.getByText('Subdomain uses a sensitive-sounding word')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Recommended next steps' })).toBeInTheDocument();
    postSpy.mockClear();
    expect(queryClient.getMutationCache().getAll()).toHaveLength(0);
    for (const sentinel of RAW_URL_SENTINELS) {
      expect(document.body.innerHTML).not.toContain(sentinel);
    }
  });

  it('rechecks the active tab on click and skips the request if it became unsupported', async () => {
    const query = stubActiveTab(SCAN_URL);
    const postSpy = vi.spyOn(apiClient, 'post');
    const user = userEvent.setup();

    renderWithProviders(<Popup />);
    await waitFor(() => expect(scanButton()).toBeEnabled());
    query.mockResolvedValueOnce([{ url: 'file:///C:/secrets.txt' }]);

    await user.click(scanButton());

    await waitFor(() => expect(screen.getByText(/cannot be scanned/i)).toBeInTheDocument());
    expect(query).toHaveBeenCalledTimes(2);
    expect(postSpy).not.toHaveBeenCalled();
  });

  it('focuses the scan button when ready and after a scan settles', async () => {
    stubActiveTab(SCAN_URL);
    let resolveScan!: (value: { data: typeof validScanResponse }) => void;
    const pendingResponse = new Promise<{ data: typeof validScanResponse }>((resolve) => {
      resolveScan = resolve;
    });
    vi.spyOn(apiClient, 'post').mockReturnValue(pendingResponse as never);
    const user = userEvent.setup();

    renderWithProviders(<Popup />);
    await waitFor(() => expect(scanButton()).toHaveFocus());

    await user.click(scanButton());
    expect(scanButton()).toBeDisabled();
    scanButton().blur();
    resolveScan({ data: validScanResponse });

    await screen.findByText('55');
    expect(scanButton()).toHaveFocus();
  });

  it('never renders an anchor, iframe, or script for the result', async () => {
    stubActiveTab(SCAN_URL);
    vi.spyOn(apiClient, 'post').mockResolvedValue({ data: validScanResponse });
    const user = userEvent.setup();

    const { container } = renderWithProviders(<Popup />);
    await waitFor(() => expect(scanButton()).toBeEnabled());
    await user.click(scanButton());
    await screen.findByText('55');

    expect(container.querySelector('a')).toBeNull();
    expect(container.querySelector('iframe')).toBeNull();
    expect(container.querySelector('script')).toBeNull();
  });

  it('shows the backend message and never calls the API before the tab is classified', async () => {
    stubActiveTab(SCAN_URL);
    vi.spyOn(apiClient, 'post').mockRejectedValue(
      axiosErrorWithResponse(400, {
        code: 'INVALID_URL',
        message: 'The submitted value is not a supported HTTP or HTTPS URL.',
        fieldErrors: { url: 'Enter a valid HTTP or HTTPS URL.' },
        traceId: 'trace-1',
      }),
    );
    const user = userEvent.setup();

    renderWithProviders(<Popup />);
    await waitFor(() => expect(scanButton()).toBeEnabled());
    await user.click(scanButton());

    expect(
      await screen.findByText('The submitted value is not a supported HTTP or HTTPS URL.'),
    ).toBeInTheDocument();
    expect(screen.queryByText(/trace-1/)).not.toBeInTheDocument();
  });

  it('shows scanner-specific rate-limit copy, then recovers on an explicit retry click', async () => {
    stubActiveTab(SCAN_URL);
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

    renderWithProviders(<Popup />);
    await waitFor(() => expect(scanButton()).toBeEnabled());
    await user.click(scanButton());

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Too many scan requests. Wait a moment before trying again.');
    // No countdown, no seconds, no quota: the backend publishes none of it, and
    // nothing here may invent one.
    expect(alert).not.toHaveTextContent(/\d+\s*(second|minute|s\b)/i);

    await waitFor(() => expect(scanButton()).toBeEnabled());
    await user.click(scanButton());

    expect(await screen.findByText('55')).toBeInTheDocument();
    expect(postSpy).toHaveBeenCalledTimes(2);
  });

  it('sanitizes a failed scan instead of retaining the Axios error or raw request body', async () => {
    stubActiveTab(SCAN_URL);
    const postSpy = vi.spyOn(apiClient, 'post').mockRejectedValueOnce(
      axiosErrorWithResponse(
        500,
        {
          code: 'INTERNAL_ERROR',
          message: 'The request could not be completed. Please try again later.',
          traceId: 'trace-2',
        },
        { url: SCAN_URL },
      ),
    );
    const user = userEvent.setup();
    const { queryClient } = renderPopupWithQueryClient();

    await waitFor(() => expect(scanButton()).toBeEnabled());
    await user.click(scanButton());

    expect(
      await screen.findByText('The request could not be completed. Please try again later.'),
    ).toBeInTheDocument();
    expect(screen.queryByText(/trace-2/)).not.toBeInTheDocument();
    for (const sentinel of RAW_URL_SENTINELS) {
      expect(document.body.innerHTML).not.toContain(sentinel);
    }
    expect(queryClient.getMutationCache().getAll()).toHaveLength(0);
    postSpy.mockReset();
  });

  it('shows a network-failure message when the request never reaches the server', async () => {
    stubActiveTab(SCAN_URL);
    vi.spyOn(apiClient, 'post').mockRejectedValue(new AxiosError('Network Error', AxiosError.ERR_NETWORK));
    const user = userEvent.setup();

    renderWithProviders(<Popup />);
    await waitFor(() => expect(scanButton()).toBeEnabled());
    await user.click(scanButton());

    expect(await screen.findByRole('alert')).toHaveTextContent(/could not reach the linksentry api/i);
  });

  it('never logs the raw tab URL or its sensitive parts to the console, success or failure', async () => {
    stubActiveTab(SCAN_URL);
    const logSpy = vi.spyOn(console, 'log');
    const infoSpy = vi.spyOn(console, 'info');
    const warnSpy = vi.spyOn(console, 'warn');
    const errorSpy = vi.spyOn(console, 'error');
    const postSpy = vi.spyOn(apiClient, 'post').mockRejectedValueOnce(
      axiosErrorWithResponse(
        500,
        {
          code: 'INTERNAL_ERROR',
          message: 'The request could not be completed. Please try again later.',
        },
        { url: SCAN_URL },
      ),
    );
    const user = userEvent.setup();

    renderWithProviders(<Popup />);
    await waitFor(() => expect(scanButton()).toBeEnabled());
    await user.click(scanButton());
    await screen.findByRole('alert');

    for (const spy of [logSpy, infoSpy, warnSpy, errorSpy]) {
      for (const call of spy.mock.calls) {
        const rendered = call.map((arg) => String(arg)).join(' ');
        for (const sentinel of RAW_URL_SENTINELS) {
          expect(rendered).not.toContain(sentinel);
        }
      }
    }
    postSpy.mockReset();
  });

  it('never places the raw tab URL or its sensitive parts anywhere in the DOM, success or failure', async () => {
    stubActiveTab(SCAN_URL);
    vi.spyOn(apiClient, 'post').mockResolvedValueOnce({ data: validScanResponse });
    const user = userEvent.setup();

    renderWithProviders(<Popup />);
    await waitFor(() => expect(scanButton()).toBeEnabled());
    await user.click(scanButton());
    await screen.findByText('55');

    for (const sentinel of RAW_URL_SENTINELS) {
      expect(document.body.innerHTML).not.toContain(sentinel);
    }
  });
});
