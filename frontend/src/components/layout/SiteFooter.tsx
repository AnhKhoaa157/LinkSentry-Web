import { useLocale } from '@/lib/i18n/useLocale';

/** Footer carrying the product boundary and limitations disclaimer. */
export function SiteFooter() {
  const { t } = useLocale();

  return (
    <footer className="border-border-default mt-auto border-t">
      <div className="page-container body-muted flex flex-col gap-2 py-8 text-sm sm:flex-row sm:items-start sm:justify-between">
        <p className="max-w-2xl">{t('footer.description')}</p>
        <p className="font-mono text-xs">{t('footer.staticOnly')}</p>
      </div>
    </footer>
  );
}
