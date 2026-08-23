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

        <LanguageSwitcher className="order-2 sm:order-3" />
      </div>
    </header>
  );
}

/**
 * Shield-and-link mark, drawn in inline SVG.
 *
 * Uses one semantic foreground colour, so the same mark remains legible on the
 * application's light and dark surfaces without an alternate asset.
 */
function ShieldMark() {
  return (
    <svg
      viewBox="0 0 32 32"
      className="text-content-primary size-7"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.7"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M16 3.5 25 7v6.8c0 6-3.8 11.1-9 13.2-5.2-2.1-9-7.2-9-13.2V7l9-3.5Z" />
      <path d="m13.2 18.8 5.6-5.6" />
      <path d="m12 13.2 1.2-1.2a3.4 3.4 0 0 1 4.8 0" />
      <path d="m20 18.8-1.2 1.2a3.4 3.4 0 0 1-4.8 0" />
    </svg>
  );
}
