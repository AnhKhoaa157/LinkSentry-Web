import type { ReactNode } from 'react';

import { cn } from '@/lib/utils/cn';

interface CardProps {
  readonly children: ReactNode;
  readonly className?: string;
  /** Renders as `<section>` when given, with the heading as its accessible name. */
  readonly title?: string;
  readonly description?: string;
}

/** Bordered surface used to group related content. */
export function Card({ children, className, title, description }: CardProps) {
  const surface = cn(
    'rounded-xl border border-ink-800 bg-ink-900/60 p-5 shadow-sm backdrop-blur-sm sm:p-6',
    className,
  );

  if (!title) {
    return <div className={surface}>{children}</div>;
  }

  return (
    <section className={surface} aria-labelledby={toId(title)}>
      <h2 id={toId(title)} className="text-ink-100 text-base font-semibold tracking-tight">
        {title}
      </h2>
      {description ? <p className="text-ink-300 mt-1 text-sm">{description}</p> : null}
      <div className="mt-4">{children}</div>
    </section>
  );
}

function toId(title: string): string {
  return `card-${title
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '')}`;
}
