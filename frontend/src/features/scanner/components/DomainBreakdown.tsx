import type { NormalizedUrlResponse } from '@/features/scanner/schemas/scanResponse';

interface Props {
  readonly normalized: NormalizedUrlResponse;
}

/**
 * Splits `host` into the attacker-controlled prefix and the domain actually
 * registered, so the two are never visually equal weight — the registrable
 * domain is what the visitor should trust, and it is easy to bury under a
 * convincing subdomain.
 */
function splitHost(
  host: string,
  registrableDomain: string | null,
): { readonly prefix: string | null; readonly registrable: string | null } {
  if (!registrableDomain || !host.endsWith(registrableDomain)) {
    return { prefix: null, registrable: null };
  }
  const prefixEnd = host.length - registrableDomain.length;
  const prefix = host.slice(0, prefixEnd).replace(/\.$/, '');
  return { prefix: prefix.length > 0 ? prefix : null, registrable: registrableDomain };
}

/**
 * Renders the analysed host as text only — never a link, never an iframe
 * source — with the registrable domain visually distinguished from any
 * subdomain labels in front of it.
 */
export function DomainBreakdown({ normalized }: Props) {
  const { prefix, registrable } = splitHost(normalized.host, normalized.registrableDomain);

  return (
    <div className="min-w-0">
      <p className="text-ink-500 text-xs font-medium tracking-wide uppercase">Host</p>
      <p className="mt-1 font-mono text-sm break-all">
        {registrable ? (
          <>
            {prefix ? <span className="text-ink-400">{prefix}.</span> : null}
            <span className="text-ink-100 font-semibold">{registrable}</span>
          </>
        ) : (
          <span className="text-ink-100 font-semibold">{normalized.host}</span>
        )}
        {normalized.port !== null ? <span className="text-ink-400">:{normalized.port}</span> : null}
      </p>
      {!registrable ? (
        <p className="text-ink-500 mt-1 text-xs">
          No registered domain could be determined for this host (e.g. an IP address).
        </p>
      ) : null}
    </div>
  );
}
