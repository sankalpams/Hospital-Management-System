import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { UserPlus } from 'lucide-react';
import { patientsApi } from '../../api/patients';
import { toApiError } from '../../api/client';
import { useAuth } from '../../auth/AuthProvider';
import { PageHeader } from '../../components/layout/AppShell';
import { DataTable } from '../../components/ui/DataTable';
import { FilterSelect, SearchInput, Toolbar } from '../../components/ui/Toolbar';
import { StatusBadge, humanise } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { formatDate } from '../../lib/format';
import { useListParams } from '../../lib/useListParams';

export default function PatientsPage() {
  const navigate = useNavigate();
  const { hasPermission } = useAuth();
  const [params, setParams] = useListParams({ sort: 'createdAt,desc' });
  const list = useQuery({ queryKey: ['patients', params], queryFn: () => patientsApi.search(params), placeholderData: keepPreviousData });
  const canRegister = hasPermission('patient:create');

  const columns = [
    { key: 'mrn', header: 'MRN', sortKey: 'mrn', render: (p) => <span className="font-mono text-[13px]">{p.mrn}</span> },
    { key: 'name', header: 'Name', sortKey: 'name', render: (p) => <span className="font-medium">{p.fullName}</span> },
    { key: 'age', header: 'Age / sex', render: (p) => `${p.age} · ${humanise(p.gender)}` },
    { key: 'dateOfBirth', header: 'Date of birth', sortKey: 'dateOfBirth', render: (p) => <span className="tabular">{formatDate(p.dateOfBirth)}</span> },
    { key: 'phone', header: 'Phone', render: (p) => <span className="tabular">{p.phone}</span> },
    { key: 'nic', header: 'NIC', render: (p) => <span className="font-mono text-[13px]">{p.nic ?? '—'}</span> },
    { key: 'status', header: 'Status', render: (p) => <StatusBadge status={p.status} /> },
  ];

  return (
    <>
      <PageHeader
        title="Patients"
        description="Search by MRN, NIC, phone number or name."
        actions={canRegister && <Button icon={UserPlus} onClick={() => navigate('/patients/new')}>Register patient</Button>}
      />
      <Toolbar>
        <SearchInput value={params.q} onChange={(q) => setParams({ q })} placeholder="MRN, NIC, phone or name" label="Search patients" />
        <FilterSelect label="Status" value={params.status ?? ''} onChange={(status) => setParams({ status })}
                      options={[{ value: '', label: 'Any' }, { value: 'ACTIVE', label: 'Active' }, { value: 'INACTIVE', label: 'Inactive' }, { value: 'DECEASED', label: 'Deceased' }]} />
      </Toolbar>
      <DataTable
        caption="Patients"
        columns={columns}
        page={list.data}
        loading={list.isFetching}
        error={list.isError ? toApiError(list.error).message : null}
        sort={params.sort}
        onSortChange={(sort) => setParams({ sort })}
        onPageChange={(page) => setParams({ page }, { resetPage: false })}
        onRowClick={(p) => navigate(`/patients/${p.id}`)}
        emptyTitle={params.q ? 'No patient matches this search' : 'No patients registered yet'}
        emptyMessage={params.q ? 'Check the spelling or search by MRN, NIC or phone instead.' : undefined}
        emptyAction={canRegister && <Button icon={UserPlus} variant="secondary" onClick={() => navigate('/patients/new')}>Register a new patient</Button>}
      />
    </>
  );
}
