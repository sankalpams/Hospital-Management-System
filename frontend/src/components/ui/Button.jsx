import { forwardRef } from 'react';
import { Link } from 'react-router-dom';
import { Loader2 } from 'lucide-react';
import clsx from 'clsx';

const variants = {
  primary: 'bg-primary text-primary-foreground hover:bg-primary-hover shadow-xs',
  secondary: 'bg-surface text-primary border border-border-strong hover:bg-primary-soft',
  ghost: 'bg-transparent text-foreground-muted hover:bg-surface-muted hover:text-foreground',
  danger: 'bg-danger text-white hover:opacity-90 shadow-xs',
};

const sizes = {
  sm: 'h-8 px-3 text-sm gap-1.5',
  md: 'h-10 px-4 text-sm gap-2',
  lg: 'h-11 px-5 text-base gap-2',
};

export function buttonClasses({ variant = 'primary', size = 'md', className } = {}) {
  return clsx(
    'inline-flex select-none items-center justify-center whitespace-nowrap rounded-md font-medium',
    'transition-colors duration-fast disabled:cursor-not-allowed disabled:opacity-50',
    variants[variant],
    sizes[size],
    className,
  );
}

/**
 * MASTER.md §6 button. While `loading`, the label stays visible (no layout shift),
 * the button is disabled and announces aria-busy.
 */
export const Button = forwardRef(function Button(
  { variant = 'primary', size = 'md', loading = false, disabled, icon: Icon, className, children, type = 'button', ...rest },
  ref,
) {
  return (
    <button
      ref={ref}
      type={type}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={buttonClasses({ variant, size, className })}
      {...rest}
    >
      {loading ? (
        <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
      ) : (
        Icon && <Icon className="h-4 w-4" aria-hidden="true" />
      )}
      {children}
    </button>
  );
});

/** Navigation that looks like a button: a real link (never a button nested in a link). */
export function LinkButton({ to, variant = 'primary', size = 'md', icon: Icon, className, children, ...rest }) {
  return (
    <Link to={to} className={buttonClasses({ variant, size, className })} {...rest}>
      {Icon && <Icon className="h-4 w-4" aria-hidden="true" />}
      {children}
    </Link>
  );
}
