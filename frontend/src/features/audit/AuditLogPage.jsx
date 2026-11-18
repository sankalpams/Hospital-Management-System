import { useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import clsx from 'clsx';
import { auditApi } from '../../api/admin';
import { toApiError } from '../../api/client';
import { PageHeader } from '../../components/layout/AppShell';
import { DataTable } from '../../components/ui/DataTable';
import { FilterSelect, SearchInput, Toolbar } from '../../components/ui/Toolbar';
import { Badge } from '../../components/ui/Badge';
import { Modal } from '../../components/ui/Modal';
import { formatDateTime } from '../../lib/format';
import { useListParams } from '../../lib/useListParams';
import { controlClass } from '../../components/ui/Field';

const TABS = [{ id: 'changes', label: 'Audit trail' }, { id: 'logins', label: 'Sign-in activity' }];

function DateRange({ from, to, onChange }) {
  return (
    <div className="flex flex-wrap items-center gap-2 text-sm text-foreground-muted">
      <label className="flex min-w-0 items-center gap-2">From <input type="date" value={from ?? ''} onChange={(e) => onChange({ from: e.target.value })} className={controlClass(false, 'h-10 w-auto min-w-0')} /></label>
      <label className="flex min-w-0 items-center gap-2">To <input type="date" value={to ?? ''} onChange={(e) => onChange({ to: e.target.value })} className={controlClass(false, 'h-10 w-auto min-w-0')} /></label>
    </div>
  );
}

export default function AuditLogPage() {
  const [params, setParams] = useListParams({ tab: 'changes', size: 25 });
  const tab = params.tab === 'logins' ? 'logins' : 'changes';
  const [detail, setDetail] = useState(null);

  const query = useQuery({
    queryKey: ['audit', tab, params],
    queryFn: () => {
      const { tab: _t, ...rest } = params;
      return tab === 'logins' ? auditApi.loginActivity(rest) : auditApi.logs(rest);
    },
    placeholderData: keepPreviousData,
  });

  const changeColumns = [
    { key: 'createdAt', header: 'When', render: (a) => <span className="tabular whitespace-nowrap">{formatDateTime(a.createdAt)}</span> },
    { key: 'actorUsername', header: 'User', render: (a) => <span className="font-mono text-xs">{a.actorUsername ?? 'system'}</span> },
    { key: 'action', header: 'Action', render: (a) => <Badge tone="primary">{a.action}</Badge> },
    { key: 'entity', header: 'Record', render: (a) => <span>{a.entityType}{a.entityId ? ` #${a.entityId}` : ''}</span> },
    { key: 'summary', header: 'Summary', render: (a) => <span className="text-foreground-muted">{a.summary}</span> },
  ];
  const loginColumns = [
    { key: 'attemptedAt', header: 'When', render: (a) => <span className="tabular whitespace-nowrap">{formatDateTime(a.attemptedAt)}</span> },
    { key: 'username', header: 'Username', render: (a) => <span className="font-mono text-xs">{a.username}</span> },
    { key: 'success', header: 'Result', render: (a) => (a.success ? <Badge tone="success">Signed in</Badge> : <Badge tone="danger">{a.failureReason?.replace(/_/g, ' ').toLowerCase() ?? 'failed'}</Badge>) },
    { key: 'ipAddress', header: 'IP address', render: (a) => <span className="font-mono text-xs">{a.ipAddress}</span> },
    { key: 'userAgent', header: 'Device', render: (a) => <span className="line-clamp-1 max-w-xs text-xs text-foreground-subtle">{a.userAgent}</span> },
  ];

  return (
    <>
      <PageHeader title="Audit log" description="Who changed what, and every sign-in attempt. Entries cannot be edited or deleted." />
      <div role="tablist" aria-label="Log type" className="mb-4 inline-flex rounded-md border border-border bg-surface p-1">
        {TABS.map((t) => (
          <button
            key={t.id}
            role="tab"
            type="button"
            aria-selected={tab === t.id}
            onClick={() => setParams({ tab: t.id, q: '', action: '', entityType: '', success: '' })}
            className={clsx('rounded px-3 py-1.5 text-sm font-medium', tab === t.id ? 'bg-primary text-primary-foreground' : 'text-foreground-muted hover:text-foreground')}
          >
            {t.label}
          </button>
        ))}
      </div>
      <Toolbar>
        <SearchInput value={params.q} onChange={(q) => setParams({ q })} placeholder={tab === 'logins' ? 'Search username or IP' : 'Search user, summary or record id'} />
        {tab === 'logins' ? (
          <FilterSelect label="Result" value={params.success ?? ''} onChange={(success) => setParams({ success })}
                        options={[{ value: '', label: 'All' }, { value: 'true', label: 'Successful' }, { value: 'false', label: 'Failed' }]} />
        ) : (
          <FilterSelect label="Record" value={params.entityType ?? ''} onChange={(entityType) => setParams({ entityType })}
                        options={[{ value: '', label: 'All' }, ...['USER', 'DEPARTMENT', 'PATIENT', 'DOCTOR', 'APPOINTMENT'].map((v) => ({ value: v, label: v.charAt(0) + v.slice(1).toLowerCase() }))]} />
        )}
        <DateRange from={params.from} to={params.to} onChange={setParams} />
      </Toolbar>
      <DataTable
        caption={tab === 'logins' ? 'Sign-in activity' : 'Audit trail'}
        columns={tab === 'logins' ? loginColumns : changeColumns}
        page={query.data}
        loading={query.isFetching}
        error={query.isError ? toApiError(query.error).message : null}
        onPageChange={(page) => setParams({ page }, { resetPage: false })}
        onRowClick={tab === 'changes' ? setDetail : undefined}
        emptyTitle="No entries for these filters"
      />
      {detail && (
        <Modal open onClose={() => setDetail(null)} size="lg" title={`${detail.action} · ${detail.entityType}${detail.entityId ? ` #${detail.entityId}` : ''}`}
               description={`${formatDateTime(detail.createdAt)} by ${detail.actorUsername ?? 'system'} · reference ${detail.requestId ?? '—'}`}>
          <div className="grid gap-4 sm:grid-cols-2">
            {[['Before', detail.beforeJson], ['After', detail.afterJson]].map(([label, value]) => (
              <div key={label}>
                <p className="mb-1 text-[13px] font-medium">{label}</p>
                <pre className="max-h-80 overflow-auto rounded-md bg-surface-muted p-3 font-mono text-xs">{value ? JSON.stringify(JSON.parse(value), null, 2) : '—'}</pre>
              </div>
            ))}
          </div>
        </Modal>
      )}
    </>
  );
}
