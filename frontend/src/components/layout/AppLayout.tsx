import { Outlet } from 'react-router';

import { SiteFooter } from '@/components/layout/SiteFooter';
import { SiteHeader } from '@/components/layout/SiteHeader';
import { LicenseProvider } from '@/features/license/context/LicenseProvider';

/**
 * Shared shell: skip link, header, routed content, footer.
 *
 * Owns `LicenseProvider` so the public shell's device installation exists for every route it
 * renders (including the header's status badge), without reaching `/admin`'s separate console.
 */
export function AppLayout() {
  return (
    <LicenseProvider clientLabel="web">
      <div className="flex min-h-dvh flex-col">
        {/* First tab stop, so keyboard users can bypass the navigation. */}
        <a
          href="#main-content"
          className="sr-only-focusable bg-accent-500 text-ink-950 absolute top-4 left-4 z-20 rounded-md px-4 py-2 text-sm font-medium"
        >
          Skip to main content
        </a>

        <SiteHeader />

        <main id="main-content" className="page-main flex-1">
          <Outlet />
        </main>

        <SiteFooter />
      </div>
    </LicenseProvider>
  );
}
