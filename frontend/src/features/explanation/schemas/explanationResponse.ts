import { z } from 'zod';

import { riskLevelSchema, severitySchema } from '@/features/scanner/schemas/scanResponse';

/**
 * Response of `POST /api/v1/scans/{scanId}/explanation`. Mirrors
 * `docs/API_CONTRACT.md`. Parsed rather than merely typed, the same reasoning as
 * `features/scanner/schemas/scanResponse.ts`: a backend contract change must
 * surface as a clear parse failure, not `undefined` quietly rendered as AI text.
 *
 * `riskLevel` and `keyFindings` are backend-owned, deterministic values — the
 * same enums and shape as a scan's own findings, minus `ruleId` and `evidence`.
 * Only `summary` and `recommendedActions` originate from the AI provider.
 */
export const explanationKeyFindingSchema = z.object({
  title: z.string(),
  explanation: z.string(),
  severity: severitySchema,
  points: z.number().int().nonnegative(),
});

export const explanationDataSchema = z.object({
  riskLevel: riskLevelSchema,
  keyFindings: z.array(explanationKeyFindingSchema),
  summary: z.string(),
  recommendedActions: z.array(z.string()),
});

export const explanationResponseSchema = z.object({
  data: explanationDataSchema,
});

export type ExplanationResponse = z.infer<typeof explanationResponseSchema>;
export type ExplanationData = z.infer<typeof explanationDataSchema>;
export type ExplanationKeyFinding = z.infer<typeof explanationKeyFindingSchema>;
