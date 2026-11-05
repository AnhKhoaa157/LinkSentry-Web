// Applies the saved or system theme before first paint, so the page never
// flashes the wrong theme. It is a file rather than an inline <script> so the
// Content-Security-Policy in vercel.json can forbid inline script entirely.
try {
  const storedTheme = localStorage.getItem('linksentry.theme');
  const theme =
    storedTheme === 'light' || storedTheme === 'dark'
      ? storedTheme
      : matchMedia('(prefers-color-scheme: dark)').matches
        ? 'dark'
        : 'light';
  document.documentElement.classList.add(theme);
  document
    .querySelector('meta[name="theme-color"]')
    ?.setAttribute('content', theme === 'dark' ? '#05080f' : '#f3f5f8');
  document
    .querySelector('link[data-app-favicon]')
    ?.setAttribute(
      'href',
      theme === 'dark' ? '/brand/linksentry-mark-white.png' : '/brand/linksentry-mark.png',
    );
} catch {
  document.documentElement.classList.add('light');
}
