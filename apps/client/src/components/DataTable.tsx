import { Fragment, type ReactNode } from 'react';
import { Table, TBody, Td, Th, THead, Tr } from './ui/Table';
import { Spinner } from './ui/Spinner';

export interface Column<T> {
  key: string;
  header: string;
  cell: (row: T) => ReactNode;
  className?: string;
}

export interface DataTableProps<T> {
  /** Accessible name for the table (rendered as a visually hidden caption). */
  caption: string;
  columns: Column<T>[];
  rows: T[];
  getRowKey: (row: T) => string;
  loading?: boolean;
  emptyMessage?: string;
  /** Optional detail row shown beneath a row (e.g. expandable JSON). Return null to hide. */
  renderExpanded?: (row: T) => ReactNode;
}

const SKELETON_ROWS = 5;

/** Reusable list table: captioned, scoped headers, skeleton loading rows and an empty state. */
export function DataTable<T>({
  caption,
  columns,
  rows,
  getRowKey,
  loading = false,
  emptyMessage = 'Nothing to show.',
  renderExpanded,
}: DataTableProps<T>) {
  const span = columns.length;
  return (
    <Table aria-busy={loading || undefined}>
      <caption className="sr-only">{caption}</caption>
      <THead>
        <Tr>
          {columns.map((c) => (
            <Th key={c.key} className={c.className}>
              {c.header}
            </Th>
          ))}
        </Tr>
      </THead>
      <TBody>
        {loading ? (
          <>
            <Tr>
              <Td colSpan={span}>
                <Spinner label="Loading" />
              </Td>
            </Tr>
            {Array.from({ length: SKELETON_ROWS - 1 }, (_, i) => (
              <Tr key={i} aria-hidden="true">
                <Td colSpan={span}>
                  <div className="h-4 w-full animate-pulse rounded-control bg-border" />
                </Td>
              </Tr>
            ))}
          </>
        ) : rows.length === 0 ? (
          <Tr>
            <Td colSpan={span} className="py-10 text-center text-muted">
              {emptyMessage}
            </Td>
          </Tr>
        ) : (
          rows.map((row) => {
            const key = getRowKey(row);
            const extra = renderExpanded?.(row);
            return (
              <Fragment key={key}>
                <Tr>
                  {columns.map((c) => (
                    <Td key={c.key} className={c.className}>
                      {c.cell(row)}
                    </Td>
                  ))}
                </Tr>
                {extra ? (
                  <Tr>
                    <Td colSpan={span} className="bg-bg">
                      {extra}
                    </Td>
                  </Tr>
                ) : null}
              </Fragment>
            );
          })
        )}
      </TBody>
    </Table>
  );
}
