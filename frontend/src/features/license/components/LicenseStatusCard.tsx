import { useState } from 'react';

import { Badge } from '@/components/ui/Badge';
import { useLicense } from '@/features/license/context/useLicense';
import type { DeviceState } from '@/features/license/schemas/deviceResponse';
import type { TranslationKey } from '@/lib/i18n/translations';
import { useLocale } from '@/lib/i18n/useLocale';

const STATE_TEXT_KEY: Record<DeviceState, TranslationKey> = {
  PENDING: 'license.state.pending',
  LICENSED: 'license.state.licensed',
  EXPIRED: 'license.state.expired',
  REVOKED: 'license.state.revoked',
};

const STATE_TONE: Record<DeviceState, 'muted' | 'low' | 'moderate' | 'critical'> = {
  PENDING: 'muted',
  LICENSED: 'low',
  EXPIRED: 'moderate',
  REVOKED: 'critical',
};

const STATE_DESCRIPTION_KEY: Record<DeviceState, TranslationKey> = {
  PENDING: 'license.description.pending',
  LICENSED: 'license.description.licensed',
  EXPIRED: 'license.description.expired',
  REVOKED: 'license.description.revoked',
};

type CopyStatus = 'idle' | 'copied' | 'failed';

/**
 * Full status card: state, a safe Copy activation code action while unlicensed, and license
 * expiry while licensed. Used on the dedicated License page and in the extension popup. Never
 * renders the device credential — only the public activation code.
 */
export function LicenseStatusCard() {
  const { state, activationCode, licenseExpiresAt, isLoading, refresh } = useLicense();
  const { t } = useLocale();
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
        {t('license.checking')}
      </p>
    );
  }

  return (
    <div className="border-ink-700 bg-ink-900/40 space-y-4 rounded-xl border p-5">
      <div className="flex items-center gap-2">
        <Badge tone={STATE_TONE[state]}>{t(STATE_TEXT_KEY[state])}</Badge>
        {state === 'LICENSED' ? (
          <span className="text-ink-300 text-sm">
            {licenseExpiresAt
              ? t('license.renewsOrExpires', { date: new Date(licenseExpiresAt).toLocaleDateString() })
              : t('license.noExpiry')}
          </span>
        ) : null}
      </div>

      <p className="text-ink-300 text-sm">{t(STATE_DESCRIPTION_KEY[state])}</p>

      {state === 'EXPIRED' && licenseExpiresAt ? (
        <p className="text-ink-500 text-xs">
          {t('license.expiredOn', { date: new Date(licenseExpiresAt).toLocaleDateString() })}
        </p>
      ) : null}

      {state !== 'LICENSED' && activationCode ? (
        <div className="border-ink-800 bg-ink-950/50 rounded-lg border p-4">
          <p className="text-ink-100 text-sm font-semibold">{t('license.pendingActivation.title')}</p>
          <p className="text-ink-300 mt-1 text-sm">{t('license.pendingActivation.body')}</p>
          <div className="mt-3 flex flex-wrap items-center gap-3">
            <code className="border-ink-800 bg-ink-950 text-ink-100 rounded-lg border px-3 py-2 font-mono text-base tracking-wider">
              {activationCode}
            </code>
            <button
              type="button"
              onClick={() => void handleCopy(activationCode)}
              className="border-ink-700 bg-ink-850 text-ink-100 hover:bg-ink-800 rounded-lg border px-4 py-2 text-sm font-medium transition-colors"
            >
              {t('license.copyButton')}
            </button>
          </div>
          <p role="status" className="text-ink-300 mt-2 text-xs">
            {copyStatus === 'copied'
              ? t('license.copyStatus.copied')
              : copyStatus === 'failed'
                ? t('license.copyStatus.failed')
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
        {isRefreshing ? t('license.checkingAgain') : t('license.checkAgain')}
      </button>
    </div>
  );
}
