import { StatusDot } from '@/components/ui/StatusDot';
import { useHealth } from '@/features/health/api/useHealth';
import { normalizeApiError } from '@/lib/api/errors';
import { localizeApiError } from '@/lib/i18n/messages';
import { useLocale } from '@/lib/i18n/useLocale';

/** Shows API reachability with explicit text, recovery, and live-state feedback. */
export function HealthStatus() {
  const { data, isPending, isError, error, refetch, isFetching } = useHealth();
  const { t } = useLocale();

  const label = isPending
    ? t('health.checking')
    : isError
      ? t('health.offline')
      : (data?.status ?? t('health.unknown'));
  const state = isPending ? 'pending' : isError ? 'offline' : 'online';

  return (
    <div className="flex flex-wrap items-center justify-between gap-4" aria-live="polite">
      <p className="flex items-center gap-3 text-sm">
        <StatusDot state={state} />
        <span className="text-content-secondary font-medium">{t('health.api')}</span>
        <span className="text-content-primary font-bold">{label}</span>
        {data ? <span className="body-muted font-mono text-xs">{data.service}</span> : null}
      </p>

      {isError ? (
        <div className="flex flex-wrap items-center gap-3">
          <p role="alert" className="body-secondary text-sm">
            {localizeApiError(t, normalizeApiError(error))}
          </p>
          <button
            type="button"
            onClick={() => void refetch()}
            disabled={isFetching}
            className="ui-button ui-button-secondary min-h-9 px-3 text-xs"
          >
            {isFetching ? t('health.retrying') : t('health.retry')}
          </button>
        </div>
      ) : null}
    </div>
  );
}
