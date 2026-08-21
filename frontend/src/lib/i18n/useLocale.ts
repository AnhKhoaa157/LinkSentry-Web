import { useContext } from 'react';

import { LocaleContext } from '@/lib/i18n/LocaleContext';

export function useLocale() {
  const value = useContext(LocaleContext);
  if (value === null) {
    throw new Error('useLocale must be used inside LocaleProvider');
  }
  return value;
}
