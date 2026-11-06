import { Link } from 'react-router';

import { Card } from '@/components/ui/Card';
import type { TranslationKey } from '@/lib/i18n/translations';
import { LicenseStatusCard } from '@/features/license/components/LicenseStatusCard';
import { useLocale } from '@/lib/i18n/useLocale';

const activationSteps = [
  ['license.steps.share.title', 'license.steps.share.body'],
  ['license.steps.grant.title', 'license.steps.grant.body'],
  ['license.steps.check.title', 'license.steps.check.body'],
] as const satisfies ReadonlyArray<readonly [TranslationKey, TranslationKey]>;

export function LicensePage() {
  const { t } = useLocale();

  return (
    <div className="license-page mx-auto w-full max-w-6xl space-y-7">
      <header className="page-intro space-y-4">
        <p className="eyebrow">{t('license.pageEyebrow')}</p>
        <h1 className="display-title text-content-primary text-4xl sm:text-5xl">{t('license.pageTitle')}</h1>
        <p className="body-secondary text-sm leading-6">{t('license.pageIntro')}</p>
      </header>

      <LicenseStatusCard />

      <section className="license-steps grid gap-4 md:grid-cols-3" aria-label={t('license.steps.heading')}>
        {activationSteps.map(([titleKey, bodyKey], index) => (
          <Card key={titleKey} className="license-steps__card h-full">
            <p className="eyebrow">0{index + 1}</p>
            <h2 className="text-content-primary mt-3 text-base font-bold">{t(titleKey)}</h2>
            <p className="body-secondary mt-2 text-sm leading-6">{t(bodyKey)}</p>
          </Card>
        ))}
      </section>

      <p className="body-muted text-xs leading-5">{t('license.pageFootnote')}</p>

      <Link to="/" className="text-accent-strong inline-flex text-sm font-bold underline underline-offset-4">
        {t('license.back')}
      </Link>
    </div>
  );
}
