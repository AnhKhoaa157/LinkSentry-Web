import { z } from 'zod';

export const adminLoginRequestSchema = z.object({
  username: z.string().trim().min(1, 'validation.username.required').max(120),
  password: z.string().min(1, 'validation.password.required').max(72),
});
