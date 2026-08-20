import { Link } from 'react-router';

import { LicenseStatusCard } from '@/features/license/components/LicenseStatusCard';

/**
 * Replaces the removed email/password sign-in page. There is no account to create or sign into —
 * a license is granted only by an administrator, out of band, using the activation code shown
 * below. See `backend/DEPLOYMENT.md` for the administrator side of this flow.
 */
export function LicensePage() {
  return (
    <div className="mx-auto max-w-md">
      <p className="text-accent-400 font-mono text-sm">This installation</p>
      <h1 className="mt-2 text-3xl font-semibold tracking-tight">License</h1>
      <p className="text-ink-300 mt-3 text-sm">
        LinkSentry has no accounts, passwords, or sign-in. Every browser and every installation of the
        extension is its own independent installation with its own activation code. An administrator grants a
        license by attaching that code to it — copy the code below and send it to them.
      </p>

      <div className="mt-7">
        <LicenseStatusCard />
      </div>

      <p className="text-ink-500 mt-5 text-xs">
        Clearing this browser's site data, or reinstalling the extension, creates a new installation with a
        new activation code and requires a new grant from an administrator.
      </p>

      <p className="mt-4 text-center text-sm">
        <Link to="/" className="text-accent-400 underline underline-offset-4">
          Back to scanner
        </Link>
      </p>
    </div>
  );
}
