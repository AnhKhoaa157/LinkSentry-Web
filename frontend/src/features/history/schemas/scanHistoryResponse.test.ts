import { describe, expect, it } from 'vitest';

import {
  scanHistoryFiltersSchema,
  scanHistoryResponseSchema,
  scanInsightsResponseSchema,
} from '@/features/history/schemas/scanHistoryResponse';

const validResponse = {
  data: {
    items: [
      {
        scanId: '2ce16fb9-d52d-4310-8d45-a4e48f31889e',
        displayValue: 'https://example.com/account',
        registrableDomain: 'example.com',
        score: 40,
        riskLevel: 'HIGH',
        findingCount: 2,
        analyzedAt: '2026-08-24T00:00:00Z',
      },
    ],
    nextCursor: 'opaque-page-2',
  },
  meta: { retentionDays: 30 },
} as const;

describe('scanHistoryResponseSchema', () => {
  it('parses the exact M9.1 list response', () => {
    expect(scanHistoryResponseSchema.parse(validResponse)).toEqual(validResponse);
  });

  it.each([
    [
      'an out-of-range score',
      {
        ...validResponse,
        data: { ...validResponse.data, items: [{ ...validResponse.data.items[0], score: 101 }] },
      },
    ],
    [
      'an unknown risk level',
      {
        ...validResponse,
        data: { ...validResponse.data, items: [{ ...validResponse.data.items[0], riskLevel: 'SAFE' }] },
      },
    ],
    [
      'a missing finding count',
      {
        ...validResponse,
        data: {
          ...validResponse.data,
          items: validResponse.data.items.map(({ findingCount: _findingCount, ...item }) => item),
        },
      },
    ],
    ['an unexpected contract field', { ...validResponse, ownerLicenseId: 'must-not-cross-the-wire' }],
  ])('rejects %s', (_label, response) => {
    expect(scanHistoryResponseSchema.safeParse(response).success).toBe(false);
  });
});

describe('scanHistoryFiltersSchema', () => {
  it('accepts only the bounded enum filters', () => {
    expect(scanHistoryFiltersSchema.parse({ riskLevel: 'HIGH', ruleId: 'MISSING_HTTPS' })).toEqual({
      riskLevel: 'HIGH',
      ruleId: 'MISSING_HTTPS',
    });
    expect(scanHistoryFiltersSchema.parse({ riskLevel: null, ruleId: null })).toEqual({
      riskLevel: null,
      ruleId: null,
    });
  });

  it.each([
    { riskLevel: 'SAFE', ruleId: null },
    { riskLevel: null, ruleId: 'free-text-search' },
    { riskLevel: 'HIGH', ruleId: 'https://example.test?token=secret' },
  ])('rejects unsafe filter values', (filters) => {
    expect(scanHistoryFiltersSchema.safeParse(filters).success).toBe(false);
  });
});

describe('scanInsightsResponseSchema', () => {
  const validInsights = {
    data: {
      windowDays: 30,
      totalScans: 4,
      scansLast7Days: 2,
      previous7Days: 1,
      riskDistribution: { LOW: 1, MODERATE: 1, HIGH: 2, CRITICAL: 0 },
      topFindings: [{ ruleId: 'MISSING_HTTPS', title: 'Connection is not encrypted', count: 2 }],
    },
    meta: { retentionDays: 30 },
  } as const;

  it('parses the strict aggregate response contract', () => {
    expect(scanInsightsResponseSchema.parse(validInsights)).toEqual(validInsights);
  });

  it.each([
    [
      'a mismatched risk total',
      {
        ...validInsights,
        data: {
          ...validInsights.data,
          riskDistribution: { LOW: 0, MODERATE: 0, HIGH: 0, CRITICAL: 0 },
        },
      },
    ],
    ['an unsupported insight window', { ...validInsights, data: { ...validInsights.data, windowDays: 14 } }],
    [
      'a sensitive unexpected field',
      { ...validInsights, data: { ...validInsights.data, evidence: 'query-secret' } },
    ],
    [
      'an unbounded rule identifier',
      {
        ...validInsights,
        data: {
          ...validInsights.data,
          topFindings: [{ ruleId: 'query-secret', title: 'Unsafe', count: 1 }],
        },
      },
    ],
  ])('rejects %s', (_label, response) => {
    expect(scanInsightsResponseSchema.safeParse(response).success).toBe(false);
  });
});
