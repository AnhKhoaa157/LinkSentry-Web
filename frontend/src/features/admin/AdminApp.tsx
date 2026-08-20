import { Route, Routes } from 'react-router';

import { AdminProtectedRoute } from '@/features/admin/components/AdminProtectedRoute';
import { AdminAuthProvider } from '@/features/admin/context/AdminAuthProvider';
import { AdminDashboardPage } from '@/features/admin/pages/AdminDashboardPage';
import { AdminLoginPage } from '@/features/admin/pages/AdminLoginPage';

/**
 * Self-contained admin console: its own auth provider and route table, mounted once at
 * `/admin/*` outside the public `AppLayout` shell. Deliberately not nested inside
 * `features/auth`'s `AuthProvider` — an administrator is not an end-user account.
 */
export function AdminApp() {
  return (
    <AdminAuthProvider>
      <Routes>
        <Route path="login" element={<AdminLoginPage />} />
        <Route
          index
          element={
            <AdminProtectedRoute>
              <AdminDashboardPage />
            </AdminProtectedRoute>
          }
        />
      </Routes>
    </AdminAuthProvider>
  );
}
