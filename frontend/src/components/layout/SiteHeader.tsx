import { NavLink } from 'react-router';

import { cn } from '@/lib/utils/cn';

const navigation = [
  { to: '/', label: 'Home' },
  { to: '/methodology', label: 'Methodology' },
] as const;

/** Wordmark and primary navigation. */
export function SiteHeader() {
  return (
    <header className="border-ink-800 bg-ink-950/85 sticky top-0 z-10 border-b backdrop-blur">
      <div className="mx-auto flex max-w-5xl items-center justify-between gap-4 px-4 py-3 sm:px-6">
        <NavLink to="/" className="group flex items-center gap-2.5" aria-label="LinkSentry home">
          <ShieldMark />
          <span className="text-base font-semibold tracking-tight">
            Link<span className="text-accent-400">Sentry</span>
          </span>
        </NavLink>

        <nav aria-label="Main">
          <ul className="flex items-center gap-1">
            {navigation.map((item) => (
              <li key={item.to}>
                <NavLink
                  to={item.to}
                  end={item.to === '/'}
                  className={({ isActive }) =>
                    cn(
                      'rounded-md px-3 py-1.5 text-sm transition-colors',
                      isActive
                        ? 'bg-ink-850 text-ink-100 font-medium'
                        : 'text-ink-300 hover:bg-ink-900 hover:text-ink-100',
                    )
                  }
                >
                  {item.label}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>
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
      className="text-accent-400 size-6"
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
