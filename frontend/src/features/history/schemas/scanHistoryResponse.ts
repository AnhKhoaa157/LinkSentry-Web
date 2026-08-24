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

/** Server-owned rule identifiers exposed as exact history filter values. */
export const historyRuleIds = [
  'BRAND_DOMAIN_MISMATCH',
  'BRAND_LOOKALIKE_HOSTNAME',
  'BRAND_IMPERSONATION_APK',
  'ENCODED_CHARACTERS',
  'EXCESSIVE_SUBDOMAINS',
  'EXCESSIVE_URL_LENGTH',
  'IP_LITERAL_HOST',
  'KNOWN_URL_SHORTENER',
  'MISSING_HTTPS',
  'PUNYCODE_HOST',
  'SPECIAL_USE_OR_PRIVATE_HOST',
  'SUSPICIOUS_KEYWORDS',
] as const;

export const historyRuleIdSchema = z.enum(historyRuleIds);

export const scanHistoryFiltersSchema = z
  .object({
    riskLevel: riskLevelSchema.nullable(),
    ruleId: historyRuleIdSchema.nullable(),
  })
  .strict();

export const DEFAULT_SCAN_HISTORY_FILTERS = {
  riskLevel: null,
  ruleId: null,
} as const;

export const scanInsightsWindowDaysSchema = z.union([z.literal(7), z.literal(30)]);

export const scanInsightsTopFindingSchema = z
  .object({
    ruleId: historyRuleIdSchema,
    title: z.string().trim().min(1).max(255),
    count: z.number().int().positive(),
  })
  .strict();

const scanInsightsDataSchema = z
  .object({
    windowDays: scanInsightsWindowDaysSchema,
    totalScans: z.number().int().nonnegative(),
    scansLast7Days: z.number().int().nonnegative(),
    previous7Days: z.number().int().nonnegative(),
    riskDistribution: z
      .object({
        LOW: z.number().int().nonnegative(),
        MODERATE: z.number().int().nonnegative(),
        HIGH: z.number().int().nonnegative(),
        CRITICAL: z.number().int().nonnegative(),
      })
      .strict(),
    topFindings: z.array(scanInsightsTopFindingSchema).max(5),
  })
  .strict()
  .superRefine((data, context) => {
    const distributionTotal = Object.values(data.riskDistribution).reduce((sum, count) => sum + count, 0);
    if (distributionTotal !== data.totalScans) {
      context.addIssue({
        code: 'custom',
        path: ['riskDistribution'],
        message: 'Risk distribution must equal total scans.',
      });
    }
  });

export const scanInsightsResponseSchema = z
  .object({
    data: scanInsightsDataSchema,
    meta: z
      .object({
        retentionDays: z.number().int().positive(),
      })
      .strict(),
  })
  .strict();

export type ScanHistoryItem = z.infer<typeof scanHistoryItemSchema>;
export type ScanHistoryResponse = z.infer<typeof scanHistoryResponseSchema>;
export type ScanHistoryFilters = z.infer<typeof scanHistoryFiltersSchema>;
export type HistoryRuleId = z.infer<typeof historyRuleIdSchema>;
export type ScanInsightsWindowDays = z.infer<typeof scanInsightsWindowDaysSchema>;
export type ScanInsightsResponse = z.infer<typeof scanInsightsResponseSchema>;
