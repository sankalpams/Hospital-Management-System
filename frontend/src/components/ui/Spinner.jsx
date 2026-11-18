import { Loader2 } from 'lucide-react';
import clsx from 'clsx';

export function Spinner({ className, label }) {
  return (
    <span role="status" className={clsx('inline-flex items-center gap-2', className)}>
      <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
      {label ? <span>{label}</span> : <span className="sr-only">Loading</span>}
    </span>
  );
}

export function FullPageSpinner({ label = 'Loading' }) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-background text-foreground-muted">
      <Spinner label={label} />
    </div>
  );
}
