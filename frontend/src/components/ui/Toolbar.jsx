import { useEffect, useState } from 'react';
import { Search, X } from 'lucide-react';
import { controlClass } from './Field';

/** Debounced search box (300 ms, MASTER.md §6). Calls onChange only after typing pauses. */
export function SearchInput({ value, onChange, placeholder = 'Search', label = 'Search', delay = 300 }) {
  const [text, setText] = useState(value ?? '');
  useEffect(() => { setText(value ?? ''); }, [value]);
  useEffect(() => {
    if (text === (value ?? '')) return undefined;
    const t = window.setTimeout(() => onChange(text), delay);
    return () => window.clearTimeout(t);
  }, [text, value, onChange, delay]);

  return (
    <div className="relative w-full sm:max-w-sm">
      <label className="sr-only" htmlFor="table-search">{label}</label>
      <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-foreground-subtle" aria-hidden="true" />
      <input
        id="table-search"
        type="search"
        value={text}
        onChange={(e) => setText(e.target.value)}
        placeholder={placeholder}
        className={controlClass(false, 'h-10 pl-9 pr-9')}
      />
      {text && (
        <button type="button" onClick={() => { setText(''); onChange(''); }} className="absolute right-2 top-1/2 -translate-y-1/2 rounded p-1 text-foreground-subtle hover:text-foreground" aria-label="Clear search">
          <X className="h-4 w-4" aria-hidden="true" />
        </button>
      )}
    </div>
  );
}

/** Compact filter select used in table toolbars. */
export function FilterSelect({ label, value, onChange, options }) {
  return (
    <label className="flex min-w-0 max-w-full items-center gap-2 text-sm text-foreground-muted">
      <span className="whitespace-nowrap">{label}</span>
      {/* min-w-0 lets a select with long options shrink on a phone instead of widening the page */}
      <select value={value} onChange={(e) => onChange(e.target.value)} className={controlClass(false, 'h-10 w-auto min-w-0 flex-1 pr-8 sm:flex-initial')}>
        {options.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
      </select>
    </label>
  );
}

export function Toolbar({ children }) {
  return <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-center">{children}</div>;
}
