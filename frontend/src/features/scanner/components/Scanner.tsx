import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link } from 'react-router';

import { useScanMutation } from '@/features/scanner/api/useScanMutation';
import { ScanResult } from '@/features/scanner/components/ScanResult';
import { scanRequestSchema } from '@/features/scanner/schemas/scanRequest';
import { normalizeApiError, type NormalizedApiError } from '@/lib/api/errors';

const HINT_ID = 'scanner-url-hint';
const CLIENT_ERROR_ID = 'scanner-url-error';
const SERVER_FIELD_ERROR_ID = 'scanner-url-field-error';

/**
 * Rate limiting is a scanner condition, not a generic failure, so it gets
 * wording that tells the user what to do. Nothing about the quota is inferred:
 * no countdown, no `Retry-After` parsing, and no automatic retry — the backend
 * deliberately publishes no quota detail (docs/API_CONTRACT.md).
 */
const RATE_LIMITED_MESSAGE = 'Too many scan requests. Wait a moment before trying again.';

function displayMessage(error: NormalizedApiError): string {
  return error.code === 'RATE_LIMITED' ? RATE_LIMITED_MESSAGE : error.message;
}

/**
 * The real scanner: a validated URL form plus its result.
 *
 * States are explicit rather than inferred: idle (nothing submitted yet), a
 * client-side validation error (caught before any request is sent), loading,
 * an API error, and success. Never more than one of these is shown at once.
 */
export function Scanner() {
  const [url, setUrl] = useState('');
  const [clientError, setClientError] = useState<string | null>(null);
  const urlInputRef = useRef<HTMLInputElement>(null);
  const mutation = useScanMutation();

  const apiError = mutation.isError ? normalizeApiError(mutation.error) : null;
  // Only a `url` field error says anything about the input. A 429, a 500, or a
  // dropped connection is a request-level failure and must leave the field valid.
  const serverFieldError = apiError?.fieldErrors?.url ?? null;

  // A field error is only actionable inside the input, so send keyboard and
  // screen-reader users there when the server reports one. Typing cannot trigger
  // this: `handleChange` resets the mutation, so the value returns to null
  // before it can change again.
  useEffect(() => {
    if (serverFieldError !== null) {
      urlInputRef.current?.focus();
    }
  }, [serverFieldError]);

  function handleChange(value: string) {
    setUrl(value);
    if (clientError) {
      setClientError(null);
    }
    if (mutation.isSuccess || mutation.isError) {
      mutation.reset();
    }
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const parsed = scanRequestSchema.safeParse({ url });
    if (!parsed.success) {
      setClientError(parsed.error.issues[0]?.message ?? 'Enter a valid URL.');
      urlInputRef.current?.focus();
      return;
    }

    setClientError(null);
    mutation.mutate(parsed.data.url);
  }

  // Exactly one field message can be live: editing clears both sources, and a
  // submit clears the client error before the request is sent.
  const activeErrorId =
    clientError !== null ? CLIENT_ERROR_ID : serverFieldError !== null ? SERVER_FIELD_ERROR_ID : null;
  // The hint always stays described; the error ID is appended only while its
  // element is rendered, so the list never duplicates or dangles.
  const describedBy = activeErrorId === null ? HINT_ID : `${HINT_ID} ${activeErrorId}`;

  return (
    <div className="border-ink-700 bg-ink-900/40 rounded-xl border p-5 sm:p-6">
      <form onSubmit={handleSubmit} noValidate>
        <label htmlFor="scanner-url" className="text-ink-100 block text-sm font-medium">
          Suspicious URL
        </label>
        <p id={HINT_ID} className="text-ink-500 mt-1 text-sm">
          Paste a link to analyse its structure. LinkSentry never visits it.
        </p>

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
            placeholder="https://login.example.com.security-check.invalid/account"
            className="border-ink-800 bg-ink-950 text-ink-100 placeholder:text-ink-500 min-w-0 flex-1 rounded-lg border px-3.5 py-2.5 font-mono text-sm"
          />
          <button
            type="submit"
            disabled={mutation.isPending}
            className="bg-accent-500 text-ink-950 rounded-lg px-5 py-2.5 text-sm font-semibold disabled:cursor-not-allowed disabled:opacity-50"
          >
            {mutation.isPending ? 'Analyzing…' : 'Analyze'}
          </button>
        </div>

        {clientError ? (
          <p id={CLIENT_ERROR_ID} role="alert" className="mt-2 text-sm text-rose-400">
            {clientError}
          </p>
        ) : null}
      </form>

      {apiError ? (
        <div role="alert" className="mt-5 rounded-lg border border-rose-600/40 bg-rose-500/10 p-4">
          <p className="text-sm font-medium text-rose-400">{displayMessage(apiError)}</p>
          {serverFieldError ? (
            <p id={SERVER_FIELD_ERROR_ID} className="mt-1 text-sm text-rose-400/80">
              {serverFieldError}
            </p>
          ) : null}
        </div>
      ) : null}

      {mutation.isSuccess ? (
        <>
          <ScanResult data={mutation.data.data} />
          <div className="border-ink-800 mt-5 border-t pt-4">
            <Link
              to={`/scans/${encodeURIComponent(mutation.data.data.scanId)}`}
              className="text-accent-400 hover:text-accent-300 text-sm font-medium underline underline-offset-4"
            >
              Open shareable result
            </Link>
            <p className="text-ink-500 mt-1 text-xs">
              Anyone with this opaque link can view the result for the configured period (30 days by default).
            </p>
          </div>
        </>
      ) : null}
    </div>
  );
}
