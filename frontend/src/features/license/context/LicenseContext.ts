import { createContext } from 'react';

import type { DeviceState } from '@/features/license/schemas/deviceResponse';

export interface LicenseContextValue {
  /** `null` only while the very first bootstrap/status check has not yet settled. */
  readonly state: DeviceState | null;
  readonly activationCode: string | null;
  readonly licenseExpiresAt: string | null;
  readonly isLoading: boolean;
  readonly isLicensed: boolean;
  /** Re-checks status against the server, e.g. after the user has sent their code to an admin. */
  readonly refresh: () => Promise<void>;
}

export const LicenseContext = createContext<LicenseContextValue | null>(null);
