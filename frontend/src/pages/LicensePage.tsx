import { Link } from 'react-router';

import { LicenseStatusCard } from '@/features/license/components/LicenseStatusCard';
import { useLocale } from '@/lib/i18n/useLocale';

export function LicensePage() {
  const { t } = useLocale();

  return (
    <div className="mx-auto w-full max-w-2xl space-y-7">
      <header className="max-w-xl space-y-4">
        <p className="eyebrow">{t('license.pageEyebrow')}</p>
        <h1 className="display-title text-content-primary text-4xl sm:text-5xl">{t('license.pageTitle')}</h1>
        <p className="body-secondary text-sm leading-6">{t('license.pageIntro')}</p>
      </header>

      <LicenseStatusCard />

      <p className="body-muted text-xs leading-5">{t('license.pageFootnote')}</p>

      <Link to="/" className="text-accent-strong inline-flex text-sm font-bold underline underline-offset-4">
        {t('license.back')}
      </Link>
    </div>
  );
}
