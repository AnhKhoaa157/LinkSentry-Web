import { screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { AppRoutes } from '@/app/router';
import { renderWithProviders } from '@/test/renderWithProviders';

// The shell renders the health widget, which would otherwise fire a real request.
vi.mock('@/features/health/api/useHealth', () => ({
  useHealth: () => ({
    data: { status: 'UP', service: 'linksentry-api' },
    isPending: false,
    isError: false,
    error: null,
    isFetching: false,
    refetch: vi.fn(),
  }),
}));

describe('application shell', () => {
  it('renders the home page inside the shell', () => {
    renderWithProviders(<AppRoutes />, { route: '/' });

    expect(
      screen.getByRole('heading', { name: /analyze suspicious links before you trust them/i, level: 1 }),
    ).toBeInTheDocument();
  });

  it('exposes accessible landmarks and a skip link', () => {
    renderWithProviders(<AppRoutes />, { route: '/' });

    expect(screen.getByRole('banner')).toBeInTheDocument();
    expect(screen.getByRole('main')).toBeInTheDocument();
    expect(screen.getByRole('contentinfo')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /skip to main content/i })).toHaveAttribute(
      'href',
      '#main-content',
    );
  });

  it('exposes the main navigation with both routes', () => {
    renderWithProviders(<AppRoutes />, { route: '/' });

    const nav = screen.getByRole('navigation', { name: /main/i });

    expect(nav).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Home' })).toHaveAttribute('href', '/');
    expect(screen.getByRole('link', { name: 'Methodology' })).toHaveAttribute('href', '/methodology');
  });

  it('renders the methodology page on its route', () => {
    renderWithProviders(<AppRoutes />, { route: '/methodology' });

    expect(screen.getByRole('heading', { name: /methodology/i, level: 1 })).toBeInTheDocument();
  });

  it('renders the not-found page for an unknown route', () => {
    renderWithProviders(<AppRoutes />, { route: '/this-route-does-not-exist' });

    expect(screen.getByRole('heading', { name: /page not found/i, level: 1 })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /back to home/i })).toHaveAttribute('href', '/');
  });
});
