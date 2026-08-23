import { NavLink } from 'react-router';

import { LanguageSwitcher } from '@/components/ui/LanguageSwitcher';
import { LicenseBadge } from '@/features/license/components/LicenseBadge';
import { useLocale } from '@/lib/i18n/useLocale';
import { cn } from '@/lib/utils/cn';

/** Wordmark and primary navigation. */
export function SiteHeader() {
  const { t } = useLocale();
  const navigation = [
    { to: '/', label: t('nav.home') },
    { to: '/methodology', label: t('nav.methodology') },
  ] as const;

  return (
    <header className="border-border-default bg-surface-canvas/85 sticky top-0 z-10 border-b backdrop-blur-xl">
      <div className="page-container flex flex-wrap items-center justify-between gap-x-6 gap-y-3 py-3.5">
        <NavLink to="/" className="group flex items-center gap-3" aria-label={t('nav.homeAria')}>
          <ShieldMark />
          <span className="text-content-primary text-base font-bold tracking-tight">
            Link<span className="text-accent-strong">Sentry</span>
          </span>
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

        <LanguageSwitcher className="order-2 sm:order-3" />
      </div>
    </header>
  );
}

/**
 * Shield-and-link mark, drawn in inline SVG.
 *
 * Deliberately a simple placeholder: a shield outline enclosing a chain link. No
 * external asset, no request, no build step.
 */
function ShieldMark() {
  return (
    <svg
      viewBox="0 0 24 24"
      className="text-accent-strong size-7"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M12 2.5l7.5 2.6v5.6c0 4.4-3 7.9-7.5 9.3-4.5-1.4-7.5-4.9-7.5-9.3V5.1L12 2.5z" />
      <path d="M10.4 13.6a1.9 1.9 0 010-2.7l1-1a1.9 1.9 0 012.7 0" />
      <path d="M13.6 10.4a1.9 1.9 0 010 2.7l-1 1a1.9 1.9 0 01-2.7 0" />
    </svg>
  );
}
