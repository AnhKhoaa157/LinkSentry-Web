import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { ThemeSwitcher } from '@/components/ui/ThemeSwitcher';
import { renderWithProviders } from '@/test/renderWithProviders';

describe('ThemeSwitcher', () => {
  it('switches the document, favicon, and persisted preference together', async () => {
    const user = userEvent.setup();
    localStorage.setItem('linksentry.theme', 'light');

    renderWithProviders(<ThemeSwitcher />);

    expect(document.documentElement).toHaveClass('light');
    expect(document.querySelector('link[data-app-favicon]')).toHaveAttribute('data-theme', 'light');
    expect(document.querySelector('link[data-app-favicon]')).toHaveAttribute(
      'href',
      '/brand/linksentry-mark.png',
    );

    await user.click(screen.getByRole('button', { name: /switch to dark theme/i }));

    expect(document.documentElement).toHaveClass('dark');
    expect(document.documentElement).not.toHaveClass('light');
    expect(localStorage.getItem('linksentry.theme')).toBe('dark');
    expect(document.querySelector('link[data-app-favicon]')).toHaveAttribute('data-theme', 'dark');
    expect(document.querySelector('link[data-app-favicon]')).toHaveAttribute(
      'href',
      '/brand/linksentry-mark-white.png',
    );
    expect(screen.getByRole('button', { name: /switch to light theme/i })).toBeInTheDocument();
  });
});
