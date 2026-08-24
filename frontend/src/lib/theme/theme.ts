export const THEMES = ['light', 'dark'] as const;

export type Theme = (typeof THEMES)[number];

const STORAGE_KEY = 'linksentry.theme';

export function isTheme(value: unknown): value is Theme {
  return typeof value === 'string' && THEMES.some((theme) => theme === value);
}

export function getInitialTheme(): Theme {
  try {
    const storedTheme = localStorage.getItem(STORAGE_KEY);
    if (isTheme(storedTheme)) {
      return storedTheme;
    }
  } catch {
    // Fall through to the system preference when storage is unavailable.
  }

  return typeof window.matchMedia === 'function' && window.matchMedia('(prefers-color-scheme: dark)').matches
    ? 'dark'
    : 'light';
}

export function persistTheme(theme: Theme): void {
  try {
    localStorage.setItem(STORAGE_KEY, theme);
  } catch {
    // A blocked storage implementation leaves the choice active for this tab only.
  }
}

interface ApplyThemeOptions {
  readonly syncBrowserChrome?: boolean;
}

/** Applies the semantic theme and, for the web app, its browser-tab chrome. */
export function applyTheme(theme: Theme, { syncBrowserChrome = true }: ApplyThemeOptions = {}): void {
  const root = document.documentElement;
  root.classList.toggle('light', theme === 'light');
  root.classList.toggle('dark', theme === 'dark');

  if (!syncBrowserChrome) {
    return;
  }

  const themeColor = document.querySelector<HTMLMetaElement>('meta[name="theme-color"]');
  themeColor?.setAttribute('content', theme === 'dark' ? '#05080f' : '#f3f5f8');

  let favicon = document.querySelector<HTMLLinkElement>('link[data-app-favicon]');
  if (favicon === null) {
    favicon = document.createElement('link');
    favicon.rel = 'icon';
    favicon.dataset.appFavicon = '';
    document.head.append(favicon);
  }
  favicon.dataset.theme = theme;
  favicon.href = theme === 'dark' ? '/brand/linksentry-mark-white.png' : '/brand/linksentry-mark.png';
}
