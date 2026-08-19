import { createContext } from 'react';

import type { AuthUser, RegistrationStartedResponse } from '@/features/auth/api/auth';

export interface AuthContextValue {
  readonly user: AuthUser | null;
  readonly expiresAt: string | null;
  readonly isLoading: boolean;
  readonly isAuthenticated: boolean;
  readonly register: (email: string, password: string) => Promise<RegistrationStartedResponse>;
  readonly verifyRegistration: (email: string, code: string) => Promise<void>;
  readonly resendRegistrationCode: (email: string) => Promise<RegistrationStartedResponse>;
  readonly login: (email: string, password: string) => Promise<void>;
  readonly logout: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | null>(null);
