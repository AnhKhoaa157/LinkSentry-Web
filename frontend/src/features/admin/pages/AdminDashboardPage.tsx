import { useState } from 'react';
import { useNavigate } from 'react-router';

import { LanguageSwitcher } from '@/components/ui/LanguageSwitcher';
import { Card } from '@/components/ui/Card';
import { AdminLicenseManagement } from '@/features/admin/components/AdminLicenseManagement';
import { useAdminAuth } from '@/features/admin/context/useAdminAuth';
import { useLocale } from '@/lib/i18n/useLocale';

/** Protected dashboard shell for administrator identity and license operations. */
export function AdminDashboardPage() {
  const navigate = useNavigate();
  const auth = useAdminAuth();
  const { locale, t } = useLocale();
  const [isLoggingOut, setIsLoggingOut] = useState(false);

  async function handleLogout() {
    setIsLoggingOut(true);
    try {
      await auth.logout();
    } finally {
      navigate('login', { replace: true });
    }
  }

  return (
    <div className="min-h-dvh px-4 py-8 sm:px-6">
      <header className="mx-auto flex max-w-5xl flex-wrap items-start justify-between gap-4">
        <div>
          <p className="eyebrow">{t('admin.dashboard.eyebrow')}</p>
          <h1 className="display-title text-content-primary mt-3 text-4xl sm:text-5xl">
            {t('admin.dashboard.title')}
          </h1>
          <p className="body-secondary mt-4 max-w-xl text-sm leading-6">{t('admin.dashboard.description')}</p>
        </div>
        <div className="flex items-center gap-3">
          <LanguageSwitcher />
          <button
            type="button"
            onClick={() => void handleLogout()}
            disabled={isLoggingOut}
            className="ui-button ui-button-secondary"
          >
            {isLoggingOut ? t('admin.dashboard.loggingOut') : t('admin.dashboard.logout')}
          </button>
        </div>
      </header>

      <main className="mx-auto mt-8 max-w-5xl space-y-6">
        <Card title={t('admin.dashboard.signedInAs')}>
          <dl className="grid grid-cols-1 gap-5 sm:grid-cols-2">
            <div>
              <dt className="body-muted text-xs font-bold tracking-wide uppercase">
                {t('admin.dashboard.administrator')}
              </dt>
              <dd className="text-content-primary mt-1 text-sm">{auth.admin?.username}</dd>
            </div>
            <div>
              <dt className="body-muted text-xs font-bold tracking-wide uppercase">
                {t('admin.dashboard.sessionExpires')}
              </dt>
              <dd className="text-content-primary mt-1 text-sm">
                {auth.expiresAt
                  ? new Intl.DateTimeFormat(locale === 'vi' ? 'vi-VN' : 'en-US', {
                      dateStyle: 'medium',
                      timeStyle: 'short',
                    }).format(new Date(auth.expiresAt))
                  : t('admin.dashboard.sessionUnknown')}
              </dd>
            </div>
          </dl>
        </Card>

        <AdminLicenseManagement />
      </main>
    </div>
  );
}
