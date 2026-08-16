import { z } from 'zod';

/**
 * Client-side input validation, mirroring the backend's rules
 * (`docs/API_CONTRACT.md`) so feedback is instant.
 *
 * This is a convenience, never a security control: the backend re-validates and
 * re-normalises every submission regardless of what passes here.
 */
export const scanRequestSchema = z.object({
  url: z
    .string()
    .trim()
    .min(1, 'Enter a URL to analyze.')
    .max(2048, 'URL must be at most 2048 characters.')
    .refine((value) => /^https?:\/\//i.test(value), 'Enter a URL starting with http:// or https://'),
});

export type ScanRequest = z.infer<typeof scanRequestSchema>;
