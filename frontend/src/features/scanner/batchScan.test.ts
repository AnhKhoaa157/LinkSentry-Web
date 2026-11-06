import { describe, expect, it } from 'vitest';

import {
  buildBatchScanCsv,
  MAX_BATCH_SCAN_URLS,
  parseBatchScanInput,
  shouldStopBatchAfter,
} from '@/features/scanner/batchScan';
import type { ScanResponse } from '@/features/scanner/schemas/scanResponse';
import type { NormalizedApiError } from '@/lib/api/errors';

function scanResponse(input: string): ScanResponse {
  return {
    data: {
      scanId: null,
      input,
      normalized: {
        scheme: 'https',
        host: 'example.invalid',
        asciiHost: 'example.invalid',
        registrableDomain: 'example.invalid',
        port: null,
        path: '/',
        queryPresent: false,
        fragmentPresent: false,
      },
      score: 10,
      riskLevel: 'MODERATE',
      findings: [
        {
          ruleId: 'SUSPICIOUS_KEYWORDS',
          severity: 'LOW',
          points: 10,
          title: 'Suspicious keyword',
          explanation: 'A static rule matched.',
        },
      ],
      analyzedAt: '2026-11-06T00:00:00Z',
    },
    meta: { engineVersion: '0.1.0' },
  };
}

describe('parseBatchScanInput', () => {
  it('ignores blank lines, trims each entry, and preserves source line numbers', () => {
    expect(parseBatchScanInput('\n  https://one.example.invalid/a  \r\nnot a URL\n')).toEqual({
      error: null,
      lines: [
        { lineNumber: 2, url: 'https://one.example.invalid/a', validationMessage: null },
        { lineNumber: 3, url: null, validationMessage: 'validation.url.scheme' },
      ],
    });
  });

  it('rejects empty input and batches over the maximum before any request is made', () => {
    expect(parseBatchScanInput(' \n\r\n')).toEqual({ lines: [], error: 'empty' });

    const tooMany = Array.from(
      { length: MAX_BATCH_SCAN_URLS + 1 },
      (_, index) => 'https://example-' + index + '.invalid',
    ).join('\n');
    expect(parseBatchScanInput(tooMany)).toEqual({ lines: [], error: 'tooMany' });
  });
});

describe('shouldStopBatchAfter', () => {
  const error = (code: string | undefined): NormalizedApiError => ({
    message: 'safe error',
    code,
    fieldErrors: undefined,
    isNetworkError: code === 'NETWORK_ERROR',
  });

  it('continues after item validation errors and stops after service-wide errors', () => {
    expect(shouldStopBatchAfter(error('INVALID_URL'))).toBe(false);
    expect(shouldStopBatchAfter(error('VALIDATION_ERROR'))).toBe(false);
    expect(shouldStopBatchAfter(error('RATE_LIMITED'))).toBe(true);
    expect(shouldStopBatchAfter(error('ANONYMOUS_TRIAL_EXHAUSTED'))).toBe(true);
    expect(shouldStopBatchAfter(error('NETWORK_ERROR'))).toBe(true);
    expect(shouldStopBatchAfter(error(undefined))).toBe(true);
  });
});

describe('buildBatchScanCsv', () => {
  it('exports only the redacted values returned by the API', () => {
    const csv = buildBatchScanCsv([scanResponse('https://example.invalid/path?[redacted]')], {
      input: 'Redacted input',
      riskLevel: 'Risk level',
      score: 'Score',
      findings: 'Findings',
      analyzedAt: 'Analyzed at',
    });

    expect(csv).toContain('"https://example.invalid/path?[redacted]"');
    expect(csv).not.toContain('password=');
    expect(csv).toContain('"MODERATE","10","1","2026-11-06T00:00:00Z"');
  });

  it('quotes commas and neutralizes spreadsheet formula prefixes', () => {
    const csv = buildBatchScanCsv([scanResponse('=HYPERLINK("https://example.invalid")')], {
      input: 'Input, URL',
      riskLevel: 'Risk',
      score: 'Score',
      findings: 'Findings',
      analyzedAt: 'Time',
    });

    expect(csv.split('\r\n')[0]).toContain('"Input, URL"');
    expect(csv).toContain("'=HYPERLINK");
    expect(csv).toContain('""https://example.invalid""');
  });
});
