import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AxiosError, AxiosHeaders } from 'axios';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { HistoryPage } from '@/features/history/pages/HistoryPage';
import { SCAN_INSIGHTS_ENDPOINT } from '@/features/history/api/getScanInsights';
import type {
  ScanHistoryItem,
  ScanHistoryResponse,
  ScanInsightsResponse,
} from '@/features/history/schemas/scanHistoryResponse';
import { getDeviceStatus } from '@/features/license/api/device';
import type { DeviceState } from '@/features/license/schemas/deviceResponse';
import { apiClient } from '@/lib/api/client';
import { deviceCredentialStorage } from '@/lib/device/deviceCredentialStorage';
import { renderWithProviders } from '@/test/renderWithProviders';

const firstItem: ScanHistoryItem = {
  scanId: '2ce16fb9-d52d-4310-8d45-a4e48f31889e',
  displayValue: 'https://login.example.com/account',
  registrableDomain: 'example.com',
  score: 40,
  riskLevel: 'HIGH',
  findingCount: 2,
  analyzedAt: '2026-08-24T03:30:00Z',
};

const secondItem: ScanHistoryItem = {
  scanId: '3ce16fb9-d52d-4310-8d45-a4e48f31889f',
  displayValue: 'http://192.0.2.10/sign-in',
  registrableDomain: null,
  score: 15,
  riskLevel: 'MODERATE',
  findingCount: 1,
  analyzedAt: '2026-08-23T02:15:00Z',
};

function historyResponse(
  items: readonly ScanHistoryItem[],
  nextCursor: string | null = null,
): ScanHistoryResponse {
  return {
    data: { items: [...items], nextCursor },
    meta: { retentionDays: 30 },
  };
}

const emptyInsightsResponse: ScanInsightsResponse = {
  data: {
    windowDays: 30,
    totalScans: 0,
    scansLast7Days: 0,
    previous7Days: 0,
    riskDistribution: { LOW: 0, MODERATE: 0, HIGH: 0, CRITICAL: 0 },
    topFindings: [],
  },
  meta: { retentionDays: 30 },
};

const populatedInsightsResponse: ScanInsightsResponse = {
  data: {
    windowDays: 30,
    totalScans: 5,
    scansLast7Days: 2,
    previous7Days: 1,
    riskDistribution: { LOW: 1, MODERATE: 1, HIGH: 2, CRITICAL: 1 },
    topFindings: [
      { ruleId: 'MISSING_HTTPS', title: 'Missing HTTPS', count: 3 },
      { ruleId: 'SUSPICIOUS_KEYWORDS', title: 'Suspicious keywords', count: 2 },
    ],
  },
  meta: { retentionDays: 30 },
};

function mockHistoryAndInsights(
  historyResponses: ReadonlyArray<ScanHistoryResponse | Error>,
  insightsResponse: ScanInsightsResponse | Error = emptyInsightsResponse,
) {
  const historyQueue = [...historyResponses];
  return vi.spyOn(apiClient, 'get').mockImplementation((url) => {
    if (url === SCAN_INSIGHTS_ENDPOINT) {
      if (insightsResponse instanceof Error) {
        return Promise.reject(insightsResponse) as never;
      }
      return Promise.resolve({ data: insightsResponse }) as never;
    }

    const nextHistoryResponse = historyQueue.shift() ?? historyResponses[historyResponses.length - 1];
    if (nextHistoryResponse instanceof Error) {
      return Promise.reject(nextHistoryResponse) as never;
    }
    return Promise.resolve({ data: nextHistoryResponse }) as never;
  });
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

describe('HistoryPage', () => {
  beforeEach(async () => {
    vi.restoreAllMocks();
    await deviceCredentialStorage.set('existing-device-credential');
    vi.mocked(getDeviceStatus).mockResolvedValue({
      state: 'LICENSED',
      activationCode: 'AAAA-BBBB',
      licenseExpiresAt: null,
    });
  });

  it.each<[DeviceState, string]>([
    ['PENDING', 'Trial'],
    ['EXPIRED', 'Expired'],
    ['REVOKED', 'Revoked'],
  ])('does not request history while the device is %s', async (state, expectedStatus) => {
    vi.mocked(getDeviceStatus).mockResolvedValue({
      state,
      activationCode: 'AAAA-BBBB',
      licenseExpiresAt: state === 'EXPIRED' ? '2026-08-01T00:00:00Z' : null,
    });
    const getSpy = vi.spyOn(apiClient, 'get');

    renderWithProviders(<HistoryPage />);

    expect(await screen.findByText(expectedStatus)).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /license is required to view history/i })).toBeInTheDocument();
    expect(getSpy).not.toHaveBeenCalled();
  });

  it('does not request history while license status is unknown', async () => {
    vi.mocked(getDeviceStatus).mockRejectedValue(new Error('status service unavailable'));
    const getSpy = vi.spyOn(apiClient, 'get');

    renderWithProviders(<HistoryPage />);

    expect(await screen.findByRole('heading', { name: /checking history access/i })).toBeInTheDocument();
    await waitFor(() => expect(getDeviceStatus).toHaveBeenCalled());
    expect(screen.getByRole('status')).toHaveTextContent('Checking this installation');
    expect(getSpy).not.toHaveBeenCalled();
  });

  it('shows an accessible loading state after license access is confirmed', async () => {
    vi.spyOn(apiClient, 'get').mockReturnValue(new Promise(() => {}) as never);

    renderWithProviders(<HistoryPage />);

    expect(await screen.findByText('Loading your scan history…')).toHaveAttribute('role', 'status');
  });

  it('shows the empty state for a validated page with no retained scans', async () => {
    mockHistoryAndInsights([historyResponse([])]);

    renderWithProviders(<HistoryPage />);

    expect(await screen.findByRole('heading', { name: /no retained scans yet/i })).toBeInTheDocument();
    expect(screen.getByText(/trial scans are never saved/i)).toBeInTheDocument();
  });

  it('renders fixed error copy without exposing server-provided sensitive text', async () => {
    const rawQuery = 'access_token=raw-query-secret';
    const rawFragment = '#private-fragment';
    const credential = 'user:password@example.test';
    mockHistoryAndInsights([
      axiosErrorWithResponse(500, {
        code: 'INTERNAL_ERROR',
        message: `failed for ${rawQuery} ${rawFragment} ${credential}`,
        traceId: 'trace-unsafe',
      }),
    ]);

    renderWithProviders(<HistoryPage />);

    const alert = await screen.findByRole('heading', { name: /could not load scan history/i });
    expect(alert).toHaveTextContent(/could not load scan history/i);
    expect(document.body).not.toHaveTextContent(rawQuery);
    expect(document.body).not.toHaveTextContent(rawFragment);
    expect(document.body).not.toHaveTextContent(credential);
    expect(document.body).not.toHaveTextContent('trace-unsafe');
  });

  it('renders every summary field in one responsive table-to-card structure', async () => {
    mockHistoryAndInsights([historyResponse([firstItem])]);

    const { container } = renderWithProviders(<HistoryPage />);

    expect(await screen.findByRole('heading', { name: /retained scans/i })).toBeInTheDocument();
    expect(screen.getByRole('table', { name: /private retained scan summaries/i })).toHaveClass(
      'history-table',
    );
    expect(container.querySelector('.history-table__row')).toBeInTheDocument();
    expect(container.querySelector('.history-cell-label')).toBeInTheDocument();
    expect(screen.getByText(firstItem.displayValue).closest('a')).toBeNull();
    expect(screen.getByText(firstItem.registrableDomain ?? '').closest('a')).toBeNull();
    expect(within(screen.getByRole('table')).getByText('High risk')).toBeInTheDocument();
    expect(screen.getByText('40/100')).toBeInTheDocument();
    expect(screen.getByText('2 findings')).toBeInTheDocument();
    expect(container.querySelector(`time[datetime="${firstItem.analyzedAt}"]`)).toBeInTheDocument();

    const links = screen.getAllByRole('link');
    expect(links).toHaveLength(1);
    expect(links[0]).toHaveAttribute('href', `/scans/${firstItem.scanId}`);
    expect(screen.getByText(/reached the end/i)).toBeInTheDocument();
  });

  it('loads the opaque next cursor and appends pages without duplicate scan IDs', async () => {
    const cursor = 'opaque-page-2';
    const getSpy = mockHistoryAndInsights([
      historyResponse([firstItem], cursor),
      historyResponse([firstItem, secondItem]),
    ]);

    renderWithProviders(<HistoryPage />);
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: 'Load more' }));

    expect(await screen.findByText(secondItem.displayValue)).toBeInTheDocument();
    expect(screen.getAllByText(firstItem.displayValue)).toHaveLength(1);
    expect(getSpy).toHaveBeenCalledWith(
      '/api/v1/scans',
      expect.objectContaining({
        params: { limit: 20, cursor },
        signal: expect.anything(),
      }),
    );
    expect(screen.getByText(/reached the end/i)).toBeInTheDocument();
  });

  it('keeps validated rows after a next-page failure and never renders cursor internals', async () => {
    const cursor = 'opaque?access_token=cursor-secret#cursor-fragment';
    const leakedCredential = 'user:password@cursor.example';
    mockHistoryAndInsights([
      historyResponse([firstItem], cursor),
      axiosErrorWithResponse(400, {
        code: 'VALIDATION_ERROR',
        message: `Rejected ${cursor} for ${leakedCredential}`,
        fieldErrors: { cursor },
      }),
      historyResponse([secondItem]),
    ]);

    renderWithProviders(<HistoryPage />);
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Load more' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/could not load the next page/i);
    expect(screen.getByText(firstItem.displayValue)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Load more' })).toBeEnabled();
    expect(document.body).not.toHaveTextContent(cursor);
    expect(document.body).not.toHaveTextContent('cursor-secret');
    expect(document.body).not.toHaveTextContent('cursor-fragment');
    expect(document.body).not.toHaveTextContent(leakedCredential);

    await user.click(screen.getByRole('button', { name: 'Load more' }));

    expect(await screen.findByText(secondItem.displayValue)).toBeInTheDocument();
    expect(screen.getByText(firstItem.displayValue)).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.getByText(/reached the end/i)).toBeInTheDocument();
  });

  it('applies risk and rule filters, then clears them without exposing filter values in the route', async () => {
    const getSpy = mockHistoryAndInsights([
      historyResponse([firstItem]),
      historyResponse([secondItem]),
      historyResponse([secondItem]),
      historyResponse([firstItem]),
    ]);
    const user = userEvent.setup();

    renderWithProviders(<HistoryPage />);

    const riskSelect = await screen.findByLabelText('Risk level');
    const ruleSelect = screen.getByRole('button', { name: 'Detected rule' });
    expect(ruleSelect).toHaveAttribute('aria-expanded', 'false');
    await user.selectOptions(riskSelect, 'HIGH');
    expect(riskSelect).toHaveValue('HIGH');
    await waitFor(() => {
      expect(getSpy).toHaveBeenCalledWith(
        '/api/v1/scans',
        expect.objectContaining({ params: { limit: 20, riskLevel: 'HIGH' }, signal: expect.anything() }),
      );
    });

    await user.click(ruleSelect);
    expect(screen.getByRole('option', { name: 'Missing HTTPS' })).toBeInTheDocument();
    expect(screen.queryByText('MISSING_HTTPS')).not.toBeInTheDocument();
    await user.click(screen.getByRole('option', { name: 'Missing HTTPS' }));
    expect(ruleSelect).toHaveTextContent('Missing HTTPS');
    expect(ruleSelect).toHaveAttribute('aria-expanded', 'false');
    await waitFor(() => {
      expect(getSpy).toHaveBeenCalledWith(
        '/api/v1/scans',
        expect.objectContaining({
          params: { limit: 20, riskLevel: 'HIGH', ruleId: 'MISSING_HTTPS' },
          signal: expect.anything(),
        }),
      );
    });

    await user.click(screen.getByRole('button', { name: 'Clear filters' }));
    expect(riskSelect).toHaveValue('');
    expect(ruleSelect).toHaveTextContent('All rules');
    await waitFor(() => {
      expect(getSpy).toHaveBeenCalledWith(
        '/api/v1/scans',
        expect.objectContaining({ params: { limit: 20 }, signal: expect.anything() }),
      );
    });
  });

  it('supports keyboard navigation and dismissal for the detected-rule menu', async () => {
    mockHistoryAndInsights([historyResponse([])]);
    const user = userEvent.setup();

    renderWithProviders(<HistoryPage />);

    const trigger = await screen.findByRole('button', { name: 'Detected rule' });
    trigger.focus();
    await user.keyboard('{ArrowDown}');

    const firstRule = screen.getByRole('option', { name: 'Brand name does not match the registered domain' });
    expect(firstRule).toHaveFocus();

    await user.keyboard('{End}');
    expect(screen.getByRole('option', { name: 'Sensitive-looking subdomain words' })).toHaveFocus();

    await user.keyboard('{Escape}');
    expect(trigger).toHaveFocus();
    expect(trigger).toHaveAttribute('aria-expanded', 'false');
  });

  it('renders backend-owned personal insights without turning submitted values into links', async () => {
    const getSpy = mockHistoryAndInsights([historyResponse([firstItem])], populatedInsightsResponse);

    renderWithProviders(<HistoryPage />);

    expect(await screen.findByRole('heading', { name: 'Your scan insights' })).toBeInTheDocument();
    expect(await screen.findByText('Missing HTTPS')).toBeInTheDocument();
    expect(screen.queryByText('MISSING_HTTPS')).not.toBeInTheDocument();
    expect(screen.queryByText('SUSPICIOUS_KEYWORDS')).not.toBeInTheDocument();
    expect(screen.getByText('3 scans')).toBeInTheDocument();
    expect(screen.getByText('2 scans (40%)')).toBeInTheDocument();
    expect(getSpy).toHaveBeenCalledWith(
      SCAN_INSIGHTS_ENDPOINT,
      expect.objectContaining({ params: { windowDays: 30 }, signal: expect.anything() }),
    );
    expect(screen.getByText(firstItem.displayValue).closest('a')).toBeNull();
  });

  it('omits unknown rule IDs from selectable and visible history copy', async () => {
    const unknownRuleId = 'FUTURE_RULE';
    mockHistoryAndInsights([historyResponse([])]);
    const user = userEvent.setup();

    renderWithProviders(<HistoryPage />);

    const ruleSelect = await screen.findByRole('button', { name: 'Detected rule' });
    await user.click(ruleSelect);
    expect(screen.queryByRole('option', { name: unknownRuleId })).not.toBeInTheDocument();
    expect(screen.queryByText(unknownRuleId)).not.toBeInTheDocument();
    expect(screen.queryByText(unknownRuleId)).not.toBeInTheDocument();
  });

  it('keeps personal insight errors fixed and free of server response details', async () => {
    const rawServerMessage = 'insight query failed with access_token=secret';
    mockHistoryAndInsights(
      [historyResponse([firstItem])],
      axiosErrorWithResponse(500, {
        code: 'INTERNAL_ERROR',
        message: rawServerMessage,
        traceId: 'trace-secret',
      }),
    );

    renderWithProviders(<HistoryPage />);

    expect(
      await screen.findByRole('heading', { name: /could not load personal insights/i }),
    ).toBeInTheDocument();
    expect(document.body).not.toHaveTextContent(rawServerMessage);
    expect(document.body).not.toHaveTextContent('trace-secret');
  });

  it('renders localized Vietnamese detected-rule labels while preserving raw values', async () => {
    localStorage.setItem('linksentry.locale', 'vi');
    mockHistoryAndInsights([historyResponse([])]);
    const user = userEvent.setup();

    renderWithProviders(<HistoryPage />);

    const ruleSelect = await screen.findByRole('button', { name: 'Quy tắc đã phát hiện' });
    await user.click(ruleSelect);
    expect(screen.getByRole('option', { name: 'Thiếu HTTPS' })).toBeInTheDocument();
    expect(screen.queryByText('MISSING_HTTPS')).not.toBeInTheDocument();
  });

  it('renders complete Vietnamese history copy', async () => {
    localStorage.setItem('linksentry.locale', 'vi');
    mockHistoryAndInsights([historyResponse([])]);

    renderWithProviders(<HistoryPage />);

    expect(await screen.findByRole('heading', { name: 'Lịch sử quét', level: 1 })).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'Chưa có bản quét được lưu' })).toBeInTheDocument();
    expect(screen.getByText(/lần quét dùng thử không bao giờ được lưu/i)).toBeInTheDocument();
  });
});
