import { LOCALES } from '@/lib/i18n/locale';
import { useLocale } from '@/lib/i18n/useLocale';

const LOCALE_LABEL_KEY = {
  en: 'languageSwitcher.en',
  vi: 'languageSwitcher.vi',
} as const;

/** Compact EN/VI toggle for the popup header. Extension-only for now — the web app has no switcher yet. */
export function LanguageSwitcher() {
  const { locale, setLocale, t } = useLocale();

  return (
    <div role="group" aria-label={t('languageSwitcher.label')} className="popup-language-toggle">
      {LOCALES.map((option) => (
        <button
          key={option}
          type="button"
          aria-pressed={locale === option}
          disabled={locale === option}
          onClick={() => setLocale(option)}
          className="popup-language-option"
        >
          {t(LOCALE_LABEL_KEY[option])}
        </button>
      ))}
    </div>
  );
}
