import { useState } from 'react';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Check, KeyRound, Pencil, Power, UserPlus, X } from 'lucide-react';
import { usersApi } from '../../api/admin';
import { toApiError } from '../../api/client';
import { useAuth } from '../../auth/AuthProvider';
import { PageHeader } from '../../components/layout/AppShell';
import { DataTable } from '../../components/ui/DataTable';
import { FilterSelect, SearchInput, Toolbar } from '../../components/ui/Toolbar';
import { Badge, StatusBadge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { ConfirmDialog } from '../../components/ui/Modal';
import { useToast } from '../../components/ui/Toast';
import { formatDateTime } from '../../lib/format';
import { roleLabel, ROLE_LABELS } from '../../lib/roles';
import { useListParams } from '../../lib/useListParams';
import { TemporaryPasswordModal, UserFormModal } from './UserModals';

const ROLE_OPTIONS = [{ value: '', label: 'All roles' }, ...Object.entries(ROLE_LABELS).map(([value, label]) => ({ value, label }))];
const STATUS_OPTIONS = [{ value: '', label: 'Any status' }, { value: 'ACTIVE', label: 'Active' }, { value: 'INACTIVE', label: 'Inactive' }, { value: 'PENDING', label: 'Sign-up requests' }];

export default function UsersPage() {
  const { hasPermission, user: me } = useAuth();
  const toast = useToast();
  const queryClient = useQueryClient();
  const [params, setParams] = useListParams({ sort: 'fullName,asc' });
  const [editing, setEditing] = useState(null);           // null | 'new' | user
  const [issued, setIssued] = useState(null);             // { user, temporaryPassword }
  const [confirm, setConfirm] = useState(null);           // { kind: 'deactivate'|'activate'|'reset', user }
  const [confirmError, setConfirmError] = useState(null);

  const list = useQuery({
    queryKey: ['users', params],
    queryFn: () => usersApi.list(params),
    placeholderData: keepPreviousData,
  });

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['users'] });

  const action = useMutation({
    mutationFn: ({ kind, user }) => {
      if (kind === 'reset') return usersApi.resetPassword(user.id);
      return kind === 'activate' || kind === 'approve' ? usersApi.activate(user.id) : usersApi.deactivate(user.id);
    },
    onSuccess: (result, { kind, user }) => {
      setConfirm(null);
      refresh();
      if (kind === 'reset') setIssued(result);
      else toast.success(`${user.fullName} was ${{ activate: 'activated', deactivate: 'deactivated', approve: 'approved', reject: 'rejected' }[kind]}.`);
    },
    onError: (err) => setConfirmError(toApiError(err).message),
  });

  const canEdit = hasPermission('user:update');
  const canToggle = hasPermission('user:deactivate');
  const canReset = hasPermission('user:reset-password');

  const columns = [
    {
      key: 'fullName', header: 'Name', sortKey: 'fullName',
      render: (u) => (
        <div>
          <p className="font-medium">{u.fullName}</p>
          <p className="font-mono text-xs text-foreground-subtle">{u.username}</p>
        </div>
      ),
    },
    { key: 'roles', header: 'Roles', render: (u) => <div className="flex flex-wrap gap-1">{u.roles.map((r) => <Badge key={r} tone="primary">{roleLabel(r)}</Badge>)}</div> },
    {
      key: 'status', header: 'Status', sortKey: 'status',
      render: (u) => (
        <div className="flex flex-wrap gap-1">
          <StatusBadge status={u.status} />
          {u.locked && <StatusBadge status="LOCKED" />}
          {u.mustChangePassword && u.status === 'ACTIVE' && <Badge tone="warning">Temporary password</Badge>}
        </div>
      ),
    },
    { key: 'lastLoginAt', header: 'Last sign-in', sortKey: 'lastLoginAt', render: (u) => <span className="tabular text-foreground-muted">{formatDateTime(u.lastLoginAt)}</span> },
    {
      key: 'actions', header: <span className="sr-only">Actions</span>, align: 'right',
      render: (u) => (
        <div className="flex justify-end gap-1">
          {canEdit && <Button variant="ghost" size="sm" icon={Pencil} onClick={() => setEditing(u)} aria-label={`Edit ${u.fullName}`}>Edit</Button>}
          {canToggle && u.status === 'PENDING' && (
            <>
              <Button variant="ghost" size="sm" icon={Check} onClick={() => { setConfirmError(null); setConfirm({ kind: 'approve', user: u }); }} aria-label={`Approve ${u.fullName}`}>Approve</Button>
              <Button variant="ghost" size="sm" icon={X} onClick={() => { setConfirmError(null); setConfirm({ kind: 'reject', user: u }); }} aria-label={`Reject ${u.fullName}`}>Reject</Button>
            </>
          )}
          {canReset && u.id !== me.id && u.status !== 'PENDING' && (
            <Button variant="ghost" size="sm" icon={KeyRound} onClick={() => { setConfirmError(null); setConfirm({ kind: 'reset', user: u }); }} aria-label={`Reset password for ${u.fullName}`}>Reset</Button>
          )}
          {canToggle && u.id !== me.id && u.status !== 'PENDING' && (
            <Button
              variant="ghost" size="sm" icon={Power}
              onClick={() => { setConfirmError(null); setConfirm({ kind: u.status === 'ACTIVE' ? 'deactivate' : 'activate', user: u }); }}
              aria-label={`${u.status === 'ACTIVE' ? 'Deactivate' : 'Activate'} ${u.fullName}`}
            >
              {u.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}
            </Button>
          )}
        </div>
      ),
    },
  ];

  const confirmCopy = {
    deactivate: { title: 'Deactivate account?', confirmLabel: 'Deactivate', tone: 'danger',
      message: (u) => `${u.fullName} will be signed out everywhere and will not be able to sign in until the account is activated again.` },
    activate: { title: 'Activate account?', confirmLabel: 'Activate', tone: 'primary',
      message: (u) => `${u.fullName} will be able to sign in again.` },
    approve: { title: 'Approve sign-up request?', confirmLabel: 'Approve', tone: 'primary',
      message: (u) => `${u.fullName} will be able to sign in as ${u.roles.map(roleLabel).join(", ")}. To give a different role, choose Edit first.` },
    reject: { title: 'Reject sign-up request?', confirmLabel: 'Reject', tone: 'danger',
      message: (u) => `${u.fullName} will not be able to sign in. The request stays in the list as Inactive.` },
    reset: { title: 'Issue a temporary password?', confirmLabel: 'Reset password', tone: 'danger',
      message: (u) => `${u.fullName}'s current password will stop working and they will be signed out. You will see the new temporary password once.` },
  };
  const c = confirm ? confirmCopy[confirm.kind] : null;

  return (
    <>
      <PageHeader
        title="Users"
        description="Staff accounts, their roles and access status."
        actions={hasPermission('user:create') && <Button icon={UserPlus} onClick={() => setEditing('new')}>New user</Button>}
      />
      <Toolbar>
        <SearchInput value={params.q} onChange={(q) => setParams({ q })} placeholder="Search name, username or email" label="Search users" />
        <FilterSelect label="Role" value={params.role ?? ''} onChange={(role) => setParams({ role })} options={ROLE_OPTIONS} />
        <FilterSelect label="Status" value={params.status ?? ''} onChange={(status) => setParams({ status })} options={STATUS_OPTIONS} />
      </Toolbar>
      <DataTable
        caption="User accounts"
        columns={columns}
        page={list.data}
        loading={list.isFetching}
        error={list.isError ? toApiError(list.error).message : null}
        sort={params.sort}
        onSortChange={(sort) => setParams({ sort })}
        onPageChange={(page) => setParams({ page }, { resetPage: false })}
        emptyTitle="No users match these filters"
        emptyMessage="Try a different search or clear the filters."
      />

      {editing && (
        <UserFormModal
          user={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={(result, isNew) => {
            setEditing(null);
            refresh();
            if (isNew) setIssued(result);
            else toast.success('User updated.');
          }}
        />
      )}
      <TemporaryPasswordModal issued={issued} onClose={() => setIssued(null)} />
      {confirm && (
        <ConfirmDialog
          open
          title={c.title}
          message={c.message(confirm.user)}
          confirmLabel={c.confirmLabel}
          tone={c.tone}
          loading={action.isPending}
          error={confirmError}
          onClose={() => setConfirm(null)}
          onConfirm={() => action.mutate(confirm)}
        />
      )}
    </>
  );
}
