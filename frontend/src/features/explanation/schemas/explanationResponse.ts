import { z } from 'zod';

/**
 * Response of `POST /api/v1/scans/{scanId}/explanation`. Mirrors
 * `docs/API_CONTRACT.md`. Parsed rather than merely typed, the same reasoning as
 * `features/scanner/schemas/scanResponse.ts`: a backend contract change must
 * surface as a clear parse failure, not `undefined` quietly rendered as AI text.
 */
export const explanationDataSchema = z.object({
  explanation: z.string(),
});

export const explanationResponseSchema = z.object({
  data: explanationDataSchema,
});

export type ExplanationResponse = z.infer<typeof explanationResponseSchema>;
export type ExplanationData = z.infer<typeof explanationDataSchema>;
