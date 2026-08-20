import { useState } from 'react';
import { useNavigate } from 'react-router';

import { Card } from '@/components/ui/Card';
import { AdminLicenseManagement } from '@/features/admin/components/AdminLicenseManagement';
import { useAdminAuth } from '@/features/admin/context/useAdminAuth';

/** Protected dashboard shell: identity, session expiry, logout, and license/device management. */
export function AdminDashboardPage() {
  const navigate = useNavigate();
  const auth = useAdminAuth();
  const [isLoggingOut, setIsLoggingOut] = useState(false);

  async function handleLogout() {
    setIsLoggingOut(true);
    try {
      await auth.logout();
    } finally {
      // Relative "login" (a sibling route), not a hardcoded "/admin/login".
      navigate('login', { replace: true });
    }
  }

  return (
    <div className="mx-auto w-full max-w-4xl px-4 py-10 sm:px-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="text-accent-400 font-mono text-sm">LinkSentry</p>
          <h1 className="text-ink-100 mt-2 text-3xl font-semibold tracking-tight">Admin dashboard</h1>
        </div>
        <button
          type="button"
          onClick={() => void handleLogout()}
          disabled={isLoggingOut}
          className="border-ink-700 bg-ink-900/60 text-ink-100 rounded-lg border px-4 py-2 text-sm font-medium disabled:cursor-not-allowed disabled:opacity-50"
        >
          {isLoggingOut ? 'Signing out…' : 'Log out'}
        </button>
      </div>

      <Card className="mt-7" title="Signed in as">
        <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div>
            <dt className="text-ink-300 text-xs font-medium tracking-wide uppercase">Administrator</dt>
            <dd className="text-ink-100 mt-1 text-sm">{auth.admin?.username}</dd>
          </div>
          <div>
            <dt className="text-ink-300 text-xs font-medium tracking-wide uppercase">Session expires</dt>
            <dd className="text-ink-100 mt-1 text-sm">
              {auth.expiresAt ? new Date(auth.expiresAt).toLocaleString() : 'Unknown'}
            </dd>
          </div>
        </dl>
      </Card>

      <AdminLicenseManagement />
    </div>
  );
}
