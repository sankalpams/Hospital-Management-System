import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { Stethoscope } from 'lucide-react';
import { doctorsApi } from '../../api/clinic';
import { departmentsApi } from '../../api/org';
import { toApiError } from '../../api/client';
import { useAuth } from '../../auth/AuthProvider';
import { PageHeader } from '../../components/layout/AppShell';
import { DataTable } from '../../components/ui/DataTable';
import { FilterSelect, SearchInput, Toolbar } from '../../components/ui/Toolbar';
import { Badge, StatusBadge } from '../../components/ui/Badge';
import { LinkButton } from '../../components/ui/Button';
import { formatLKR } from '../../lib/format';
import { useListParams } from '../../lib/useListParams';

export default function DoctorsPage() {
  const navigate = useNavigate();
  const { hasPermission } = useAuth();
  const [params, setParams] = useListParams({ sort: 'name,asc' });
  const list = useQuery({ queryKey: ['doctors', params], queryFn: () => doctorsApi.list(params), placeholderData: keepPreviousData });
  const departments = useQuery({ queryKey: ['department-options'], queryFn: departmentsApi.options, staleTime: 300_000 });

  const columns = [
    { key: 'name', header: 'Doctor', sortKey: 'name', render: (d) => (
      <div><p className="font-medium">{d.displayName}</p><p className="font-mono text-xs text-foreground-subtle">{d.registrationNo}</p></div>
    ) },
    { key: 'specialization', header: 'Specialisation', sortKey: 'specialization' },
    { key: 'departmentName', header: 'Department' },
    { key: 'consultationFee', header: 'Fee', align: 'right', render: (d) => formatLKR(d.consultationFee) },
    { key: 'status', header: 'Status', render: (d) => (
      <div className="flex flex-wrap gap-1">
        <StatusBadge status={d.status} />
        {d.status === 'ACTIVE' && !d.acceptingPatients && <Badge tone="warning">Not taking bookings</Badge>}
      </div>
    ) },
  ];

  return (
    <>
      <PageHeader
        title="Doctors"
        description="Medical staff, specialisations and consultation schedules."
        actions={hasPermission('doctor:manage') && <LinkButton to="/doctors/new" icon={Stethoscope}>Add doctor</LinkButton>}
      />
      <Toolbar>
        <SearchInput value={params.q} onChange={(q) => setParams({ q })} placeholder="Name, specialisation or SLMC no." label="Search doctors" />
        <FilterSelect label="Department" value={params.departmentId ?? ''} onChange={(departmentId) => setParams({ departmentId })}
                      options={[{ value: '', label: 'All departments' }, ...(departments.data ?? []).map((d) => ({ value: String(d.id), label: d.name }))]} />
        <FilterSelect label="Status" value={params.status ?? ''} onChange={(status) => setParams({ status })}
                      options={[{ value: '', label: 'Any' }, { value: 'ACTIVE', label: 'Active' }, { value: 'ON_LEAVE', label: 'On leave' }, { value: 'RESIGNED', label: 'Resigned' }]} />
      </Toolbar>
      <DataTable
        caption="Doctors"
        columns={columns}
        page={list.data}
        loading={list.isFetching}
        error={list.isError ? toApiError(list.error).message : null}
        sort={params.sort}
        onSortChange={(sort) => setParams({ sort })}
        onPageChange={(page) => setParams({ page }, { resetPage: false })}
        onRowClick={(d) => navigate(`/doctors/${d.id}`)}
        emptyTitle="No doctors found"
        emptyMessage={hasPermission('doctor:manage') ? 'Add doctors and their weekly sessions so appointments can be booked.' : undefined}
      />
    </>
  );
}
