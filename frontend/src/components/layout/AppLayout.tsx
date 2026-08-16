import { Outlet } from 'react-router';

import { SiteFooter } from '@/components/layout/SiteFooter';
import { SiteHeader } from '@/components/layout/SiteHeader';

/** Shared shell: skip link, header, routed content, footer. */
export function AppLayout() {
  return (
    <div className="flex min-h-dvh flex-col">
      {/* First tab stop, so keyboard users can bypass the navigation. */}
      <a
        href="#main-content"
        className="sr-only-focusable bg-accent-500 text-ink-950 absolute top-4 left-4 z-20 rounded-md px-4 py-2 text-sm font-medium"
      >
        Skip to main content
      </a>

      <SiteHeader />

      <main id="main-content" className="mx-auto w-full max-w-5xl flex-1 px-4 py-10 sm:px-6">
        <Outlet />
      </main>

      <SiteFooter />
    </div>
  );
}
