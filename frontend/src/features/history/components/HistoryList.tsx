import { useMemo } from 'react';

import { HistoryListItem } from '@/features/history/components/HistoryListItem';
import type { ScanHistoryItem } from '@/features/history/schemas/scanHistoryResponse';
import { useLocale } from '@/lib/i18n/useLocale';

interface HistoryListProps {
  readonly items: readonly ScanHistoryItem[];
}

/** Responsive table that becomes one readable card per row on narrow screens. */
export function HistoryList({ items }: HistoryListProps) {
  const { locale, t } = useLocale();
  const uniqueItems = useMemo(() => deduplicateByScanId(items), [items]);

  return (
    <div className="history-table-shell surface-panel">
      <table className="history-table">
        <caption className="sr-only">{t('history.tableCaption')}</caption>
        <colgroup>
          <col className="history-table__target-column" />
          <col className="history-table__risk-column" />
          <col className="history-table__score-column" />
          <col className="history-table__findings-column" />
          <col className="history-table__date-column" />
          <col className="history-table__action-column" />
        </colgroup>
        <thead>
          <tr>
            <th scope="col">{t('history.column.target')}</th>
            <th scope="col">{t('history.column.risk')}</th>
            <th scope="col">{t('history.column.score')}</th>
            <th scope="col">{t('history.column.findings')}</th>
            <th scope="col">{t('history.column.analyzed')}</th>
            <th scope="col">{t('history.column.action')}</th>
          </tr>
        </thead>
        <tbody>
          {uniqueItems.map((item) => (
            <HistoryListItem key={item.scanId} item={item} locale={locale} t={t} />
          ))}
        </tbody>
      </table>
    </div>
  );
}

/** Preserve the first validated occurrence if a later page repeats a scan ID. */
function deduplicateByScanId(items: readonly ScanHistoryItem[]): ScanHistoryItem[] {
  const seen = new Set<string>();
  return items.filter((item) => {
    if (seen.has(item.scanId)) {
      return false;
    }
    seen.add(item.scanId);
    return true;
  });
}
