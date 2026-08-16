import { z } from 'zod';

/**
 * Response of `GET /api/v1/health`. Mirrors docs/API_CONTRACT.md.
 *
 * Parsed rather than merely typed: a `type` alone is erased at runtime, so a
 * backend contract change would surface as `undefined` in the UI instead of a
 * clear failure.
 */
export const healthResponseSchema = z.object({
  status: z.string(),
  service: z.string(),
});

export type HealthResponse = z.infer<typeof healthResponseSchema>;
