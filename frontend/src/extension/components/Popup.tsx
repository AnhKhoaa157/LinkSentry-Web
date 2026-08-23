import { useEffect, useRef, useState } from 'react';

import { getActiveTabUrl } from '@/extension/lib/activeTabUrl';
import { LanguageSwitcher } from '@/extension/components/LanguageSwitcher';
import { ThemeSwitcher } from '@/components/ui/ThemeSwitcher';
import { ExplainResult } from '@/features/explanation/components/ExplainResult';
import { LicenseStatusCard } from '@/features/license/components/LicenseStatusCard';
import { postScan } from '@/features/scanner/api/postScan';
import type { ScanResponse } from '@/features/scanner/schemas/scanResponse';
import { FindingsList } from '@/features/scanner/components/FindingsList';
import { NextSteps } from '@/features/scanner/components/NextSteps';
import { RiskBadge } from '@/features/scanner/components/RiskBadge';
import { normalizeApiError, type NormalizedApiError } from '@/lib/api/errors';
import { env } from '@/lib/config/env';
import type { Translate } from '@/lib/i18n/LocaleContext';
import { useLocale } from '@/lib/i18n/useLocale';

const STATUS_ID = 'popup-tab-status';
const TITLE_ID = 'popup-title';

type TabPhase = 'checking' | 'ready' | 'unsupported';

function displayMessage(t: Translate, error: NormalizedApiError): string {
  return error.code === 'RATE_LIMITED' ? t('popup.status.rateLimited') : error.message;
}

function statusText(t: Translate, phase: TabPhase): string {
  if (phase === 'checking') {
    return t('popup.status.checking');
  }
  return phase === 'ready' ? t('popup.status.ready') : t('popup.status.unsupported');
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
  const { locale, t } = useLocale();
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

      setScanResponse(await postScan(lookup.url, { locale }));
    } catch (error) {
      setApiError(normalizeApiError(error));
    } finally {
      setIsPending(false);
    }
  }

  function openWebApp() {
    void chrome.tabs.create({ url: env.webAppUrl });
  }

  const canScan = tabPhase === 'ready' && !isPending;

  return (
    <main className="popup-shell">
      <section className="popup-command-deck" aria-labelledby={TITLE_ID}>
        <header className="popup-brand">
          <span className="popup-brand-mark-wrap" aria-hidden="true">
            <img src="./icons/logo-mark.png" alt="" className="popup-brand-mark" />
          </span>
          <div className="popup-brand-copy">
            <p className="popup-eyebrow">{t('popup.eyebrow')}</p>
            <h1 id={TITLE_ID}>{t('popup.title')}</h1>
          </div>
          <div className="popup-controls">
            <ThemeSwitcher />
            <LanguageSwitcher />
          </div>
        </header>

        <p id={STATUS_ID} role="status" aria-live="polite" className="popup-tab-status" data-phase={tabPhase}>
          <span aria-hidden="true" className="popup-status-dot" />
          {statusText(t, tabPhase)}
        </p>
      </section>

      <section className="popup-license-panel" aria-label={t('popup.license.panelLabel')}>
        <LicenseStatusCard variant="compact" />
      </section>

      <div className="popup-actions">
        <button
          ref={scanButtonRef}
          type="button"
          onClick={() => void handleScan()}
          disabled={!canScan}
          aria-describedby={STATUS_ID}
          className="popup-primary-action"
        >
          <svg aria-hidden="true" viewBox="0 0 24 24" focusable="false">
            <path d="M12 3v3m0 12v3M3 12h3m12 0h3m-3.64-6.36-2.12 2.12M8.76 15.24l-2.12 2.12m0-11.72 2.12 2.12m6.48 7.48 2.12 2.12" />
            <circle cx="12" cy="12" r="3.5" />
          </svg>
          <span>
            {isPending
              ? t('popup.scan.button.scanning')
              : scanResponse
                ? t('popup.scan.button.scanAgain')
                : t('popup.scan.button.scan')}
          </span>
        </button>

        <button type="button" onClick={openWebApp} className="popup-web-action">
          <span>{t('popup.web.open')}</span>
          <svg aria-hidden="true" viewBox="0 0 24 24" focusable="false">
            <path d="M14 5h5v5M19 5l-8 8M19 13v5a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1h5" />
          </svg>
        </button>
      </div>

      {apiError ? (
        <div role="alert" className="popup-error">
          <p>{displayMessage(t, apiError)}</p>
        </div>
      ) : null}

      {scanResponse ? (
        <section className="popup-result" aria-live="polite">
          <div className="popup-score-card">
            <div className="popup-score-card__topline">
              <p className="popup-section-label">{t('popup.result.label')}</p>
              <RiskBadge riskLevel={scanResponse.data.riskLevel} />
            </div>
            <div className="popup-score-card__body">
              <p className="popup-score">
                <span>{scanResponse.data.score}</span>
                <small>/100</small>
              </p>
              <div className="popup-score-track" aria-hidden="true">
                <span style={{ width: `${scanResponse.data.score}%` }} />
              </div>
            </div>
          </div>

          <div className="popup-findings">
            <div className="popup-section-heading">
              <h2>{t('popup.result.findingsHeading')}</h2>
              <span>{scanResponse.data.findings.length}</span>
            </div>
            <FindingsList findings={scanResponse.data.findings} />
          </div>

          {/* Only risk level, score, registrable domain, and finding titles are
              handed on — never scanResponse.data.input. */}
          <NextSteps
            riskLevel={scanResponse.data.riskLevel}
            score={scanResponse.data.score}
            registrableDomain={scanResponse.data.normalized.registrableDomain}
            findings={scanResponse.data.findings}
          />

          {/* AI advisory is only ever reachable for a licensed device: `scanId` is
              `null` for every trial (unlicensed) scan, since the backend only
              persists scan history for a licensed device's request
              (docs/ARCHITECTURE.md §5). `ExplainResult` itself also self-gates on
              a `null` id, so this mirrors the same server-truth check
              `Scanner.tsx` already uses on the web — a trial device sees only the
              core scan flow above, with no AI section at all. */}
          <ExplainResult scanId={scanResponse.data.scanId} />
        </section>
      ) : null}
    </main>
  );
}
