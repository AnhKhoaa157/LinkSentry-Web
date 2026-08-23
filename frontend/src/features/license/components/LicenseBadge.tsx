import { NavLink } from 'react-router';

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

/** Compact license state indicator for the site header; links to the full License page. */
export function LicenseBadge() {
  const { state, isLoading } = useLicense();
  const { t } = useLocale();

  if (isLoading || state === null) {
    return <span className="text-content-muted px-3 py-2 text-sm">{t('common.checking')}</span>;
  }

  return (
    <NavLink
      to="/license"
      className="rounded-lg px-1 py-1.5 text-sm"
      aria-label={t('nav.installationStatus', { state: t(STATE_TEXT_KEY[state]) })}
    >
      <Badge tone={STATE_TONE[state]}>{t(STATE_TEXT_KEY[state])}</Badge>
    </NavLink>
  );
}
