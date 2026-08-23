import { NavLink } from 'react-router';

import { LanguageSwitcher } from '@/components/ui/LanguageSwitcher';
import { ThemeSwitcher } from '@/components/ui/ThemeSwitcher';
import { LicenseBadge } from '@/features/license/components/LicenseBadge';
import { useLicense } from '@/features/license/context/useLicense';
import { useLocale } from '@/lib/i18n/useLocale';
import { cn } from '@/lib/utils/cn';

/** Wordmark and primary navigation. */
export function SiteHeader() {
  const { t } = useLocale();
  const { isLicensed } = useLicense();
  const navigation = [
    { to: '/', label: t('nav.home') },
    ...(isLicensed ? [{ to: '/history', label: t('nav.history') }] : []),
    { to: '/methodology', label: t('nav.methodology') },
  ] as const;

  return (
    <header className="border-border-default bg-surface-canvas/85 sticky top-0 z-10 border-b backdrop-blur-xl">
      <div className="page-container flex flex-wrap items-center justify-between gap-x-6 gap-y-3 py-3.5">
        <NavLink to="/" className="group flex items-center gap-3" aria-label={t('nav.homeAria')}>
          <img src="/brand/linksentry-mark.png" alt="" className="brand-mark size-7" aria-hidden="true" />
          <span className="text-content-primary text-base font-bold tracking-tight">LinkSentry</span>
        </NavLink>

        <nav aria-label={t('nav.main')} className="order-3 w-full sm:order-2 sm:w-auto">
          <ul className="flex flex-wrap items-center justify-end gap-1">
            {navigation.map((item) => (
              <li key={item.to}>
                <NavLink
                  to={item.to}
                  end={item.to === '/'}
                  className={({ isActive }) =>
                    cn(
                      'rounded-lg px-3 py-2 text-sm transition-colors',
                      isActive
                        ? 'bg-accent-soft text-content-primary font-bold'
                        : 'text-content-secondary hover:bg-surface-inset hover:text-content-primary',
                    )
                  }
                >
                  {item.label}
                </NavLink>
              </li>
            ))}
            <li>
              <LicenseBadge />
            </li>
            <li>
              <NavLink
                to="/admin"
                className="text-content-muted hover:bg-surface-inset hover:text-content-primary rounded-lg px-3 py-2 text-sm transition-colors"
              >
                {t('nav.admin')}
              </NavLink>
            </li>
          </ul>
        </nav>

        <div className="order-2 flex items-center gap-2 sm:order-3">
          <ThemeSwitcher />
          <LanguageSwitcher />
        </div>
      </div>
    </header>
  );
}
