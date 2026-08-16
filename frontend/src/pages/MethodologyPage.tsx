import { Card } from '@/components/ui/Card';

const signals = [
  {
    id: 'Registrable domain',
    body: 'Which domain was actually registered, separated from the subdomains anyone can invent. This is the signal the rest depends on.',
  },
  {
    id: 'Subdomain depth',
    body: 'How many labels sit left of the registrable domain. Deep nesting is where a familiar brand name is usually hidden.',
  },
  {
    id: 'Transport',
    body: 'Whether the link uses HTTPS at all.',
  },
  {
    id: 'Host form',
    body: 'Whether the host is a bare IP address rather than a name, and whether it uses Punycode.',
  },
  {
    id: 'Lexical patterns',
    body: 'Authentication-related keywords, percent-encoding and other obfuscation, unusual length, and known URL shorteners.',
  },
] as const;

/** Explains how the analysis will work, and what it cannot do. */
export function MethodologyPage() {
  return (
    <div className="max-w-3xl space-y-10">
      <header className="space-y-4">
        <h1 className="text-3xl font-semibold tracking-tight">Methodology</h1>
        <p className="text-ink-300">
          LinkSentry scores a URL from named rules, each contributing an explicit number of points. The score
          and the explanation come from the same data, so the number always has a reason attached.
        </p>
      </header>

      <section aria-labelledby="example-heading" className="space-y-4">
        <h2 id="example-heading" className="text-xl font-semibold tracking-tight">
          Why the domain is not the last two labels
        </h2>
        <p className="text-ink-300">
          Consider this hostname. The brand name is present, but not in the part that was registered:
        </p>

        {/* Rendered as text, never as a link — see docs/SECURITY_BOUNDARY.md. */}
        <p className="border-ink-800 bg-ink-950 overflow-x-auto rounded-lg border px-4 py-3 font-mono text-sm">
          login.vietcombank.com.vn.evil-domain.xyz
        </p>

        <p className="text-ink-300">
          The registered domain here is <span className="text-ink-100 font-mono">evil-domain.xyz</span>.
          Everything to its left — including{' '}
          <span className="text-ink-100 font-mono">vietcombank.com.vn</span> — is a subdomain, and whoever
          owns the domain can set those to anything they like. Reading a hostname left to right is what makes
          this trick work; the part that matters is at the end.
        </p>

        <p className="text-ink-300">
          Finding that boundary is not as simple as taking the last two labels, because{' '}
          <span className="text-ink-100 font-mono">com.vn</span> and{' '}
          <span className="text-ink-100 font-mono">co.uk</span> are public suffixes — anyone can register
          directly beneath them, so they are not domains in their own right.
        </p>
      </section>

      <section aria-labelledby="signals-heading" className="space-y-4">
        <h2 id="signals-heading" className="text-xl font-semibold tracking-tight">
          Signals used today
        </h2>
        <p className="text-ink-300 text-sm">
          These are the deterministic signals the stateless analyzer evaluates. Each finding explains its
          contribution to the score.
        </p>

        <dl className="space-y-3">
          {signals.map((signal) => (
            <div key={signal.id} className="border-ink-800 bg-ink-900/50 rounded-lg border p-4">
              <dt className="text-ink-100 text-sm font-semibold">{signal.id}</dt>
              <dd className="text-ink-300 mt-1 text-sm">{signal.body}</dd>
            </div>
          ))}
        </dl>
      </section>

      <Card title="What this cannot tell you">
        <ul className="text-ink-300 list-inside list-disc space-y-2 text-sm">
          <li>
            A low score is not evidence that a link is safe. A hostile page on an ordinary-looking domain
            produces no lexical signals at all.
          </li>
          <li>
            A high score is not proof of malice. Long, deeply nested URLs occur in legitimate systems too.
          </li>
          <li>
            Nothing about the destination is checked — not its content, not its reputation, not where its
            redirects lead.
          </li>
        </ul>
      </Card>
    </div>
  );
}
