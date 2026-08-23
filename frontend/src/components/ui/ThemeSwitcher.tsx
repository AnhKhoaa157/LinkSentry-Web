import { useLocale } from '@/lib/i18n/useLocale';
import { useTheme } from '@/lib/theme/useTheme';

/** Compact theme toggle shared by every route in the web shell. */
export function ThemeSwitcher() {
  const { t } = useLocale();
  const { theme, toggleTheme } = useTheme();
  const isDark = theme === 'dark';
  const label = t(isDark ? 'themeSwitcher.toLight' : 'themeSwitcher.toDark');

  return (
    <button type="button" className="theme-switcher" onClick={toggleTheme} aria-label={label} title={label}>
      {isDark ? (
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <circle cx="12" cy="12" r="3.5" />
          <path d="M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.3 5.3l1.4 1.4M17.3 17.3l1.4 1.4M18.7 5.3l-1.4 1.4M6.7 17.3l-1.4 1.4" />
        </svg>
      ) : (
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M20.2 15.2A8.5 8.5 0 0 1 8.8 3.8 8.5 8.5 0 1 0 20.2 15.2Z" />
        </svg>
      )}
    </button>
  );
}
