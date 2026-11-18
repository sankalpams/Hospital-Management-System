import { AlertTriangle, CheckCircle2, Info, XCircle } from 'lucide-react';
import clsx from 'clsx';

const tones = {
  info: { box: 'bg-info-soft text-info border-border-strong', Icon: Info },
  success: { box: 'bg-success-soft text-success border-border', Icon: CheckCircle2 },
  warning: { box: 'bg-warning-soft text-warning border-border', Icon: AlertTriangle },
  danger: { box: 'bg-danger-soft text-danger border-border', Icon: XCircle },
};

/** Inline message. `requestId` (from API errors) is shown so users can quote it to support. */
export function Alert({ tone = 'info', title, children, requestId, className }) {
  const { box, Icon } = tones[tone];
  return (
    <div
      role={tone === 'danger' || tone === 'warning' ? 'alert' : 'status'}
      className={clsx('flex gap-3 rounded-md border px-3.5 py-3 text-sm', box, className)}
    >
      <Icon className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
      <div className="min-w-0">
        {title && <p className="font-semibold">{title}</p>}
        {children && <div className={clsx(title && 'mt-0.5')}>{children}</div>}
        {requestId && <p className="mt-1 font-mono text-xs opacity-80">Reference: {requestId}</p>}
      </div>
    </div>
  );
}
