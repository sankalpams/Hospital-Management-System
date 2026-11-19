import clsx from 'clsx';

/** Brand mark: medical cross in a rounded square, drawn with semantic colours. */
export function Logo({ className, inverted = false, showText = true }) {
  return (
    <span className={clsx('inline-flex items-center gap-2.5', className)}>
      <svg viewBox="0 0 32 32" className="h-8 w-8 shrink-0" aria-hidden="true">
        <rect width="32" height="32" rx="8" className={inverted ? 'fill-white' : 'fill-primary'} />
        <path d="M13 7h6v6h6v6h-6v6h-6v-6H7v-6h6z" className={inverted ? 'fill-sidebar' : 'fill-white'} />
      </svg>
      {showText && (
        <span className="flex flex-col leading-tight">
          <span className={clsx('font-heading text-[15px] font-semibold', inverted ? 'text-white' : 'text-foreground')}>
            HMS
          </span>
          <span className={clsx('text-[11px]', inverted ? 'text-sidebar-foreground' : 'text-foreground-subtle')}>
            Prabodha Hospital
          </span>
        </span>
      )}
    </span>
  );
}
