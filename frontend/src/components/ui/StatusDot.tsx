import { cn } from '@/lib/utils/cn';

type StatusState = 'online' | 'offline' | 'pending';

interface StatusDotProps {
  readonly state: StatusState;
  readonly className?: string;
}

const stateClasses: Record<StatusState, string> = {
  online: 'bg-emerald-500',
  offline: 'bg-rose-500',
  pending: 'bg-amber-500',
};

/**
 * Decorative status indicator.
 *
 * `aria-hidden` because it carries no information of its own: the component using
 * it is responsible for stating the status in text. A dot is a colour, and colour
 * is never the only channel here.
 */
export function StatusDot({ state, className }: StatusDotProps) {
  return (
    <span
      aria-hidden="true"
      className={cn('inline-block size-2.5 shrink-0 rounded-full ring-4 ring-current/10', stateClasses[state], className)}
    />
  );
}
