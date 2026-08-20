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
    <main className="text-ink-100 w-full p-4">
      <div className="flex items-center gap-2">
        <img src="./icons/icon-32.png" alt="" className="size-7 rounded-lg" />
        <h1 className="text-ink-100 text-sm font-semibold">LinkSentry</h1>
      </div>

      <p id={STATUS_ID} role="status" aria-live="polite" className="text-ink-300 mt-2 text-sm">
        {statusText(tabPhase)}
      </p>

      <div className="mt-3">
        <LicenseStatusCard />
      </div>

      <button
        ref={scanButtonRef}
        type="button"
        onClick={() => void handleScan()}
        disabled={!canScan}
        aria-describedby={STATUS_ID}
        className="bg-accent-500 text-ink-950 mt-3 w-full rounded-lg px-4 py-2.5 text-sm font-semibold disabled:cursor-not-allowed disabled:opacity-50"
      >
        {isPending ? 'Scanning…' : scanResponse ? 'Scan again' : 'Scan this tab'}
      </button>

      {apiError ? (
        <div role="alert" className="mt-4 rounded-lg border border-rose-600/40 bg-rose-500/10 p-3">
          <p className="text-sm font-medium text-rose-400">{displayMessage(apiError)}</p>
        </div>
      ) : null}

      {scanResponse ? (
        <div className="mt-4 space-y-4" aria-live="polite">
          <div className="flex flex-wrap items-center gap-3">
            <p className="flex items-baseline gap-1">
              <span className="text-2xl font-bold tracking-tight">{scanResponse.data.score}</span>
              <span className="text-ink-500 text-xs">/100</span>
            </p>
            <RiskBadge riskLevel={scanResponse.data.riskLevel} />
          </div>

          <div>
            <h2 className="text-ink-100 text-sm font-semibold">Findings</h2>
            <div className="mt-2">
              <FindingsList findings={scanResponse.data.findings} />
            </div>
          </div>

          {/* Only risk level, score, registrable domain, and finding titles are
              handed on — never scanResponse.data.input or .scanId. */}
          <NextSteps
            riskLevel={scanResponse.data.riskLevel}
            score={scanResponse.data.score}
            registrableDomain={scanResponse.data.normalized.registrableDomain}
            findings={scanResponse.data.findings}
          />
        </div>
      ) : null}
    </main>
  );
}
