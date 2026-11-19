import { useQuery } from '@tanstack/react-query';
import { usersApi } from '../../api/admin';
import { toApiError } from '../../api/client';
import { PageHeader } from '../../components/layout/AppShell';
import { DataTable } from '../../components/ui/DataTable';
import { Badge } from '../../components/ui/Badge';
import { roleLabel } from '../../lib/roles';

/** Super Admin only: demo staff logins still on the shared password set in Railway (HMS_DEMO_STAFF_PASSWORD). */
export default function DemoStaffPage() {
  const { data, isLoading, error } = useQuery({ queryKey: ['users', 'demo-staff'], queryFn: usersApi.demoStaff });
  const page = data && { content: data, page: 0, size: data.length || 1, totalElements: data.length, totalPages: 1 };

  const columns = [
    {
      key: 'fullName', header: 'Name',
      render: (u) => <span className="font-medium">{u.fullName}</span>,
    },
    { key: 'username', header: 'Username', render: (u) => <span className="font-mono text-sm">{u.username}</span> },
    { key: 'roles', header: 'Role', render: (u) => <div className="flex flex-wrap gap-1">{u.roles.map((r) => <Badge key={r} tone="primary">{roleLabel(r)}</Badge>)}</div> },
  ];

  return (
    <>
      <PageHeader title="Staff logins" description="Demo staff accounts and the usernames to sign in with." />
      <DataTable
        columns={columns}
        page={page}
        loading={isLoading}
        error={error && toApiError(error)}
        emptyTitle="No demo staff logins"
        emptyMessage="Every demo staff account has changed its password, or none were created."
        onPageChange={() => {}}
        caption="Demo staff logins"
      />
    </>
  );
}
