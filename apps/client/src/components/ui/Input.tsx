import { forwardRef, type InputHTMLAttributes } from 'react';
import { cx } from './cx';

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  invalid?: boolean;
}

export const Input = forwardRef<HTMLInputElement, InputProps>(function Input(
  { invalid, className, ...rest },
  ref,
) {
  return (
    <input
      ref={ref}
      aria-invalid={invalid || undefined}
      className={cx(
        'min-h-11 w-full rounded-control border bg-surface px-3 py-2 text-sm text-text placeholder:text-muted disabled:opacity-60',
        invalid ? 'border-danger' : 'border-border',
        className,
      )}
      {...rest}
    />
  );
});
