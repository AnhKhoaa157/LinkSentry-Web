import { DomainBreakdown } from '@/features/scanner/components/DomainBreakdown';
import { FindingsList } from '@/features/scanner/components/FindingsList';
import { NextSteps } from '@/features/scanner/components/NextSteps';
import { RiskBadge } from '@/features/scanner/components/RiskBadge';
import type { ScanData } from '@/features/scanner/schemas/scanResponse';
import { useLocale } from '@/lib/i18n/useLocale';

interface Props {
  readonly data: ScanData;
}

/**
 * A result hierarchy that puts the backend-owned risk state first, then shows
 * evidence and safe actions. The submitted value remains a plain text node.
 */
export function ScanResult({ data }: Props) {
  const { locale, t } = useLocale();
  const analyzedDate = new Intl.DateTimeFormat(locale === 'vi' ? 'vi-VN' : 'en-US', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(data.analyzedAt));

  return (
    <section className="scan-result mt-7 space-y-5" aria-live="polite" aria-labelledby="scan-result-heading">
      <div className="scan-result__score scanner-panel__score grid gap-5 rounded-[var(--radius-panel)] border p-5 sm:grid-cols-[1fr_auto] sm:items-center sm:p-7">
        <div>
          <p className="eyebrow">{t('scanner.scoreLabel')}</p>
          <div className="mt-2 flex flex-wrap items-end gap-3">
            <h2
              id="scan-result-heading"
              className="text-content-primary text-5xl font-bold tracking-[-0.06em]"
            >
              {data.score}
              <span className="text-content-muted ml-1 text-base font-semibold tracking-normal">
                {t('scanner.scoreOutOf')}
              </span>
            </h2>
            <RiskBadge riskLevel={data.riskLevel} />
          </div>
          <p className="body-secondary mt-3 max-w-xl text-sm leading-6">{t('scanner.resultNote')}</p>
        </div>
        <div className="sm:text-right">
          <p className="body-muted text-xs">{t('scanner.analyzedAt', { date: analyzedDate })}</p>
          <p className="body-muted mt-1 text-xs">{t('scanner.serverOwned')}</p>
        </div>
      </div>

      <div className="surface-inset p-4 sm:p-5">
        <p className="eyebrow">{t('scanner.analyzedLink')}</p>
        <p className="text-content-primary mt-2 font-mono text-sm leading-6 break-all">{data.input}</p>
      </div>

      <div className="grid gap-5 lg:grid-cols-[minmax(0,0.85fr)_minmax(0,1.15fr)]">
        <DomainBreakdown normalized={data.normalized} />

        <section aria-labelledby="findings-heading">
          <div className="flex flex-wrap items-end justify-between gap-2">
            <div>
              <p className="eyebrow">{t('scanner.resultHeading')}</p>
              <h3 id="findings-heading" className="text-content-primary mt-1 text-lg font-bold">
                {t('scanner.findingsHeading')}
              </h3>
            </div>
            <span className="body-muted text-xs">
              {t('scanner.findingsCount', { count: data.findings.length })}
            </span>
          </div>
          <div className="mt-3">
            <FindingsList findings={data.findings} />
          </div>
        </section>
      </div>

      <NextSteps
        riskLevel={data.riskLevel}
        score={data.score}
        registrableDomain={data.normalized.registrableDomain}
        findings={data.findings}
      />

      <p className="body-muted text-xs leading-5">{t('scanner.limitBody')}</p>
    </section>
  );
}
