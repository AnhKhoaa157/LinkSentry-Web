import { useState } from 'react';

import { postExplanation } from '@/features/explanation/api/postExplanation';
import { normalizeApiError, type NormalizedApiError } from '@/lib/api/errors';

interface Props {
  /**
   * The owning retained scan's opaque ID, or `null` for a result with no
   * persisted scan (an anonymous scan never has one). `null` renders nothing —
   * this control is never shown for an anonymous result.
   */
  readonly scanId: string | null;
}

type Status = 'idle' | 'loading' | 'success' | 'error';

/**
 * "Explain this result" — an optional, advisory AI explanation of one owned,
 * retained scan result.
 *
 * <p>AI is advisory only: the backend already computed the score, risk level,
 * and findings before this control ever renders, and this request cannot
 * change any of them. The explanation is rendered as inert plain text via a
 * `<p>` text node only — never `dangerouslySetInnerHTML`, never parsed as
 * Markdown or HTML, and never turned into a link — because model output is not
 * trusted content, the same rule the redacted scan URL already follows. See
 * `docs/SECURITY_BOUNDARY.md` and `docs/adr/0005-deepseek-scan-explanation-integration.md`.
 */
export function ExplainResult({ scanId }: Props) {
  const [status, setStatus] = useState<Status>('idle');
  const [explanation, setExplanation] = useState<string | null>(null);
  const [error, setError] = useState<NormalizedApiError | null>(null);

  if (scanId === null) {
    return null;
  }

  // Narrowed once into a stable local: `scanId`'s outer-scope type is still
  // `string | null`, and TypeScript does not carry the guard above into a
  // nested function declaration's closure.
  const ownedScanId: string = scanId;

  function handleExplain() {
    setStatus('loading');
    setError(null);
    void postExplanation(ownedScanId)
      .then((response) => {
        setExplanation(response.data.explanation);
        setStatus('success');
      })
      .catch((caught: unknown) => {
        setError(normalizeApiError(caught));
        setStatus('error');
      });
  }

  return (
    <div className="border-ink-800 mt-5 border-t pt-4">
      {status === 'idle' || status === 'loading' ? (
        <button
          type="button"
          onClick={handleExplain}
          disabled={status === 'loading'}
          aria-busy={status === 'loading'}
          className="border-ink-700 text-ink-100 hover:bg-ink-800 rounded-lg border px-4 py-2 text-sm font-medium disabled:cursor-not-allowed disabled:opacity-50"
        >
          {status === 'loading' ? 'Generating explanation…' : 'Explain this result'}
        </button>
      ) : null}

      {status === 'loading' ? (
        <p role="status" className="text-ink-500 mt-2 text-sm">
          Generating explanation…
        </p>
      ) : null}

      {status === 'error' && error ? (
        <div role="alert" className="mt-2">
          <p className="text-sm text-rose-400">{error.message}</p>
          <button
            type="button"
            onClick={handleExplain}
            className="text-accent-400 hover:text-accent-300 mt-1 text-sm font-medium underline underline-offset-4"
          >
            Try again
          </button>
        </div>
      ) : null}

      {status === 'success' && explanation !== null ? (
        <div aria-live="polite">
          <p className="text-ink-500 text-xs font-medium tracking-wide uppercase">
            AI explanation (advisory)
          </p>
          <p className="text-ink-100 mt-1 text-sm">{explanation}</p>
        </div>
      ) : null}
    </div>
  );
}
