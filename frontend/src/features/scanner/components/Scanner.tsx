import { useState, type FormEvent } from 'react';

import { useScanMutation } from '@/features/scanner/api/useScanMutation';
import { ScanResult } from '@/features/scanner/components/ScanResult';
import { scanRequestSchema } from '@/features/scanner/schemas/scanRequest';
import { normalizeApiError } from '@/lib/api/errors';

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
  const mutation = useScanMutation();

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
      return;
    }

    setClientError(null);
    mutation.mutate(parsed.data.url);
  }

  const apiError = mutation.isError ? normalizeApiError(mutation.error) : null;
  const describedBy = clientError ? 'scanner-url-error' : 'scanner-url-hint';

  return (
    <div className="border-ink-700 bg-ink-900/40 rounded-xl border p-5 sm:p-6">
      <form onSubmit={handleSubmit} noValidate>
        <label htmlFor="scanner-url" className="text-ink-100 block text-sm font-medium">
          Suspicious URL
        </label>
        <p id="scanner-url-hint" className="text-ink-500 mt-1 text-sm">
          Paste a link to analyse its structure. LinkSentry never visits it.
        </p>

        <div className="mt-3 flex flex-col gap-3 sm:flex-row">
          <input
            id="scanner-url"
            name="url"
            type="text"
            inputMode="url"
            autoComplete="off"
            spellCheck={false}
            value={url}
            onChange={(event) => handleChange(event.target.value)}
            aria-describedby={describedBy}
            aria-invalid={clientError !== null}
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
          <p id="scanner-url-error" role="alert" className="mt-2 text-sm text-rose-400">
            {clientError}
          </p>
        ) : null}
      </form>

      {apiError ? (
        <div role="alert" className="mt-5 rounded-lg border border-rose-600/40 bg-rose-500/10 p-4">
          <p className="text-sm font-medium text-rose-400">{apiError.message}</p>
          {apiError.fieldErrors?.url ? (
            <p className="mt-1 text-sm text-rose-400/80">{apiError.fieldErrors.url}</p>
          ) : null}
        </div>
      ) : null}

      {mutation.isSuccess ? <ScanResult data={mutation.data.data} /> : null}
    </div>
  );
}
