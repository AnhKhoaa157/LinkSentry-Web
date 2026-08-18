import { fireEvent, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { NextSteps } from '@/features/scanner/components/NextSteps';
import type { Finding, RiskLevel } from '@/features/scanner/schemas/scanResponse';
import { renderWithProviders } from '@/test/renderWithProviders';

const findings: readonly Finding[] = [
  {
    ruleId: 'SUSPICIOUS_KEYWORDS',
    severity: 'MEDIUM',
    points: 20,
    title: 'Subdomain uses a sensitive-sounding word',
    explanation: 'A subdomain contains a word commonly used in credential phishing.',
    evidence: null,
  },
  {
    ruleId: 'MISSING_HTTPS',
    severity: 'LOW',
    points: 5,
    title: 'Connection is not encrypted',
    explanation: 'This link uses HTTP instead of HTTPS.',
    evidence: null,
  },
];

const baseProps = {
  riskLevel: 'HIGH' as RiskLevel,
  score: 45,
  registrableDomain: 'security-check.invalid' as string | null,
  findings,
};

/**
 * jsdom ships no Clipboard API, so every test installs its own. `configurable`
 * lets the next test replace it; nothing here leaks into the real navigator.
 */
function mockClipboard(writeText: (text: string) => Promise<void>) {
  const spy = vi.fn(writeText);
  Object.defineProperty(navigator, 'clipboard', {
    value: { writeText: spy },
    configurable: true,
    writable: true,
  });
  return spy;
}

/** Renders, presses the copy button, and returns exactly what reached the clipboard. */
async function copiedTextFor(props: Partial<typeof baseProps> = {}): Promise<string> {
  const writeText = mockClipboard(() => Promise.resolve());
  renderWithProviders(<NextSteps {...baseProps} {...props} />);

  fireEvent.click(screen.getByRole('button', { name: 'Copy safe summary' }));
  await waitFor(() => expect(writeText).toHaveBeenCalledTimes(1));

  return writeText.mock.calls[0]![0];
}

describe('NextSteps guidance', () => {
  const cases: ReadonlyArray<readonly [RiskLevel, RegExp]> = [
    ['LOW', /No strong lexical risk signals were detected\. Still verify the sender/],
    ['MODERATE', /Review the registered domain carefully\./],
    ['HIGH', /Avoid opening the link or entering credentials\./],
    ['CRITICAL', /Do not open, sign in to, download from, or forward the link\./],
  ];

  it.each(cases)('renders the %s recommendation', (riskLevel, expected) => {
    renderWithProviders(<NextSteps {...baseProps} riskLevel={riskLevel} />);

    expect(screen.getByRole('heading', { name: 'Recommended next steps' })).toBeInTheDocument();
    expect(screen.getByText(expected)).toBeInTheDocument();
  });

  it('never tells the user a LOW result is safe', () => {
    const { container } = renderWithProviders(
      <NextSteps {...baseProps} riskLevel="LOW" score={0} findings={[]} />,
    );

    expect(screen.getByText(/No strong lexical risk signals were detected/)).toBeInTheDocument();

    // "Copy safe summary" is a control label, not a verdict. What must never
    // appear is an affirmative claim about the destination.
    expect(container.textContent).not.toMatch(/\b(is|are|looks?|seems?|appears?)\s+safe\b/i);
    expect(container.textContent).not.toMatch(/\bno risk\b/i);
  });

  it('exposes the guidance as a labelled region so it is reachable as a landmark', () => {
    renderWithProviders(<NextSteps {...baseProps} />);

    expect(screen.getByRole('region', { name: 'Recommended next steps' })).toBeInTheDocument();
  });
});

describe('NextSteps safe summary', () => {
  it('copies only allow-listed values, in the server-provided finding order', async () => {
    expect(await copiedTextFor()).toBe(
      [
        'LinkSentry link analysis',
        'Risk level: High (score 45/100)',
        'Registered domain: security-check.invalid',
        'Findings:',
        '- Subdomain uses a sensitive-sounding word',
        '- Connection is not encrypted',
        'Recommended action: Avoid opening the link or entering credentials. Verify the request through the organization’s official app, website, or support channel.',
        'Note: Lexical analysis inspects only the text of a link. It cannot prove that a destination is safe.',
      ].join('\n'),
    );
  });

  it('preserves finding order when the server order changes', async () => {
    const copied = await copiedTextFor({ findings: [...findings].reverse() });
    const titles = copied
      .split('\n')
      .filter((line) => line.startsWith('- '))
      .map((line) => line.slice(2));

    expect(titles).toEqual(['Connection is not encrypted', 'Subdomain uses a sensitive-sounding word']);
  });

  it('never substitutes the host when no registrable domain was resolved', async () => {
    const copied = await copiedTextFor({ registrableDomain: null });

    expect(copied).toContain('Registered domain: not determined');
    expect(copied).not.toContain('security-check.invalid');
  });

  it('states an empty finding list without ever claiming the link is safe', async () => {
    const copied = await copiedTextFor({ riskLevel: 'LOW', score: 0, findings: [] });

    expect(copied).toContain('Findings: none detected');
    expect(copied).toContain('Risk level: Low (score 0/100)');

    // The word "safe" is permitted in exactly one place: the negated caveat that
    // docs/SECURITY_BOUNDARY.md §4 requires. Remove that sentence and no claim
    // about safety may remain anywhere in the copied text.
    const caveat =
      'Note: Lexical analysis inspects only the text of a link. It cannot prove that a destination is safe.';
    expect(copied).toContain(caveat);
    expect(copied.replace(caveat, '')).not.toMatch(/safe/i);
  });

  it.each([
    ['LOW', 'Recommended action: No strong lexical risk signals were detected.'],
    ['MODERATE', 'Recommended action: Review the registered domain carefully.'],
    ['HIGH', 'Recommended action: Avoid opening the link or entering credentials.'],
    ['CRITICAL', 'Recommended action: Do not open, sign in to, download from, or forward the link.'],
  ] as ReadonlyArray<readonly [RiskLevel, string]>)(
    'copies the %s action that matches the rendered guidance',
    async (riskLevel, expected) => {
      expect(await copiedTextFor({ riskLevel })).toContain(expected);
    },
  );

  it('copies nothing that could identify or reach the submitted target', async () => {
    const copied = await copiedTextFor();

    // Sentinels for every value the boundary forbids leaving the browser.
    const forbidden = [
      'https://login.example.com.security-check.invalid/account', // data.input
      'login.example.com.security-check.invalid', // host
      '/account', // path
      ':8443', // port
      'token=secret123', // query
      'secret123',
      'fragsentinel456', // fragment
      'user:password@', // credentials
      '2ce16fb9-d52d-4310-8d45-a4e48f31889e', // scanId
      '/scans/', // permalink shape
      'trace-id-abc', // trace ID
    ];
    for (const sentinel of forbidden) {
      expect(copied).not.toContain(sentinel);
    }

    expect(copied).not.toMatch(/https?:\/\//);
  });
});

describe('NextSteps copy status', () => {
  it('confirms a successful copy accessibly', async () => {
    mockClipboard(() => Promise.resolve());
    renderWithProviders(<NextSteps {...baseProps} />);

    // Idle: the live region exists but announces nothing.
    expect(screen.getByRole('status')).toHaveTextContent('');

    fireEvent.click(screen.getByRole('button', { name: 'Copy safe summary' }));

    await waitFor(() => {
      expect(screen.getByRole('status')).toHaveTextContent('Summary copied.');
    });
  });

  it('reports a calm failure and keeps the result when the Clipboard API rejects', async () => {
    const writeText = mockClipboard(() => Promise.reject(new Error('NotAllowedError: denied')));
    renderWithProviders(<NextSteps {...baseProps} />);

    fireEvent.click(screen.getByRole('button', { name: 'Copy safe summary' }));

    await waitFor(() => {
      expect(screen.getByRole('status')).toHaveTextContent(
        'Could not copy the summary. Your browser blocked clipboard access.',
      );
    });

    // The guidance survives the failure, and the raw rejection text is not shown.
    expect(screen.getByText(/Avoid opening the link or entering credentials/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Copy safe summary' })).toBeEnabled();
    expect(document.body.textContent).not.toContain('NotAllowedError');
    expect(writeText).toHaveBeenCalledTimes(1);
  });

  it('reports failure rather than throwing when the Clipboard API is absent', async () => {
    Object.defineProperty(navigator, 'clipboard', { value: undefined, configurable: true });
    renderWithProviders(<NextSteps {...baseProps} />);

    fireEvent.click(screen.getByRole('button', { name: 'Copy safe summary' }));

    await waitFor(() => {
      expect(screen.getByRole('status')).toHaveTextContent(/Could not copy the summary/);
    });
  });

  it('does not retry after a failure', async () => {
    const writeText = mockClipboard(() => Promise.reject(new Error('denied')));
    renderWithProviders(<NextSteps {...baseProps} />);

    fireEvent.click(screen.getByRole('button', { name: 'Copy safe summary' }));
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent(/Could not copy/));

    // One click, one attempt — no timer-driven second write.
    await new Promise((resolve) => setTimeout(resolve, 50));
    expect(writeText).toHaveBeenCalledTimes(1);
  });
});
