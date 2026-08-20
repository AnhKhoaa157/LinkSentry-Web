import type { ReactNode } from 'react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import { bootstrapDevice, getDeviceStatus } from '@/features/license/api/device';
import { LicenseContext, type LicenseContextValue } from '@/features/license/context/LicenseContext';
import type { DeviceState } from '@/features/license/schemas/deviceResponse';
import { deviceCredentialStorage } from '@/lib/device/deviceCredentialStorage';
import { normalizeApiError } from '@/lib/api/errors';

interface LicenseProviderProps {
  readonly children: ReactNode;
  /** Cosmetic only, shown to an admin inspecting the device — never used in any access decision. */
  readonly clientLabel: string;
}

/**
 * Owns device installation bootstrap and status. On mount: use a stored credential if one exists,
 * otherwise create a new installation. A credential that the server no longer recognises
 * (`INVALID_DEVICE_CREDENTIAL`, e.g. after a database reset) is replaced by bootstrapping a fresh
 * installation; a recognised-but-unlicensed, expired, or revoked device is left exactly as the
 * server reports it and falls back to trial scanning — its credential is never cleared, since it
 * remains this device's valid identity.
 */
export function LicenseProvider({ children, clientLabel }: LicenseProviderProps) {
  const [state, setState] = useState<DeviceState | null>(null);
  const [activationCode, setActivationCode] = useState<string | null>(null);
  const [licenseExpiresAt, setLicenseExpiresAt] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const hasInitialized = useRef(false);

  const bootstrap = useCallback(async () => {
    const response = await bootstrapDevice(clientLabel);
    await deviceCredentialStorage.set(response.credential);
    setState('PENDING');
    setActivationCode(response.activationCode);
    setLicenseExpiresAt(null);
  }, [clientLabel]);

  const checkStatus = useCallback(async () => {
    try {
      const response = await getDeviceStatus();
      setState(response.state);
      setActivationCode(response.activationCode);
      setLicenseExpiresAt(response.licenseExpiresAt);
    } catch (error) {
      const apiError = normalizeApiError(error);
      if (apiError.code === 'INVALID_DEVICE_CREDENTIAL') {
        await deviceCredentialStorage.clear();
        await bootstrap();
      }
      // Any other failure (network error, 5xx, rate limited) leaves the existing
      // state and the stored credential untouched — a transient failure must not
      // look like, or cause, a revoked device.
    }
  }, [bootstrap]);

  useEffect(() => {
    // Bootstrap creates a device row as a side effect, so it must run at most once
    // per real mount — this guard, not the dependency array, is what protects that
    // in React's development Strict Mode double-invoke.
    if (hasInitialized.current) {
      return;
    }
    hasInitialized.current = true;

    void (async () => {
      const stored = await deviceCredentialStorage.get();
      if (stored === null) {
        await bootstrap().catch(() => {
          // Leave state null; the UI shows a safe loading/unknown fallback rather
          // than retrying in a loop.
        });
      } else {
        await checkStatus();
      }
      setIsLoading(false);
    })();
    // Runs once per mount by design; see the hasInitialized guard above.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const refresh = useCallback(async () => {
    await checkStatus();
  }, [checkStatus]);

  const value = useMemo<LicenseContextValue>(
    () => ({
      state,
      activationCode,
      licenseExpiresAt,
      isLoading,
      isLicensed: state === 'LICENSED',
      refresh,
    }),
    [state, activationCode, licenseExpiresAt, isLoading, refresh],
  );

  return <LicenseContext.Provider value={value}>{children}</LicenseContext.Provider>;
}
