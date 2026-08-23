import { useContext } from 'react';

import { ThemeContext } from '@/lib/theme/ThemeContext';

export function useTheme() {
  const value = useContext(ThemeContext);
  if (value === null) {
    throw new Error('useTheme must be used inside ThemeProvider');
  }
  return value;
}
