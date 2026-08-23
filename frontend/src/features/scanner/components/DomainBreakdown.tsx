import type { NormalizedUrlResponse } from '@/features/scanner/schemas/scanResponse';
import { useLocale } from '@/lib/i18n/useLocale';

interface Props {
  readonly normalized: NormalizedUrlResponse;
}

function splitHost(
  host: string,
  registrableDomain: string | null,
): { readonly prefix: string | null; readonly registrable: string | null } {
  if (!registrableDomain || (host !== registrableDomain && !host.endsWith('.' + registrableDomain))) {
    return { prefix: null, registrable: null };
  }
  const prefixEnd = host.length - registrableDomain.length;
  const prefix = host.slice(0, prefixEnd).replace(/\.$/, '');
  return { prefix: prefix.length > 0 ? prefix : null, registrable: registrableDomain };
}

/** Presents normalized server fields as text, with the registered boundary visually separated. */
export function DomainBreakdown({ normalized }: Props) {
  const { t } = useLocale();
  const { prefix, registrable } = splitHost(normalized.host, normalized.registrableDomain);

  return (
    <section className="surface-panel p-5" aria-labelledby="domain-breakdown-heading">
      <p className="eyebrow">{t('domain.label')}</p>
      <h3 id="domain-breakdown-heading" className="text-content-primary mt-1 text-lg font-bold">
        {t('domain.host')}
      </h3>

      <p className="border-border-default bg-surface-inset mt-4 overflow-x-auto rounded-lg border px-3 py-3 font-mono text-sm whitespace-nowrap">
        {registrable ? (
          <>
            {prefix ? <span className="text-content-muted">{prefix}.</span> : null}
            <span className="text-content-primary font-bold">{registrable}</span>
          </>
        ) : (
          <span className="text-content-primary font-bold">{normalized.host}</span>
        )}
        {normalized.port !== null ? <span className="text-content-secondary">:{normalized.port}</span> : null}
      </p>

      {!registrable ? <p className="body-muted mt-2 text-xs">{t('domain.unknown')}</p> : null}

      <dl className="border-border-default mt-4 grid grid-cols-2 gap-x-4 gap-y-3 border-t pt-4">
        <DataPoint label={t('domain.registered')} value={registrable ?? t('domain.unknown')} wide={!registrable} />
        <DataPoint label={t('domain.scheme')} value={normalized.scheme.toUpperCase()} />
        {normalized.port !== null ? <DataPoint label={t('domain.port')} value={String(normalized.port)} /> : null}
        <DataPoint label={t('domain.path')} value={normalized.path || '/'} wide />
        <DataPoint
          label={t('domain.query')}
          value={normalized.queryPresent ? t('domain.yes') : t('domain.no')}
        />
        <DataPoint
          label={t('domain.fragment')}
          value={normalized.fragmentPresent ? t('domain.yes') : t('domain.no')}
        />
      </dl>
    </section>
  );
}

function DataPoint({
  label,
  value,
  wide = false,
}: {
  readonly label: string;
  readonly value: string;
  readonly wide?: boolean;
}) {
  return (
    <div className={wide ? 'col-span-2 min-w-0' : 'min-w-0'}>
      <dt className="body-muted text-[0.68rem] font-bold tracking-wide uppercase">{label}</dt>
      <dd className="text-content-primary mt-1 break-words font-mono text-xs">{value}</dd>
    </div>
  );
}
