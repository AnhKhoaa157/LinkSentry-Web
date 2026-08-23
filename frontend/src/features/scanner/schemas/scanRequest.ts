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
    .min(1, 'validation.url.required')
    .max(2048, 'validation.url.maxLength')
    .refine((value) => value === value.trim(), 'validation.url.trim')
    .refine((value) => /^https?:\/\//i.test(value), 'validation.url.scheme'),
});

export type ScanRequest = z.infer<typeof scanRequestSchema>;
