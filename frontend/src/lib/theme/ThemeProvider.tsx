import type { ReactNode } from 'react';
import { useCallback, useLayoutEffect, useMemo, useState } from 'react';

import { ThemeContext } from '@/lib/theme/ThemeContext';
import { applyTheme, getInitialTheme, persistTheme } from '@/lib/theme/theme';

interface ThemeProviderProps {
  readonly children: ReactNode;
  readonly syncBrowserChrome?: boolean;
}

/** Owns the active theme and optionally keeps the browser-tab chrome in sync. */
export function ThemeProvider({ children, syncBrowserChrome = true }: ThemeProviderProps) {
  const [theme, setTheme] = useState(getInitialTheme);

  useLayoutEffect(() => {
    applyTheme(theme, { syncBrowserChrome });
  }, [syncBrowserChrome, theme]);

  const toggleTheme = useCallback(() => {
    setTheme((currentTheme) => {
      const nextTheme = currentTheme === 'light' ? 'dark' : 'light';
      persistTheme(nextTheme);
      return nextTheme;
    });
  }, []);

  const value = useMemo(() => ({ theme, toggleTheme }), [theme, toggleTheme]);

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}
