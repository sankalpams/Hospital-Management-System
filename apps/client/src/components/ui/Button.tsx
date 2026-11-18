import type { ButtonHTMLAttributes } from 'react';
import { cx } from './cx';
import { Spinner } from './Spinner';

type Variant = 'primary' | 'secondary' | 'danger' | 'ghost';

const VARIANTS: Record<Variant, string> = {
  primary: 'bg-primary text-on-primary hover:bg-primary-hover border-transparent',
  secondary: 'bg-surface text-text border-border hover:bg-bg',
  danger: 'bg-danger text-on-primary border-transparent hover:opacity-90',
  ghost: 'bg-transparent text-text border-transparent hover:bg-primary-subtle',
};

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  loading?: boolean;
}

export function Button({
  variant = 'primary',
  loading,
  disabled,
  className,
  children,
  type = 'button',
  ...rest
}: ButtonProps) {
  return (
    <button
      type={type}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={cx(
        'inline-flex min-h-11 items-center justify-center gap-2 rounded-control border px-4 py-2 text-sm font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-60',
        VARIANTS[variant],
        className,
      )}
      {...rest}
    >
      {loading && <Spinner label="" decorative />}
      {children}
    </button>
  );
}
