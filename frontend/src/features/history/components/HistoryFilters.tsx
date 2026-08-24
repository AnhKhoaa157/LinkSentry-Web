import {
  DEFAULT_SCAN_HISTORY_FILTERS,
  historyRuleIdSchema,
  historyRuleIds,
  scanHistoryFiltersSchema,
  type ScanHistoryFilters,
} from '@/features/history/schemas/scanHistoryResponse';
import { riskLevelSchema, type RiskLevel } from '@/features/scanner/schemas/scanResponse';
import type { Translate } from '@/lib/i18n/LocaleContext';

const RISK_LEVELS: readonly RiskLevel[] = ['LOW', 'MODERATE', 'HIGH', 'CRITICAL'];

interface HistoryFiltersProps {
  readonly filters: ScanHistoryFilters;
  readonly onChange: (filters: ScanHistoryFilters) => void;
  readonly t: Translate;
}

/** Keyboard-accessible select filters with no free-text URL or domain input. */
export function HistoryFilters({ filters, onChange, t }: HistoryFiltersProps) {
  const hasActiveFilters = filters.riskLevel !== null || filters.ruleId !== null;

  function handleRiskChange(value: string) {
    const nextValue = value === '' ? null : riskLevelSchema.nullable().safeParse(value);
    if (nextValue === null) {
      onChange({ ...filters, riskLevel: null });
      return;
    }
    if (nextValue.success) {
      onChange(scanHistoryFiltersSchema.parse({ ...filters, riskLevel: nextValue.data }));
    }
  }

  function handleRuleChange(value: string) {
    const nextValue = value === '' ? null : historyRuleIdSchema.nullable().safeParse(value);
    if (nextValue === null) {
      onChange({ ...filters, ruleId: null });
      return;
    }
    if (nextValue.success) {
      onChange(scanHistoryFiltersSchema.parse({ ...filters, ruleId: nextValue.data }));
    }
  }

  return (
    <form className="history-filters surface-inset p-4 sm:p-5" aria-label={t('history.filters.legend')}>
      <fieldset className="space-y-4">
        <legend className="text-content-primary text-sm font-bold">{t('history.filters.legend')}</legend>
        <div className="grid gap-4 sm:grid-cols-2">
          <label className="space-y-2">
            <span className="ui-label">{t('history.filters.riskLabel')}</span>
            <select
              className="ui-input min-h-11 px-3 text-sm"
              value={filters.riskLevel ?? ''}
              onChange={(event) => handleRiskChange(event.target.value)}
            >
              <option value="">{t('history.filters.riskAll')}</option>
              {RISK_LEVELS.map((riskLevel) => (
                <option key={riskLevel} value={riskLevel}>
                  {t(`riskBadge.${riskLevel.toLowerCase() as 'low' | 'moderate' | 'high' | 'critical'}`)}
                </option>
              ))}
            </select>
          </label>

          <label className="space-y-2">
            <span className="ui-label">{t('history.filters.ruleLabel')}</span>
            <select
              className="ui-input min-h-11 px-3 font-mono text-sm"
              value={filters.ruleId ?? ''}
              onChange={(event) => handleRuleChange(event.target.value)}
            >
              <option value="">{t('history.filters.ruleAll')}</option>
              {historyRuleIds.map((ruleId) => (
                <option key={ruleId} value={ruleId}>
                  {ruleId}
                </option>
              ))}
            </select>
          </label>
        </div>

        <div className="flex flex-wrap items-center gap-3">
          {hasActiveFilters ? (
            <button
              type="button"
              className="ui-button ui-button-quiet"
              onClick={() => onChange(DEFAULT_SCAN_HISTORY_FILTERS)}
            >
              {t('history.filters.reset')}
            </button>
          ) : null}
          <p className="body-muted text-xs" aria-live="polite">
            {hasActiveFilters ? t('history.filters.active') : t('history.filters.none')}
          </p>
        </div>
      </fieldset>
    </form>
  );
}
