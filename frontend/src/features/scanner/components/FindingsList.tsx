import { Badge } from '@/components/ui/Badge';
import type { Finding, Severity } from '@/features/scanner/schemas/scanResponse';

const SEVERITY_TONE: Record<Severity, 'low' | 'moderate' | 'high' | 'critical'> = {
  INFO: 'low',
  LOW: 'low',
  MEDIUM: 'moderate',
  HIGH: 'high',
};

interface Props {
  readonly findings: readonly Finding[];
}

/**
 * Lists every finding that fired, in the order the API already sorted them.
 *
 * An empty list is rendered as its own state, worded to avoid ever reading as
 * "safe" — the absence of a detected signal is not the presence of safety.
 */
export function FindingsList({ findings }: Props) {
  if (findings.length === 0) {
    return (
      <p className="text-ink-300 text-sm">
        No signals were detected by the current rules. This does not mean the link is safe.
      </p>
    );
  }

  return (
    <ul className="space-y-3">
      {findings.map((finding) => (
        <li key={finding.ruleId} className="border-ink-800 bg-ink-950/40 rounded-lg border p-4">
          <div className="flex flex-wrap items-start justify-between gap-2">
            <h3 className="text-ink-100 text-sm font-semibold">{finding.title}</h3>
            <div className="flex items-center gap-2">
              <Badge tone={SEVERITY_TONE[finding.severity]}>{finding.severity}</Badge>
              <span className="text-ink-500 font-mono text-xs">+{finding.points}</span>
            </div>
          </div>
          <p className="text-ink-300 mt-2 text-sm">{finding.explanation}</p>
          {finding.evidence ? (
            <p className="text-ink-500 mt-2 font-mono text-xs break-all">{finding.evidence}</p>
          ) : null}
        </li>
      ))}
    </ul>
  );
}
