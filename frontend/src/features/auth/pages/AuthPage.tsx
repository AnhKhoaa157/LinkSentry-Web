import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router';

import { useAuth } from '@/features/auth/context/useAuth';
import {
  loginRequestSchema,
  registerRequestSchema,
  registrationVerificationSchema,
} from '@/features/auth/schemas/authRequest';
import { normalizeApiError, type NormalizedApiError } from '@/lib/api/errors';

type Mode = 'login' | 'register';
type RegistrationStep = 'form' | 'verification';

/** Minimal email/password account form with safe, plain-text errors. */
export function AuthPage() {
  const navigate = useNavigate();
  const auth = useAuth();
  const [mode, setMode] = useState<Mode>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [verificationCode, setVerificationCode] = useState('');
  const [registrationStep, setRegistrationStep] = useState<RegistrationStep>('form');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [validationError, setValidationError] = useState<string | null>(null);
  const [apiError, setApiError] = useState<NormalizedApiError | null>(null);

  function switchMode(nextMode: Mode) {
    setMode(nextMode);
    setValidationError(null);
    setApiError(null);
    setConfirmation('');
    setVerificationCode('');
    setRegistrationStep('form');
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setValidationError(null);
    setApiError(null);

    if (mode === 'register' && registrationStep === 'verification') {
      const parsedCode = registrationVerificationSchema.safeParse({ email, code: verificationCode });
      if (!parsedCode.success) {
        setValidationError(parsedCode.error.issues[0]?.message ?? 'Enter the verification code.');
        return;
      }

      setIsSubmitting(true);
      try {
        await auth.verifyRegistration(parsedCode.data.email, parsedCode.data.code);
        navigate('/', { replace: true });
      } catch (error) {
        setApiError(normalizeApiError(error));
      } finally {
        setIsSubmitting(false);
      }
      return;
    }

    const parsed =
      mode === 'register'
        ? registerRequestSchema.safeParse({ email, password, confirmation })
        : loginRequestSchema.safeParse({ email, password });
    if (!parsed.success) {
      setValidationError(parsed.error.issues[0]?.message ?? 'Check the form and try again.');
      return;
    }

    setIsSubmitting(true);
    try {
      if (mode === 'register') {
        await auth.register(parsed.data.email, parsed.data.password);
        setRegistrationStep('verification');
        setPassword('');
        setConfirmation('');
      } else {
        await auth.login(parsed.data.email, parsed.data.password);
        navigate('/', { replace: true });
      }
    } catch (error) {
      setApiError(normalizeApiError(error));
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleResend() {
    setValidationError(null);
    setApiError(null);
    setIsSubmitting(true);
    try {
      await auth.resendRegistrationCode(email);
    } catch (error) {
      setApiError(normalizeApiError(error));
    } finally {
      setIsSubmitting(false);
    }
  }

  const activeError = validationError ?? apiError?.message ?? null;
  if (mode === 'register' && registrationStep === 'verification') {
    return (
      <div className="mx-auto max-w-md">
        <p className="text-accent-400 font-mono text-sm">One more step</p>
        <h1 className="mt-2 text-3xl font-semibold tracking-tight">Check your email</h1>
        <p className="text-ink-300 mt-3 text-sm">
          Enter the 6-digit code sent to <span className="text-ink-100 font-medium">{email}</span>. The code
          expires soon and is required before your account is created.
        </p>

        <form
          onSubmit={handleSubmit}
          noValidate
          className="border-ink-700 bg-ink-900/40 mt-7 space-y-4 rounded-xl border p-5"
        >
          <div>
            <label htmlFor="auth-verification-code" className="text-ink-100 block text-sm font-medium">
              Verification code
            </label>
            <input
              id="auth-verification-code"
              name="verificationCode"
              type="text"
              inputMode="numeric"
              autoComplete="one-time-code"
              maxLength={6}
              value={verificationCode}
              onChange={(event) => setVerificationCode(event.target.value.replace(/\D/g, ''))}
              className="border-ink-800 bg-ink-950 text-ink-100 mt-2 w-full rounded-lg border px-3.5 py-2.5 text-center font-mono text-lg tracking-[0.35em]"
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
            {isSubmitting ? 'Verifying…' : 'Verify email'}
          </button>
          <button
            type="button"
            onClick={() => void handleResend()}
            disabled={isSubmitting}
            className="text-accent-400 w-full text-sm underline underline-offset-4 disabled:opacity-50"
          >
            Resend code
          </button>
        </form>

        <button
          type="button"
          onClick={() => setRegistrationStep('form')}
          className="text-ink-300 hover:text-ink-100 mt-5 block w-full text-center text-sm underline underline-offset-4"
        >
          Use a different email
        </button>
      </div>
    );
  }

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
          disabled={auth.isLoading || isSubmitting}
          className="bg-accent-500 text-ink-950 w-full rounded-lg px-5 py-2.5 text-sm font-semibold disabled:cursor-not-allowed disabled:opacity-50"
        >
          {isSubmitting ? 'Please wait…' : mode === 'register' ? 'Create account' : 'Sign in'}
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
