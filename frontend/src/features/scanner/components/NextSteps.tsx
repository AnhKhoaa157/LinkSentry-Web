import { useMemo, useState } from 'react';

import type { Finding, RiskLevel } from '@/features/scanner/schemas/scanResponse';

/**
 * What a non-expert should actually do, chosen only by the server's risk level.
 *
 * The frontend never re-derives risk (docs/ARCHITECTURE.md §1), so this is a
 * lookup and nothing more: no score thresholds, no finding inspection, no
 * second opinion about the link.
 */
const RECOMMENDED_ACTION: Record<RiskLevel, string> = {
  LOW: 'No strong lexical risk signals were detected. Still verify the sender and use official channels before entering information.',
  MODERATE:
    'Review the registered domain carefully. If the link arrived unexpectedly, open the official website yourself instead of using the link.',
  HIGH: 'Avoid opening the link or entering credentials. Verify the request through the organization’s official app, website, or support channel.',
  CRITICAL:
    'Do not open, sign in to, download from, or forward the link. Report it to your organization’s security or IT team.',
};

const RISK_LEVEL_TEXT: Record<RiskLevel, string> = {
  LOW: 'Low',
  MODERATE: 'Moderate',
  HIGH: 'High',
  CRITICAL: 'Critical',
};

const SAFETY_NOTE =
  'Note: Lexical analysis inspects only the text of a link. It cannot prove that a destination is safe.';

const COPY_SUCCESS = 'Summary copied.';
const COPY_FAILURE = 'Could not copy the summary. Your browser blocked clipboard access.';

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
function buildSafeSummary({ riskLevel, score, registrableDomain, findings }: SummaryInput): string {
  const lines = [
    'LinkSentry link analysis',
    `Risk level: ${RISK_LEVEL_TEXT[riskLevel]} (score ${score}/100)`,
    `Registered domain: ${registrableDomain ?? 'not determined'}`,
  ];

  if (findings.length === 0) {
    lines.push('Findings: none detected');
  } else {
    lines.push('Findings:');
    for (const finding of findings) {
      lines.push(`- ${finding.title}`);
    }
  }

  lines.push(`Recommended action: ${RECOMMENDED_ACTION[riskLevel]}`, SAFETY_NOTE);

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
  const [status, setStatus] = useState<CopyStatus>('idle');

  const summary = useMemo(
    () => buildSafeSummary({ riskLevel, score, registrableDomain, findings }),
    [riskLevel, score, registrableDomain, findings],
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
        Recommended next steps
      </h3>
      <p className="text-ink-300 mt-2 text-sm">{RECOMMENDED_ACTION[riskLevel]}</p>

      <div className="mt-4 flex flex-wrap items-center gap-x-3 gap-y-2">
        <button
          type="button"
          onClick={handleCopy}
          className="border-ink-700 bg-ink-850 text-ink-100 hover:bg-ink-800 rounded-lg border px-4 py-2 text-sm font-medium transition-colors"
        >
          Copy safe summary
        </button>

        {/* Always mounted so the message is announced when its text changes, and
            polite because a failed copy is an inconvenience, not an emergency. */}
        <p role="status" className={status === 'failed' ? 'text-sm text-rose-400' : 'text-ink-300 text-sm'}>
          {status === 'copied' ? COPY_SUCCESS : status === 'failed' ? COPY_FAILURE : ''}
        </p>
      </div>

      <p className="text-ink-500 mt-3 text-xs">
        The summary contains the risk level, score, registered domain, and finding titles. It never includes
        the submitted link.
      </p>
    </section>
  );
}
