import { z } from 'zod';

import { riskLevelSchema } from '@/features/scanner/schemas/scanResponse';

/**
 * Exact response of the licensed `GET /api/v1/scans` collection.
 *
 * Every object is strict so additive or breaking contract drift is surfaced at
 * the network boundary instead of being silently ignored by the history UI.
 */
export const scanHistoryItemSchema = z
  .object({
    scanId: z.string().uuid(),
    displayValue: z.string().min(1),
    registrableDomain: z.string().min(1).nullable(),
    score: z.number().int().min(0).max(100),
    riskLevel: riskLevelSchema,
    findingCount: z.number().int().nonnegative(),
    analyzedAt: z.string().datetime({ offset: true }),
  })
  .strict();

export const scanHistoryResponseSchema = z
  .object({
    data: z
      .object({
        items: z.array(scanHistoryItemSchema),
        nextCursor: z.string().min(1).nullable(),
      })
      .strict(),
    meta: z
      .object({
        retentionDays: z.number().int().positive(),
      })
      .strict(),
  })
  .strict();

export type ScanHistoryItem = z.infer<typeof scanHistoryItemSchema>;
export type ScanHistoryResponse = z.infer<typeof scanHistoryResponseSchema>;
