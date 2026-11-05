import { Brand } from '@/components/layout/Brand';
import { useLocale } from '@/lib/i18n/useLocale';

/** Footer carrying the product boundary and limitations disclaimer. */
export function SiteFooter() {
  const { t } = useLocale();

  return (
    <footer className="site-footer border-border-default mt-auto border-t">
      <div className="page-container grid gap-5 py-7 text-sm sm:grid-cols-[auto_minmax(0,1fr)_auto] sm:items-center sm:gap-7">
        <Brand />
        <p className="body-muted max-w-2xl leading-6">{t('footer.description')}</p>
        <p className="text-content-secondary border-border-default bg-surface-panel rounded-full border px-3 py-2 font-mono text-[0.68rem] leading-5 sm:max-w-60">
          {t('footer.staticOnly')}
        </p>
      </div>
    </footer>
  );
}
