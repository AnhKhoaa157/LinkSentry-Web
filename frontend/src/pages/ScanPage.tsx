import type { ReactNode } from 'react';
import { Link, useParams } from 'react-router';

import { ExplainResult } from '@/features/explanation/components/ExplainResult';
import { useScanQuery } from '@/features/scanner/api/useScanQuery';
import { ScanResult } from '@/features/scanner/components/ScanResult';
import { normalizeApiError } from '@/lib/api/errors';
import { useLocale } from '@/lib/i18n/useLocale';

/** Renders a retained result loaded from an opaque scan permalink. */
export function ScanPage() {
  const { scanId } = useParams<{ scanId: string }>();
  const { locale, t } = useLocale();
  const query = useScanQuery(scanId, locale);

  if (query.isPending) {
    return (
      <p role="status" className="body-secondary text-sm">
        {t('scanPage.loading')}
      </p>
    );
  }

  if (query.isError) {
    const apiError = normalizeApiError(query.error);
    if (apiError.code === 'UNAUTHORIZED') {
      return (
        <StateMessage title={t('scanPage.licenseRequiredTitle')}>
          {t('scanPage.licenseRequiredBody')}
          <br />
          <Link to="/license" className="text-accent-strong font-bold underline underline-offset-4">
            {t('scanPage.licenseLink')}
          </Link>
        </StateMessage>
      );
    }
    if (apiError.code === 'SCAN_NOT_FOUND') {
      return (
        <StateMessage title={t('scanPage.unavailableTitle')}>{t('scanPage.unavailableBody')}</StateMessage>
      );
    }
    if (apiError.code === 'RATE_LIMITED') {
      return <StateMessage title={t('scanPage.rateTitle')}>{t('scanPage.rateBody')}</StateMessage>;
    }
    return <StateMessage title={t('scanPage.errorTitle')}>{t('scanPage.errorBody')}</StateMessage>;
  }

  if (!query.data) {
    return (
      <StateMessage title={t('scanPage.unavailableTitle')}>{t('scanPage.unavailableBody')}</StateMessage>
    );
  }

  return (
    <div className="mx-auto max-w-4xl">
      <header className="page-intro space-y-4">
        <p className="eyebrow">{t('scanPage.eyebrow')}</p>
        <h1 className="display-title text-content-primary text-4xl sm:text-5xl">{t('scanPage.title')}</h1>
        <p className="body-secondary text-sm leading-6">{t('scanPage.description')}</p>
      </header>

      <ScanResult data={query.data.data} />
      <ExplainResult scanId={query.data.data.scanId} />
    </div>
  );
}

function StateMessage({ title, children }: { readonly title: string; readonly children: ReactNode }) {
  const { t } = useLocale();

  return (
    <div role="alert" className="surface-panel max-w-2xl p-6 sm:p-8">
      <p className="eyebrow">{t('scanPage.eyebrow')}</p>
      <h1 className="section-title text-content-primary mt-2">{title}</h1>
      <p className="body-secondary mt-3 text-sm leading-6">{children}</p>
      <Link to="/" className="ui-button ui-button-primary mt-7">
        {t('common.backToScanner')}
      </Link>
    </div>
  );
}
