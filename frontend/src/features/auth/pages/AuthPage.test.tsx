import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AxiosError, AxiosHeaders } from 'axios';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthPage } from '@/features/auth/pages/AuthPage';
import { apiClient } from '@/lib/api/client';
import { renderWithProviders } from '@/test/renderWithProviders';

function errorWithResponse(status: number, data: unknown) {
  const headers = new AxiosHeaders();
  return new AxiosError('Request failed', 'ERR_BAD_RESPONSE', { headers }, null, {
    status,
    statusText: 'Error',
    headers,
    config: { headers },
    data,
  });
}

describe('AuthPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('validates registration fields before making a request', async () => {
    const postSpy = vi.spyOn(apiClient, 'post');
    const user = userEvent.setup();

    renderWithProviders(<AuthPage />, { route: '/auth' });
    await user.click(screen.getByRole('button', { name: 'Register' }));
    await user.type(screen.getByLabelText('Email'), 'person@example.com');
    await user.type(screen.getByLabelText('Password'), 'short');
    await user.type(screen.getByLabelText('Confirm password'), 'different');
    await user.click(screen.getByRole('button', { name: 'Create account' }));

    expect(screen.getByRole('alert')).toHaveTextContent(/8 and 72 characters/i);
    expect(postSpy).not.toHaveBeenCalled();
  });

  it('stores a successful bearer only in sessionStorage and never renders it', async () => {
    const token = 'test-only-bearer-token';
    const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValue({
      data: {
        accessToken: token,
        tokenType: 'Bearer',
        expiresAt: '2026-08-19T12:00:00Z',
        user: { email: 'person@example.com' },
      },
    });
    const user = userEvent.setup();

    renderWithProviders(<AuthPage />, { route: '/auth' });
    await user.type(screen.getByLabelText('Email'), 'person@example.com');
    await user.type(screen.getByLabelText('Password'), 'correct-horse');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    await waitFor(() =>
      expect(postSpy).toHaveBeenCalledWith('/api/v1/auth/login', {
        email: 'person@example.com',
        password: 'correct-horse',
      }),
    );
    expect(sessionStorage.getItem('linksentry.accessToken')).toBe(token);
    expect(document.body).not.toHaveTextContent(token);
  });

  it('registers with matching passwords and uses the same one-time token boundary', async () => {
    const token = 'test-only-registration-token';
    const postSpy = vi
      .spyOn(apiClient, 'post')
      .mockResolvedValueOnce({
        data: {
          message: 'A verification code was sent to your email address.',
          expiresAt: '2026-08-19T12:10:00Z',
        },
      })
      .mockResolvedValueOnce({
        data: {
          accessToken: token,
          tokenType: 'Bearer',
          expiresAt: '2026-08-19T12:00:00Z',
          user: { email: 'new@example.com' },
        },
      });
    const user = userEvent.setup();

    renderWithProviders(<AuthPage />, { route: '/auth' });
    await user.click(screen.getByRole('button', { name: 'Register' }));
    await user.type(screen.getByLabelText('Email'), 'new@example.com');
    await user.type(screen.getByLabelText('Password'), 'correct-horse');
    await user.type(screen.getByLabelText('Confirm password'), 'correct-horse');
    await user.click(screen.getByRole('button', { name: 'Create account' }));

    await waitFor(() =>
      expect(postSpy).toHaveBeenCalledWith('/api/v2/auth/register', {
        email: 'new@example.com',
        password: 'correct-horse',
      }),
    );
    await user.type(await screen.findByLabelText('Verification code'), '123456');
    await user.click(screen.getByRole('button', { name: 'Verify email' }));
    await waitFor(() =>
      expect(postSpy).toHaveBeenCalledWith('/api/v2/auth/register/verify', {
        email: 'new@example.com',
        code: '123456',
      }),
    );
    expect(sessionStorage.getItem('linksentry.accessToken')).toBe(token);
    expect(document.body).not.toHaveTextContent(token);
  });

  it('renders a normalized login failure without retaining the Axios error or password', async () => {
    const password = 'correct-horse';
    vi.spyOn(apiClient, 'post').mockRejectedValue(
      errorWithResponse(401, {
        code: 'INVALID_CREDENTIALS',
        message: 'Email or password is incorrect.',
        traceId: 'trace-secret',
      }),
    );
    const user = userEvent.setup();

    renderWithProviders(<AuthPage />, { route: '/auth' });
    await user.type(screen.getByLabelText('Email'), 'person@example.com');
    await user.type(screen.getByLabelText('Password'), password);
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Email or password is incorrect.');
    expect(alert).not.toHaveTextContent('trace-secret');
    expect(document.body).not.toHaveTextContent(password);
    expect(sessionStorage.getItem('linksentry.accessToken')).toBeNull();
  });
});
