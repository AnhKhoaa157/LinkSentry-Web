import { Link } from 'react-router';

import { useLocale } from '@/lib/i18n/useLocale';

/** 404 page with an explicit recovery path. */
export function NotFoundPage() {
  const { t } = useLocale();

  return (
    <div className="mx-auto max-w-xl py-12 text-center sm:py-20">
      <p className="eyebrow">{t('notFound.eyebrow')}</p>
      <h1 className="display-title text-content-primary mt-3 text-4xl sm:text-5xl">{t('notFound.title')}</h1>
      <p className="body-secondary mt-4 leading-6">{t('notFound.body')}</p>
      <Link to="/" className="ui-button ui-button-primary mt-8">
        {t('common.backToHome')}
      </Link>
    </div>
  );
}
