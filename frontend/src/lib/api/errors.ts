import { AxiosError } from 'axios';
import { z } from 'zod';

/** The backend's error envelope. Mirrors docs/API_CONTRACT.md. */
export const apiErrorSchema = z.object({
  code: z.string(),
  message: z.string(),
  fieldErrors: z.record(z.string(), z.string()).optional(),
  traceId: z.string().optional(),
  timestamp: z.string().optional(),
});

export type ApiError = z.infer<typeof apiErrorSchema>;

/** What the UI needs to know about a failed request. */
export interface NormalizedApiError {
  /** Message safe to show a user. */
  readonly message: string;
  /** Machine-readable code when the backend supplied one. */
  readonly code: string | undefined;
  /** Per-field validation messages when the backend supplied them. */
  readonly fieldErrors: Readonly<Record<string, string>> | undefined;
  /** True when the request never reached the server. */
  readonly isNetworkError: boolean;
}

const NETWORK_MESSAGE = 'Could not reach the LinkSentry API. Check that the backend is running.';
const UNEXPECTED_MESSAGE = 'Something went wrong. Please try again.';

export function isNormalizedApiError(error: unknown): error is NormalizedApiError {
  return (
    typeof error === 'object' &&
    error !== null &&
    'message' in error &&
    typeof error.message === 'string' &&
    'isNetworkError' in error &&
    typeof error.isNetworkError === 'boolean'
  );
}

/**
 * Turns anything a failed request can throw into one predictable shape.
 *
 * Components should never inspect an `AxiosError` directly: doing so leaks HTTP
 * detail into the view layer and makes every error branch a special case.
 */
export function normalizeApiError(error: unknown): NormalizedApiError {
  if (isNormalizedApiError(error)) {
    return error;
  }

  if (error instanceof AxiosError) {
    if (!error.response) {
      return {
        message: NETWORK_MESSAGE,
        code: 'NETWORK_ERROR',
        fieldErrors: undefined,
        isNetworkError: true,
      };
    }

    const parsed = apiErrorSchema.safeParse(error.response.data);
    if (parsed.success) {
      return {
        message: parsed.data.message,
        code: parsed.data.code,
        fieldErrors: parsed.data.fieldErrors,
        isNetworkError: false,
      };
    }
  }

  return {
    message: UNEXPECTED_MESSAGE,
    code: undefined,
    fieldErrors: undefined,
    isNetworkError: false,
  };
}
