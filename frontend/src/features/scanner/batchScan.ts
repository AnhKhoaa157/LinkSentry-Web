import type { ScanResponse } from '@/features/scanner/schemas/scanResponse';
import { scanRequestSchema } from '@/features/scanner/schemas/scanRequest';
import type { NormalizedApiError } from '@/lib/api/errors';

export const MAX_BATCH_SCAN_URLS = 10;

export interface ParsedBatchScanLine {
  readonly lineNumber: number;
  readonly url: string | null;
  readonly validationMessage: string | null;
}

export type BatchScanInputError = 'empty' | 'tooMany';

export interface ParsedBatchScanInput {
  readonly lines: readonly ParsedBatchScanLine[];
  readonly error: BatchScanInputError | null;
}

/** Splits plain text into independently validated, non-empty URL lines. */
export function parseBatchScanInput(value: string): ParsedBatchScanInput {
  const nonEmptyLines = value
    .split(/\r\n|\n|\r/)
    .map((line, index) => ({ lineNumber: index + 1, url: line.trim() }))
    .filter((line) => line.url.length > 0);

  if (nonEmptyLines.length === 0) {
    return { lines: [], error: 'empty' };
  }
  if (nonEmptyLines.length > MAX_BATCH_SCAN_URLS) {
    return { lines: [], error: 'tooMany' };
  }

  return {
    error: null,
    lines: nonEmptyLines.map(({ lineNumber, url }) => {
      const parsed = scanRequestSchema.safeParse({ url });
      return parsed.success
        ? { lineNumber, url: parsed.data.url, validationMessage: null }
        : {
            lineNumber,
            url: null,
            validationMessage: parsed.error.issues[0]?.message ?? 'validation.generic',
          };
    }),
  };
}

/** Stop a queue after service-wide failures; URL validation remains item-scoped. */
export function shouldStopBatchAfter(error: NormalizedApiError): boolean {
  return error.code !== 'INVALID_URL' && error.code !== 'VALIDATION_ERROR';
}

export interface BatchScanCsvHeaders {
  readonly input: string;
  readonly riskLevel: string;
  readonly score: string;
  readonly findings: string;
  readonly analyzedAt: string;
}

/** Builds a spreadsheet-safe report from backend-redacted result values only. */
export function buildBatchScanCsv(results: readonly ScanResponse[], headers: BatchScanCsvHeaders): string {
  const rows: (readonly (string | number)[])[] = [
    [headers.input, headers.riskLevel, headers.score, headers.findings, headers.analyzedAt],
    ...results.map(({ data }) => [
      data.input,
      data.riskLevel,
      data.score,
      data.findings.length,
      data.analyzedAt,
    ]),
  ];

  return rows.map((row) => row.map(csvCell).join(',')).join('\r\n');
}

function csvCell(value: string | number): string {
  const text = String(value);
  const spreadsheetSafe = /^[=+\-@]/.test(text.trimStart()) ? "'" + text : text;
  return '"' + spreadsheetSafe.replace(/"/g, '""') + '"';
}
