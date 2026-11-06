import { NavLink } from 'react-router';

import { Brand } from '@/components/layout/Brand';
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
    { to: '/license', label: t('nav.license') },
    { to: '/admin', label: t('nav.admin') },
  ] as const;

  return (
    <header className="site-header sticky top-0 z-10 border-b backdrop-blur-xl">
      <div className="site-header__inner page-container">
        <Brand />

        <nav aria-label={t('nav.main')} className="site-header__navigation">
          <ul className="site-navigation">
            {navigation.map((item) => (
              <li key={item.to}>
                <NavLink
                  to={item.to}
                  end={item.to === '/'}
                  className={({ isActive }) =>
                    cn(
                      'site-navigation__link rounded-full px-3.5 py-2 text-sm transition-colors',
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
          </ul>
        </nav>

        <div className="site-header__controls">
          <LicenseBadge />
          <ThemeSwitcher />
          <LanguageSwitcher />
        </div>
      </div>
    </header>
  );
}
