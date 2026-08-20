import { useState } from 'react';

import { Badge } from '@/components/ui/Badge';
import { useLicense } from '@/features/license/context/useLicense';
import type { DeviceState } from '@/features/license/schemas/deviceResponse';

const STATE_TEXT: Record<DeviceState, string> = {
  PENDING: 'Trial',
  LICENSED: 'Licensed',
  EXPIRED: 'Expired',
  REVOKED: 'Revoked',
};

const STATE_TONE: Record<DeviceState, 'muted' | 'low' | 'moderate' | 'critical'> = {
  PENDING: 'muted',
  LICENSED: 'low',
  EXPIRED: 'moderate',
  REVOKED: 'critical',
};

const STATE_DESCRIPTION: Record<DeviceState, string> = {
  PENDING:
    'This installation has not been granted a license yet. It can still scan under the free trial allowance.',
  LICENSED: 'This installation currently has full access.',
  EXPIRED: "This installation's license has expired. It has fallen back to the free trial allowance.",
  REVOKED: 'This installation has been revoked. It has fallen back to the free trial allowance.',
};

type CopyStatus = 'idle' | 'copied' | 'failed';

/**
 * Full status card: state, a safe Copy activation code action while unlicensed, and license
 * expiry while licensed. Used on the dedicated License page and in the extension popup. Never
 * renders the device credential — only the public activation code.
 */
export function LicenseStatusCard() {
  const { state, activationCode, licenseExpiresAt, isLoading, refresh } = useLicense();
  const [copyStatus, setCopyStatus] = useState<CopyStatus>('idle');
  const [isRefreshing, setIsRefreshing] = useState(false);

  async function handleCopy(code: string) {
    try {
      await navigator.clipboard.writeText(code);
      setCopyStatus('copied');
    } catch {
      setCopyStatus('failed');
    }
  }

  async function handleRefresh() {
    setIsRefreshing(true);
    setCopyStatus('idle');
    try {
      await refresh();
    } finally {
      setIsRefreshing(false);
    }
  }

  if (isLoading || state === null) {
    return (
      <p role="status" className="text-ink-300 text-sm">
        Checking this installation…
      </p>
    );
  }

  return (
    <div className="border-ink-700 bg-ink-900/40 space-y-4 rounded-xl border p-5">
      <div className="flex items-center gap-2">
        <Badge tone={STATE_TONE[state]}>{STATE_TEXT[state]}</Badge>
        {state === 'LICENSED' ? (
          <span className="text-ink-300 text-sm">
            {licenseExpiresAt
              ? `Renews or expires ${new Date(licenseExpiresAt).toLocaleDateString()}`
              : 'No expiry'}
          </span>
        ) : null}
      </div>

      <p className="text-ink-300 text-sm">{STATE_DESCRIPTION[state]}</p>

      {state === 'EXPIRED' && licenseExpiresAt ? (
        <p className="text-ink-500 text-xs">Expired {new Date(licenseExpiresAt).toLocaleDateString()}.</p>
      ) : null}

      {state !== 'LICENSED' && activationCode ? (
        <div className="border-ink-800 bg-ink-950/50 rounded-lg border p-4">
          <p className="text-ink-100 text-sm font-semibold">Pending activation</p>
          <p className="text-ink-300 mt-1 text-sm">
            Send this code to your administrator to request a license for this installation. Copying it does
            not by itself grant access — an administrator must attach it to a license.
          </p>
          <div className="mt-3 flex flex-wrap items-center gap-3">
            <code className="border-ink-800 bg-ink-950 text-ink-100 rounded-lg border px-3 py-2 font-mono text-base tracking-wider">
              {activationCode}
            </code>
            <button
              type="button"
              onClick={() => void handleCopy(activationCode)}
              className="border-ink-700 bg-ink-850 text-ink-100 hover:bg-ink-800 rounded-lg border px-4 py-2 text-sm font-medium transition-colors"
            >
              Copy activation code
            </button>
          </div>
          <p role="status" className="text-ink-300 mt-2 text-xs">
            {copyStatus === 'copied'
              ? 'Copied.'
              : copyStatus === 'failed'
                ? 'Could not copy — select the code manually.'
                : ''}
          </p>
        </div>
      ) : null}

      <button
        type="button"
        onClick={() => void handleRefresh()}
        disabled={isRefreshing}
        className="text-accent-400 hover:text-accent-300 text-sm font-medium underline underline-offset-4 disabled:opacity-50"
      >
        {isRefreshing ? 'Checking…' : 'Check status again'}
      </button>
    </div>
  );
}
