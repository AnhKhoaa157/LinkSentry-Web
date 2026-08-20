import { NavLink } from 'react-router';

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

/** Compact license state indicator for the site header; links to the full License page. */
export function LicenseBadge() {
  const { state, isLoading } = useLicense();

  if (isLoading || state === null) {
    return <span className="text-ink-500 px-3 py-1.5 text-sm">Checking…</span>;
  }

  return (
    <NavLink
      to="/license"
      className="rounded-md px-1 py-1.5 text-sm"
      aria-label={`License status: ${STATE_TEXT[state]}`}
    >
      <Badge tone={STATE_TONE[state]}>{STATE_TEXT[state]}</Badge>
    </NavLink>
  );
}
