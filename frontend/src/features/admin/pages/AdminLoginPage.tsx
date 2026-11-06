import { useState, type FormEvent } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router';

import { Brand } from '@/components/layout/Brand';
import { LanguageSwitcher } from '@/components/ui/LanguageSwitcher';
import { ThemeSwitcher } from '@/components/ui/ThemeSwitcher';
import { useAdminAuth } from '@/features/admin/context/useAdminAuth';
import { adminLoginRequestSchema } from '@/features/admin/schemas/adminLoginRequest';
import { normalizeApiError, type NormalizedApiError } from '@/lib/api/errors';
import { localizeApiError, localizeValidationMessage } from '@/lib/i18n/messages';
import { useLocale } from '@/lib/i18n/useLocale';

/** Restricted administrator sign-in, separate from the public device-license flow. */
export function AdminLoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const auth = useAdminAuth();
  const { t } = useLocale();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [validationError, setValidationError] = useState<string | null>(null);
  const [apiError, setApiError] = useState<NormalizedApiError | null>(null);

  if (auth.isAuthenticated) {
    const redirectTo = (location.state as { from?: string } | null)?.from ?? '..';
    return <Navigate to={redirectTo} replace />;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setValidationError(null);
    setApiError(null);

    const parsed = adminLoginRequestSchema.safeParse({ username, password });
    if (!parsed.success) {
      setValidationError(localizeValidationMessage(t, parsed.error.issues[0]?.message));
      return;
    }

    setIsSubmitting(true);
    try {
      await auth.login(parsed.data.username, parsed.data.password);
      navigate('..', { replace: true });
    } catch (error) {
      setApiError(normalizeApiError(error));
    } finally {
      setIsSubmitting(false);
    }
  }

  const activeError = validationError ?? (apiError ? localizeApiError(t, apiError) : null);

  return (
    <div className="admin-shell min-h-dvh">
      <header className="admin-topbar backdrop-blur-xl">
        <div className="page-container flex flex-wrap items-center justify-between gap-3 py-3">
          <Brand />
          <div className="flex items-center gap-2.5">
            <ThemeSwitcher />
            <LanguageSwitcher />
          </div>
        </div>
      </header>
      <main className="admin-login-main page-container flex min-h-[calc(100dvh-4.5rem)] items-center justify-center py-10 sm:py-14">
        <div className="admin-login-card surface-panel w-full max-w-md p-6 sm:p-8">
          <p className="eyebrow">{t('admin.login.eyebrow')}</p>
          <h1 className="display-title text-content-primary mt-3 text-4xl">{t('admin.login.title')}</h1>
          <p className="body-secondary mt-4 text-sm leading-6">{t('admin.login.description')}</p>

          <form onSubmit={handleSubmit} noValidate className="admin-login-form mt-7 space-y-5">
            <div>
              <label htmlFor="admin-username" className="ui-label">
                {t('admin.login.username')}
              </label>
              <input
                id="admin-username"
                name="username"
                type="text"
                autoComplete="username"
                autoFocus
                value={username}
                onChange={(event) => setUsername(event.target.value)}
                className="ui-input mt-2 px-3.5 py-3 text-sm"
              />
            </div>
            <div>
              <label htmlFor="admin-password" className="ui-label">
                {t('admin.login.password')}
              </label>
              <input
                id="admin-password"
                name="password"
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                className="ui-input mt-2 px-3.5 py-3 text-sm"
              />
            </div>

            {activeError ? (
              <p role="alert" className="ui-alert p-3 text-sm">
                {activeError}
              </p>
            ) : null}

            <button type="submit" disabled={isSubmitting} className="ui-button ui-button-primary w-full">
              {isSubmitting ? t('admin.login.submitting') : t('admin.login.submit')}
            </button>
          </form>
        </div>
      </main>
    </div>
  );
}
