import { Link } from 'react-router';

/** 404 page. */
export function NotFoundPage() {
  return (
    <div className="mx-auto max-w-xl py-12 text-center">
      <p className="text-accent-400 font-mono text-sm">404</p>
      <h1 className="mt-3 text-3xl font-semibold tracking-tight">Page not found</h1>
      <p className="text-ink-300 mt-3">
        That page does not exist. It may have been moved, or the address may be mistyped.
      </p>
      <Link
        to="/"
        className="bg-accent-500 text-ink-950 hover:bg-accent-400 mt-7 inline-block rounded-lg px-5 py-2.5 text-sm font-semibold transition-colors"
      >
        Back to home
      </Link>
    </div>
  );
}
