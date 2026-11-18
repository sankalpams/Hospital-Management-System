import clsx from 'clsx';

const tones = {
  neutral: 'bg-neutral-soft text-neutral',
  info: 'bg-info-soft text-info',
  special: 'bg-special-soft text-special',
  success: 'bg-success-soft text-success',
  warning: 'bg-warning-soft text-warning',
  danger: 'bg-danger-soft text-danger',
  primary: 'bg-primary-soft text-primary-soft-foreground',
};

/** Status → tone mapping from MASTER.md §2. Badges always carry a text label, never colour alone. */
const STATUS_TONE = {
  PENDING: 'neutral', DRAFT: 'neutral', REQUESTED: 'neutral', INACTIVE: 'neutral',
  CONFIRMED: 'info', SAMPLE_COLLECTED: 'info', ISSUED: 'info', ON_LEAVE: 'info',
  CHECKED_IN: 'special', PROCESSING: 'special', IN_PROGRESS: 'special',
  COMPLETED: 'success', REVIEWED: 'success', PAID: 'success', ACTIVE: 'success', AVAILABLE: 'success', APPROVED: 'success',
  PARTIALLY_PAID: 'warning', NO_SHOW: 'warning', CLEANING: 'warning', LOW_STOCK: 'warning', EXPIRING: 'warning',
  CANCELLED: 'danger', VOID: 'danger', OCCUPIED: 'danger', EXPIRED: 'danger', REJECTED: 'danger', LOCKED: 'danger',
};

export function toneForStatus(status) {
  return STATUS_TONE[status] ?? 'neutral';
}

export function humanise(code) {
  return String(code ?? '')
    .toLowerCase()
    .split('_')
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(' ');
}

export function Badge({ tone = 'neutral', children, className }) {
  return (
    <span className={clsx('inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium', tones[tone], className)}>
      {children}
    </span>
  );
}

export function StatusBadge({ status }) {
  return <Badge tone={toneForStatus(status)}>{humanise(status)}</Badge>;
}
