import { describe, expect, it } from 'vitest';

import { scanHistoryResponseSchema } from '@/features/history/schemas/scanHistoryResponse';

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
