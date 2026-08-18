import { act, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { useAuth } from '@/features/auth/context/useAuth';
import { AUTH_UNAUTHORIZED_EVENT } from '@/features/auth/sessionStorage';
import { apiClient } from '@/lib/api/client';
import { renderWithProviders } from '@/test/renderWithProviders';

function deferred<T>() {
  let resolve!: (value: T | PromiseLike<T>) => void;
  const promise = new Promise<T>((resolvePromise) => {
    resolve = resolvePromise;
  });
  return { promise, resolve };
}

function AuthStateProbe() {
  const { expiresAt, user, login, logout } = useAuth();

  return (
    <>
      <output data-testid="auth-state">{user?.email ?? 'signed-out'}</output>
      <output data-testid="auth-expiry">{expiresAt ?? 'no-expiry'}</output>
      <button type="button" onClick={() => void login('new@example.com', 'new-password')}>
        Test login
      </button>
      <button type="button" onClick={() => void logout()}>
        Test logout
      </button>
    </>
  );
}

const bootstrappedSession = {
  data: {
    expiresAt: '2026-08-19T12:00:00Z',
    user: { email: 'bootstrapped@example.com' },
  },
};

const successfulLogin = {
  data: {
    accessToken: 'new-session-token',
    tokenType: 'Bearer' as const,
    expiresAt: '2026-08-20T12:00:00Z',
    user: { email: 'new@example.com' },
  },
};

describe('AuthProvider bootstrap invalidation', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('does not restore a delayed bootstrap session after explicit logout', async () => {
    sessionStorage.setItem('linksentry.accessToken', 'test-only-token');
    const bootstrap = deferred<typeof bootstrappedSession>();
    const getSpy = vi.spyOn(apiClient, 'get').mockImplementation(() => bootstrap.promise as never);
    const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValue({ data: undefined });
    const user = userEvent.setup();

    renderWithProviders(<AuthStateProbe />);
    await waitFor(() => expect(getSpy).toHaveBeenCalledWith('/api/v1/auth/session'));

    await user.click(screen.getByRole('button', { name: 'Test logout' }));
    await waitFor(() => expect(postSpy).toHaveBeenCalledWith('/api/v1/auth/logout'));

    await act(async () => {
      bootstrap.resolve(bootstrappedSession);
      await bootstrap.promise;
    });

    expect(screen.getByTestId('auth-state')).toHaveTextContent('signed-out');
    expect(document.body).not.toHaveTextContent('bootstrapped@example.com');
    expect(sessionStorage.getItem('linksentry.accessToken')).toBeNull();
  });

  it('does not let an old bootstrap overwrite a newly logged-in user', async () => {
    sessionStorage.setItem('linksentry.accessToken', 'old-session-token');
    const bootstrap = deferred<typeof bootstrappedSession>();
    const getSpy = vi.spyOn(apiClient, 'get').mockImplementation(() => bootstrap.promise as never);
    const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValue(successfulLogin);
    const user = userEvent.setup();

    renderWithProviders(<AuthStateProbe />);
    await waitFor(() => expect(getSpy).toHaveBeenCalledWith('/api/v1/auth/session'));

    await user.click(screen.getByRole('button', { name: 'Test login' }));
    await waitFor(() =>
      expect(postSpy).toHaveBeenCalledWith('/api/v1/auth/login', {
        email: 'new@example.com',
        password: 'new-password',
      }),
    );
    expect(screen.getByTestId('auth-state')).toHaveTextContent('new@example.com');
    expect(screen.getByTestId('auth-expiry')).toHaveTextContent('2026-08-20T12:00:00Z');

    await act(async () => {
      bootstrap.resolve(bootstrappedSession);
      await bootstrap.promise;
    });

    expect(screen.getByTestId('auth-state')).toHaveTextContent('new@example.com');
    expect(screen.getByTestId('auth-expiry')).toHaveTextContent('2026-08-20T12:00:00Z');
    expect(document.body).not.toHaveTextContent('bootstrapped@example.com');
    expect(document.body).not.toHaveTextContent('old-session-token');
    expect(sessionStorage.getItem('linksentry.accessToken')).toBe('new-session-token');
  });

  it('does not restore a delayed bootstrap session after an unauthorized event', async () => {
    sessionStorage.setItem('linksentry.accessToken', 'test-only-token');
    const bootstrap = deferred<typeof bootstrappedSession>();
    const getSpy = vi.spyOn(apiClient, 'get').mockImplementation(() => bootstrap.promise as never);

    renderWithProviders(<AuthStateProbe />);
    await waitFor(() => expect(getSpy).toHaveBeenCalledWith('/api/v1/auth/session'));

    window.dispatchEvent(new Event(AUTH_UNAUTHORIZED_EVENT));

    await act(async () => {
      bootstrap.resolve(bootstrappedSession);
      await bootstrap.promise;
    });

    expect(screen.getByTestId('auth-state')).toHaveTextContent('signed-out');
    expect(document.body).not.toHaveTextContent('bootstrapped@example.com');
    expect(sessionStorage.getItem('linksentry.accessToken')).toBeNull();
  });
});
