import { createContext } from 'react';

import type { AdminIdentity } from '@/features/admin/api/adminAuth';

export interface AdminAuthContextValue {
  readonly admin: AdminIdentity | null;
  readonly expiresAt: string | null;
  readonly isLoading: boolean;
  readonly isAuthenticated: boolean;
  readonly login: (username: string, password: string) => Promise<void>;
  readonly logout: () => Promise<void>;
}

export const AdminAuthContext = createContext<AdminAuthContextValue | null>(null);
