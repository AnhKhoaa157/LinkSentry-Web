import { useEffect, useRef, useState } from 'react';

import { getActiveTabUrl } from '@/extension/lib/activeTabUrl';
import { LicenseStatusCard } from '@/features/license/components/LicenseStatusCard';
import { postScan } from '@/features/scanner/api/postScan';
import type { ScanResponse } from '@/features/scanner/schemas/scanResponse';
import { FindingsList } from '@/features/scanner/components/FindingsList';
import { NextSteps } from '@/features/scanner/components/NextSteps';
import { RiskBadge } from '@/features/scanner/components/RiskBadge';
import { normalizeApiError, type NormalizedApiError } from '@/lib/api/errors';

const STATUS_ID = 'popup-tab-status';

const CHECKING_MESSAGE = 'Checking this tab…';
const READY_MESSAGE = 'Ready to scan the current tab.';
const UNSUPPORTED_MESSAGE =
  'This tab cannot be scanned. Open a regular http:// or https:// website, then reopen this popup.';
const RATE_LIMITED_MESSAGE = 'Too many scan requests. Wait a moment before trying again.';

type TabPhase = 'checking' | 'ready' | 'unsupported';

function displayMessage(error: NormalizedApiError): string {
  return error.code === 'RATE_LIMITED' ? RATE_LIMITED_MESSAGE : error.message;
}

function statusText(phase: TabPhase): string {
  if (phase === 'checking') {
    return CHECKING_MESSAGE;
  }
  return phase === 'ready' ? READY_MESSAGE : UNSUPPORTED_MESSAGE;
}

/**
 * The extension popup classifies the active tab on open, then re-reads and
 * submits it only after an explicit click.
 *
 * The URL itself is never put in React state — only a `ready`/`unsupported`
 * classification is. Raw URL data is held only in the click handler while the
 * direct request is in flight; it is never put in React or TanStack Query
 * state, satisfying the
 * never-render/never-persist requirement in docs/SECURITY_BOUNDARY.md.
 */
export function Popup() {
  const [tabPhase, setTabPhase] = useState<TabPhase>('checking');
  const scanButtonRef = useRef<HTMLButtonElement>(null);
  const [isPending, setIsPending] = useState(false);
  const [scanResponse, setScanResponse] = useState<ScanResponse | null>(null);
  const [apiError, setApiError] = useState<NormalizedApiError | null>(null);

  useEffect(() => {
    let cancelled = false;
    void getActiveTabUrl().then(({ scannable }) => {
      if (cancelled) {
        return;
      }
      if (scannable) {
        setTabPhase('ready');
      } else {
        setTabPhase('unsupported');
      }
    });
    return () => {
      cancelled = true;
    };
  }, []);

  // Keyboard users land on the primary action as soon as it exists, and get
  // focus back on it after any attempt settles — mirroring Scanner.tsx, there
  // is no input field here for a server error to blame instead.
  useEffect(() => {
    if (tabPhase === 'ready') {
      scanButtonRef.current?.focus();
    }
  }, [tabPhase]);

  useEffect(() => {
    if (!isPending && (scanResponse !== null || apiError !== null)) {
      scanButtonRef.current?.focus();
    }
  }, [isPending, scanResponse, apiError]);

  async function handleScan() {
    if (tabPhase !== 'ready' || isPending) {
      return;
    }

    setIsPending(true);
    setScanResponse(null);
    setApiError(null);

    try {
      const lookup = await getActiveTabUrl();
      if (!lookup.scannable) {
        setTabPhase('unsupported');
        return;
      }

      setScanResponse(await postScan(lookup.url));
    } catch (error) {
      setApiError(normalizeApiError(error));
    } finally {
      setIsPending(false);
    }
  }

  const canScan = tabPhase === 'ready' && !isPending;

  return (
    <main className="popup-shell">
      <header className="popup-brand">
        <img src="./icons/icon-32.png" alt="" className="popup-brand-mark" />
        <div>
          <p className="popup-eyebrow">Tab analysis</p>
          <h1>LinkSentry</h1>
        </div>
      </header>

      <p id={STATUS_ID} role="status" aria-live="polite" className="popup-tab-status">
        <span aria-hidden="true" className="popup-status-dot" />
        {statusText(tabPhase)}
      </p>

      <section className="popup-license-panel" aria-label="License status">
        <LicenseStatusCard />
      </section>

      <button
        ref={scanButtonRef}
        type="button"
        onClick={() => void handleScan()}
        disabled={!canScan}
        aria-describedby={STATUS_ID}
        className="popup-primary-action"
      >
        {isPending ? 'Scanning…' : scanResponse ? 'Scan again' : 'Scan this tab'}
      </button>

      {apiError ? (
        <div role="alert" className="popup-error">
          <p>{displayMessage(apiError)}</p>
        </div>
      ) : null}

      {scanResponse ? (
        <section className="popup-result" aria-live="polite">
          <div className="popup-score-card">
            <div>
              <p className="popup-section-label">Analysis result</p>
              <p className="popup-score">
                <span>{scanResponse.data.score}</span>
                <small>/100</small>
              </p>
            </div>
            <RiskBadge riskLevel={scanResponse.data.riskLevel} />
          </div>

          <div className="popup-findings">
            <div className="popup-section-heading">
              <h2>Signals detected</h2>
              <span>{scanResponse.data.findings.length}</span>
            </div>
            <FindingsList findings={scanResponse.data.findings} />
          </div>

          {/* Only risk level, score, registrable domain, and finding titles are
              handed on — never scanResponse.data.input or .scanId. */}
          <NextSteps
            riskLevel={scanResponse.data.riskLevel}
            score={scanResponse.data.score}
            registrableDomain={scanResponse.data.normalized.registrableDomain}
            findings={scanResponse.data.findings}
          />
        </section>
      ) : null}
    </main>
  );
}
