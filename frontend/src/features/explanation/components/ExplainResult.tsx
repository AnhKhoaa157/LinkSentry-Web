import { useState } from 'react';

import { Badge } from '@/components/ui/Badge';
import { postExplanation } from '@/features/explanation/api/postExplanation';
import type { ExplanationData } from '@/features/explanation/schemas/explanationResponse';
import type { RiskLevel, Severity } from '@/features/scanner/schemas/scanResponse';
import { normalizeApiError, type NormalizedApiError } from '@/lib/api/errors';
import { useLocale } from '@/lib/i18n/useLocale';

interface Props {
  /**
   * The owning retained scan's opaque ID, or `null` for a result with no
   * persisted scan (an anonymous scan never has one). `null` renders nothing —
   * this control is never shown for an anonymous result.
   */
  readonly scanId: string | null;
}

type Status = 'idle' | 'loading' | 'success' | 'error';

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
  const { t } = useLocale();
  const [status, setStatus] = useState<Status>('idle');
  const [data, setData] = useState<ExplanationData | null>(null);
  const [error, setError] = useState<NormalizedApiError | null>(null);

  if (scanId === null) {
    return null;
  }

  // Narrowed once into a stable local: `scanId`'s outer-scope type is still
  // `string | null`, and TypeScript does not carry the guard above into a
  // nested function declaration's closure.
  const ownedScanId: string = scanId;

  function handleExplain() {
    setStatus('loading');
    setError(null);
    void postExplanation(ownedScanId)
      .then((response) => {
        setData(response.data);
        setStatus('success');
      })
      .catch((caught: unknown) => {
        setError(normalizeApiError(caught));
        setStatus('error');
      });
  }

  return (
    <div className="border-ink-800 mt-5 border-t pt-4">
      {status === 'idle' || status === 'loading' ? (
        <button
          type="button"
          onClick={handleExplain}
          disabled={status === 'loading'}
          aria-busy={status === 'loading'}
          className="border-ink-700 text-ink-100 hover:bg-ink-800 rounded-lg border px-4 py-2 text-sm font-medium disabled:cursor-not-allowed disabled:opacity-50"
        >
          {status === 'loading' ? t('explain.button.loading') : t('explain.button.idle')}
        </button>
      ) : null}

      {status === 'loading' ? (
        <p role="status" className="text-ink-500 mt-2 text-sm">
          {t('explain.button.loading')}
        </p>
      ) : null}

      {status === 'error' && error ? (
        <div role="alert" className="mt-2">
          <p className="text-sm text-rose-400">{error.message}</p>
          <button
            type="button"
            onClick={handleExplain}
            className="text-accent-400 hover:text-accent-300 mt-1 text-sm font-medium underline underline-offset-4"
          >
            {t('explain.tryAgain')}
          </button>
        </div>
      ) : null}

      {status === 'success' && data ? (
        <div aria-live="polite" className="space-y-4">
          <Badge tone={RISK_TONE[data.riskLevel]}>
            {t('explain.riskBadgeLabel', { level: data.riskLevel })}
          </Badge>

          <section>
            <h3 className="text-ink-100 text-sm font-semibold">{t('explain.detectedHeading')}</h3>
            {data.keyFindings.length === 0 ? (
              <p className="text-ink-300 mt-2 text-sm">{t('explain.noSignals')}</p>
            ) : (
              <ul className="mt-2 space-y-2">
                {data.keyFindings.map((finding, index) => (
                  <li
                    key={`${finding.title}-${index}`}
                    className="border-ink-800 bg-ink-950/40 rounded-lg border p-3"
                  >
                    <div className="flex flex-wrap items-start justify-between gap-2">
                      <h4 className="text-ink-100 text-sm font-medium">{finding.title}</h4>
                      <div className="flex items-center gap-2">
                        <Badge tone={SEVERITY_TONE[finding.severity]}>{finding.severity}</Badge>
                        <span className="text-ink-500 font-mono text-xs">+{finding.points}</span>
                      </div>
                    </div>
                    <p className="text-ink-300 mt-1 text-sm">{finding.explanation}</p>
                  </li>
                ))}
              </ul>
            )}
          </section>

          <section>
            <p className="text-ink-500 text-xs font-medium tracking-wide uppercase">
              {t('explain.aiContextLabel')}
            </p>
            <p className="text-ink-100 mt-1 text-sm">{data.summary}</p>
          </section>

          <section>
            <h3 className="text-ink-100 text-sm font-semibold">{t('explain.whatToDoHeading')}</h3>
            <ul className="text-ink-300 mt-2 list-disc space-y-1 pl-5 text-sm">
              {data.recommendedActions.map((action, index) => (
                <li key={index}>{action}</li>
              ))}
            </ul>
          </section>

          <p className="text-ink-500 text-xs">{t('explain.disclaimer')}</p>
        </div>
      ) : null}
    </div>
  );
}
