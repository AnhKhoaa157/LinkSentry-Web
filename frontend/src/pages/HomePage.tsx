import { Link } from 'react-router';

import { Badge } from '@/components/ui/Badge';
import { Card } from '@/components/ui/Card';
import { HealthStatus } from '@/features/health/components/HealthStatus';
import { Scanner } from '@/features/scanner/components/Scanner';

const boundaries = [
  {
    title: 'The link is never visited',
    body: 'LinkSentry reads the URL as text. It does not open the page, resolve the hostname, follow redirects, or download anything.',
  },
  {
    title: 'Every point is traceable',
    body: 'A score comes from named rules, each contributing an explicit number. There is no opaque model deciding for you.',
  },
  {
    title: 'No result is a clickable link',
    body: 'A suspicious URL is only ever shown as text, so it cannot be opened by accident from this page.',
  },
] as const;

/** Landing page: product statement, scanner, boundary, and service health. */
export function HomePage() {
  return (
    <div className="space-y-12">
      <section className="space-y-5">
        <Badge tone="muted">Static analysis only</Badge>

        <h1 className="max-w-3xl text-3xl font-semibold tracking-tight text-balance sm:text-4xl">
          Analyze suspicious links before you trust them.
        </h1>

        <p className="text-ink-300 max-w-2xl text-base sm:text-lg">
          LinkSentry inspects the structure of a URL and explains what looks wrong — which domain is really
          registered, where a familiar brand name is hiding, and which patterns phishing links tend to share.
        </p>
      </section>

      <Scanner />

      <section aria-labelledby="boundary-heading" className="space-y-4">
        <h2 id="boundary-heading" className="text-xl font-semibold tracking-tight">
          How the analysis stays safe
        </h2>
        <p className="text-ink-300 max-w-2xl text-sm">
          A tool that fetched every submitted link would be a tool an attacker could point at anything.
          LinkSentry works entirely on the text of the URL, which removes that risk — and means it can never
          confirm that a site is safe, only describe what the link itself reveals.
        </p>

        <ul className="grid gap-4 sm:grid-cols-3">
          {boundaries.map((item) => (
            <li key={item.title}>
              <Card className="h-full">
                <h3 className="text-ink-100 text-sm font-semibold">{item.title}</h3>
                <p className="text-ink-300 mt-2 text-sm">{item.body}</p>
              </Card>
            </li>
          ))}
        </ul>

        <p className="text-sm">
          <Link
            to="/methodology"
            className="text-accent-400 decoration-accent-600 hover:text-accent-500 font-medium underline underline-offset-4"
          >
            Read the methodology
          </Link>
        </p>
      </section>

      <Card title="Service status" description="Whether this page can currently reach the analysis API.">
        <HealthStatus />
      </Card>
    </div>
  );
}
