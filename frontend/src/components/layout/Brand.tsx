import { NavLink } from 'react-router';

import { useLocale } from '@/lib/i18n/useLocale';

/** The shared wordmark always uses the original LinkSentry image asset. */
export function Brand() {
  const { t } = useLocale();

  return (
    <NavLink to="/" className="brand-lockup group flex items-center gap-2.5" aria-label={t('nav.homeAria')}>
      <img src="/brand/linksentry-mark.png" alt="" className="brand-mark size-7" aria-hidden="true" />
      <span className="text-content-primary text-base font-bold tracking-tight">LinkSentry</span>
    </NavLink>
  );
}
