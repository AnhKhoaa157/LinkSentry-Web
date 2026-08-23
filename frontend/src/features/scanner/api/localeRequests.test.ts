import { beforeEach, describe, expect, it, vi } from 'vitest';

import { postExplanation } from '@/features/explanation/api/postExplanation';
import { getScan } from '@/features/scanner/api/getScan';
import { postScan } from '@/features/scanner/api/postScan';
import { apiClient } from '@/lib/api/client';

const scanResponse = {
  data: {
    scanId: null,
    input: 'https://example.com/account',
    normalized: {
      scheme: 'https',
      host: 'example.com',
      asciiHost: 'example.com',
      registrableDomain: 'example.com',
      port: null,
      path: '/account',
      queryPresent: false,
      fragmentPresent: false,
    },
    score: 0,
    riskLevel: 'LOW',
    findings: [],
    analyzedAt: '2026-08-23T00:00:00Z',
  },
  meta: { engineVersion: '0.1.0' },
};

const explanationResponse = {
  data: {
    riskLevel: 'LOW',
    keyFindings: [],
    summary: 'No lexical risk signals were detected.',
    recommendedActions: ['Verify the sender through an official channel.'],
  },
};

describe('scan-related locale request headers', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('defaults scan creation to English and validates the unchanged response shape', async () => {
    const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValue({ data: scanResponse });

    await postScan('https://example.com/account');

    expect(postSpy).toHaveBeenCalledWith(
      '/api/v1/scans',
      { url: 'https://example.com/account' },
      { headers: { 'Accept-Language': 'en' } },
    );
  });

  it('sends Vietnamese for scan creation and retained retrieval', async () => {
    const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValue({ data: scanResponse });
    const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValue({ data: scanResponse });

    await postScan('https://example.com/account', { locale: 'vi' });
    await getScan('scan-id', { locale: 'vi' });

    expect(postSpy).toHaveBeenCalledWith(
      '/api/v1/scans',
      { url: 'https://example.com/account' },
      { headers: { 'Accept-Language': 'vi' } },
    );
    expect(getSpy).toHaveBeenCalledWith('/api/v1/scans/scan-id', {
      headers: { 'Accept-Language': 'vi' },
    });
  });

  it('sends the selected locale for deterministic explanation findings', async () => {
    const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValue({ data: explanationResponse });

    await postExplanation('scan-id', { locale: 'vi' });

    expect(postSpy).toHaveBeenCalledWith('/api/v1/scans/scan-id/explanation', undefined, {
      headers: { 'Accept-Language': 'vi' },
    });
  });
});
