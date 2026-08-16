import { cn } from '@/lib/utils/cn';

type StatusState = 'online' | 'offline' | 'pending';

interface StatusDotProps {
  readonly state: StatusState;
  readonly className?: string;
}

const stateClasses: Record<StatusState, string> = {
  online: 'bg-emerald-400',
  offline: 'bg-rose-400',
  pending: 'bg-amber-300',
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
      className={cn('inline-block size-2 shrink-0 rounded-full', stateClasses[state], className)}
    />
  );
}
