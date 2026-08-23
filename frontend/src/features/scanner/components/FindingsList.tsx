import { Badge } from '@/components/ui/Badge';
import type { Finding, Severity } from '@/features/scanner/schemas/scanResponse';
import type { TranslationKey } from '@/lib/i18n/translations';
import { useLocale } from '@/lib/i18n/useLocale';

const SEVERITY_TONE: Record<Severity, 'low' | 'moderate' | 'high' | 'critical'> = {
  INFO: 'low',
  LOW: 'low',
  MEDIUM: 'moderate',
  HIGH: 'high',
};

const SEVERITY_KEY: Record<Severity, TranslationKey> = {
  INFO: 'severity.INFO',
  LOW: 'severity.LOW',
  MEDIUM: 'severity.MEDIUM',
  HIGH: 'severity.HIGH',
};

interface Props {
  readonly findings: readonly Finding[];
}

/** Renders server-owned findings in stable order as inert, readable evidence. */
export function FindingsList({ findings }: Props) {
  const { t } = useLocale();

  if (findings.length === 0) {
    return <p className="body-secondary text-sm leading-6">{t('findings.empty')}</p>;
  }

  return (
    <ul className="space-y-3">
      {findings.map((finding) => (
        <li key={finding.ruleId} className="surface-inset p-4">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <h4 className="text-content-primary max-w-xl text-sm font-bold leading-5">{finding.title}</h4>
            <div className="flex shrink-0 items-center gap-2">
              <Badge tone={SEVERITY_TONE[finding.severity]}>
                {t(SEVERITY_KEY[finding.severity])}
              </Badge>
              <span className="body-muted font-mono text-xs">
                {t('findings.points', { points: finding.points })}
              </span>
            </div>
          </div>
          <p className="body-secondary mt-2 text-sm leading-6">{finding.explanation}</p>
          {finding.evidence ? (
            <p
              aria-label={t('findings.evidence') + ': ' + finding.evidence}
              className="body-muted mt-3 break-all border-l-2 border-accent/50 pl-3 font-mono text-xs leading-5"
            >
              {finding.evidence}
            </p>
          ) : null}
        </li>
      ))}
    </ul>
  );
}
