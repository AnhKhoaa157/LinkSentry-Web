import { Link } from 'react-router';

import { Badge } from '@/components/ui/Badge';
import { Card } from '@/components/ui/Card';
import { HealthStatus } from '@/features/health/components/HealthStatus';
import { Scanner } from '@/features/scanner/components/Scanner';
import type { TranslationKey } from '@/lib/i18n/translations';
import { useLocale } from '@/lib/i18n/useLocale';

const boundaryKeys = [
  ['home.boundary.1.title', 'home.boundary.1.body'],
  ['home.boundary.2.title', 'home.boundary.2.body'],
  ['home.boundary.3.title', 'home.boundary.3.body'],
] as const satisfies ReadonlyArray<readonly [TranslationKey, TranslationKey]>;

/** Landing page: product statement, scanner, boundary, and service health. */
export function HomePage() {
  const { t } = useLocale();

  return (
    <div className="home-page">
      <section
        className="home-hero grid items-end gap-8 lg:grid-cols-[minmax(0,1fr)_18rem]"
        aria-labelledby="home-title"
      >
        <div className="home-hero__copy max-w-3xl space-y-5">
          <Badge tone="accent">{t('home.eyebrow')}</Badge>
          <h1 id="home-title" className="display-title text-content-primary">
            {t('home.title')}
          </h1>
          <p className="body-secondary max-w-2xl text-base leading-7 sm:text-lg">{t('home.description')}</p>
        </div>

        <aside className="home-hero__note surface-inset p-5" aria-label={t('common.staticAnalysisOnly')}>
          <p className="eyebrow">{t('common.staticAnalysisOnly')}</p>
          <p className="text-content-primary mt-3 text-sm font-bold">{t('home.boundary.1.title')}</p>
          <p className="body-secondary mt-2 text-sm leading-6">{t('home.boundary.1.body')}</p>
        </aside>
      </section>

      <Scanner />

      <section aria-labelledby="boundary-heading" className="home-boundary-section space-y-5">
        <div className="max-w-2xl space-y-3">
          <p className="eyebrow">{t('common.staticAnalysisOnly')}</p>
          <h2 id="boundary-heading" className="section-title text-content-primary">
            {t('home.boundaryHeading')}
          </h2>
          <p className="body-secondary text-sm leading-6">{t('home.boundaryIntro')}</p>
        </div>

        <ul className="home-boundary-grid grid gap-4 md:grid-cols-3">
          {boundaryKeys.map(([titleKey, bodyKey], index) => (
            <li key={titleKey} className="h-full">
              <Card className="h-full">
                <p className="eyebrow">0{index + 1}</p>
                <h3 className="text-content-primary mt-3 text-base font-bold">{t(titleKey)}</h3>
                <p className="body-secondary mt-2 text-sm leading-6">{t(bodyKey)}</p>
              </Card>
            </li>
          ))}
        </ul>

        <Link
          to="/methodology"
          className="text-accent-strong inline-flex items-center gap-2 text-sm font-bold underline underline-offset-4"
        >
          {t('home.methodologyLink')}
          <span aria-hidden="true">↗</span>
        </Link>
      </section>

      <Card title={t('home.statusTitle')} description={t('home.statusDescription')}>
        <HealthStatus />
      </Card>
    </div>
  );
}
