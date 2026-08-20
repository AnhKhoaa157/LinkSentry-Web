import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router';

import { useAdminAuth } from '@/features/admin/context/useAdminAuth';

/** Redirects to admin sign-in when no admin session is active. */
export function AdminProtectedRoute({ children }: { readonly children: ReactNode }) {
  const auth = useAdminAuth();
  const location = useLocation();

  if (auth.isLoading) {
    return (
      <div className="flex min-h-dvh items-center justify-center">
        <p className="text-ink-300 text-sm">Loading…</p>
      </div>
    );
  }

  if (!auth.isAuthenticated) {
    // Relative "login" (a sibling route), not a hardcoded "/admin/login": this component does
    // not need to know it is mounted at that prefix.
    return <Navigate to="login" replace state={{ from: location.pathname }} />;
  }

  return <>{children}</>;
}
