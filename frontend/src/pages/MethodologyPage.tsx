import { Card } from '@/components/ui/Card';
import type { TranslationKey } from '@/lib/i18n/translations';
import { useLocale } from '@/lib/i18n/useLocale';

const signals = [
  ['methodology.signal.registered.title', 'methodology.signal.registered.body'],
  ['methodology.signal.subdomain.title', 'methodology.signal.subdomain.body'],
  ['methodology.signal.transport.title', 'methodology.signal.transport.body'],
  ['methodology.signal.host.title', 'methodology.signal.host.body'],
  ['methodology.signal.lexical.title', 'methodology.signal.lexical.body'],
] as const satisfies ReadonlyArray<readonly [TranslationKey, TranslationKey]>;

const limits = ['methodology.limit.1', 'methodology.limit.2', 'methodology.limit.3'] as const;

/** Explains how the analysis works without implying that a result proves safety. */
export function MethodologyPage() {
  const { t } = useLocale();

  return (
    <div className="methodology-page mx-auto w-full max-w-6xl space-y-10">
      <header className="page-intro space-y-4">
        <p className="eyebrow">{t('methodology.eyebrow')}</p>
        <h1 className="display-title text-content-primary text-4xl sm:text-5xl">{t('methodology.title')}</h1>
        <p className="body-secondary text-base leading-7">{t('methodology.intro')}</p>
      </header>

      <section aria-labelledby="example-heading" className="grid gap-6 lg:grid-cols-[0.75fr_1.25fr]">
        <div className="space-y-3">
          <p className="eyebrow">01</p>
          <h2 id="example-heading" className="section-title text-content-primary">
            {t('methodology.exampleHeading')}
          </h2>
          <p className="body-secondary text-sm leading-6">{t('methodology.exampleIntro')}</p>
        </div>

        <div className="surface-panel p-5 sm:p-6">
          <p className="border-border-default bg-surface-inset overflow-x-auto rounded-lg border px-4 py-4 font-mono text-sm whitespace-nowrap">
            login.vietcombank.com.vn.evil-domain.xyz
          </p>
          <p className="body-secondary mt-4 text-sm leading-6">{t('methodology.exampleBody')}</p>
          <p className="body-secondary mt-3 text-sm leading-6">{t('methodology.exampleSuffix')}</p>
        </div>
      </section>

      <section aria-labelledby="signals-heading" className="space-y-5">
        <div className="max-w-2xl space-y-3">
          <p className="eyebrow">02</p>
          <h2 id="signals-heading" className="section-title text-content-primary">
            {t('methodology.signalsHeading')}
          </h2>
          <p className="body-secondary text-sm leading-6">{t('methodology.signalsIntro')}</p>
        </div>

        <ol className="grid gap-3 md:grid-cols-2">
          {signals.map(([titleKey, bodyKey], index) => (
            <li key={titleKey} className="surface-inset p-5">
              <p className="eyebrow">0{index + 1}</p>
              <h3 className="text-content-primary mt-3 text-sm font-bold">{t(titleKey)}</h3>
              <p className="body-secondary mt-2 text-sm leading-6">{t(bodyKey)}</p>
            </li>
          ))}
        </ol>
      </section>

      <Card title={t('methodology.limitTitle')}>
        <ul className="body-secondary list-inside list-disc space-y-3 text-sm leading-6">
          {limits.map((key) => (
            <li key={key}>{t(key)}</li>
          ))}
        </ul>
      </Card>
    </div>
  );
}
