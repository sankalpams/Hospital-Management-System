import { forwardRef, type SelectHTMLAttributes } from 'react';
import { cx } from './cx';

export interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  invalid?: boolean;
}

export const Select = forwardRef<HTMLSelectElement, SelectProps>(function Select(
  { invalid, className, ...rest },
  ref,
) {
  return (
    <select
      ref={ref}
      aria-invalid={invalid || undefined}
      className={cx(
        'min-h-11 w-full rounded-control border bg-surface px-3 py-2 text-sm text-text disabled:opacity-60',
        invalid ? 'border-danger' : 'border-border',
        className,
      )}
      {...rest}
    />
  );
});
