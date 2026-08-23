import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AxiosError, AxiosHeaders } from 'axios';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { HistoryPage } from '@/features/history/pages/HistoryPage';
import type { ScanHistoryItem, ScanHistoryResponse } from '@/features/history/schemas/scanHistoryResponse';
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
    vi.spyOn(apiClient, 'get').mockResolvedValue({ data: historyResponse([]) });

    renderWithProviders(<HistoryPage />);

    expect(await screen.findByRole('heading', { name: /no retained scans yet/i })).toBeInTheDocument();
    expect(screen.getByText(/trial scans are never saved/i)).toBeInTheDocument();
  });

  it('renders fixed error copy without exposing server-provided sensitive text', async () => {
    const rawQuery = 'access_token=raw-query-secret';
    const rawFragment = '#private-fragment';
    const credential = 'user:password@example.test';
    vi.spyOn(apiClient, 'get').mockRejectedValue(
      axiosErrorWithResponse(500, {
        code: 'INTERNAL_ERROR',
        message: `failed for ${rawQuery} ${rawFragment} ${credential}`,
        traceId: 'trace-unsafe',
      }),
    );

    renderWithProviders(<HistoryPage />);

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(/could not load scan history/i);
    expect(document.body).not.toHaveTextContent(rawQuery);
    expect(document.body).not.toHaveTextContent(rawFragment);
    expect(document.body).not.toHaveTextContent(credential);
    expect(document.body).not.toHaveTextContent('trace-unsafe');
  });

  it('renders every summary field in one responsive table-to-card structure', async () => {
    vi.spyOn(apiClient, 'get').mockResolvedValue({ data: historyResponse([firstItem]) });

    const { container } = renderWithProviders(<HistoryPage />);

    expect(await screen.findByRole('heading', { name: /retained scans/i })).toBeInTheDocument();
    expect(screen.getByRole('table', { name: /private retained scan summaries/i })).toHaveClass(
      'history-table',
    );
    expect(container.querySelector('.history-table__row')).toBeInTheDocument();
    expect(container.querySelector('.history-cell-label')).toBeInTheDocument();
    expect(screen.getByText(firstItem.displayValue).closest('a')).toBeNull();
    expect(screen.getByText(firstItem.registrableDomain ?? '').closest('a')).toBeNull();
    expect(screen.getByText('High risk')).toBeInTheDocument();
    expect(screen.getByText('40/100')).toBeInTheDocument();
    expect(screen.getByText('2 findings')).toBeInTheDocument();
    expect(container.querySelector(`time[datetime="${firstItem.analyzedAt}"]`)).toBeInTheDocument();

    const links = screen.getAllByRole('link');
    expect(links).toHaveLength(1);
    expect(links[0]).toHaveAttribute('href', `/scans/${firstItem.scanId}`);
    expect(screen.getByRole('status')).toHaveTextContent(/reached the end/i);
  });

  it('loads the opaque next cursor and appends pages without duplicate scan IDs', async () => {
    const cursor = 'opaque-page-2';
    const getSpy = vi
      .spyOn(apiClient, 'get')
      .mockResolvedValueOnce({ data: historyResponse([firstItem], cursor) })
      .mockResolvedValueOnce({ data: historyResponse([firstItem, secondItem]) });

    renderWithProviders(<HistoryPage />);
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: 'Load more' }));

    expect(await screen.findByText(secondItem.displayValue)).toBeInTheDocument();
    expect(screen.getAllByText(firstItem.displayValue)).toHaveLength(1);
    expect(getSpy).toHaveBeenNthCalledWith(
      2,
      '/api/v1/scans',
      expect.objectContaining({
        params: { limit: 20, cursor },
        signal: expect.anything(),
      }),
    );
    expect(screen.getByRole('status')).toHaveTextContent(/reached the end/i);
  });

  it('keeps validated rows after a next-page failure and never renders cursor internals', async () => {
    const cursor = 'opaque?access_token=cursor-secret#cursor-fragment';
    const leakedCredential = 'user:password@cursor.example';
    vi.spyOn(apiClient, 'get')
      .mockResolvedValueOnce({ data: historyResponse([firstItem], cursor) })
      .mockRejectedValueOnce(
        axiosErrorWithResponse(400, {
          code: 'VALIDATION_ERROR',
          message: `Rejected ${cursor} for ${leakedCredential}`,
          fieldErrors: { cursor },
        }),
      )
      .mockResolvedValueOnce({ data: historyResponse([secondItem]) });

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
    expect(screen.getByRole('status')).toHaveTextContent(/reached the end/i);
  });

  it('renders complete Vietnamese history copy', async () => {
    localStorage.setItem('linksentry.locale', 'vi');
    vi.spyOn(apiClient, 'get').mockResolvedValue({ data: historyResponse([]) });

    renderWithProviders(<HistoryPage />);

    expect(await screen.findByRole('heading', { name: 'Lịch sử quét', level: 1 })).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'Chưa có bản quét được lưu' })).toBeInTheDocument();
    expect(screen.getByText(/lần quét dùng thử không bao giờ được lưu/i)).toBeInTheDocument();
  });
});
