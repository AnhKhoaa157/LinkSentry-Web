import { useState, type ReactNode } from 'react';

import { HistoryList } from '@/features/history/components/HistoryList';
import { HistoryFilters } from '@/features/history/components/HistoryFilters';
import { PersonalInsights } from '@/features/history/components/PersonalInsights';
import { useScanHistoryQuery } from '@/features/history/api/useScanHistoryQuery';
import {
  DEFAULT_SCAN_HISTORY_FILTERS,
  type ScanHistoryFilters,
} from '@/features/history/schemas/scanHistoryResponse';
import { LicenseStatusCard } from '@/features/license/components/LicenseStatusCard';
import { useLicense } from '@/features/license/context/useLicense';
import { normalizeApiError } from '@/lib/api/errors';
import type { Translate } from '@/lib/i18n/LocaleContext';
import { useLocale } from '@/lib/i18n/useLocale';

/** Private, licensed scan-history route. */
export function HistoryPage() {
  const { isLicensed, isLoading, state } = useLicense();
  const { t } = useLocale();

  return (
    <div className="mx-auto w-full max-w-6xl space-y-8">
      <header className="page-intro space-y-4">
        <p className="eyebrow">{t('history.eyebrow')}</p>
        <h1 className="display-title text-content-primary text-4xl sm:text-5xl">{t('history.title')}</h1>
        <p className="body-secondary text-sm leading-6 sm:text-base">{t('history.description')}</p>
      </header>

      {isLicensed ? (
        <LicensedHistory />
      ) : (
        <section className="max-w-2xl space-y-4" aria-labelledby="history-license-heading">
          <div>
            <h2 id="history-license-heading" className="section-title text-content-primary">
              {isLoading || state === null
                ? t('history.licenseCheckingTitle')
                : t('history.licenseRequiredTitle')}
            </h2>
            <p className="body-secondary mt-2 text-sm leading-6">
              {isLoading || state === null
                ? t('history.licenseCheckingBody')
                : t('history.licenseRequiredBody')}
            </p>
          </div>
          <LicenseStatusCard />
        </section>
      )}
    </div>
  );
}

/** Mounted only after LicenseProvider reports LICENSED, so the query cannot run before then. */
function LicensedHistory() {
  const [filters, setFilters] = useState<ScanHistoryFilters>(DEFAULT_SCAN_HISTORY_FILTERS);
  const query = useScanHistoryQuery(filters);
  const { t } = useLocale();

  return (
    <div className="space-y-8">
      <HistoryFilters filters={filters} onChange={setFilters} t={t} />

      {query.isPending ? (
        <p role="status" aria-live="polite" className="surface-inset body-secondary p-5 text-sm">
          {t('history.loading')}
        </p>
      ) : null}

      {query.isError && query.data === undefined ? (
        <HistoryError title={historyErrorTitle(t, query.error)}>
          <p>{historyErrorBody(t, query.error)}</p>
          <button
            type="button"
            onClick={() => void query.refetch()}
            disabled={query.isFetching}
            className="ui-button ui-button-secondary mt-5"
          >
            {query.isFetching ? t('common.retrying') : t('common.retry')}
          </button>
        </HistoryError>
      ) : null}

      {query.data !== undefined ? <HistoryResults query={query} t={t} /> : null}

      <PersonalInsights />
    </div>
  );
}

function HistoryResults({
  query,
  t,
}: {
  readonly query: ReturnType<typeof useScanHistoryQuery>;
  readonly t: Translate;
}) {
  const pages = query.data?.pages ?? [];
  const items = pages.flatMap((page) => page.data.items);
  const retentionDays = pages[0]?.meta.retentionDays;

  if (items.length === 0) {
    return (
      <div role="status" className="surface-panel max-w-2xl p-6 sm:p-8">
        <h2 className="section-title text-content-primary">{t('history.emptyTitle')}</h2>
        <p className="body-secondary mt-3 text-sm leading-6">{t('history.emptyBody')}</p>
      </div>
    );
  }

  return (
    <section className="space-y-5" aria-labelledby="history-results-heading">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 id="history-results-heading" className="section-title text-content-primary">
            {t('history.resultsTitle')}
          </h2>
          {retentionDays ? (
            <p className="body-muted mt-1 text-xs leading-5">
              {t('history.retention', { days: retentionDays })}
            </p>
          ) : null}
        </div>
        <p className="body-muted text-xs">{t('history.backendOwned')}</p>
      </div>

      <HistoryList items={items} />

      <div className="flex flex-col items-start gap-3">
        {query.isFetchNextPageError ? (
          <div role="alert" className="ui-alert w-full max-w-2xl p-4 text-sm" aria-live="assertive">
            <p className="font-bold">{t('history.nextPageErrorTitle')}</p>
            <p className="mt-1">{t('history.nextPageErrorBody')}</p>
          </div>
        ) : null}

        {query.hasNextPage ? (
          <button
            type="button"
            onClick={() => void query.fetchNextPage()}
            disabled={query.isFetchingNextPage}
            className="ui-button ui-button-secondary"
          >
            {query.isFetchingNextPage ? t('history.loadingMore') : t('history.loadMore')}
          </button>
        ) : (
          <p role="status" className="body-muted text-sm">
            {t('history.finalPage')}
          </p>
        )}
      </div>
    </section>
  );
}

function HistoryError({ title, children }: { readonly title: string; readonly children: ReactNode }) {
  return (
    <div role="alert" className="ui-alert max-w-2xl p-5 text-sm" aria-live="assertive">
      <h2 className="text-base font-bold">{title}</h2>
      <div className="mt-2 leading-6">{children}</div>
    </div>
  );
}

function historyErrorTitle(t: Translate, error: unknown): string {
  const code = normalizeApiError(error).code;
  if (code === 'RATE_LIMITED') {
    return t('history.rateErrorTitle');
  }
  if (code === 'UNAUTHORIZED' || code === 'FORBIDDEN') {
    return t('history.accessErrorTitle');
  }
  return t('history.errorTitle');
}

function historyErrorBody(t: Translate, error: unknown): string {
  const code = normalizeApiError(error).code;
  if (code === 'RATE_LIMITED') {
    return t('history.rateErrorBody');
  }
  if (code === 'UNAUTHORIZED' || code === 'FORBIDDEN') {
    return t('history.accessErrorBody');
  }
  return t('history.errorBody');
}
