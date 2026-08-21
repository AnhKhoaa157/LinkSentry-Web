import { useMemo, useState } from 'react';

import type { Finding, RiskLevel } from '@/features/scanner/schemas/scanResponse';
import type { Translate } from '@/lib/i18n/LocaleContext';
import type { TranslationKey } from '@/lib/i18n/translations';
import { useLocale } from '@/lib/i18n/useLocale';

/**
 * What a non-expert should actually do, chosen only by the server's risk level.
 *
 * The frontend never re-derives risk (docs/ARCHITECTURE.md §1), so this is a
 * lookup and nothing more: no score thresholds, no finding inspection, no
 * second opinion about the link.
 */
const RECOMMENDED_ACTION_KEY: Record<RiskLevel, TranslationKey> = {
  LOW: 'nextSteps.action.low',
  MODERATE: 'nextSteps.action.moderate',
  HIGH: 'nextSteps.action.high',
  CRITICAL: 'nextSteps.action.critical',
};

const RISK_LEVEL_WORD_KEY: Record<RiskLevel, TranslationKey> = {
  LOW: 'nextSteps.summary.riskLevelWord.low',
  MODERATE: 'nextSteps.summary.riskLevelWord.moderate',
  HIGH: 'nextSteps.summary.riskLevelWord.high',
  CRITICAL: 'nextSteps.summary.riskLevelWord.critical',
};

const HEADING_ID = 'next-steps-heading';

interface SummaryInput {
  readonly riskLevel: RiskLevel;
  readonly score: number;
  readonly registrableDomain: string | null;
  readonly findings: readonly Finding[];
}

/**
 * Builds the plain text placed on the clipboard.
 *
 * The allow-list is the point: risk level, score, registrable domain, finding
 * titles in the server's order, the matching action, and the caveat. The
 * submitted URL, its path, port, query, fragment, any credentials, and the scan
 * ID are all absent — and cannot be present, because this module is never given
 * them. A user pasting this into a ticket or an email must not be the reason a
 * session token leaves the browser (docs/SECURITY_BOUNDARY.md §2, §5).
 *
 * When no registrable domain was resolved (an IP literal, for example) the host
 * is *not* substituted: the host is not on the allow-list.
 */
function buildSafeSummary(
  t: Translate,
  { riskLevel, score, registrableDomain, findings }: SummaryInput,
): string {
  const lines = [
    t('nextSteps.summary.title'),
    t('nextSteps.summary.riskLevel', { level: t(RISK_LEVEL_WORD_KEY[riskLevel]), score }),
    t('nextSteps.summary.registeredDomain', {
      domain: registrableDomain ?? t('nextSteps.summary.domainUnknown'),
    }),
  ];

  if (findings.length === 0) {
    lines.push(t('nextSteps.summary.findingsNone'));
  } else {
    lines.push(t('nextSteps.summary.findingsHeading'));
    for (const finding of findings) {
      lines.push(`- ${finding.title}`);
    }
  }

  lines.push(
    t('nextSteps.summary.recommendedAction', { action: t(RECOMMENDED_ACTION_KEY[riskLevel]) }),
    t('nextSteps.summary.safetyNote'),
  );

  return lines.join('\n');
}

type CopyStatus = 'idle' | 'copied' | 'failed';

interface Props {
  readonly riskLevel: RiskLevel;
  readonly score: number;
  readonly registrableDomain: string | null;
  readonly findings: readonly Finding[];
}

/**
 * Guidance card shown after the findings.
 *
 * Props are deliberately narrow rather than the whole `ScanData`: this component
 * is the one that writes text to the clipboard, so the values it must never
 * disclose are values it is never handed.
 */
export function NextSteps({ riskLevel, score, registrableDomain, findings }: Props) {
  const { t } = useLocale();
  const [status, setStatus] = useState<CopyStatus>('idle');

  const summary = useMemo(
    () => buildSafeSummary(t, { riskLevel, score, registrableDomain, findings }),
    [t, riskLevel, score, registrableDomain, findings],
  );

  // A new scan can reuse this component instance, so a stale "Summary copied."
  // would otherwise sit under a result the user has not copied. React's
  // documented adjust-state-during-render pattern, not an effect: it re-renders
  // before the browser paints, so the stale message is never visible.
  const [summarySnapshot, setSummarySnapshot] = useState(summary);
  if (summary !== summarySnapshot) {
    setSummarySnapshot(summary);
    setStatus('idle');
  }

  async function handleCopy() {
    try {
      await navigator.clipboard.writeText(summary);
      setStatus('copied');
    } catch {
      // No retry and no textarea fallback: a silent second attempt tells the
      // user nothing, and a DOM fallback is a place for text to leak.
      setStatus('failed');
    }
  }

  return (
    <section aria-labelledby={HEADING_ID} className="border-ink-800 bg-ink-950/40 rounded-lg border p-4">
      <h3 id={HEADING_ID} className="text-ink-100 text-sm font-semibold">
        {t('nextSteps.heading')}
      </h3>
      <p className="text-ink-300 mt-2 text-sm">{t(RECOMMENDED_ACTION_KEY[riskLevel])}</p>

      <div className="mt-4 flex flex-wrap items-center gap-x-3 gap-y-2">
        <button
          type="button"
          onClick={handleCopy}
          className="border-ink-700 bg-ink-850 text-ink-100 hover:bg-ink-800 rounded-lg border px-4 py-2 text-sm font-medium transition-colors"
        >
          {t('nextSteps.copyButton')}
        </button>

        {/* Always mounted so the message is announced when its text changes, and
            polite because a failed copy is an inconvenience, not an emergency. */}
        <p role="status" className={status === 'failed' ? 'text-sm text-rose-400' : 'text-ink-300 text-sm'}>
          {status === 'copied'
            ? t('nextSteps.copySuccess')
            : status === 'failed'
              ? t('nextSteps.copyFailure')
              : ''}
        </p>
      </div>

      <p className="text-ink-500 mt-3 text-xs">{t('nextSteps.disclaimer')}</p>
    </section>
  );
}
