import { useState } from 'react';

import { Badge } from '@/components/ui/Badge';
import { useExplanationQuery } from '@/features/explanation/api/useExplanationQuery';
import { useLicense } from '@/features/license/context/useLicense';
import type { RiskLevel, Severity } from '@/features/scanner/schemas/scanResponse';
import { normalizeApiError } from '@/lib/api/errors';
import { localizeApiError } from '@/lib/i18n/messages';
import { useLocale } from '@/lib/i18n/useLocale';

interface Props {
  /**
   * The owning retained scan's opaque ID, or `null` for a result with no
   * persisted scan (an anonymous scan never has one). `null` renders nothing —
   * this control is never shown for an anonymous result.
   */
  readonly scanId: string | null;
}

const RISK_TONE: Record<RiskLevel, 'low' | 'moderate' | 'high' | 'critical'> = {
  LOW: 'low',
  MODERATE: 'moderate',
  HIGH: 'high',
  CRITICAL: 'critical',
};

const SEVERITY_TONE: Record<Severity, 'low' | 'moderate' | 'high' | 'critical'> = {
  INFO: 'low',
  LOW: 'low',
  MEDIUM: 'moderate',
  HIGH: 'high',
};

/**
 * "Explain this result" — an optional, advisory AI explanation of one owned,
 * retained scan result.
 *
 * <p>The backend, not the AI, owns `riskLevel` and `keyFindings`: both are
 * assembled deterministically from the already-computed scan result before
 * this control ever renders, and this request cannot change either. Only
 * `summary` and `recommendedActions` are AI-produced, and every AI-produced
 * value is rendered as inert plain text through ordinary React text nodes —
 * never `dangerouslySetInnerHTML`, never parsed as Markdown or HTML, and never
 * turned into a link — because model output is not trusted content, the same
 * rule the redacted scan URL already follows. See `docs/SECURITY_BOUNDARY.md`
 * and `docs/adr/0005-deepseek-scan-explanation-integration.md`.
 */
export function ExplainResult({ scanId }: Props) {
  const { locale, t } = useLocale();
  const { isLicensed } = useLicense();
  const [requested, setRequested] = useState(false);

  const query = useExplanationQuery({ scanId, locale, requested, isLicensed });

  if (scanId === null) {
    return null;
  }

  function handleExplain() {
    setRequested(true);
  }

  function handleRetry() {
    if (!isLicensed) {
      // Defensive: the "Try again" control only ever renders while isError is
      // true, which already requires isLicensed — but refetch() ignores the
      // query's own `enabled` option, so nothing else stops an in-flight call
      // from reaching the backend if this were ever invoked in a race where
      // license was just lost. An explicit retry never bypasses that.
      return;
    }
    // An explicit retry after a failure always calls the backend again —
    // there is no valid cached success to preserve in that state, and
    // `refetch` (unlike enabling the query) never reuses a stale result.
    void query.refetch();
  }

  const isLoading = requested && query.isFetching;
  // isLicensed gates both branches below directly, at render time, so a
  // cached advisory or a stale error/retry state can never appear even in the
  // single render right after a LICENSED → non-LICENSED transition — this
  // does not depend on useExplanationQuery's own purge effect having run yet.
  const isError = requested && query.isError && !isLoading && isLicensed;
  const isSuccess = requested && query.isSuccess && !isLoading && isLicensed;
  const data = query.data?.data ?? null;
  const error = query.error ? normalizeApiError(query.error) : null;

  return (
    <div className="border-border-default mt-7 border-t pt-6">
      {!requested || isLoading ? (
        <button
          type="button"
          onClick={handleExplain}
          disabled={isLoading}
          aria-busy={isLoading}
          className="ui-button ui-button-secondary disabled:opacity-50"
        >
          {isLoading ? t('explain.button.loading') : t('explain.button.idle')}
        </button>
      ) : null}

      {isLoading ? (
        <p role="status" className="body-muted mt-2 text-sm">
          {t('explain.button.loading')}
        </p>
      ) : null}

      {isError && error ? (
        <div role="alert" className="mt-2">
          <p className="text-danger text-sm">{localizeApiError(t, error)}</p>
          <button
            type="button"
            onClick={handleRetry}
            className="text-accent-strong mt-2 text-sm font-bold underline underline-offset-4"
          >
            {t('explain.tryAgain')}
          </button>
        </div>
      ) : null}

      {isSuccess && data ? (
        <div aria-live="polite" className="space-y-4">
          <Badge tone={RISK_TONE[data.riskLevel]}>
            {t('explain.riskBadgeLabel', { level: data.riskLevel })}
          </Badge>

          <section className="surface-inset p-4">
            <h3 className="text-content-primary text-sm font-bold">{t('explain.detectedHeading')}</h3>
            {data.keyFindings.length === 0 ? (
              <p className="body-secondary mt-2 text-sm">{t('explain.noSignals')}</p>
            ) : (
              <ul className="mt-2 space-y-2">
                {data.keyFindings.map((finding, index) => (
                  <li
                    key={`${finding.title}-${index}`}
                    className="border-border-default bg-surface-panel rounded-lg border p-3"
                  >
                    <div className="flex flex-wrap items-start justify-between gap-2">
                      <h4 className="text-content-primary text-sm font-bold">{finding.title}</h4>
                      <div className="flex items-center gap-2">
                        <Badge tone={SEVERITY_TONE[finding.severity]}>{finding.severity}</Badge>
                        <span className="body-muted font-mono text-xs">+{finding.points}</span>
                      </div>
                    </div>
                    <p className="body-secondary mt-1 text-sm">{finding.explanation}</p>
                  </li>
                ))}
              </ul>
            )}
          </section>

          <section className="surface-panel p-4">
            <p className="body-muted text-xs font-bold tracking-wide uppercase">
              {t('explain.aiContextLabel')}
            </p>
            <p className="text-content-primary mt-2 text-sm leading-6">{data.summary}</p>
          </section>

          <section>
            <h3 className="text-content-primary text-sm font-bold">{t('explain.whatToDoHeading')}</h3>
            <ul className="body-secondary mt-2 list-disc space-y-1 pl-5 text-sm">
              {data.recommendedActions.map((action, index) => (
                <li key={index}>{action}</li>
              ))}
            </ul>
          </section>

          <p className="body-muted text-xs">{t('explain.disclaimer')}</p>
        </div>
      ) : null}
    </div>
  );
}
