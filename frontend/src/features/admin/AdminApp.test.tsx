import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from 'axios';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AdminApp } from '@/features/admin/AdminApp';
import { adminApiClient } from '@/features/admin/api/adminClient';
import { createQueryClient } from '@/lib/api/queryClient';
import { ADMIN_UNAUTHORIZED_EVENT } from '@/features/admin/sessionStorage';

function renderAdminApp(route: string) {
  const queryClient = createQueryClient();
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[route]}>
        <AdminApp />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('AdminApp routing and session handling', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    sessionStorage.clear();
  });

  it('redirects an unauthenticated visitor from the dashboard to admin sign-in', async () => {
    renderAdminApp('/');

    await waitFor(() => expect(screen.getByRole('heading', { name: 'Admin sign-in' })).toBeInTheDocument());
  });

  it('signs in and shows the protected dashboard shell with identity and management sections', async () => {
    vi.spyOn(adminApiClient, 'post').mockResolvedValue({
      data: {
        accessToken: 'dashboard-session-token',
        tokenType: 'Bearer',
        expiresAt: '2026-08-20T13:00:00Z',
        admin: { username: 'ops' },
      },
    });
    vi.spyOn(adminApiClient, 'get').mockResolvedValue({ data: [] });
    const user = userEvent.setup();
    renderAdminApp('/login');

    await user.type(screen.getByLabelText('Username'), 'ops');
    await user.type(screen.getByLabelText('Password'), 'correct-horse-123');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    await waitFor(() => expect(screen.getByRole('heading', { name: 'Admin dashboard' })).toBeInTheDocument());
    expect(screen.getByText('ops')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Licenses' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Device activations' })).toBeInTheDocument();
    expect(sessionStorage.getItem('linksentry.admin.accessToken')).toBe('dashboard-session-token');
  });

  it('shows a safe error and never reveals whether the username exists on invalid login', async () => {
    vi.spyOn(adminApiClient, 'post').mockImplementation(() => {
      const config = { headers: new AxiosHeaders() } as InternalAxiosRequestConfig;
      return Promise.reject(
        new AxiosError('Unauthorized', 'ERR_BAD_REQUEST', config, null, {
          status: 401,
          statusText: 'Unauthorized',
          headers: new AxiosHeaders(),
          config,
          data: { code: 'INVALID_CREDENTIALS', message: 'Username or password is incorrect.' },
        }),
      );
    });
    const user = userEvent.setup();
    renderAdminApp('/login');

    await user.type(screen.getByLabelText('Username'), 'ops');
    await user.type(screen.getByLabelText('Password'), 'wrong-password');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Username or password is incorrect.');
    expect(sessionStorage.getItem('linksentry.admin.accessToken')).toBeNull();
  });

  it('logout clears the session and returns to admin sign-in', async () => {
    sessionStorage.setItem('linksentry.admin.accessToken', 'existing-session-token');
    vi.spyOn(adminApiClient, 'get')
      .mockResolvedValueOnce({
        data: { expiresAt: '2026-08-20T13:00:00Z', admin: { username: 'ops' } },
      })
      .mockResolvedValueOnce({ data: [] });
    vi.spyOn(adminApiClient, 'post').mockResolvedValue({ data: undefined });
    const user = userEvent.setup();
    renderAdminApp('/');
    await waitFor(() => expect(screen.getByRole('heading', { name: 'Admin dashboard' })).toBeInTheDocument());

    await user.click(screen.getByRole('button', { name: 'Log out' }));

    await waitFor(() => expect(screen.getByRole('heading', { name: 'Admin sign-in' })).toBeInTheDocument());
    expect(sessionStorage.getItem('linksentry.admin.accessToken')).toBeNull();
  });

  it('returns to admin sign-in when an admin API request reports an expired session', async () => {
    sessionStorage.setItem('linksentry.admin.accessToken', 'expired-session-token');
    vi.spyOn(adminApiClient, 'get')
      .mockResolvedValueOnce({
        data: { expiresAt: '2026-08-20T13:00:00Z', admin: { username: 'ops' } },
      })
      .mockResolvedValueOnce({ data: [] });
    renderAdminApp('/');

    await waitFor(() => expect(screen.getByRole('heading', { name: 'Admin dashboard' })).toBeInTheDocument());
    window.dispatchEvent(new Event(ADMIN_UNAUTHORIZED_EVENT));

    await waitFor(() => expect(screen.getByRole('heading', { name: 'Admin sign-in' })).toBeInTheDocument());
    expect(sessionStorage.getItem('linksentry.admin.accessToken')).toBeNull();
  });
});
