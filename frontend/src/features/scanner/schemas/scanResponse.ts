import { z } from 'zod';

/**
 * Response of `POST /api/v1/scans` and `GET /api/v1/scans/{scanId}`. Mirrors
 * `docs/API_CONTRACT.md`.
 *
 * Parsed rather than merely typed: a backend contract change must surface as a
 * clear parse failure, not as `undefined` quietly rendered in the UI.
 */
export const severitySchema = z.enum(['INFO', 'LOW', 'MEDIUM', 'HIGH']);

export const riskLevelSchema = z.enum(['LOW', 'MODERATE', 'HIGH', 'CRITICAL']);

export const findingSchema = z.object({
  ruleId: z.string(),
  severity: severitySchema,
  points: z.number().int().nonnegative(),
  title: z.string(),
  explanation: z.string(),
  evidence: z.string().nullable().optional(),
});

export const normalizedUrlSchema = z.object({
  scheme: z.string(),
  host: z.string(),
  asciiHost: z.string(),
  registrableDomain: z.string().nullable(),
  port: z.number().int().nullable(),
  path: z.string(),
  queryPresent: z.boolean(),
  fragmentPresent: z.boolean(),
});

export const scanDataSchema = z.object({
  scanId: z.string(),
  input: z.string(),
  normalized: normalizedUrlSchema,
  score: z.number().int().min(0).max(100),
  riskLevel: riskLevelSchema,
  findings: z.array(findingSchema),
  analyzedAt: z.string(),
});

export const scanResponseSchema = z.object({
  data: scanDataSchema,
  meta: z.object({
    engineVersion: z.string(),
  }),
});

export type ScanResponse = z.infer<typeof scanResponseSchema>;
export type ScanData = z.infer<typeof scanDataSchema>;
export type Finding = z.infer<typeof findingSchema>;
export type NormalizedUrlResponse = z.infer<typeof normalizedUrlSchema>;
export type RiskLevel = z.infer<typeof riskLevelSchema>;
export type Severity = z.infer<typeof severitySchema>;
