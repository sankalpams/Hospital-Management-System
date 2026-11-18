import clsx from 'clsx';

export function Card({ className, children, as: Tag = 'section', ...rest }) {
  return (
    <Tag className={clsx('rounded-lg border border-border bg-surface shadow-sm', className)} {...rest}>
      {children}
    </Tag>
  );
}

export function CardHeader({ title, description, action, icon: Icon }) {
  return (
    <header className="flex items-start justify-between gap-4 border-b border-border px-5 py-4">
      <div className="flex min-w-0 items-start gap-3">
        {Icon && (
          <span className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-md bg-primary-soft text-primary-soft-foreground">
            <Icon className="h-4 w-4" aria-hidden="true" />
          </span>
        )}
        <div className="min-w-0">
          <h2 className="text-base">{title}</h2>
          {description && <p className="mt-0.5 text-sm text-foreground-muted">{description}</p>}
        </div>
      </div>
      {action}
    </header>
  );
}

export function CardBody({ className, children }) {
  return <div className={clsx('px-5 py-4', className)}>{children}</div>;
}
