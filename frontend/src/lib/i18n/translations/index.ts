import { en, type TranslationKey } from '@/lib/i18n/translations/en';
import { vi } from '@/lib/i18n/translations/vi';
import type { Locale } from '@/lib/i18n/locale';

export type { TranslationKey };

export const dictionaries: Record<Locale, Record<TranslationKey, string>> = { en, vi };
