import { z } from 'zod';

export const adminLoginRequestSchema = z.object({
  username: z.string().trim().min(1, 'Enter your username.').max(120),
  password: z.string().min(1, 'Enter your password.').max(72),
});
