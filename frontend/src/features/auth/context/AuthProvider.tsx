import type { ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import {
  getCurrentSession,
  login as loginRequest,
  logout as logoutRequest,
  register as registerRequest,
  resendRegistrationCode as resendRegistrationCodeRequest,
  verifyRegistration as verifyRegistrationRequest,
  type AuthUser,
} from '@/features/auth/api/auth';
import { AuthContext, type AuthContextValue } from '@/features/auth/context/AuthContext';
import {
  AUTH_UNAUTHORIZED_EVENT,
  clearAccessToken,
  getAccessToken,
  setAccessToken,
} from '@/features/auth/sessionStorage';

/**
 * Owns only safe session identity in React. The bearer value is read from and
 * written to sessionStorage as a transient local variable and is never exposed
 * through this context.
 */
export function AuthProvider({ children }: { readonly children: ReactNode }) {
  const queryClient = useQueryClient();
  const [user, setUser] = useState<AuthUser | null>(null);
  const [expiresAt, setExpiresAt] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(() => getAccessToken() !== null);
  const bootstrapGeneration = useRef(0);

  useEffect(() => {
    const generation = ++bootstrapGeneration.current;
    let active = true;

    function clearSession() {
      // Invalidate a response that may already be in flight. The request is
      // intentionally not retained here; only its generation is compared.
      bootstrapGeneration.current += 1;
      clearAccessToken();
      // A logout/401 must not leave a private result or a submitted URL in the
      // client-side server-state cache for the next browser identity.
      queryClient.clear();
      setUser(null);
      setExpiresAt(null);
      setIsLoading(false);
    }

    function handleUnauthorized() {
      clearSession();
    }

    window.addEventListener(AUTH_UNAUTHORIZED_EVENT, handleUnauthorized);

    if (getAccessToken() === null) {
      return () => window.removeEventListener(AUTH_UNAUTHORIZED_EVENT, handleUnauthorized);
    }

    void getCurrentSession()
      .then((session) => {
        if (!active || bootstrapGeneration.current !== generation) {
          return;
        }
        setUser(session.user);
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
      window.removeEventListener(AUTH_UNAUTHORIZED_EVENT, handleUnauthorized);
    };
  }, [queryClient]);

  const completeAuthentication = useCallback(
    async (request: Promise<Awaited<ReturnType<typeof loginRequest>>>) => {
      const response = await request;
      bootstrapGeneration.current += 1;
      setAccessToken(response.accessToken);
      // Do not carry anonymous request state or private results across an
      // identity transition.
      queryClient.clear();
      setUser(response.user);
      setExpiresAt(response.expiresAt);
    },
    [queryClient],
  );

  const register = useCallback(async (email: string, password: string) => {
    return registerRequest(email, password);
  }, []);

  const verifyRegistration = useCallback(
    async (email: string, code: string) => {
      await completeAuthentication(verifyRegistrationRequest(email, code));
    },
    [completeAuthentication],
  );

  const resendRegistrationCode = useCallback(async (email: string) => {
    return resendRegistrationCodeRequest(email);
  }, []);

  const login = useCallback(
    async (email: string, password: string) => {
      await completeAuthentication(loginRequest(email, password));
    },
    [completeAuthentication],
  );

  const logout = useCallback(async () => {
    bootstrapGeneration.current += 1;
    try {
      await logoutRequest();
    } finally {
      clearAccessToken();
      queryClient.clear();
      setUser(null);
      setExpiresAt(null);
      setIsLoading(false);
    }
  }, [queryClient]);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      expiresAt,
      isLoading,
      isAuthenticated: user !== null,
      register,
      verifyRegistration,
      resendRegistrationCode,
      login,
      logout,
    }),
    [user, expiresAt, isLoading, register, verifyRegistration, resendRegistrationCode, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
