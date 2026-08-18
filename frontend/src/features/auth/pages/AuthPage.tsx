import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router';

import { useAuth } from '@/features/auth/context/useAuth';
import { loginRequestSchema, registerRequestSchema } from '@/features/auth/schemas/authRequest';
import { normalizeApiError, type NormalizedApiError } from '@/lib/api/errors';

type Mode = 'login' | 'register';

/** Minimal email/password account form with safe, plain-text errors. */
export function AuthPage() {
  const navigate = useNavigate();
  const auth = useAuth();
  const [mode, setMode] = useState<Mode>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [validationError, setValidationError] = useState<string | null>(null);
  const [apiError, setApiError] = useState<NormalizedApiError | null>(null);

  function switchMode(nextMode: Mode) {
    setMode(nextMode);
    setValidationError(null);
    setApiError(null);
    setConfirmation('');
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setValidationError(null);
    setApiError(null);

    const parsed =
      mode === 'register'
        ? registerRequestSchema.safeParse({ email, password, confirmation })
        : loginRequestSchema.safeParse({ email, password });
    if (!parsed.success) {
      setValidationError(parsed.error.issues[0]?.message ?? 'Check the form and try again.');
      return;
    }

    try {
      if (mode === 'register') {
        await auth.register(parsed.data.email, parsed.data.password);
      } else {
        await auth.login(parsed.data.email, parsed.data.password);
      }
      navigate('/', { replace: true });
    } catch (error) {
      setApiError(normalizeApiError(error));
    }
  }

  const activeError = validationError ?? apiError?.message ?? null;
  const title = mode === 'register' ? 'Create your account' : 'Sign in to LinkSentry';

  return (
    <div className="mx-auto max-w-md">
      <p className="text-accent-400 font-mono text-sm">Private scan history</p>
      <h1 className="mt-2 text-3xl font-semibold tracking-tight">{title}</h1>
      <p className="text-ink-300 mt-3 text-sm">
        Sign in to save scan results privately to your account. Anonymous scans remain available without an
        account, but are not saved.
      </p>

      <form
        onSubmit={handleSubmit}
        noValidate
        className="border-ink-700 bg-ink-900/40 mt-7 space-y-4 rounded-xl border p-5"
      >
        <div>
          <label htmlFor="auth-email" className="text-ink-100 block text-sm font-medium">
            Email
          </label>
          <input
            id="auth-email"
            name="email"
            type="email"
            autoComplete="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            className="border-ink-800 bg-ink-950 text-ink-100 mt-2 w-full rounded-lg border px-3.5 py-2.5 text-sm"
          />
        </div>
        <div>
          <label htmlFor="auth-password" className="text-ink-100 block text-sm font-medium">
            Password
          </label>
          <input
            id="auth-password"
            name="password"
            type="password"
            autoComplete={mode === 'register' ? 'new-password' : 'current-password'}
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            className="border-ink-800 bg-ink-950 text-ink-100 mt-2 w-full rounded-lg border px-3.5 py-2.5 text-sm"
          />
        </div>

        {mode === 'register' ? (
          <div>
            <label htmlFor="auth-confirmation" className="text-ink-100 block text-sm font-medium">
              Confirm password
            </label>
            <input
              id="auth-confirmation"
              name="confirmation"
              type="password"
              autoComplete="new-password"
              value={confirmation}
              onChange={(event) => setConfirmation(event.target.value)}
              className="border-ink-800 bg-ink-950 text-ink-100 mt-2 w-full rounded-lg border px-3.5 py-2.5 text-sm"
            />
          </div>
        ) : null}

        {activeError ? (
          <p role="alert" className="text-sm text-rose-400">
            {activeError}
          </p>
        ) : null}

        <button
          type="submit"
          disabled={auth.isLoading}
          className="bg-accent-500 text-ink-950 w-full rounded-lg px-5 py-2.5 text-sm font-semibold disabled:cursor-not-allowed disabled:opacity-50"
        >
          {mode === 'register' ? 'Create account' : 'Sign in'}
        </button>
      </form>

      <p className="text-ink-300 mt-5 text-center text-sm">
        {mode === 'register' ? 'Already have an account?' : 'Need an account?'}{' '}
        <button
          type="button"
          onClick={() => switchMode(mode === 'register' ? 'login' : 'register')}
          className="text-accent-400 underline underline-offset-4"
        >
          {mode === 'register' ? 'Sign in' : 'Register'}
        </button>
      </p>
      <p className="mt-4 text-center text-sm">
        <Link to="/" className="text-accent-400 underline underline-offset-4">
          Back to scanner
        </Link>
      </p>
    </div>
  );
}
