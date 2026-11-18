import { cloneElement, type ReactElement } from 'react';
import { Label } from './Label';

interface FormFieldProps {
  id: string;
  label: string;
  error?: string;
  hint?: string;
  children: ReactElement<{ id?: string; invalid?: boolean; 'aria-describedby'?: string }>;
}

/** Visible label, optional hint, inline error wired via aria-describedby. */
export function FormField({ id, label, error, hint, children }: FormFieldProps) {
  const errorId = `${id}-error`;
  const hintId = `${id}-hint`;
  const describedBy =
    [hint && !error ? hintId : null, error ? errorId : null].filter(Boolean).join(' ') || undefined;
  return (
    <div>
      <Label htmlFor={id}>{label}</Label>
      {cloneElement(children, { id, invalid: !!error, 'aria-describedby': describedBy })}
      {hint && !error && (
        <p id={hintId} className="mt-1 text-xs text-muted">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} className="mt-1 text-xs font-medium text-danger">
          {error}
        </p>
      )}
    </div>
  );
}
