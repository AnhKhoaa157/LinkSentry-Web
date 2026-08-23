import { Link } from 'react-router';

import type { ScanHistoryItem } from '@/features/history/schemas/scanHistoryResponse';
import { RiskBadge } from '@/features/scanner/components/RiskBadge';
import type { Locale } from '@/lib/i18n/locale';
import type { Translate } from '@/lib/i18n/LocaleContext';

interface HistoryListItemProps {
  readonly item: ScanHistoryItem;
  readonly locale: Locale;
  readonly t: Translate;
}

/** One responsive table row. Submitted values remain ordinary, inert text. */
export function HistoryListItem({ item, locale, t }: HistoryListItemProps) {
  const detailLabelValue = item.registrableDomain ?? item.displayValue;

  return (
    <tr className="history-table__row">
      <td className="history-table__target">
        <span className="history-cell-label">{t('history.column.target')}</span>
        <p className="history-display-value text-content-primary font-mono text-sm" dir="ltr">
          {item.displayValue}
        </p>
        {item.registrableDomain ? (
          <p className="text-content-muted mt-1 text-xs">
            <span className="sr-only">{t('history.column.domain')}: </span>
            <span className="history-domain-value font-mono" dir="ltr">
              {item.registrableDomain}
            </span>
          </p>
        ) : (
          <p className="text-content-muted mt-1 text-xs">{t('history.domainUnavailable')}</p>
        )}
      </td>
      <td>
        <span className="history-cell-label">{t('history.column.risk')}</span>
        <RiskBadge riskLevel={item.riskLevel} />
      </td>
      <td>
        <span className="history-cell-label">{t('history.column.score')}</span>
        <span className="text-content-primary text-sm font-bold tabular-nums">
          {t('history.scoreValue', { score: item.score })}
        </span>
      </td>
      <td>
        <span className="history-cell-label">{t('history.column.findings')}</span>
        <span className="text-content-secondary text-sm tabular-nums">
          {t(item.findingCount === 1 ? 'history.findingCount.one' : 'history.findingCount.many', {
            count: item.findingCount,
          })}
        </span>
      </td>
      <td>
        <span className="history-cell-label">{t('history.column.analyzed')}</span>
        <time className="text-content-secondary text-sm" dateTime={item.analyzedAt}>
          {formatAnalyzedAt(item.analyzedAt, locale)}
        </time>
      </td>
      <td className="history-table__action">
        <span className="history-cell-label">{t('history.column.action')}</span>
        <Link
          to={`/scans/${item.scanId}`}
          className="text-accent-strong inline-flex text-sm font-bold underline underline-offset-4"
          aria-label={t('history.viewDetailsAria', { value: detailLabelValue })}
        >
          {t('history.viewDetails')}
        </Link>
      </td>
    </tr>
  );
}

function formatAnalyzedAt(value: string, locale: Locale): string {
  return new Intl.DateTimeFormat(locale === 'vi' ? 'vi-VN' : 'en-US', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value));
}
