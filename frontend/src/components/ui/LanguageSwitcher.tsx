import { LOCALES } from '@/lib/i18n/locale';
import { useLocale } from '@/lib/i18n/useLocale';
import { cn } from '@/lib/utils/cn';

const LOCALE_LABEL_KEY = {
  en: 'languageSwitcher.en',
  vi: 'languageSwitcher.vi',
} as const;

interface LanguageSwitcherProps {
  readonly className?: string;
}

/** Shared keyboard-friendly locale control used by the web shell and popup. */
export function LanguageSwitcher({ className }: LanguageSwitcherProps) {
  const { locale, setLocale, t } = useLocale();

  return (
    <div role="group" aria-label={t('languageSwitcher.label')} className={cn('language-switcher', className)}>
      {LOCALES.map((option) => (
        <button
          key={option}
          type="button"
          aria-pressed={locale === option}
          disabled={locale === option}
          onClick={() => setLocale(option)}
        >
          {t(LOCALE_LABEL_KEY[option])}
        </button>
      ))}
    </div>
  );
}
