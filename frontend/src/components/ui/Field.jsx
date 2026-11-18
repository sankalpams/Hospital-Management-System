import { forwardRef, useId, useState } from 'react';
import { AlertCircle, ChevronDown, Eye, EyeOff } from 'lucide-react';
import clsx from 'clsx';

/**
 * Labelled form controls (MASTER.md §6): visible label, helper text, inline error with
 * aria-invalid + aria-describedby so screen readers announce the problem.
 */
function useFieldIds(idProp, error, hint) {
  const generated = useId();
  const id = idProp ?? generated;
  const describedBy = [error ? `${id}-error` : null, hint && !error ? `${id}-hint` : null].filter(Boolean).join(' ') || undefined;
  return { id, describedBy };
}

export function controlClass(error, extra) {
  return clsx(
    'w-full rounded-md border bg-surface px-3 text-[15px] text-foreground',
    'placeholder:text-foreground-subtle transition-colors duration-fast',
    'hover:border-border-strong focus-visible:ring-offset-0',
    'disabled:cursor-not-allowed disabled:bg-surface-muted disabled:text-foreground-muted',
    error ? 'border-danger focus-visible:ring-danger' : 'border-border',
    extra,
  );
}

function FieldShell({ id, label, required, error, hint, className, children }) {
  return (
    <div className={clsx('flex flex-col gap-1.5', className)}>
      {label && (
        <label htmlFor={id} className="text-[13px] font-medium text-foreground">
          {label}
          {required && <span className="ml-0.5 text-danger" aria-hidden="true">*</span>}
        </label>
      )}
      {children}
      {hint && !error && <p id={`${id}-hint`} className="text-xs text-foreground-subtle">{hint}</p>}
      {error && (
        <p id={`${id}-error`} className="flex items-center gap-1 text-xs font-medium text-danger">
          <AlertCircle className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
          {error}
        </p>
      )}
    </div>
  );
}

export const TextField = forwardRef(function TextField(
  { id: idProp, label, error, hint, type = 'text', required, className, inputClassName, ...rest },
  ref,
) {
  const { id, describedBy } = useFieldIds(idProp, error, hint);
  const [reveal, setReveal] = useState(false);
  const isPassword = type === 'password';
  return (
    <FieldShell id={id} label={label} required={required} error={error} hint={hint} className={className}>
      <div className="relative">
        <input
          ref={ref}
          id={id}
          type={isPassword && reveal ? 'text' : type}
          required={required}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          className={controlClass(error, clsx('h-10', isPassword && 'pr-11', inputClassName))}
          {...rest}
        />
        {isPassword && (
          <button
            type="button"
            onClick={() => setReveal((r) => !r)}
            className="absolute inset-y-0 right-0 flex w-10 items-center justify-center rounded-r-md text-foreground-subtle hover:text-foreground"
            aria-label={reveal ? 'Hide password' : 'Show password'}
            aria-pressed={reveal}
          >
            {reveal ? <EyeOff className="h-4 w-4" aria-hidden="true" /> : <Eye className="h-4 w-4" aria-hidden="true" />}
          </button>
        )}
      </div>
    </FieldShell>
  );
});

/** options: [{ value, label }]. `placeholder` renders an empty first option. */
export const SelectField = forwardRef(function SelectField(
  { id: idProp, label, error, hint, required, className, options = [], placeholder, ...rest },
  ref,
) {
  const { id, describedBy } = useFieldIds(idProp, error, hint);
  return (
    <FieldShell id={id} label={label} required={required} error={error} hint={hint} className={className}>
      <div className="relative">
        <select
          ref={ref}
          id={id}
          required={required}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          className={controlClass(error, 'h-10 appearance-none pr-9')}
          {...rest}
        >
          {placeholder !== undefined && <option value="">{placeholder}</option>}
          {options.map((o) => (
            <option key={o.value} value={o.value} disabled={o.disabled}>{o.label}</option>
          ))}
        </select>
        <ChevronDown className="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-foreground-subtle" aria-hidden="true" />
      </div>
    </FieldShell>
  );
});

export const TextAreaField = forwardRef(function TextAreaField(
  { id: idProp, label, error, hint, required, className, rows = 3, ...rest },
  ref,
) {
  const { id, describedBy } = useFieldIds(idProp, error, hint);
  return (
    <FieldShell id={id} label={label} required={required} error={error} hint={hint} className={className}>
      <textarea
        ref={ref}
        id={id}
        rows={rows}
        required={required}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        className={controlClass(error, 'py-2')}
        {...rest}
      />
    </FieldShell>
  );
});

export function CheckboxField({ label, description, className, ...rest }) {
  const id = useId();
  return (
    <div className={clsx('flex items-start gap-2.5', className)}>
      <input id={id} type="checkbox" className="mt-0.5 h-4 w-4 rounded border-border accent-[var(--color-primary)]" {...rest} />
      <label htmlFor={id} className="text-sm">
        <span className="font-medium">{label}</span>
        {description && <span className="block text-xs text-foreground-subtle">{description}</span>}
      </label>
    </div>
  );
}
