import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { type ColumnDef } from '@tanstack/react-table';
import { Plus, Search } from 'lucide-react';
import { api, apiMessage } from '../api/client';
import type { Department, Employee, PageResponse, User } from '../types/api';
import { DataTable } from '../components/DataTable';
import { PageHeader } from '../components/PageHeader';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { Dialog, DialogContent } from '../components/ui/dialog';
import { StatusBadge } from '../components/StatusBadge';
import { FormField } from '../components/FormField';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';
import { useAuth } from '../hooks/use-auth';

const employeeSchema = z.object({
  userId: z.string().uuid().optional().or(z.literal('')),
  departmentId: z.coerce.number().optional(),
  employeeNumber: z.string().min(2, 'Employee number is required.').max(50),
  designation: z.string().min(2, 'Designation is required.').max(100),
  contactPhone: z.string().regex(/^$|^\+94\d{9}$/, 'Use +94XXXXXXXXX format.').optional(),
  active: z.boolean().optional(),
});

type EmployeeFormValues = z.infer<typeof employeeSchema>;

function EmployeeForm({
  employee,
  departments,
  users,
  onDone,
}: {
  employee?: Employee;
  departments: Department[];
  users: User[];
  onDone: () => void;
}) {
  const client = useQueryClient();
  const [error, setError] = useState('');
  const form = useForm<EmployeeFormValues>({
    resolver: zodResolver(employeeSchema),
    defaultValues: employee
      ? {
          userId: employee.userId ?? '',
          departmentId: employee.departmentId,
          employeeNumber: employee.employeeNumber,
          designation: employee.designation,
          contactPhone: employee.contactPhone ?? '',
          active: employee.active,
        }
      : {
          userId: '',
          employeeNumber: 'EMP-',
          designation: '',
          contactPhone: '',
          active: true,
        },
  });

  const mutation = useMutation({
    mutationFn: (values: EmployeeFormValues) => {
      const payload = {
        ...values,
        userId: values.userId ? values.userId : null,
        departmentId: values.departmentId ? Number(values.departmentId) : null,
      };
      return employee ? api.put(`/employees/${employee.id}`, payload) : api.post('/employees', payload);
    },
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['employees'] });
      onDone();
    },
    onError: (err) => setError(apiMessage(err)),
  });

  return (
    <form
      className="grid gap-4 sm:grid-cols-2"
      onSubmit={form.handleSubmit((values) => mutation.mutate(values))}
    >
      <FormField label="Employee Number" error={form.formState.errors.employeeNumber?.message}>
        <Input
          placeholder="e.g. EMP-NUR-001"
          disabled={!!employee}
          {...form.register('employeeNumber')}
        />
      </FormField>

      <FormField label="Designation / Role Title" error={form.formState.errors.designation?.message}>
        <Input placeholder="e.g. Staff Nurse - ICU" {...form.register('designation')} />
      </FormField>

      <FormField label="Assigned Department" error={form.formState.errors.departmentId?.message}>
        <select
          className="h-10 w-full rounded-md border bg-surface px-3 text-sm"
          {...form.register('departmentId')}
        >
          <option value="">None / Facility-wide</option>
          {departments.map((d) => (
            <option key={d.id} value={d.id}>
              {d.name}
            </option>
          ))}
        </select>
      </FormField>

      <FormField label="Linked User Account" error={form.formState.errors.userId?.message}>
        <select
          className="h-10 w-full rounded-md border bg-surface px-3 text-sm"
          {...form.register('userId')}
        >
          <option value="">None / Not linked to login account</option>
          {users.map((u) => (
            <option key={u.id} value={u.id}>
              {u.firstName} {u.lastName} ({u.username} - {u.role})
            </option>
          ))}
        </select>
      </FormField>

      <FormField label="Contact Phone" error={form.formState.errors.contactPhone?.message}>
        <Input placeholder="+94771234567" {...form.register('contactPhone')} />
      </FormField>

      {employee && (
        <FormField label="Status">
          <select
            className="h-10 w-full rounded-md border bg-surface px-3 text-sm"
            value={String(form.watch('active'))}
            onChange={(e) => form.setValue('active', e.target.value === 'true')}
          >
            <option value="true">Active</option>
            <option value="false">Inactive</option>
          </select>
        </FormField>
      )}

      {error && <p className="sm:col-span-2 text-sm text-danger">{error}</p>}

      <div className="sm:col-span-2 flex justify-end gap-2">
        <Button type="button" variant="secondary" onClick={onDone}>
          Cancel
        </Button>
        <Button type="submit" disabled={mutation.isPending}>
          {mutation.isPending ? 'Saving…' : employee ? 'Save changes' : 'Register employee'}
        </Button>
      </div>
    </form>
  );
}

export function EmployeesPage() {
  const { has } = useAuth();
  const [q, setQ] = useState('');
  const [deptFilter, setDeptFilter] = useState('');
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<Employee | null | undefined>(undefined);

  const departmentsQuery = useQuery({
    queryKey: ['departments', 'active'],
    queryFn: async () => (await api.get<Department[]>('/departments/active')).data,
  });

  const usersQuery = useQuery({
    queryKey: ['users', 'all'],
    queryFn: async () =>
      (
        await api.get<PageResponse<User>>('/users', {
          params: { size: 100 },
        })
      ).data.content,
  });

  const query = useQuery({
    queryKey: ['employees', q, deptFilter, page],
    queryFn: async () =>
      (
        await api.get<PageResponse<Employee>>('/employees', {
          params: {
            q: q || undefined,
            departmentId: deptFilter || undefined,
            page,
            size: 25,
            sort: 'employeeNumber,asc',
          },
        })
      ).data,
  });

  const columns = useMemo<ColumnDef<Employee>[]>(
    () => [
      {
        header: 'Staff Member',
        cell: ({ row }) => (
          <div>
            <p className="font-medium">{row.original.employeeName ?? '—'}</p>
            <p className="font-mono text-xs text-muted-foreground">{row.original.employeeNumber}</p>
          </div>
        ),
      },
      {
        header: 'Designation',
        cell: ({ row }) => row.original.designation,
      },
      {
        header: 'Department',
        cell: ({ row }) => row.original.departmentName ?? 'Facility-wide',
      },
      {
        header: 'Contact',
        cell: ({ row }) => (
          <span className="font-mono text-xs">{row.original.contactPhone ?? '—'}</span>
        ),
      },
      {
        header: 'Status',
        cell: ({ row }) => <StatusBadge status={row.original.active} />,
      },
      ...(has('USER_MANAGE')
        ? [
            {
              id: 'actions',
              header: '',
              cell: ({ row }: { row: { original: Employee } }) => (
                <Button size="sm" variant="ghost" onClick={() => setEditing(row.original)}>
                  Edit
                </Button>
              ),
            },
          ]
        : []),
    ],
    [has]
  );

  return (
    <>
      <PageHeader
        title="Staff & Employees"
        description="Nursing, administrative, laboratory, and pharmacy personnel registry."
        action={
          has('USER_MANAGE') ? (
            <Button onClick={() => setEditing(null)}>
              <Plus className="h-4 w-4" />
              New employee
            </Button>
          ) : undefined
        }
      />

      <div className="mb-4 flex flex-wrap items-center gap-3">
        <div className="flex max-w-md items-center gap-2">
          <Search className="h-4 w-4 text-muted-foreground" />
          <Input
            value={q}
            onChange={(e) => {
              setQ(e.target.value);
              setPage(0);
            }}
            placeholder="Search by staff name, employee # or title"
          />
        </div>

        <select
          className="h-10 rounded-md border bg-surface px-3 text-sm"
          value={deptFilter}
          onChange={(e) => {
            setDeptFilter(e.target.value);
            setPage(0);
          }}
        >
          <option value="">All Departments</option>
          {departmentsQuery.data?.map((d) => (
            <option key={d.id} value={d.id}>
              {d.name}
            </option>
          ))}
        </select>
      </div>

      {query.isLoading ? (
        <p className="text-sm text-muted-foreground">Loading employee directory…</p>
      ) : query.isError ? (
        <p className="text-sm text-danger">{apiMessage(query.error)}</p>
      ) : (
        <>
          <DataTable
            data={query.data?.content ?? []}
            columns={columns}
            emptyTitle="No staff members match"
            emptyDetail="Try adjusting search query or department filter."
          />
          <div className="mt-3 flex items-center justify-between text-sm text-muted-foreground">
            <span>{query.data?.totalElements ?? 0} staff members</span>
            <div className="flex gap-2">
              <Button
                size="sm"
                variant="secondary"
                disabled={query.data?.first}
                onClick={() => setPage((p) => p - 1)}
              >
                Previous
              </Button>
              <Button
                size="sm"
                variant="secondary"
                disabled={query.data?.last}
                onClick={() => setPage((p) => p + 1)}
              >
                Next
              </Button>
            </div>
          </div>
        </>
      )}

      {editing !== undefined && (
        <Dialog open onOpenChange={(open) => !open && setEditing(undefined)}>
          <DialogContent title={editing ? 'Edit Employee Details' : 'Register New Employee'}>
            <EmployeeForm
              employee={editing ?? undefined}
              departments={departmentsQuery.data ?? []}
              users={usersQuery.data ?? []}
              onDone={() => setEditing(undefined)}
            />
          </DialogContent>
        </Dialog>
      )}
    </>
  );
}

