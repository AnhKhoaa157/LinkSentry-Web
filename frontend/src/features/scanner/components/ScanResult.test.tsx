import { fireEvent, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { ScanResult } from '@/features/scanner/components/ScanResult';
import type { ScanData } from '@/features/scanner/schemas/scanResponse';
import { renderWithProviders } from '@/test/renderWithProviders';

const baseData: ScanData = {
  scanId: '2ce16fb9-d52d-4310-8d45-a4e48f31889e',
  input: 'https://login.example.com.security-check.invalid/account',
  normalized: {
    scheme: 'https',
    host: 'login.example.com.security-check.invalid',
    asciiHost: 'login.example.com.security-check.invalid',
    registrableDomain: 'security-check.invalid',
    port: null,
    path: '/account',
    queryPresent: false,
    fragmentPresent: false,
  },
  score: 70,
  riskLevel: 'CRITICAL',
  findings: [
    {
      ruleId: 'SUSPICIOUS_KEYWORDS',
      severity: 'MEDIUM',
      points: 20,
      title: 'Subdomain uses a sensitive-sounding word',
      explanation: 'A subdomain contains a word commonly used in credential phishing.',
      evidence: null,
    },
    {
      ruleId: 'EXCESSIVE_SUBDOMAINS',
      severity: 'MEDIUM',
      points: 20,
      title: 'Unusually deep subdomain structure',
      explanation: 'The hostname contains more subdomain levels than expected.',
      evidence: null,
    },
  ],
  analyzedAt: '2026-08-16T12:00:00Z',
};

describe('ScanResult', () => {
  it('renders the score, domain boundary, risk label, and every finding', () => {
    renderWithProviders(<ScanResult data={baseData} />);

    expect(screen.getByText('70')).toBeInTheDocument();
    expect(screen.getByText('/100')).toBeInTheDocument();
    expect(screen.getByText('Critical risk')).toBeInTheDocument();
    expect(screen.getByText('login.example.com.')).toBeInTheDocument();
    expect(screen.getByText('security-check.invalid')).toBeInTheDocument();
    expect(screen.getByText('Subdomain uses a sensitive-sounding word')).toBeInTheDocument();
    expect(screen.getByText('Unusually deep subdomain structure')).toBeInTheDocument();
  });

  it('renders an explicit caveat when no signals were detected', () => {
    renderWithProviders(<ScanResult data={{ ...baseData, score: 0, riskLevel: 'LOW', findings: [] }} />);

    expect(screen.getByText('Low risk')).toBeInTheDocument();
    expect(screen.getByText(/No signals were detected by the current rules/i)).toBeInTheDocument();
    expect(screen.getByText(/does not mean the link is safe/i)).toBeInTheDocument();
  });

  it('renders HTML-shaped submitted content as inert plain text, never markup, a link, or an iframe', () => {
    // Backend redaction should already strip anything hostile, but the frontend
    // must never trust that: rendering this verbatim must not produce a live
    // element. If ScanResult ever used dangerouslySetInnerHTML or wrapped input
    // in an <a>/<iframe>, this is what would catch it.
    const hostileInput = '<img src=x onerror=alert(1)>https://example.com/<script>alert(2)</script>';
    const { container } = renderWithProviders(<ScanResult data={{ ...baseData, input: hostileInput }} />);

    const rendered = screen.getByText(hostileInput);
    expect(rendered.tagName).toBe('P');
    expect(container.querySelector('img')).toBeNull();
    expect(container.querySelector('script')).toBeNull();
    expect(container.querySelector('iframe')).toBeNull();
    expect(container.querySelector('a')).toBeNull();

    // The next-steps card adds the only interactive control in the result, and
    // it must stay a button — never an anchor that could carry the target.
    const copyButton = screen.getByRole('button', { name: 'Copy safe summary' });
    expect(copyButton.tagName).toBe('BUTTON');
    expect(copyButton).not.toHaveAttribute('href');
  });

  it('places the recommended next steps after the findings', () => {
    renderWithProviders(<ScanResult data={baseData} />);

    const headings = screen.getAllByRole('heading').map((heading) => heading.textContent);

    expect(headings).toContain('Findings');
    expect(headings).toContain('Recommended next steps');
    expect(headings.indexOf('Recommended next steps')).toBeGreaterThan(headings.indexOf('Findings'));
  });

  it('copies a summary that omits the submitted link, its path, and the scan ID', async () => {
    const writeText = vi.fn((_text: string) => Promise.resolve());
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText },
      configurable: true,
      writable: true,
    });

    renderWithProviders(<ScanResult data={baseData} />);
    fireEvent.click(screen.getByRole('button', { name: 'Copy safe summary' }));

    await waitFor(() => expect(writeText).toHaveBeenCalledTimes(1));
    const copied = writeText.mock.calls[0]![0];

    expect(copied).not.toContain(baseData.input);
    expect(copied).not.toContain(baseData.scanId);
    expect(copied).not.toContain(baseData.normalized.path);
    expect(copied).not.toContain(baseData.normalized.host);
    expect(copied).not.toMatch(/https?:\/\//);

    // ...while still carrying everything the summary is for.
    expect(copied).toContain('Risk level: Critical (score 70/100)');
    expect(copied).toContain('Registered domain: security-check.invalid');
    expect(copied).toContain('- Subdomain uses a sensitive-sounding word');
    expect(copied).toContain('- Unusually deep subdomain structure');
    expect(copied).toContain('Do not open, sign in to, download from, or forward the link.');
  });
});
