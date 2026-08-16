import { DomainBreakdown } from '@/features/scanner/components/DomainBreakdown';
import { FindingsList } from '@/features/scanner/components/FindingsList';
import { RiskBadge } from '@/features/scanner/components/RiskBadge';
import type { ScanData } from '@/features/scanner/schemas/scanResponse';

interface Props {
  readonly data: ScanData;
}

/**
 * The analysed URL is rendered as plain text via `<p>`, never as an `<a>` href,
 * an iframe `src`, or `dangerouslySetInnerHTML` — see docs/SECURITY_BOUNDARY.md.
 * `data.input` is already the backend's redacted display value.
 */
export function ScanResult({ data }: Props) {
  return (
    <div className="mt-6 space-y-5" aria-live="polite">
      <div>
        <p className="text-ink-500 text-xs font-medium tracking-wide uppercase">Analysed link</p>
        <p className="text-ink-100 mt-1 font-mono text-sm break-all">{data.input}</p>
      </div>

      <div className="flex flex-wrap items-center gap-4">
        <p className="flex items-baseline gap-1">
          <span className="text-3xl font-bold tracking-tight">{data.score}</span>
          <span className="text-ink-500 text-sm">/100</span>
        </p>
        <RiskBadge riskLevel={data.riskLevel} />
      </div>

      <DomainBreakdown normalized={data.normalized} />

      <div>
        <h3 className="text-ink-100 text-sm font-semibold">Findings</h3>
        <div className="mt-2">
          <FindingsList findings={data.findings} />
        </div>
      </div>

      <p className="text-ink-500 text-xs">
        This score reflects lexical signals only. LinkSentry never visits the link, so a low score is not
        confirmation that the destination is safe.
      </p>
    </div>
  );
}
