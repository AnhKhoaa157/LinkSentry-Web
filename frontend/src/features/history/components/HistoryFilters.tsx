import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react';

import {
  DEFAULT_SCAN_HISTORY_FILTERS,
  historyRuleIdSchema,
  historyRuleLabelKeys,
  historyRuleIds,
  scanHistoryFiltersSchema,
  type HistoryRuleId,
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

          <RuleFilterDropdown value={filters.ruleId} onChange={handleRuleChange} t={t} />
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

interface RuleFilterDropdownProps {
  readonly value: HistoryRuleId | null;
  readonly onChange: (value: string) => void;
  readonly t: Translate;
}

const RULE_FILTER_OPTIONS: readonly (HistoryRuleId | null)[] = [null, ...historyRuleIds];

function RuleFilterDropdown({ value, onChange, t }: RuleFilterDropdownProps) {
  const [isOpen, setIsOpen] = useState(false);
  const [focusedIndex, setFocusedIndex] = useState<number | null>(null);
  const rootRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const optionRefs = useRef<Array<HTMLButtonElement | null>>([]);
  const labelId = useId();
  const menuId = useId();
  const selectedIndex = RULE_FILTER_OPTIONS.indexOf(value);
  const selectedLabel = value === null ? t('history.filters.ruleAll') : t(historyRuleLabelKeys[value]);

  useEffect(() => {
    function handlePointerDown(event: PointerEvent) {
      if (rootRef.current?.contains(event.target as Node) !== true) {
        setIsOpen(false);
      }
    }

    document.addEventListener('pointerdown', handlePointerDown);
    return () => document.removeEventListener('pointerdown', handlePointerDown);
  }, []);

  useEffect(() => {
    if (isOpen && focusedIndex !== null) {
      optionRefs.current[focusedIndex]?.focus();
    }
  }, [focusedIndex, isOpen]);

  function openAt(index: number) {
    setFocusedIndex(index);
    setIsOpen(true);
  }

  function selectOption(option: HistoryRuleId | null) {
    onChange(option ?? '');
    setIsOpen(false);
    triggerRef.current?.focus();
  }

  function handleTriggerKeyDown(event: KeyboardEvent<HTMLButtonElement>) {
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      openAt(Math.min(selectedIndex + 1, RULE_FILTER_OPTIONS.length - 1));
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      openAt(Math.max(selectedIndex - 1, 0));
    }
  }

  function handleOptionKeyDown(event: KeyboardEvent<HTMLButtonElement>, index: number) {
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      setFocusedIndex(Math.min(index + 1, RULE_FILTER_OPTIONS.length - 1));
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      setFocusedIndex(Math.max(index - 1, 0));
    } else if (event.key === 'Home') {
      event.preventDefault();
      setFocusedIndex(0);
    } else if (event.key === 'End') {
      event.preventDefault();
      setFocusedIndex(RULE_FILTER_OPTIONS.length - 1);
    } else if (event.key === 'Escape') {
      event.preventDefault();
      setIsOpen(false);
      triggerRef.current?.focus();
    }
  }

  return (
    <div ref={rootRef} className="history-rule-select space-y-2">
      <span id={labelId} className="ui-label">
        {t('history.filters.ruleLabel')}
      </span>
      <button
        ref={triggerRef}
        type="button"
        className="history-rule-select__trigger"
        aria-labelledby={labelId}
        aria-controls={menuId}
        aria-expanded={isOpen}
        aria-haspopup="listbox"
        onClick={() => (isOpen ? setIsOpen(false) : openAt(selectedIndex))}
        onKeyDown={handleTriggerKeyDown}
      >
        <span>{selectedLabel}</span>
        <ChevronIcon expanded={isOpen} />
      </button>

      {isOpen ? (
        <div id={menuId} className="history-rule-select__menu" role="listbox" aria-labelledby={labelId}>
          {RULE_FILTER_OPTIONS.map((option, index) => {
            const isSelected = option === value;
            const label = option === null ? t('history.filters.ruleAll') : t(historyRuleLabelKeys[option]);
            return (
              <button
                key={option ?? 'all'}
                ref={(element) => {
                  optionRefs.current[index] = element;
                }}
                type="button"
                role="option"
                aria-selected={isSelected}
                className="history-rule-select__option"
                onClick={() => selectOption(option)}
                onKeyDown={(event) => handleOptionKeyDown(event, index)}
              >
                <span>{label}</span>
                {isSelected ? <CheckIcon /> : null}
              </button>
            );
          })}
        </div>
      ) : null}
    </div>
  );
}

function ChevronIcon({ expanded }: { readonly expanded: boolean }) {
  return (
    <svg className="history-rule-select__chevron" aria-hidden="true" viewBox="0 0 16 16">
      <path d={expanded ? 'm4 10 4-4 4 4' : 'm4 6 4 4 4-4'} />
    </svg>
  );
}

function CheckIcon() {
  return (
    <svg className="history-rule-select__check" aria-hidden="true" viewBox="0 0 16 16">
      <path d="m3.5 8.5 2.7 2.7 6.3-6.4" />
    </svg>
  );
}
