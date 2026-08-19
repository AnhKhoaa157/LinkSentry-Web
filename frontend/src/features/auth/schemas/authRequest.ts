import { z } from 'zod';

export const emailSchema = z.string().trim().email('Enter a valid email address.').max(320);
export const passwordSchema = z.string().min(8, 'Use a password between 8 and 72 characters.').max(72);

export const loginRequestSchema = z.object({
  email: emailSchema,
  password: passwordSchema,
});

export const registerRequestSchema = loginRequestSchema
  .extend({
    confirmation: z.string(),
  })
  .superRefine((value, context) => {
    if (value.password !== value.confirmation) {
      context.addIssue({
        code: 'custom',
        path: ['confirmation'],
        message: 'Passwords must match.',
      });
    }
  });

export const registrationVerificationSchema = z.object({
  email: emailSchema,
  code: z.string().regex(/^\d{6}$/, 'Enter the 6-digit verification code.'),
});
