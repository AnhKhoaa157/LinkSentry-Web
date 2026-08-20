import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { adminApiClient } from '@/features/admin/api/adminClient';
import { AdminAuthProvider } from '@/features/admin/context/AdminAuthProvider';
import { useAdminAuth } from '@/features/admin/context/useAdminAuth';
import { ADMIN_UNAUTHORIZED_EVENT } from '@/features/admin/sessionStorage';

function deferred<T>() {
  let resolve!: (value: T | PromiseLike<T>) => void;
  const promise = new Promise<T>((resolvePromise) => {
    resolve = resolvePromise;
  });
  return { promise, resolve };
}

function AdminAuthStateProbe() {
  const { expiresAt, admin, login, logout } = useAdminAuth();

  return (
    <>
      <output data-testid="admin-auth-state">{admin?.username ?? 'signed-out'}</output>
      <output data-testid="admin-auth-expiry">{expiresAt ?? 'no-expiry'}</output>
      <button type="button" onClick={() => void login('new-admin', 'new-password')}>
        Test login
      </button>
      <button type="button" onClick={() => void logout().catch(() => {})}>
        Test logout
      </button>
    </>
  );
}

function renderProbe() {
  return render(
    <AdminAuthProvider>
      <AdminAuthStateProbe />
    </AdminAuthProvider>,
  );
}

const bootstrappedSession = {
  data: {
    expiresAt: '2026-08-20T12:00:00Z',
    admin: { username: 'bootstrapped-admin' },
  },
};

const successfulLogin = {
  data: {
    accessToken: 'new-admin-session-token',
    tokenType: 'Bearer' as const,
    expiresAt: '2026-08-20T12:30:00Z',
    admin: { username: 'new-admin' },
  },
};

describe('AdminAuthProvider', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    sessionStorage.clear();
  });

  it('keeps a signed-out state and never calls the session endpoint when no token is stored', () => {
    const getSpy = vi.spyOn(adminApiClient, 'get');

    renderProbe();

    expect(screen.getByTestId('admin-auth-state')).toHaveTextContent('signed-out');
    expect(getSpy).not.toHaveBeenCalled();
  });

  it('logs in, stores the bearer only in sessionStorage, and never in localStorage', async () => {
    const postSpy = vi.spyOn(adminApiClient, 'post').mockResolvedValue(successfulLogin);
    const user = userEvent.setup();
    renderProbe();

    await user.click(screen.getByRole('button', { name: 'Test login' }));

    await waitFor(() =>
      expect(postSpy).toHaveBeenCalledWith('/api/v1/admin-auth/login', {
        username: 'new-admin',
        password: 'new-password',
      }),
    );
    expect(screen.getByTestId('admin-auth-state')).toHaveTextContent('new-admin');
    expect(sessionStorage.getItem('linksentry.admin.accessToken')).toBe('new-admin-session-token');
    expect(localStorage.getItem('linksentry.admin.accessToken')).toBeNull();
  });

  it('logout clears the session even when the request fails, and redirects state to signed-out', async () => {
    sessionStorage.setItem('linksentry.admin.accessToken', 'existing-token');
    vi.spyOn(adminApiClient, 'get').mockResolvedValue(bootstrappedSession);
    vi.spyOn(adminApiClient, 'post').mockRejectedValue(new Error('network down'));
    const user = userEvent.setup();
    renderProbe();
    await waitFor(() =>
      expect(screen.getByTestId('admin-auth-state')).toHaveTextContent('bootstrapped-admin'),
    );

    await user.click(screen.getByRole('button', { name: 'Test logout' }));

    await waitFor(() => expect(screen.getByTestId('admin-auth-state')).toHaveTextContent('signed-out'));
    expect(sessionStorage.getItem('linksentry.admin.accessToken')).toBeNull();
  });

  it('does not restore a delayed bootstrap session after an unauthorized event', async () => {
    sessionStorage.setItem('linksentry.admin.accessToken', 'test-only-token');
    const bootstrap = deferred<typeof bootstrappedSession>();
    vi.spyOn(adminApiClient, 'get').mockImplementation(() => bootstrap.promise as never);
    renderProbe();
    await waitFor(() => expect(screen.getByTestId('admin-auth-state')).toHaveTextContent('signed-out'));

    window.dispatchEvent(new Event(ADMIN_UNAUTHORIZED_EVENT));

    await act(async () => {
      bootstrap.resolve(bootstrappedSession);
      await bootstrap.promise;
    });

    expect(screen.getByTestId('admin-auth-state')).toHaveTextContent('signed-out');
    expect(document.body).not.toHaveTextContent('bootstrapped-admin');
    expect(sessionStorage.getItem('linksentry.admin.accessToken')).toBeNull();
  });
});
