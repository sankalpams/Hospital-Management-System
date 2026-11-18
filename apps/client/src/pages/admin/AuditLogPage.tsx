import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { DataTable, type Column } from '../../components/DataTable';
import { Pagination } from '../../components/Pagination';
import { Badge } from '../../components/ui/Badge';
import { Input } from '../../components/ui/Input';
import { FormField } from '../../components/ui/FormField';
import { Select } from '../../components/ui/Select';
import { api } from '../../lib/api';

const ACTIONS = ['CREATE', 'UPDATE', 'DELETE', 'LOGIN', 'LOGIN_FAILED', 'LOGOUT', 'VIEW_EMR', 'EXPORT'] as const;
const PAGE_SIZE = 25;

interface AuditRow {
  id: string;
  userId: string | null;
  action: string;
  entityType: string;
  entityId: string | null;
  before: unknown;
  after: unknown;
  ip: string | null;
  createdAt: string;
  user: { username: string; fullName: string } | null;
}
interface Paged<T> {
  items: T[];
  page: number;
  pageSize: number;
  total: number;
}
interface UserOption {
  id: string;
  username: string;
  fullName: string;
}

const formatWhen = (iso: string) =>
  new Date(iso).toLocaleString(undefined, { timeZone: 'Asia/Colombo', dateStyle: 'medium', timeStyle: 'medium' });

const hasJson = (v: unknown) => v !== null && v !== undefined;

export function AuditLogPage() {
  const [userId, setUserId] = useState('');
  const [action, setAction] = useState('');
  const [entityType, setEntityType] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [page, setPage] = useState(1);
  const [expanded, setExpanded] = useState<ReadonlySet<string>>(new Set());

  const rangeError = from && to && from > to ? 'The start date must be on or before the end date.' : undefined;

  const users = useQuery({
    queryKey: ['users', 'options'],
    queryFn: () => api<Paged<UserOption>>('/users?pageSize=200'),
    staleTime: 60_000,
  });

  const query = useQuery({
    queryKey: ['audit', { userId, action, entityType: entityType.trim(), from, to, page }],
    enabled: !rangeError,
    queryFn: () => {
      const p = new URLSearchParams({ page: String(page), pageSize: String(PAGE_SIZE) });
      if (userId) p.set('userId', userId);
      if (action) p.set('action', action);
      if (entityType.trim()) p.set('entityType', entityType.trim());
      // Date-only values: the server treats them as whole Asia/Colombo days.
      if (from) p.set('from', from);
      if (to) p.set('to', to);
      return api<Paged<AuditRow>>(`/audit-logs?${p}`);
    },
  });

  const filter = <T,>(set: (v: T) => void) => (v: T) => {
    set(v);
    setPage(1);
    setExpanded(new Set());
  };

  const toggle = (id: string) =>
    setExpanded((cur) => {
      const next = new Set(cur);
      if (!next.delete(id)) next.add(id);
      return next;
    });

  const columns: Column<AuditRow>[] = [
    { key: 'when', header: 'When (Colombo)', cell: (r) => <span className="whitespace-nowrap">{formatWhen(r.createdAt)}</span> },
    { key: 'user', header: 'User', cell: (r) => (r.user ? `${r.user.fullName} (${r.user.username})` : 'System') },
    { key: 'action', header: 'Action', cell: (r) => <Badge tone={r.action === 'LOGIN_FAILED' ? 'danger' : 'info'}>{r.action}</Badge> },
    {
      key: 'entity',
      header: 'Entity',
      cell: (r) => (
        <span>
          {r.entityType}
          {r.entityId && <span className="block max-w-40 truncate text-xs text-muted">{r.entityId}</span>}
        </span>
      ),
    },
    { key: 'ip', header: 'IP', cell: (r) => r.ip ?? '-' },
    {
      key: 'details',
      header: 'Details',
      cell: (r) =>
        hasJson(r.before) || hasJson(r.after) ? (
          <button
            type="button"
            aria-expanded={expanded.has(r.id)}
            aria-controls={`audit-detail-${r.id}`}
            onClick={() => toggle(r.id)}
            className="min-h-11 rounded-control px-2 text-sm font-medium text-primary underline"
          >
            {expanded.has(r.id) ? 'Hide changes' : 'Show changes'}
          </button>
        ) : (
          <span className="text-muted">None</span>
        ),
    },
  ];

  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-2xl font-semibold text-text">Audit log</h1>

      <form
        onSubmit={(e) => e.preventDefault()}
        aria-label="Audit log filters"
        className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5"
      >
        <FormField id="audit-user" label="User">
          <Select value={userId} onChange={(e) => filter(setUserId)(e.target.value)}>
            <option value="">All users</option>
            {users.data?.items.map((u) => (
              <option key={u.id} value={u.id}>
                {u.fullName} ({u.username})
              </option>
            ))}
          </Select>
        </FormField>
        <FormField id="audit-action" label="Action">
          <Select value={action} onChange={(e) => filter(setAction)(e.target.value)}>
            <option value="">All actions</option>
            {ACTIONS.map((a) => (
              <option key={a} value={a}>
                {a}
              </option>
            ))}
          </Select>
        </FormField>
        <FormField id="audit-entity" label="Entity type">
          <Input placeholder="e.g. User" value={entityType} onChange={(e) => filter(setEntityType)(e.target.value)} />
        </FormField>
        <FormField id="audit-from" label="From date">
          <Input type="date" value={from} onChange={(e) => filter(setFrom)(e.target.value)} />
        </FormField>
        <FormField id="audit-to" label="To date" error={rangeError}>
          <Input type="date" value={to} onChange={(e) => filter(setTo)(e.target.value)} />
        </FormField>
      </form>

      {query.isError ? (
        <div role="alert" className="rounded-control border border-danger bg-surface px-3 py-2 text-sm text-danger">
          Could not load the audit log.{' '}
          <button type="button" className="font-medium underline" onClick={() => void query.refetch()}>
            Try again
          </button>
        </div>
      ) : (
        <>
          <DataTable
            caption="Audit log"
            columns={columns}
            rows={rangeError ? [] : (query.data?.items ?? [])}
            getRowKey={(r) => r.id}
            loading={!rangeError && query.isLoading}
            emptyMessage="No audit entries match your filters."
            renderExpanded={(r) =>
              expanded.has(r.id) ? (
                <div id={`audit-detail-${r.id}`} className="grid gap-3 md:grid-cols-2">
                  <JsonBlock label="Before" value={r.before} />
                  <JsonBlock label="After" value={r.after} />
                </div>
              ) : null
            }
          />
          <Pagination page={page} pageSize={PAGE_SIZE} total={query.data?.total ?? 0} onChange={setPage} />
        </>
      )}
    </div>
  );
}

function JsonBlock({ label, value }: { label: string; value: unknown }) {
  return (
    <div>
      <p className="mb-1 text-xs font-medium uppercase text-muted">{label}</p>
      <pre className="max-h-64 overflow-auto rounded-control border border-border bg-surface p-3 font-mono text-xs text-text">
        {hasJson(value) ? JSON.stringify(value, null, 2) : '(none)'}
      </pre>
    </div>
  );
}
