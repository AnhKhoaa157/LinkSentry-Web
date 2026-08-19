import type { ReactNode } from 'react';
import { Link, useParams } from 'react-router';

import { ExplainResult } from '@/features/explanation/components/ExplainResult';
import { useScanQuery } from '@/features/scanner/api/useScanQuery';
import { ScanResult } from '@/features/scanner/components/ScanResult';
import { normalizeApiError } from '@/lib/api/errors';

const GENERIC_LOAD_ERROR = 'We could not load this saved scan. Please try again later.';

/** Renders a retained result loaded from an opaque scan permalink. */
export function ScanPage() {
  const { scanId } = useParams<{ scanId: string }>();
  const query = useScanQuery(scanId);

  if (query.isPending) {
    return <p role="status">Loading saved scan…</p>;
  }

  if (query.isError) {
    const apiError = normalizeApiError(query.error);
    if (apiError.code === 'UNAUTHORIZED') {
      return (
        <StateMessage title="Sign in to view this scan">
          Saved scan history is private to the account that created it.
          <br />
          <Link to="/auth" className="text-accent-400 underline underline-offset-4">
            Sign in or register
          </Link>
        </StateMessage>
      );
    }
    if (apiError.code === 'SCAN_NOT_FOUND') {
      return (
        <StateMessage title="Saved scan unavailable">
          This scan ID is invalid, expired, ownerless, or not available to the signed-in account.
        </StateMessage>
      );
    }
    if (apiError.code === 'RATE_LIMITED') {
      return (
        <StateMessage title="Too many requests">
          This link is being requested too quickly. Wait a moment and try again.
        </StateMessage>
      );
    }
    return <StateMessage title="Could not load saved scan">{GENERIC_LOAD_ERROR}</StateMessage>;
  }

  if (!query.data) {
    return (
      <StateMessage title="Saved scan unavailable">This scan result is no longer available.</StateMessage>
    );
  }

  return (
    <div>
      <p className="text-accent-400 font-mono text-sm">Saved result</p>
      <h1 className="mt-2 text-3xl font-semibold tracking-tight">Link analysis</h1>
      <p className="text-ink-300 mt-3 text-sm">
        This private result is available only to its owner during the configured retention period (30 days by
        default).
      </p>
      <ScanResult data={query.data.data} />
      <ExplainResult scanId={query.data.data.scanId} />
    </div>
  );
}

function StateMessage({ title, children }: { readonly title: string; readonly children: ReactNode }) {
  return (
    <div role="alert" className="max-w-xl">
      <p className="text-accent-400 font-mono text-sm">Scan history</p>
      <h1 className="mt-2 text-3xl font-semibold tracking-tight">{title}</h1>
      <p className="text-ink-300 mt-3 text-sm">{children}</p>
      <Link
        to="/"
        className="bg-accent-500 text-ink-950 hover:bg-accent-400 mt-7 inline-block rounded-lg px-5 py-2.5 text-sm font-semibold transition-colors"
      >
        Back to scanner
      </Link>
    </div>
  );
}
