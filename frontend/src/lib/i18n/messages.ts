import type { NormalizedApiError } from '@/lib/api/errors';
import type { Translate } from '@/lib/i18n/LocaleContext';
import type { TranslationKey } from '@/lib/i18n/translations';
import { isTranslationKey } from '@/lib/i18n/translations';

const API_ERROR_KEYS: Partial<Record<string, TranslationKey>> = {
  NETWORK_ERROR: 'errors.network',
  INVALID_URL: 'errors.invalidUrl',
  RATE_LIMITED: 'errors.rateLimited',
  ANONYMOUS_TRIAL_EXHAUSTED: 'errors.trialExhausted',
  SCAN_NOT_FOUND: 'errors.scanNotFound',
  UNAUTHORIZED: 'errors.unauthorized',
  FORBIDDEN: 'errors.forbidden',
  AI_EXPLANATION_UNAVAILABLE: 'errors.aiUnavailable',
};

/**
 * API errors are safe server output, but known contract codes get a local
 * translation so the shell remains bilingual without sending URLs or finding
 * text to a translation service.
 */
export function localizeApiError(t: Translate, error: NormalizedApiError): string {
  const key = error.code === undefined ? undefined : API_ERROR_KEYS[error.code];
  return key === undefined ? error.message : t(key);
}

const VALIDATION_MESSAGE_KEYS: Record<string, TranslationKey> = {
  'validation.url.required': 'validation.url.required',
  'validation.url.maxLength': 'validation.url.maxLength',
  'validation.url.trim': 'validation.url.trim',
  'validation.url.scheme': 'validation.url.scheme',
  'validation.username.required': 'validation.username.required',
  'validation.password.required': 'validation.password.required',
  'validation.license.label': 'validation.license.label',
  'validation.activationCode.required': 'validation.activationCode.required',
  'validation.expiry.required': 'validation.expiry.required',
  'Enter a URL to analyze.': 'validation.url.required',
  'URL must be at most 2048 characters.': 'validation.url.maxLength',
  'Enter a URL without surrounding spaces.': 'validation.url.trim',
  'Enter a URL starting with http:// or https://': 'validation.url.scheme',
  'Enter a valid HTTP or HTTPS URL.': 'validation.url.server',
  'Enter your username.': 'validation.username.required',
  'Enter your password.': 'validation.password.required',
  'Enter a label for this license.': 'validation.license.label',
  'Enter an activation code.': 'validation.activationCode.required',
  'Choose an expiry date or no expiry.': 'validation.expiry.required',
};

export function localizeValidationMessage(t: Translate, message: string | undefined): string {
  if (message === undefined) {
    return t('validation.generic');
  }

  const key = VALIDATION_MESSAGE_KEYS[message];
  if (key !== undefined) {
    return t(key);
  }

  return isTranslationKey(message) ? t(message) : message;
}
