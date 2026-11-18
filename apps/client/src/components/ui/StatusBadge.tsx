import type { ReactNode } from 'react';
import { cn } from './cn';

export type StatusTone = 'neutral' | 'primary' | 'success' | 'warning' | 'danger' | 'info';

const TONES: Record<StatusTone, { badge: string; dot: string }> = {
  neutral: { badge: 'bg-surface-sunken text-muted border-border', dot: 'bg-muted' },
  primary: { badge: 'bg-primary-subtle text-primary-hover border-transparent', dot: 'bg-primary' },
  success: { badge: 'bg-success-subtle text-success border-transparent', dot: 'bg-success' },
  warning: { badge: 'bg-warning-subtle text-warning border-transparent', dot: 'bg-warning' },
  danger: { badge: 'bg-danger-subtle text-danger border-transparent', dot: 'bg-danger' },
  info: { badge: 'bg-info-subtle text-info border-transparent', dot: 'bg-info' },
};

export function StatusBadge({
  tone,
  children,
  dot,
}: {
  tone: StatusTone;
  children: ReactNode;
  dot?: boolean;
}) {
  const t = TONES[tone];
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-pill border px-2 py-0.5 text-xs font-medium',
        t.badge,
      )}
    >
      {dot && <span aria-hidden="true" className={cn('h-1.5 w-1.5 rounded-full', t.dot)} />}
      {children}
    </span>
  );
}
