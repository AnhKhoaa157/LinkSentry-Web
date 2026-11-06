import { useEffect, useId, useRef, useState, type FormEvent } from 'react';
import { Link } from 'react-router';

import {
  buildBatchScanCsv,
  MAX_BATCH_SCAN_URLS,
  parseBatchScanInput,
  shouldStopBatchAfter,
} from '@/features/scanner/batchScan';
import { useLicense } from '@/features/license/context/useLicense';
import { postBatchScan } from '@/features/scanner/api/postBatchScan';
import { RiskBadge } from '@/features/scanner/components/RiskBadge';
import type { ScanData } from '@/features/scanner/schemas/scanResponse';
import { localizeApiError, localizeValidationMessage } from '@/lib/i18n/messages';
import type { TranslationKey } from '@/lib/i18n/translations';
import { normalizeApiError, type ApiError, type NormalizedApiError } from '@/lib/api/errors';
import { useLocale } from '@/lib/i18n/useLocale';

type BatchRowStatus = 'invalid' | 'queued' | 'scanning' | 'complete' | 'failed' | 'skipped';

interface BatchRow {
  readonly lineNumber: number;
  readonly status: BatchRowStatus;
  readonly validationMessage?: string;
  readonly error?: NormalizedApiError;
  readonly result?: ScanData;
}

interface BatchScannerProps {
  readonly onPendingChange: (isPending: boolean) => void;
}

const BATCH_STATUS_KEYS: Record<BatchRowStatus, TranslationKey> = {
  invalid: 'scanner.batch.status.invalid',
  queued: 'scanner.batch.status.queued',
  scanning: 'scanner.batch.status.scanning',
  complete: 'scanner.batch.status.complete',
  failed: 'scanner.batch.status.failed',
  skipped: 'scanner.batch.status.skipped',
};

/** Runs a bounded list through the backend batch endpoint and renders safe per-item outcomes. */
export function BatchScanner({ onPendingChange }: BatchScannerProps) {
  const { isLoading: isLicenseLoading } = useLicense();
  const { locale, t } = useLocale();
  const [input, setInput] = useState('');
  const [rows, setRows] = useState<BatchRow[]>([]);
  const [inputError, setInputError] = useState<'empty' | 'tooMany' | null>(null);
  const [isPending, setIsPending] = useState(false);
  const [isStopped, setIsStopped] = useState(false);
  const activeRequestRef = useRef<AbortController | null>(null);
  const inputId = useId();
  const hintId = useId();
  const errorId = useId();

  useEffect(
    () => () => {
      activeRequestRef.current?.abort();
    },
    [],
  );

  const validRowCount = rows.filter((row) => row.status !== 'invalid').length;
  const processedRowCount = rows.filter((row) => row.status === 'complete' || row.status === 'failed').length;
  const completedResponses = rows.flatMap((row) => (row.result ? [row.result] : []));

  function handleInputChange(value: string) {
    setInput(value);
    setRows([]);
    setInputError(null);
    setIsStopped(false);
  }

  function updateRow(lineNumber: number, changes: Partial<BatchRow>) {
    setRows((current) =>
      current.map((row) => (row.lineNumber === lineNumber ? { ...row, ...changes } : row)),
    );
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const parsed = parseBatchScanInput(input);
    if (parsed.error !== null) {
      setRows([]);
      setInputError(parsed.error);
      setIsStopped(false);
      return;
    }

    setInputError(null);
    setIsStopped(false);
    setRows(
      parsed.lines.map((line) => ({
        lineNumber: line.lineNumber,
        status: line.url === null ? 'invalid' : 'queued',
        ...(line.validationMessage === null ? {} : { validationMessage: line.validationMessage }),
      })),
    );
    const validLines = parsed.lines.filter(
      (line): line is typeof line & { url: string } => line.url !== null,
    );
    if (validLines.length === 0) {
      return;
    }

    setIsPending(true);
    onPendingChange(true);
    const controller = new AbortController();
    activeRequestRef.current = controller;

    try {
      for (const line of validLines) {
        updateRow(line.lineNumber, { status: 'scanning' });
      }

      const response = await postBatchScan(
        validLines.map((line) => line.url),
        { locale, signal: controller.signal },
      );
      const resultIndices = new Set(response.data.results.map((item) => item.index));
      if (
        response.data.results.length !== validLines.length ||
        resultIndices.size !== validLines.length ||
        [...resultIndices].some((index) => index >= validLines.length)
      ) {
        throw new Error('The batch API returned an incomplete result list.');
      }

      for (const item of response.data.results) {
        const line = validLines[item.index];
        if (!line) {
          continue;
        }
        if (item.status === 'COMPLETE') {
          updateRow(line.lineNumber, { status: 'complete', result: item.result });
        } else if (item.status === 'FAILED') {
          updateRow(line.lineNumber, { status: 'failed', error: normalizeBatchItemError(item.error) });
        } else {
          updateRow(line.lineNumber, { status: 'skipped' });
        }
      }

      setIsStopped(
        response.data.results.some(
          (item) =>
            item.status === 'SKIPPED' ||
            (item.status === 'FAILED' && shouldStopBatchAfter(normalizeBatchItemError(item.error))),
        ),
      );
    } catch (error: unknown) {
      const normalizedError = normalizeApiError(error);
      for (const line of validLines) {
        updateRow(line.lineNumber, { status: 'failed', error: normalizedError });
      }
      setIsStopped(true);
    } finally {
      activeRequestRef.current = null;
      setIsPending(false);
      onPendingChange(false);
    }
  }

  function downloadCsv() {
    const csv = buildBatchScanCsv(completedResponses, {
      input: t('scanner.batch.csv.input'),
      riskLevel: t('scanner.batch.csv.riskLevel'),
      score: t('scanner.batch.csv.score'),
      findings: t('scanner.batch.csv.findings'),
      analyzedAt: t('scanner.batch.csv.analyzedAt'),
    });
    const blobUrl = URL.createObjectURL(new Blob(['\uFEFF', csv], { type: 'text/csv;charset=utf-8' }));
    const anchor = document.createElement('a');
    anchor.href = blobUrl;
    anchor.download = 'linksentry-batch-' + new Date().toISOString().slice(0, 10) + '.csv';
    anchor.hidden = true;
    document.body.append(anchor);
    anchor.click();
    anchor.remove();
    window.setTimeout(() => URL.revokeObjectURL(blobUrl), 0);
  }

  const inputErrorMessage =
    inputError === 'empty'
      ? t('scanner.batch.error.empty')
      : inputError === 'tooMany'
        ? t('scanner.batch.error.tooMany', { limit: MAX_BATCH_SCAN_URLS })
        : null;

  return (
    <div className="space-y-5">
      <div className="space-y-2">
        <label htmlFor={inputId} className="ui-label">
          {t('scanner.batch.label')}
        </label>
        <p id={hintId} className="body-secondary text-sm leading-6">
          {t('scanner.batch.hint', { limit: MAX_BATCH_SCAN_URLS })}
        </p>
        <form onSubmit={(event) => void handleSubmit(event)}>
          <textarea
            id={inputId}
            name="batch-urls"
            rows={7}
            value={input}
            onChange={(event) => handleInputChange(event.target.value)}
            disabled={isPending}
            aria-describedby={inputErrorMessage ? hintId + ' ' + errorId : hintId}
            aria-invalid={inputErrorMessage !== null}
            placeholder={t('scanner.batch.placeholder')}
            className="ui-input mt-2 min-h-40 resize-y px-3.5 py-3 font-mono text-sm leading-6"
          />
          {inputErrorMessage ? (
            <p id={errorId} role="alert" className="text-danger mt-2 text-sm">
              {inputErrorMessage}
            </p>
          ) : null}
          <div className="mt-4 flex flex-wrap items-center gap-3">
            <button
              type="submit"
              disabled={isPending || isLicenseLoading || input.trim().length === 0}
              aria-busy={isPending}
              className="ui-button ui-button-primary"
            >
              {isPending
                ? t('scanner.batch.starting')
                : isLicenseLoading
                  ? t('scanner.initializing')
                  : t('scanner.batch.start')}
            </button>
            {completedResponses.length > 0 ? (
              <button
                type="button"
                onClick={downloadCsv}
                disabled={isPending}
                className="ui-button ui-button-secondary"
              >
                {t('scanner.batch.export')}
              </button>
            ) : null}
          </div>
        </form>
      </div>

      {rows.length > 0 ? (
        <section aria-labelledby={inputId + '-results-heading'} className="space-y-3">
          <div className="flex flex-wrap items-end justify-between gap-3">
            <div>
              <h3 id={inputId + '-results-heading'} className="text-content-primary text-base font-bold">
                {t('scanner.batch.resultsTitle')}
              </h3>
              <p className="body-muted mt-1 text-sm" role="status" aria-live="polite">
                {isPending
                  ? t('scanner.batch.progress', { completed: processedRowCount, total: validRowCount })
                  : t('scanner.batch.resultsSummary', {
                      completed: completedResponses.length,
                      total: validRowCount,
                    })}
              </p>
            </div>
          </div>
          {isStopped ? (
            <p role="alert" className="ui-alert p-4 text-sm">
              {t('scanner.batch.stopped')}
            </p>
          ) : null}
          <ol className="space-y-3" aria-label={t('scanner.batch.resultsTitle')} aria-busy={isPending}>
            {rows.map((row) => (
              <li key={row.lineNumber} className="surface-inset min-w-0 p-4 sm:p-5">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <p className="text-content-primary text-sm font-bold">
                    {t('scanner.batch.row', { number: row.lineNumber })}
                  </p>
                  <span className="body-muted text-xs font-bold">{t(BATCH_STATUS_KEYS[row.status])}</span>
                </div>

                {row.result ? (
                  <div className="mt-3 grid gap-3 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center">
                    <p className="text-content-secondary min-w-0 font-mono text-sm break-all">
                      {row.result.input}
                    </p>
                    <div className="flex flex-wrap items-center gap-2 sm:justify-end">
                      <RiskBadge riskLevel={row.result.riskLevel} />
                      <span className="text-content-primary text-sm font-bold">
                        {row.result.score}
                        {t('scanner.scoreOutOf')}
                      </span>
                      <span className="body-muted text-xs">
                        {t('scanner.findingsCount', { count: row.result.findings.length })}
                      </span>
                    </div>
                    {row.result.scanId ? (
                      <Link
                        to={'/scans/' + encodeURIComponent(row.result.scanId)}
                        className="text-accent-strong text-sm font-bold underline underline-offset-4 sm:col-span-2"
                      >
                        {t('scanner.savedResultLink')}
                      </Link>
                    ) : (
                      <p className="body-muted text-xs sm:col-span-2">{t('scanner.trialResult')}</p>
                    )}
                  </div>
                ) : null}

                {row.validationMessage ? (
                  <p className="text-danger mt-2 text-sm">
                    {localizeValidationMessage(t, row.validationMessage)}
                  </p>
                ) : null}
                {row.error ? (
                  <div className="mt-2 space-y-2">
                    <p className="text-danger text-sm">{batchErrorMessage(t, row.error)}</p>
                    {row.error.code === 'ANONYMOUS_TRIAL_EXHAUSTED' ? (
                      <Link
                        to="/license"
                        className="text-accent-strong inline-flex text-sm font-bold underline underline-offset-4"
                      >
                        {t('scanner.trialResultLink')}
                      </Link>
                    ) : null}
                  </div>
                ) : null}
              </li>
            ))}
          </ol>
        </section>
      ) : null}
    </div>
  );
}

function normalizeBatchItemError(error: ApiError): NormalizedApiError {
  return {
    message: error.message,
    code: error.code,
    fieldErrors: error.fieldErrors,
    isNetworkError: false,
  };
}

function batchErrorMessage(t: ReturnType<typeof useLocale>['t'], error: NormalizedApiError): string {
  if (error.code === 'VALIDATION_ERROR' && error.fieldErrors?.url) {
    return localizeValidationMessage(t, error.fieldErrors.url);
  }
  return localizeApiError(t, error);
}
