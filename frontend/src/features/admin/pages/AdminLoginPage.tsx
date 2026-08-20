import { useState, type FormEvent } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router';

import { useAdminAuth } from '@/features/admin/context/useAdminAuth';
import { adminLoginRequestSchema } from '@/features/admin/schemas/adminLoginRequest';
import { normalizeApiError, type NormalizedApiError } from '@/lib/api/errors';

/** Minimal, polished admin sign-in form. Not the end-user auth surface — no register/OTP UI here. */
export function AdminLoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const auth = useAdminAuth();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [validationError, setValidationError] = useState<string | null>(null);
  const [apiError, setApiError] = useState<NormalizedApiError | null>(null);

  if (auth.isAuthenticated) {
    // ".." (the dashboard's index route, a sibling of "login") rather than a hardcoded "/admin":
    // this page does not need to know it is mounted at that prefix.
    const redirectTo = (location.state as { from?: string } | null)?.from ?? '..';
    return <Navigate to={redirectTo} replace />;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setValidationError(null);
    setApiError(null);

    const parsed = adminLoginRequestSchema.safeParse({ username, password });
    if (!parsed.success) {
      setValidationError(parsed.error.issues[0]?.message ?? 'Check the form and try again.');
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

  const activeError = validationError ?? apiError?.message ?? null;

  return (
    <div className="flex min-h-dvh items-center justify-center px-4 py-10">
      <div className="w-full max-w-sm">
        <p className="text-accent-400 font-mono text-sm">LinkSentry</p>
        <h1 className="text-ink-100 mt-2 text-3xl font-semibold tracking-tight">Admin sign-in</h1>
        <p className="text-ink-300 mt-3 text-sm">
          Restricted to administrators. This is not the LinkSentry app.
        </p>

        <form
          onSubmit={handleSubmit}
          noValidate
          className="border-ink-700 bg-ink-900/40 mt-7 space-y-4 rounded-xl border p-5"
        >
          <div>
            <label htmlFor="admin-username" className="text-ink-100 block text-sm font-medium">
              Username
            </label>
            <input
              id="admin-username"
              name="username"
              type="text"
              autoComplete="username"
              autoFocus
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              className="border-ink-800 bg-ink-950 text-ink-100 mt-2 w-full rounded-lg border px-3.5 py-2.5 text-sm"
            />
          </div>
          <div>
            <label htmlFor="admin-password" className="text-ink-100 block text-sm font-medium">
              Password
            </label>
            <input
              id="admin-password"
              name="password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              className="border-ink-800 bg-ink-950 text-ink-100 mt-2 w-full rounded-lg border px-3.5 py-2.5 text-sm"
            />
          </div>

          {activeError ? (
            <p role="alert" className="text-sm text-rose-400">
              {activeError}
            </p>
          ) : null}

          <button
            type="submit"
            disabled={isSubmitting}
            className="bg-accent-500 text-ink-950 w-full rounded-lg px-5 py-2.5 text-sm font-semibold disabled:cursor-not-allowed disabled:opacity-50"
          >
            {isSubmitting ? 'Signing in…' : 'Sign in'}
          </button>
        </form>
      </div>
    </div>
  );
}
