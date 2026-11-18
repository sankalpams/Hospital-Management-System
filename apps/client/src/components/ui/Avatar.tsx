import { cn } from './cn';

export function initials(name: string): string {
  const words = name.trim().split(/\s+/).filter(Boolean);
  if (words.length === 0) return '';
  const first = words[0]!.charAt(0);
  const last = words.length > 1 ? words[words.length - 1]!.charAt(0) : '';
  return (first + last).toUpperCase();
}

/** Initials avatar. Decorative (aria-hidden) because the name is shown beside it. */
export function Avatar({ name, size = 'md' }: { name: string; size?: 'sm' | 'md' }) {
  return (
    <span
      aria-hidden="true"
      className={cn(
        'inline-flex shrink-0 select-none items-center justify-center rounded-full bg-primary-subtle font-semibold text-primary-hover',
        size === 'sm' ? 'h-8 w-8 text-xs' : 'h-10 w-10 text-sm',
      )}
    >
      {initials(name)}
    </span>
  );
}
