import { createContext } from 'react';

import type { AuthUser } from '@/features/auth/api/auth';

export interface AuthContextValue {
  readonly user: AuthUser | null;
  readonly expiresAt: string | null;
  readonly isLoading: boolean;
  readonly isAuthenticated: boolean;
  readonly register: (email: string, password: string) => Promise<void>;
  readonly login: (email: string, password: string) => Promise<void>;
  readonly logout: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | null>(null);
