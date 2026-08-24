import { useState } from 'react';

import { DEFAULT_SCAN_INSIGHTS_WINDOW_DAYS } from '@/features/history/api/getScanInsights';
import { useScanInsightsQuery } from '@/features/history/api/useScanInsightsQuery';
import {
  scanInsightsWindowDaysSchema,
  type ScanInsightsWindowDays,
  type ScanInsightsResponse,
} from '@/features/history/schemas/scanHistoryResponse';
import type { RiskLevel } from '@/features/scanner/schemas/scanResponse';
import type { Translate } from '@/lib/i18n/LocaleContext';
import type { TranslationKey } from '@/lib/i18n/translations';
import { useLocale } from '@/lib/i18n/useLocale';

const RISK_LEVELS: readonly RiskLevel[] = ['LOW', 'MODERATE', 'HIGH', 'CRITICAL'];

const RISK_LABEL_KEYS: Record<RiskLevel, TranslationKey> = {
  LOW: 'riskBadge.low',
  MODERATE: 'riskBadge.moderate',
  HIGH: 'riskBadge.high',
  CRITICAL: 'riskBadge.critical',
};

/** Private aggregate insight area; it never invents counts or finding copy. */
export function PersonalInsights() {
  const { t } = useLocale();
  const [windowDays, setWindowDays] = useState<ScanInsightsWindowDays>(DEFAULT_SCAN_INSIGHTS_WINDOW_DAYS);
  const query = useScanInsightsQuery(windowDays);
  const data = query.data?.data;

  return (
    <section className="space-y-5" aria-labelledby="history-insights-heading">
      <header className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="eyebrow">{t('history.insights.eyebrow')}</p>
          <h2 id="history-insights-heading" className="section-title text-content-primary">
            {t('history.insights.title')}
          </h2>
          <p className="body-secondary mt-2 max-w-2xl text-sm leading-6">
            {t('history.insights.description')}
          </p>
        </div>
        <label className="w-full space-y-2 sm:w-auto sm:min-w-48">
          <span className="ui-label">{t('history.insights.windowLabel')}</span>
          <select
            className="ui-input min-h-11 px-3 text-sm"
            value={windowDays}
            onChange={(event) => {
              const parsed = scanInsightsWindowDaysSchema.safeParse(Number(event.target.value));
              if (parsed.success) {
                setWindowDays(parsed.data);
              }
            }}
          >
            <option value={7}>{t('history.insights.window7')}</option>
            <option value={30}>{t('history.insights.window30')}</option>
          </select>
        </label>
      </header>

      {query.isPending ? <InsightsLoading t={t} /> : null}

      {query.isError && data === undefined ? (
        <InsightsError isRetrying={query.isFetching} onRetry={() => void query.refetch()} t={t} />
      ) : null}

      {query.isError && data !== undefined ? (
        <div role="alert" className="ui-alert p-4 text-sm" aria-live="assertive">
          <p className="font-bold">{t('history.insights.refreshErrorTitle')}</p>
          <p className="mt-1">{t('history.insights.refreshErrorBody')}</p>
          <button
            type="button"
            className="ui-button ui-button-secondary mt-4"
            onClick={() => void query.refetch()}
            disabled={query.isFetching}
          >
            {query.isFetching ? t('common.retrying') : t('common.retry')}
          </button>
        </div>
      ) : null}

      {data?.totalScans === 0 ? <InsightsNoData t={t} /> : null}
      {data !== undefined && query.data !== undefined && data.totalScans > 0 ? (
        <InsightsContent data={data} retentionDays={query.data.meta.retentionDays} t={t} />
      ) : null}
    </section>
  );
}

function InsightsLoading({ t }: { readonly t: Translate }) {
  return (
    <p role="status" aria-live="polite" className="surface-inset body-secondary p-5 text-sm">
      {t('history.insights.loading')}
    </p>
  );
}

function InsightsNoData({ t }: { readonly t: Translate }) {
  return (
    <div role="status" className="surface-panel p-6 sm:p-8">
      <h3 className="section-title text-content-primary">{t('history.insights.noDataTitle')}</h3>
      <p className="body-secondary mt-3 max-w-2xl text-sm leading-6">{t('history.insights.noDataBody')}</p>
    </div>
  );
}

function InsightsError({
  isRetrying,
  onRetry,
  t,
}: {
  readonly isRetrying: boolean;
  readonly onRetry: () => void;
  readonly t: Translate;
}) {
  return (
    <div role="alert" className="ui-alert p-5 text-sm" aria-live="assertive">
      <h3 className="text-base font-bold">{t('history.insights.errorTitle')}</h3>
      <p className="mt-2 leading-6">{t('history.insights.errorBody')}</p>
      <button
        type="button"
        className="ui-button ui-button-secondary mt-4"
        onClick={onRetry}
        disabled={isRetrying}
      >
        {isRetrying ? t('common.retrying') : t('common.retry')}
      </button>
    </div>
  );
}

function InsightsContent({
  data,
  retentionDays,
  t,
}: {
  readonly data: ScanInsightsResponse['data'];
  readonly retentionDays: number;
  readonly t: Translate;
}) {
  return (
    <div className="space-y-6">
      <section aria-labelledby="history-insights-summary-heading">
        <h3 id="history-insights-summary-heading" className="sr-only">
          {t('history.insights.summaryHeading')}
        </h3>
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <InsightCard label={t('history.insights.totalLabel')} value={data.totalScans} />
          <InsightCard label={t('history.insights.last7Label')} value={data.scansLast7Days} />
          <InsightCard label={t('history.insights.previous7Label')} value={data.previous7Days} />
          <InsightCard
            label={t('history.insights.retentionLabel')}
            value={t('history.insights.daysValue', { days: retentionDays })}
          />
        </div>
      </section>

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1.1fr)_minmax(18rem,0.9fr)]">
        <section className="surface-panel p-5 sm:p-6" aria-labelledby="history-insights-risk-heading">
          <h3 id="history-insights-risk-heading" className="text-content-primary text-base font-bold">
            {t('history.insights.riskHeading')}
          </h3>
          <p className="body-secondary mt-1 text-sm">{t('history.insights.riskDescription')}</p>
          <ul className="mt-5 space-y-4">
            {RISK_LEVELS.map((riskLevel) => {
              const count = data.riskDistribution[riskLevel];
              const percent = data.totalScans === 0 ? 0 : Math.round((count / data.totalScans) * 100);
              return (
                <li key={riskLevel}>
                  <div className="flex items-center justify-between gap-3 text-sm">
                    <span className="text-content-primary font-semibold">
                      {t(RISK_LABEL_KEYS[riskLevel])}
                    </span>
                    <span className="text-content-secondary tabular-nums">
                      {t('history.insights.riskCount', { count, percent })}
                    </span>
                  </div>
                  <div
                    className="insights-risk-track mt-2"
                    role="img"
                    aria-label={t('history.insights.riskCount', { count, percent })}
                  >
                    <span
                      className={`insights-risk-fill insights-risk-fill--${riskLevel.toLowerCase()}`}
                      style={{ width: `${percent}%` }}
                    />
                  </div>
                </li>
              );
            })}
          </ul>
        </section>

        <section className="surface-panel p-5 sm:p-6" aria-labelledby="history-insights-top-heading">
          <h3 id="history-insights-top-heading" className="text-content-primary text-base font-bold">
            {t('history.insights.topHeading')}
          </h3>
          <p className="body-secondary mt-1 text-sm">{t('history.insights.topDescription')}</p>
          {data.topFindings.length === 0 ? (
            <p role="status" className="body-secondary mt-5 text-sm leading-6">
              {t('history.insights.noFindings')}
            </p>
          ) : (
            <ol className="mt-5 space-y-3">
              {data.topFindings.map((finding) => (
                <li
                  key={finding.ruleId}
                  className="surface-inset flex items-start justify-between gap-3 p-3 text-sm"
                >
                  <div className="min-w-0">
                    <p className="text-content-primary font-semibold">{finding.title}</p>
                    <p className="text-content-muted mt-1 font-mono text-xs break-all">{finding.ruleId}</p>
                  </div>
                  <span className="text-accent-strong shrink-0 font-bold tabular-nums">
                    {t(
                      finding.count === 1
                        ? 'history.insights.findingCount.one'
                        : 'history.insights.findingCount.many',
                      {
                        count: finding.count,
                      },
                    )}
                  </span>
                </li>
              ))}
            </ol>
          )}
        </section>
      </div>
    </div>
  );
}

function InsightCard({ label, value }: { readonly label: string; readonly value: number | string }) {
  return (
    <div className="surface-inset p-4">
      <p className="body-muted text-xs font-bold tracking-wide uppercase">{label}</p>
      <p className="text-content-primary mt-2 text-2xl font-bold tabular-nums">{value}</p>
    </div>
  );
}
