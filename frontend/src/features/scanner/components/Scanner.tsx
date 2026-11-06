import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import { Link } from 'react-router';

import { useLicense } from '@/features/license/context/useLicense';
import { postScan } from '@/features/scanner/api/postScan';
import { ScanResult } from '@/features/scanner/components/ScanResult';
import { scanRequestSchema } from '@/features/scanner/schemas/scanRequest';
import type { ScanResponse } from '@/features/scanner/schemas/scanResponse';
import { normalizeApiError, type NormalizedApiError } from '@/lib/api/errors';
import { localizeApiError, localizeValidationMessage } from '@/lib/i18n/messages';
import { useLocale } from '@/lib/i18n/useLocale';
import { claimSharedLaunch, hasPendingSharedLaunch, startSharedLaunch } from '@/pwa/sharedLaunch';

type SharedNotice = 'receiving' | 'missing';

const HINT_ID = 'scanner-url-hint';
const CLIENT_ERROR_ID = 'scanner-url-error';
const SERVER_FIELD_ERROR_ID = 'scanner-url-field-error';

function displayMessage(t: ReturnType<typeof useLocale>['t'], error: NormalizedApiError): string {
  return localizeApiError(t, error);
}

/**
 * The real scanner: a validated text form plus a server-owned result. A link
 * shared into the installed app from the system share sheet is filled in and
 * scanned as soon as the device installation is ready.
 */
export function Scanner() {
  const { isLicensed, isLoading: isLicenseLoading } = useLicense();
  const { locale, t } = useLocale();
  const [url, setUrl] = useState('');
  const [clientError, setClientError] = useState<string | null>(null);
  const [isPending, setIsPending] = useState(false);
  const [scanResponse, setScanResponse] = useState<ScanResponse | null>(null);
  const [apiError, setApiError] = useState<NormalizedApiError | null>(null);
  const [sharedNotice, setSharedNotice] = useState<SharedNotice | null>(() =>
    hasPendingSharedLaunch() ? 'receiving' : null,
  );
  const urlInputRef = useRef<HTMLInputElement>(null);

  const serverFieldError = apiError?.fieldErrors?.url ?? null;

  useEffect(() => {
    if (serverFieldError !== null) {
      urlInputRef.current?.focus();
    }
  }, [serverFieldError]);

  const submitUrl = useCallback(
    (value: string) => {
      const parsed = scanRequestSchema.safeParse({ url: value });
      if (!parsed.success) {
        setClientError(localizeValidationMessage(t, parsed.error.issues[0]?.message));
        urlInputRef.current?.focus();
        return;
      }

      setClientError(null);
      setIsPending(true);
      setScanResponse(null);
      setApiError(null);
      void postScan(parsed.data.url, { locale })
        .then((response) => setScanResponse(response))
        .catch((error: unknown) => setApiError(normalizeApiError(error)))
        .finally(() => setIsPending(false));
    },
    [locale, t],
  );

  // Choosing LinkSentry in the share sheet is the request to scan. Taking the link
  // starts right away; the scan waits until the device installation that the trial
  // quota needs is ready.
  useEffect(() => {
    const shared = startSharedLaunch();
    if (shared === null || isLicenseLoading) {
      return undefined;
    }
    let active = true;
    void shared.then((result) => {
      if (!active || !claimSharedLaunch()) {
        return;
      }
      if (result.kind === 'link') {
        setSharedNotice(null);
        setUrl(result.url);
        submitUrl(result.url);
      } else {
        setSharedNotice(result.kind === 'missing' ? 'missing' : null);
      }
    });
    return () => {
      active = false;
    };
  }, [isLicenseLoading, submitUrl]);

  function handleChange(value: string) {
    setUrl(value);
    setClientError(null);
    setScanResponse(null);
    setApiError(null);
    setSharedNotice(null);
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    submitUrl(url);
  }

  const sharedStatus =
    sharedNotice === 'receiving'
      ? t('scanner.shared.receiving')
      : sharedNotice === 'missing'
        ? t('scanner.shared.missing')
        : null;

  const activeErrorId =
    clientError !== null ? CLIENT_ERROR_ID : serverFieldError !== null ? SERVER_FIELD_ERROR_ID : null;
  const describedBy = activeErrorId === null ? HINT_ID : HINT_ID + ' ' + activeErrorId;

  return (
    <section className="scanner-panel surface-panel overflow-hidden" aria-labelledby="scanner-heading">
      <div className="scanner-panel__header border-border-default border-b px-5 py-5 sm:px-7 sm:py-6">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="eyebrow">{t('scanner.eyebrow')}</p>
            <h2 id="scanner-heading" className="section-title text-content-primary mt-2">
              {t('scanner.formTitle')}
            </h2>
            <p id={HINT_ID} className="body-secondary mt-2 max-w-2xl text-sm leading-6">
              {t('scanner.hint')}
            </p>
          </div>
          <span className="scanner-panel__mode">{t('common.staticAnalysisOnly')}</span>
        </div>
      </div>

      <div className="p-5 sm:p-7">
        <form onSubmit={handleSubmit} noValidate>
          <label htmlFor="scanner-url" className="ui-label">
            {t('scanner.label')}
          </label>

          <div className="mt-3 flex flex-col gap-3 sm:flex-row">
            <input
              id="scanner-url"
              name="url"
              ref={urlInputRef}
              type="text"
              inputMode="url"
              autoComplete="off"
              spellCheck={false}
              value={url}
              onChange={(event) => handleChange(event.target.value)}
              aria-describedby={describedBy}
              aria-invalid={activeErrorId !== null}
              placeholder={t('scanner.placeholder')}
              className="ui-input min-w-0 flex-1 px-3.5 py-3 font-mono text-sm"
            />
            <button
              type="submit"
              disabled={isPending || isLicenseLoading}
              aria-busy={isPending}
              className="ui-button ui-button-primary shrink-0 px-6"
            >
              {isPending
                ? t('scanner.analyzing')
                : isLicenseLoading
                  ? t('scanner.initializing')
                  : t('scanner.submit')}
            </button>
          </div>

          {clientError ? (
            <p id={CLIENT_ERROR_ID} role="alert" className="text-danger mt-2 text-sm">
              {clientError}
            </p>
          ) : null}

          {sharedStatus ? (
            <p role="status" className="body-secondary mt-2 text-sm">
              {sharedStatus}
            </p>
          ) : null}
        </form>

        {apiError ? (
          <div role="alert" className="ui-alert mt-5 p-4">
            <p className="text-sm font-bold">{displayMessage(t, apiError)}</p>
            {serverFieldError ? (
              <p id={SERVER_FIELD_ERROR_ID} className="mt-1 text-sm opacity-90">
                {localizeValidationMessage(t, serverFieldError)}
              </p>
            ) : null}
            {apiError.code === 'ANONYMOUS_TRIAL_EXHAUSTED' ? (
              <Link to="/license" className="text-accent-strong mt-3 inline-flex text-sm font-bold underline">
                {t('scanner.trialResultLink')}
              </Link>
            ) : null}
          </div>
        ) : null}

        {scanResponse ? (
          <div className="border-border-default mt-8 border-t pt-7">
            <div className="flex flex-wrap items-end justify-between gap-3">
              <div>
                <p className="eyebrow">{t('scanner.resultHeading')}</p>
                <p className="body-secondary mt-1 text-sm">{t('scanner.resultDescription')}</p>
              </div>
              <p className="body-muted font-mono text-xs">
                {t('scanner.engineVersion', { version: scanResponse.meta.engineVersion })}
              </p>
            </div>

            <ScanResult data={scanResponse.data} />

            <div className="border-border-default mt-6 border-t pt-4">
              {isLicensed && scanResponse.data.scanId ? (
                <>
                  <Link
                    to={'/scans/' + encodeURIComponent(scanResponse.data.scanId)}
                    className="text-accent-strong text-sm font-bold underline underline-offset-4"
                  >
                    {t('scanner.savedResultLink')}
                  </Link>
                  <p className="body-muted mt-1 text-xs">{t('scanner.savedResultDescription')}</p>
                </>
              ) : (
                <>
                  <p className="body-muted text-xs">{t('scanner.trialResult')}</p>
                  <Link
                    to="/license"
                    className="text-accent-strong mt-1 inline-flex text-sm font-bold underline underline-offset-4"
                  >
                    {t('scanner.trialResultLink')}
                  </Link>
                </>
              )}
            </div>
          </div>
        ) : null}
      </div>
    </section>
  );
}
