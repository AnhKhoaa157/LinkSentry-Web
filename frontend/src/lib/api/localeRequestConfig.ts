import type { AxiosRequestConfig } from 'axios';

import type { Locale } from '@/lib/i18n/locale';

export interface LocaleRequestOptions {
  readonly locale?: Locale;
  readonly signal?: AbortSignal;
}

/** Adds the selected supported locale without changing the request body shape. */
export function localeRequestConfig({
  locale = 'en',
  signal,
}: LocaleRequestOptions = {}): AxiosRequestConfig {
  const config: AxiosRequestConfig = {
    headers: { 'Accept-Language': locale },
  };
  if (signal) {
    config.signal = signal;
  }
  return config;
}
