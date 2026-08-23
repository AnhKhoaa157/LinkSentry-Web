import type { ReactNode } from 'react';

import { cn } from '@/lib/utils/cn';

type BadgeTone = 'neutral' | 'accent' | 'muted' | 'low' | 'moderate' | 'high' | 'critical';

interface BadgeProps {
  readonly children: ReactNode;
  readonly tone?: BadgeTone;
  readonly className?: string;
}

const toneClasses: Record<BadgeTone, string> = {
  neutral: 'border-border-strong bg-surface-raised text-content-primary',
  accent: 'border-accent/40 bg-accent-soft text-accent-strong',
  muted: 'border-border-default bg-surface-inset text-content-secondary',
  low: 'border-emerald-600/40 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300',
  moderate: 'border-amber-600/40 bg-amber-500/10 text-amber-700 dark:text-amber-300',
  high: 'border-orange-600/40 bg-orange-500/10 text-orange-700 dark:text-orange-300',
  critical: 'border-rose-600/40 bg-rose-500/10 text-rose-700 dark:text-rose-300',
};

/**
 * Small inline label.
 *
 * The `low`/`moderate`/`high`/`critical` tones back the risk-level and severity
 * indicators in the scan result. Every caller is required to pass `children`, so
 * colour is never the only channel — this component cannot render a bare dot.
 */
export function Badge({ children, tone = 'neutral', className }: BadgeProps) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-bold tracking-wide',
        toneClasses[tone],
        className,
      )}
    >
      {children}
    </span>
  );
}
