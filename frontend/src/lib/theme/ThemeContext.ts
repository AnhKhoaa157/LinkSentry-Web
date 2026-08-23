import { createContext } from 'react';

import type { Theme } from '@/lib/theme/theme';

export interface ThemeContextValue {
  readonly theme: Theme;
  readonly toggleTheme: () => void;
}

export const ThemeContext = createContext<ThemeContextValue | null>(null);
