import { useEffect, useState } from 'react';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ROLES } from '@hms/shared';
import { useAuth } from '../../auth/useAuth';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { DataTable, type Column } from '../../components/DataTable';
import { Pagination } from '../../components/Pagination';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { Label } from '../../components/ui/Label';
import { Select } from '../../components/ui/Select';
import { useToast } from '../../components/ui/Toast';
import { roleLabel } from '../../layout/TopBar';
import { api, ApiError } from '../../lib/api';
import { TemporaryPasswordDialog, UserFormDialog, type UserRow } from './UserFormDialog';

interface Paged<T> {
  items: T[];
  page: number;
  pageSize: number;
  total: number;
}

const PAGE_SIZE = 25;

function useDebounced<T>(value: T, ms = 300): T {
  const [v, setV] = useState(value);
  useEffect(() => {
    const id = setTimeout(() => setV(value), ms);
    return () => clearTimeout(id);
  }, [value, ms]);
  return v;
}

export function UsersPage() {
  const { user: me } = useAuth();
  const toast = useToast();
  const queryClient = useQueryClient();
  const [search, setSearch] = useState('');
  const [role, setRole] = useState('');
  const [page, setPage] = useState(1);
  const [formTarget, setFormTarget] = useState<'create' | UserRow | null>(null);
  const [confirm, setConfirm] = useState<{ kind: 'deactivate' | 'reset'; user: UserRow } | null>(null);
  const [tempPassword, setTempPassword] = useState<{ password: string; username: string } | null>(null);
  const debouncedSearch = useDebounced(search.trim());

  const query = useQuery({
    queryKey: ['users', { search: debouncedSearch, role, page }],
    placeholderData: keepPreviousData,
    queryFn: () => {
      const p = new URLSearchParams({ page: String(page), pageSize: String(PAGE_SIZE) });
      if (debouncedSearch) p.set('search', debouncedSearch);
      if (role) p.set('role', role);
      return api<Paged<UserRow>>(`/users?${p}`);
    },
  });

  const onError = (err: unknown) => {
    toast(err instanceof ApiError && err.status < 500 ? err.message : 'Something went wrong. Please try again.', 'danger');
  };

  const setActive = useMutation({
    mutationFn: ({ id, isActive }: { id: string; isActive: boolean }) =>
      api(`/users/${id}`, { method: 'PATCH', json: { isActive } }),
    onSuccess: (_d, v) => {
      toast(v.isActive ? 'User activated' : 'User deactivated', 'success');
      void queryClient.invalidateQueries({ queryKey: ['users'] });
    },
    onError,
    onSettled: () => setConfirm(null),
  });

  const reset = useMutation({
    mutationFn: (u: UserRow) => api<{ temporaryPassword: string }>(`/users/${u.id}/reset-password`, { method: 'POST' }),
    onSuccess: (res, u) => {
      setTempPassword({ password: res.temporaryPassword, username: u.username });
      void queryClient.invalidateQueries({ queryKey: ['users'] });
    },
    onError,
    onSettled: () => setConfirm(null),
  });

  const columns: Column<UserRow>[] = [
    { key: 'username', header: 'Username', cell: (u) => <span className="font-medium">{u.username}</span> },
    { key: 'fullName', header: 'Full name', cell: (u) => u.fullName },
    { key: 'role', header: 'Role', cell: (u) => roleLabel(u.role) },
    {
      key: 'status',
      header: 'Status',
      cell: (u) => (
        <span className="flex flex-wrap gap-1">
          <Badge tone={u.isActive ? 'success' : 'neutral'}>{u.isActive ? 'Active' : 'Inactive'}</Badge>
          {u.mustChangePassword && <Badge tone="warning">Password change pending</Badge>}
        </span>
      ),
    },
    {
      key: 'lastLogin',
      header: 'Last sign-in',
      cell: (u) => (u.lastLoginAt ? new Date(u.lastLoginAt).toLocaleString() : 'Never'),
    },
    {
      key: 'actions',
      header: 'Actions',
      cell: (u) => (
        <span className="flex flex-wrap gap-2">
          <Button variant="secondary" aria-label={`Edit ${u.fullName}`} onClick={() => setFormTarget(u)}>
            Edit
          </Button>
          <Button
            variant="secondary"
            aria-label={`Reset password for ${u.fullName}`}
            disabled={u.id === me?.id}
            title={u.id === me?.id ? 'Use Change password in the user menu for your own account' : undefined}
            onClick={() => setConfirm({ kind: 'reset', user: u })}
          >
            Reset password
          </Button>
          {u.isActive ? (
            <Button
              variant="secondary"
              aria-label={`Deactivate ${u.fullName}`}
              disabled={u.id === me?.id}
              onClick={() => setConfirm({ kind: 'deactivate', user: u })}
            >
              Deactivate
            </Button>
          ) : (
            <Button
              variant="secondary"
              aria-label={`Activate ${u.fullName}`}
              onClick={() => setActive.mutate({ id: u.id, isActive: true })}
            >
              Activate
            </Button>
          )}
        </span>
      ),
    },
  ];

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold text-text">Users</h1>
        <Button onClick={() => setFormTarget('create')}>Add user</Button>
      </div>

      <div className="grid gap-3 sm:grid-cols-[minmax(0,1fr)_14rem]">
        <div>
          <Label htmlFor="user-search">Search users</Label>
          <Input
            id="user-search"
            type="search"
            placeholder="Name, username or email"
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(1);
            }}
          />
        </div>
        <div>
          <Label htmlFor="user-role-filter">Role</Label>
          <Select
            id="user-role-filter"
            value={role}
            onChange={(e) => {
              setRole(e.target.value);
              setPage(1);
            }}
          >
            <option value="">All roles</option>
            {ROLES.map((r) => (
              <option key={r} value={r}>
                {roleLabel(r)}
              </option>
            ))}
          </Select>
        </div>
      </div>

      {query.isError ? (
        <div role="alert" className="rounded-control border border-danger bg-surface px-3 py-2 text-sm text-danger">
          Could not load users.{' '}
          <button type="button" className="font-medium underline" onClick={() => void query.refetch()}>
            Try again
          </button>
        </div>
      ) : (
        <>
          <DataTable
            caption="Users"
            columns={columns}
            rows={query.data?.items ?? []}
            getRowKey={(u) => u.id}
            loading={query.isLoading}
            emptyMessage="No users match your filters."
          />
          <Pagination page={page} pageSize={PAGE_SIZE} total={query.data?.total ?? 0} onChange={setPage} />
        </>
      )}

      <UserFormDialog
        target={formTarget}
        onClose={() => setFormTarget(null)}
        onCreated={(u, password) => setTempPassword({ password, username: u.username })}
      />

      <ConfirmDialog
        open={confirm?.kind === 'deactivate'}
        title="Deactivate user"
        confirmLabel="Deactivate"
        danger
        loading={setActive.isPending}
        onCancel={() => setConfirm(null)}
        onConfirm={() => confirm && setActive.mutate({ id: confirm.user.id, isActive: false })}
      >
        <p>
          Deactivate <strong>{confirm?.user.fullName}</strong>? They are signed out immediately and cannot sign in until
          reactivated.
        </p>
      </ConfirmDialog>

      <ConfirmDialog
        open={confirm?.kind === 'reset'}
        title="Reset password"
        confirmLabel="Reset password"
        danger
        loading={reset.isPending}
        onCancel={() => setConfirm(null)}
        onConfirm={() => confirm && reset.mutate(confirm.user)}
      >
        <p>
          Reset the password for <strong>{confirm?.user.fullName}</strong>? Their sessions end and a new temporary
          password is generated.
        </p>
      </ConfirmDialog>

      <TemporaryPasswordDialog
        password={tempPassword?.password ?? null}
        username={tempPassword?.username ?? ''}
        onClose={() => {
          setTempPassword(null);
          reset.reset(); // drop the one-time password from mutation state
        }}
      />
    </div>
  );
}
