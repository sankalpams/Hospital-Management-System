export function Spinner({
  label = 'Loading',
  decorative = false,
}: {
  label?: string;
  decorative?: boolean;
}) {
  return (
    <span role={decorative ? undefined : 'status'} className="inline-flex items-center gap-2 text-muted">
      <svg
        className="h-5 w-5 animate-spin text-primary"
        viewBox="0 0 24 24"
        fill="none"
        aria-hidden="true"
      >
        <circle cx="12" cy="12" r="9" stroke="currentColor" strokeWidth="3" opacity="0.25" />
        <path d="M21 12a9 9 0 0 0-9-9" stroke="currentColor" strokeWidth="3" strokeLinecap="round" />
      </svg>
      {label && <span>{label}</span>}
    </span>
  );
}
