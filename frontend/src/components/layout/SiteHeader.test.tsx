import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AxiosError, AxiosHeaders } from 'axios';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { SiteHeader } from '@/components/layout/SiteHeader';
import { apiClient } from '@/lib/api/client';
import { renderWithProviders } from '@/test/renderWithProviders';

function unauthorizedError() {
  const headers = new AxiosHeaders();
  return new AxiosError('Unauthorized', 'ERR_BAD_REQUEST', { headers }, null, {
    status: 401,
    statusText: 'Unauthorized',
    headers,
    config: { headers },
    data: { code: 'UNAUTHORIZED', message: 'Authentication is required.' },
  });
}

describe('SiteHeader session controls', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('logs out, clears sessionStorage, and returns to the sign-in affordance', async () => {
    sessionStorage.setItem('linksentry.accessToken', 'test-only-token');
    const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValue({
      data: { expiresAt: '2026-08-19T12:00:00Z', user: { email: 'person@example.com' } },
    });
    const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValue({ data: undefined });
    const user = userEvent.setup();

    renderWithProviders(<SiteHeader />);
    expect(await screen.findByText('person@example.com')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /sign out/i }));

    await waitFor(() => expect(screen.getByRole('link', { name: /sign in/i })).toBeInTheDocument());
    expect(postSpy).toHaveBeenCalledWith('/api/v1/auth/logout');
    expect(getSpy).toHaveBeenCalledWith('/api/v1/auth/session');
    expect(sessionStorage.getItem('linksentry.accessToken')).toBeNull();
    expect(document.body).not.toHaveTextContent('test-only-token');
  });

  it('clears an invalid stored session and does not expose the bearer', async () => {
    sessionStorage.setItem('linksentry.accessToken', 'test-only-token');
    vi.spyOn(apiClient, 'get').mockRejectedValue(unauthorizedError());

    renderWithProviders(<SiteHeader />);

    expect(await screen.findByRole('link', { name: /sign in/i })).toBeInTheDocument();
    expect(sessionStorage.getItem('linksentry.accessToken')).toBeNull();
    expect(document.body).not.toHaveTextContent('test-only-token');
  });
});
