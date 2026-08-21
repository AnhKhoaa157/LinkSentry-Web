import type { ReactNode } from 'react';
import { useCallback, useEffect, useMemo, useState } from 'react';

import { LocaleContext, type Translate } from '@/lib/i18n/LocaleContext';
import type { Locale } from '@/lib/i18n/locale';
import { localeStorage } from '@/lib/i18n/localeStorage';
import { dictionaries } from '@/lib/i18n/translations';

interface LocaleProviderProps {
  readonly children: ReactNode;
}

/**
 * Owns the active display language. Defaults to `'en'` and renders immediately with it — a
 * persisted `'vi'` choice, once read back from storage, is a cheap local overwrite rather than
 * something worth gating first paint on, unlike the device credential bootstrap this mirrors the
 * shape of.
 */
export function LocaleProvider({ children }: LocaleProviderProps) {
  const [locale, setLocaleState] = useState<Locale>('en');

  useEffect(() => {
    let cancelled = false;
    void localeStorage.get().then((stored) => {
      if (!cancelled && stored !== null) {
        setLocaleState(stored);
      }
    });
    return () => {
      cancelled = true;
    };
  }, []);

  const setLocale = useCallback((next: Locale) => {
    setLocaleState(next);
    void localeStorage.set(next);
  }, []);

  const t = useCallback<Translate>(
    (key, params) => {
      const template = dictionaries[locale][key];
      if (!params) {
        return template;
      }
      return template.replace(/\{(\w+)\}/g, (match, token: string) =>
        token in params ? String(params[token]) : match,
      );
    },
    [locale],
  );

  const value = useMemo(() => ({ locale, setLocale, t }), [locale, setLocale, t]);

  return <LocaleContext.Provider value={value}>{children}</LocaleContext.Provider>;
}
