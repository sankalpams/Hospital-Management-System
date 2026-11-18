import { ArrowDown, ArrowUp, ArrowUpDown, ChevronLeft, ChevronRight, Inbox } from 'lucide-react';
import clsx from 'clsx';
import { Button } from './Button';

/**
 * Server-driven table: sorting and paging are requests to the API, never client-side
 * re-ordering of a partial page.
 *
 * columns: [{ key, header, sortKey?, render?(row), align?: 'right', className? }]
 * page:    PageResponse from the API ({ content, page, size, totalElements, totalPages })
 * sort:    "key,asc|desc"
 */
export function DataTable({
  columns, page, loading, error, sort, onSortChange, onPageChange, onRowClick, rowKey = 'id',
  emptyTitle = 'No records found', emptyMessage, emptyAction, caption,
}) {
  const rows = page?.content ?? [];
  const [sortKey, sortDir] = (sort ?? '').split(',');

  const toggleSort = (key) => {
    if (!onSortChange) return;
    onSortChange(sortKey === key && sortDir === 'asc' ? `${key},desc` : `${key},asc`);
  };

  return (
    <div className="overflow-hidden rounded-lg border border-border bg-surface shadow-sm">
      {/* relative: keeps absolutely positioned children (e.g. sr-only labels) inside the scroll box */}
      <div className="relative overflow-x-auto">
        <table className="w-full min-w-[640px] border-collapse text-left text-sm">
          {caption && <caption className="sr-only">{caption}</caption>}
          <thead className="bg-surface-muted text-[13px] text-foreground-muted">
            <tr>
              {columns.map((c) => {
                const active = c.sortKey && sortKey === c.sortKey;
                return (
                  <th
                    key={c.key}
                    scope="col"
                    aria-sort={active ? (sortDir === 'desc' ? 'descending' : 'ascending') : undefined}
                    className={clsx('h-10 whitespace-nowrap border-b border-border px-4 font-medium', c.align === 'right' && 'text-right', c.className)}
                  >
                    {c.sortKey ? (
                      <button type="button" onClick={() => toggleSort(c.sortKey)} className="inline-flex items-center gap-1 hover:text-foreground">
                        {c.header}
                        {active
                          ? (sortDir === 'desc' ? <ArrowDown className="h-3.5 w-3.5" aria-hidden="true" /> : <ArrowUp className="h-3.5 w-3.5" aria-hidden="true" />)
                          : <ArrowUpDown className="h-3.5 w-3.5 opacity-50" aria-hidden="true" />}
                      </button>
                    ) : c.header}
                  </th>
                );
              })}
            </tr>
          </thead>
          <tbody>
            {loading && rows.length === 0 && [...Array(5)].map((_, i) => (
              <tr key={`sk-${i}`} className="border-b border-border last:border-0">
                {columns.map((c) => (
                  <td key={c.key} className="px-4 py-3"><span className="block h-4 w-3/4 animate-pulse rounded bg-surface-muted" /></td>
                ))}
              </tr>
            ))}
            {!loading && error && (
              <tr><td colSpan={columns.length} className="px-4 py-10 text-center text-sm text-danger" role="alert">{error}</td></tr>
            )}
            {!loading && !error && rows.length === 0 && (
              <tr>
                <td colSpan={columns.length} className="px-4 py-12">
                  <div className="flex flex-col items-center text-center">
                    <Inbox className="h-8 w-8 text-foreground-subtle" aria-hidden="true" />
                    <p className="mt-2 font-medium">{emptyTitle}</p>
                    {emptyMessage && <p className="mt-0.5 max-w-sm text-sm text-foreground-muted">{emptyMessage}</p>}
                    {emptyAction && <div className="mt-4">{emptyAction}</div>}
                  </div>
                </td>
              </tr>
            )}
            {rows.map((row) => (
              <tr
                key={row[rowKey]}
                onClick={onRowClick ? () => onRowClick(row) : undefined}
                onKeyDown={onRowClick ? (e) => { if (e.key === 'Enter') onRowClick(row); } : undefined}
                tabIndex={onRowClick ? 0 : undefined}
                className={clsx('border-b border-border last:border-0', onRowClick && 'cursor-pointer hover:bg-surface-muted', loading && 'opacity-60')}
              >
                {columns.map((c) => (
                  <td key={c.key} className={clsx('px-4 py-3 align-middle', c.align === 'right' && 'text-right tabular', c.className)}>
                    {c.render ? c.render(row) : row[c.key]}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {page && page.totalElements > 0 && (
        <Pagination page={page} onPageChange={onPageChange} />
      )}
    </div>
  );
}

export function Pagination({ page, onPageChange }) {
  const from = page.page * page.size + 1;
  const to = Math.min(page.totalElements, from + page.content.length - 1);
  return (
    <nav aria-label="Pagination" className="flex flex-col items-center justify-between gap-2 border-t border-border px-4 py-2.5 text-sm text-foreground-muted sm:flex-row">
      <p className="tabular">
        Showing <span className="font-medium text-foreground">{from}–{to}</span> of{' '}
        <span className="font-medium text-foreground">{page.totalElements.toLocaleString()}</span>
      </p>
      <div className="flex items-center gap-1">
        <Button variant="ghost" size="sm" icon={ChevronLeft} disabled={page.page === 0} onClick={() => onPageChange(page.page - 1)}>Previous</Button>
        <span className="px-2 tabular">Page {page.page + 1} of {Math.max(1, page.totalPages)}</span>
        <Button variant="ghost" size="sm" disabled={page.page + 1 >= page.totalPages} onClick={() => onPageChange(page.page + 1)}>
          Next <ChevronRight className="h-4 w-4" aria-hidden="true" />
        </Button>
      </div>
    </nav>
  );
}
