import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  DEFAULT_SCAN_INSIGHTS_WINDOW_DAYS,
  getScanInsights,
  SCAN_INSIGHTS_ENDPOINT,
} from '@/features/history/api/getScanInsights';
import type { ScanInsightsResponse } from '@/features/history/schemas/scanHistoryResponse';
import { apiClient } from '@/lib/api/client';

const response: ScanInsightsResponse = {
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

describe('getScanInsights', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('requests the licensed insights endpoint with the default 30-day window', async () => {
    const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValue({ data: response });

    await expect(getScanInsights()).resolves.toEqual(response);

    expect(getSpy).toHaveBeenCalledWith(SCAN_INSIGHTS_ENDPOINT, {
      params: { windowDays: DEFAULT_SCAN_INSIGHTS_WINDOW_DAYS },
    });
  });

  it('forwards only the supported 7-day window and validates the response', async () => {
    const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValue({
      data: { ...response, data: { ...response.data, windowDays: 7 } },
    });
    const controller = new AbortController();

    await getScanInsights({ windowDays: 7, signal: controller.signal });

    expect(getSpy).toHaveBeenCalledWith(SCAN_INSIGHTS_ENDPOINT, {
      params: { windowDays: 7 },
      signal: controller.signal,
    });
  });

  it('rejects unsupported windows before making a request', async () => {
    const getSpy = vi.spyOn(apiClient, 'get');

    await expect(getScanInsights({ windowDays: 14 as never })).rejects.toThrow();

    expect(getSpy).not.toHaveBeenCalled();
  });
});
