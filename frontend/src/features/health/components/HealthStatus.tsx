import { useHealth } from '@/features/health/api/useHealth';
import { StatusDot } from '@/components/ui/StatusDot';
import { normalizeApiError } from '@/lib/api/errors';

/**
 * Shows whether the API is reachable.
 *
 * The state is announced in text as well as by the coloured dot, and the region is
 * polite-live so a change is announced without stealing focus.
 */
export function HealthStatus() {
  const { data, isPending, isError, error, refetch, isFetching } = useHealth();

  const label = isPending ? 'Checking…' : isError ? 'Offline' : (data?.status ?? 'Unknown');
  const state = isPending ? 'pending' : isError ? 'offline' : 'online';

  return (
    <div className="flex flex-wrap items-center justify-between gap-3">
      <p className="flex items-center gap-2 text-sm" aria-live="polite">
        <StatusDot state={state} />
        <span className="text-ink-300">Backend API</span>
        <span className="text-ink-100 font-medium">{label}</span>
        {data ? <span className="text-ink-500 font-mono text-xs">{data.service}</span> : null}
      </p>

      {isError ? (
        <div className="flex flex-wrap items-center gap-3">
          <p role="alert" className="text-ink-300 text-sm">
            {normalizeApiError(error).message}
          </p>
          <button
            type="button"
            onClick={() => void refetch()}
            disabled={isFetching}
            className="border-ink-700 text-ink-100 hover:bg-ink-850 rounded-md border px-3 py-1.5 text-sm font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-60"
          >
            {isFetching ? 'Retrying…' : 'Retry'}
          </button>
        </div>
      ) : null}
    </div>
  );
}
