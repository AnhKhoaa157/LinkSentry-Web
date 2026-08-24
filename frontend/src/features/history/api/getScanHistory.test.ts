import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  getScanHistory,
  SCAN_HISTORY_ENDPOINT,
  SCAN_HISTORY_PAGE_SIZE,
} from '@/features/history/api/getScanHistory';
import type { ScanHistoryResponse } from '@/features/history/schemas/scanHistoryResponse';
import { apiClient } from '@/lib/api/client';

const response: ScanHistoryResponse = {
  data: { items: [], nextCursor: null },
  meta: { retentionDays: 30 },
};

describe('getScanHistory', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('requests the collection endpoint with the initial page size and no cursor', async () => {
    const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValue({ data: response });

    await expect(getScanHistory()).resolves.toEqual(response);

    expect(getSpy).toHaveBeenCalledWith(SCAN_HISTORY_ENDPOINT, {
      params: { limit: SCAN_HISTORY_PAGE_SIZE },
    });
  });

  it('forwards the opaque cursor and abort signal without interpreting the cursor', async () => {
    const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValue({ data: response });
    const controller = new AbortController();
    const cursor = 'v1.opaque-token-with-punctuation_-/=';

    await getScanHistory({ cursor, signal: controller.signal });

    expect(getSpy).toHaveBeenCalledWith(SCAN_HISTORY_ENDPOINT, {
      params: { limit: SCAN_HISTORY_PAGE_SIZE, cursor },
      signal: controller.signal,
    });
  });

  it('sends only validated enum filters and keeps the request private', async () => {
    const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValue({ data: response });

    await getScanHistory({ riskLevel: 'HIGH', ruleId: 'MISSING_HTTPS' });

    expect(getSpy).toHaveBeenCalledWith(SCAN_HISTORY_ENDPOINT, {
      params: { limit: SCAN_HISTORY_PAGE_SIZE, riskLevel: 'HIGH', ruleId: 'MISSING_HTTPS' },
    });
  });

  it('rejects an unsafe filter before making a request', async () => {
    const getSpy = vi.spyOn(apiClient, 'get');

    await expect(getScanHistory({ ruleId: 'free-text-search' as never })).rejects.toThrow();

    expect(getSpy).not.toHaveBeenCalled();
  });
});
