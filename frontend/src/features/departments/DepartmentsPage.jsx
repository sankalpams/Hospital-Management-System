import { useState } from 'react';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Building2, Pencil, Plus, Power } from 'lucide-react';
import { DEPARTMENT_TYPES, departmentsApi } from '../../api/org';
import { toApiError } from '../../api/client';
import { useAuth } from '../../auth/AuthProvider';
import { PageHeader } from '../../components/layout/AppShell';
import { DataTable } from '../../components/ui/DataTable';
import { FilterSelect, SearchInput, Toolbar } from '../../components/ui/Toolbar';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { ConfirmDialog, Modal } from '../../components/ui/Modal';
import { SelectField, TextAreaField, TextField } from '../../components/ui/Field';
import { Alert } from '../../components/ui/Alert';
import { useToast } from '../../components/ui/Toast';
import { focusFirstInvalid, requireFields, splitApiError } from '../../lib/forms';
import { useListParams } from '../../lib/useListParams';

const typeLabel = (t) => DEPARTMENT_TYPES.find((x) => x.value === t)?.label ?? t;

export default function DepartmentsPage() {
  const { hasPermission } = useAuth();
  const canManage = hasPermission('department:manage');
  const toast = useToast();
  const queryClient = useQueryClient();
  const [params, setParams] = useListParams({ sort: 'name,asc' });
  const [editing, setEditing] = useState(null);
  const [toggling, setToggling] = useState(null);
  const [toggleError, setToggleError] = useState(null);

  const list = useQuery({ queryKey: ['departments', params], queryFn: () => departmentsApi.list(params), placeholderData: keepPreviousData });
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['departments'] });

  const toggle = useMutation({
    mutationFn: (d) => (d.active ? departmentsApi.deactivate(d.id) : departmentsApi.activate(d.id)),
    onSuccess: (d) => { setToggling(null); refresh(); toast.success(`${d.name} ${d.active ? 'activated' : 'deactivated'}.`); },
    onError: (err) => setToggleError(toApiError(err).message),
  });

  const columns = [
    { key: 'name', header: 'Department', sortKey: 'name', render: (d) => (
      <div><p className="font-medium">{d.name}</p><p className="font-mono text-xs text-foreground-subtle">{d.code}</p></div>
    ) },
    { key: 'type', header: 'Type', sortKey: 'type', render: (d) => typeLabel(d.type) },
    { key: 'location', header: 'Location', render: (d) => <span className="text-foreground-muted">{d.location ?? '—'}{d.phoneExtension ? ` · ext ${d.phoneExtension}` : ''}</span> },
    { key: 'activeStaff', header: 'Active staff', align: 'right', render: (d) => d.activeStaff },
    { key: 'active', header: 'Status', render: (d) => (d.active ? <Badge tone="success">Active</Badge> : <Badge tone="neutral">Inactive</Badge>) },
    ...(canManage ? [{
      key: 'actions', header: <span className="sr-only">Actions</span>, align: 'right', render: (d) => (
        <div className="flex justify-end gap-1">
          <Button variant="ghost" size="sm" icon={Pencil} onClick={() => setEditing(d)} aria-label={`Edit ${d.name}`}>Edit</Button>
          <Button variant="ghost" size="sm" icon={Power} onClick={() => { setToggleError(null); setToggling(d); }} aria-label={`${d.active ? 'Deactivate' : 'Activate'} ${d.name}`}>
            {d.active ? 'Deactivate' : 'Activate'}
          </Button>
        </div>
      ),
    }] : []),
  ];

  return (
    <>
      <PageHeader
        title="Departments"
        description="Clinical, diagnostic and support units of the hospital."
        actions={canManage && <Button icon={Plus} onClick={() => setEditing('new')}>New department</Button>}
      />
      <Toolbar>
        <SearchInput value={params.q} onChange={(q) => setParams({ q })} placeholder="Search name or code" label="Search departments" />
        <FilterSelect label="Type" value={params.type ?? ''} onChange={(type) => setParams({ type })} options={[{ value: '', label: 'All types' }, ...DEPARTMENT_TYPES]} />
        <FilterSelect label="Status" value={params.active ?? ''} onChange={(active) => setParams({ active })}
                      options={[{ value: '', label: 'Any' }, { value: 'true', label: 'Active' }, { value: 'false', label: 'Inactive' }]} />
      </Toolbar>
      <DataTable
        caption="Departments"
        columns={columns}
        page={list.data}
        loading={list.isFetching}
        error={list.isError ? toApiError(list.error).message : null}
        sort={params.sort}
        onSortChange={(sort) => setParams({ sort })}
        onPageChange={(page) => setParams({ page }, { resetPage: false })}
        emptyTitle="No departments yet"
        emptyMessage={canManage ? 'Create the hospital’s departments to start assigning doctors and staff.' : undefined}
        emptyAction={canManage && !params.q && <Button icon={Building2} onClick={() => setEditing('new')}>Create department</Button>}
      />
      {editing && (
        <DepartmentFormModal
          department={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={(d, isNew) => { setEditing(null); refresh(); toast.success(`${d.name} ${isNew ? 'created' : 'updated'}.`); }}
        />
      )}
      {toggling && (
        <ConfirmDialog
          open
          title={toggling.active ? 'Deactivate department?' : 'Activate department?'}
          message={toggling.active
            ? `${toggling.name} will no longer be offered for new appointments or staff assignments. Existing records are kept.`
            : `${toggling.name} will be available for appointments and staff assignments again.`}
          confirmLabel={toggling.active ? 'Deactivate' : 'Activate'}
          tone={toggling.active ? 'danger' : 'primary'}
          loading={toggle.isPending}
          error={toggleError}
          onClose={() => setToggling(null)}
          onConfirm={() => toggle.mutate(toggling)}
        />
      )}
    </>
  );
}

function DepartmentFormModal({ department, onClose, onSaved }) {
  const isNew = !department;
  const [form, setForm] = useState({
    code: department?.code ?? '', name: department?.name ?? '', type: department?.type ?? 'CLINICAL',
    description: department?.description ?? '', location: department?.location ?? '', phoneExtension: department?.phoneExtension ?? '',
  });
  const [errors, setErrors] = useState({});
  const [general, setGeneral] = useState(null);
  const set = (k) => (e) => { setForm((f) => ({ ...f, [k]: e.target.value })); setErrors((x) => ({ ...x, [k]: undefined })); };

  const save = useMutation({
    mutationFn: () => (isNew ? departmentsApi.create(form) : departmentsApi.update(department.id, form)),
    onSuccess: (d) => onSaved(d, isNew),
    onError: (err) => { const { fields, general: g } = splitApiError(err); setErrors(fields); setGeneral(g); },
  });

  const submit = (e) => {
    e.preventDefault();
    const errs = requireFields(form, { code: 'Code', name: 'Name', type: 'Type' });
    setErrors(errs);
    setGeneral(null);
    if (Object.keys(errs).length) focusFirstInvalid();
    else save.mutate();
  };

  return (
    <Modal
      open onClose={onClose} title={isNew ? 'New department' : `Edit ${department.name}`}
      footer={<><Button variant="ghost" onClick={onClose}>Cancel</Button><Button type="submit" form="dept-form" loading={save.isPending}>{isNew ? 'Create' : 'Save changes'}</Button></>}
    >
      <form id="dept-form" onSubmit={submit} noValidate className="grid gap-4 sm:grid-cols-2">
        {general && <Alert tone="danger" className="sm:col-span-2" requestId={general.requestId}>{general.message}</Alert>}
        <TextField label="Code" required value={form.code} onChange={set('code')} error={errors.code} disabled={!isNew}
                   hint={isNew ? 'Short identifier, e.g. CARD. Cannot be changed later.' : 'Codes cannot be changed.'} data-autofocus />
        <SelectField label="Type" required value={form.type} onChange={set('type')} options={DEPARTMENT_TYPES} error={errors.type} />
        <TextField label="Name" required value={form.name} onChange={set('name')} error={errors.name} className="sm:col-span-2" />
        <TextField label="Location" value={form.location} onChange={set('location')} error={errors.location} placeholder="e.g. Block B, Level 2" />
        <TextField label="Phone extension" inputMode="numeric" value={form.phoneExtension} onChange={set('phoneExtension')} error={errors.phoneExtension} />
        <TextAreaField label="Description" value={form.description} onChange={set('description')} error={errors.description} className="sm:col-span-2" />
      </form>
    </Modal>
  );
}
