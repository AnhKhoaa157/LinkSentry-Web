import { apiClient } from '@/lib/api/client';
import {
  scanHistoryFiltersSchema,
  scanHistoryResponseSchema,
  type HistoryRuleId,
  type ScanHistoryResponse,
} from '@/features/history/schemas/scanHistoryResponse';
import type { RiskLevel } from '@/features/scanner/schemas/scanResponse';

export const SCAN_HISTORY_ENDPOINT = '/api/v1/scans';
export const SCAN_HISTORY_PAGE_SIZE = 20;

export interface GetScanHistoryOptions {
  readonly cursor?: string | null;
  readonly limit?: number;
  readonly riskLevel?: RiskLevel | null;
  readonly ruleId?: HistoryRuleId | null;
  readonly signal?: AbortSignal;
}

/** Retrieves and validates one private, license-scoped history page. */
export async function getScanHistory(options: GetScanHistoryOptions = {}): Promise<ScanHistoryResponse> {
  const filters = scanHistoryFiltersSchema.parse({
    riskLevel: options.riskLevel ?? null,
    ruleId: options.ruleId ?? null,
  });
  const params: {
    limit: number;
    cursor?: string;
    riskLevel?: RiskLevel;
    ruleId?: HistoryRuleId;
  } = {
    limit: options.limit ?? SCAN_HISTORY_PAGE_SIZE,
  };
  if (options.cursor !== undefined && options.cursor !== null) {
    params.cursor = options.cursor;
  }
  if (filters.riskLevel !== null) {
    params.riskLevel = filters.riskLevel;
  }
  if (filters.ruleId !== null) {
    params.ruleId = filters.ruleId;
  }

  const response = await apiClient.get<unknown>(SCAN_HISTORY_ENDPOINT, {
    params,
    ...(options.signal ? { signal: options.signal } : {}),
  });
  return scanHistoryResponseSchema.parse(response.data);
}
