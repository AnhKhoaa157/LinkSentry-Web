import type { ReactNode } from 'react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import {
  adminLogin,
  adminLogout,
  getAdminCurrentSession,
  type AdminIdentity,
} from '@/features/admin/api/adminAuth';
import { AdminAuthContext, type AdminAuthContextValue } from '@/features/admin/context/AdminAuthContext';
import {
  ADMIN_UNAUTHORIZED_EVENT,
  clearAdminAccessToken,
  getAdminAccessToken,
  setAdminAccessToken,
} from '@/features/admin/sessionStorage';

/**
 * Owns only safe admin identity in React. The bearer value is read from and written to
 * sessionStorage as a transient local variable and is never exposed through this context.
 */
export function AdminAuthProvider({ children }: { readonly children: ReactNode }) {
  const [admin, setAdmin] = useState<AdminIdentity | null>(null);
  const [expiresAt, setExpiresAt] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(() => getAdminAccessToken() !== null);
  const bootstrapGeneration = useRef(0);

  useEffect(() => {
    const generation = ++bootstrapGeneration.current;
    let active = true;

    function clearSession() {
      // Invalidate a response that may already be in flight.
      bootstrapGeneration.current += 1;
      clearAdminAccessToken();
      setAdmin(null);
      setExpiresAt(null);
      setIsLoading(false);
    }

    function handleUnauthorized() {
      clearSession();
    }

    window.addEventListener(ADMIN_UNAUTHORIZED_EVENT, handleUnauthorized);

    if (getAdminAccessToken() === null) {
      return () => window.removeEventListener(ADMIN_UNAUTHORIZED_EVENT, handleUnauthorized);
    }

    void getAdminCurrentSession()
      .then((session) => {
        if (!active || bootstrapGeneration.current !== generation) {
          return;
        }
        setAdmin(session.admin);
        setExpiresAt(session.expiresAt);
      })
      .catch(() => {
        if (active && bootstrapGeneration.current === generation) {
          clearSession();
        }
      })
      .finally(() => {
        if (active && bootstrapGeneration.current === generation) {
          setIsLoading(false);
        }
      });

    return () => {
      active = false;
      window.removeEventListener(ADMIN_UNAUTHORIZED_EVENT, handleUnauthorized);
    };
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    const response = await adminLogin(username, password);
    bootstrapGeneration.current += 1;
    setAdminAccessToken(response.accessToken);
    setAdmin(response.admin);
    setExpiresAt(response.expiresAt);
  }, []);

  const logout = useCallback(async () => {
    bootstrapGeneration.current += 1;
    try {
      await adminLogout();
    } finally {
      clearAdminAccessToken();
      setAdmin(null);
      setExpiresAt(null);
      setIsLoading(false);
    }
  }, []);

  const value = useMemo<AdminAuthContextValue>(
    () => ({
      admin,
      expiresAt,
      isLoading,
      isAuthenticated: admin !== null,
      login,
      logout,
    }),
    [admin, expiresAt, isLoading, login, logout],
  );

  return <AdminAuthContext.Provider value={value}>{children}</AdminAuthContext.Provider>;
}
