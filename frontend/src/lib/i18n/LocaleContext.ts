import { createContext } from 'react';

import type { Locale } from '@/lib/i18n/locale';
import type { TranslationKey } from '@/lib/i18n/translations';

export type Translate = (key: TranslationKey, params?: Record<string, string | number>) => string;

export interface LocaleContextValue {
  readonly locale: Locale;
  readonly setLocale: (locale: Locale) => void;
  readonly t: Translate;
}

export const LocaleContext = createContext<LocaleContextValue | null>(null);
