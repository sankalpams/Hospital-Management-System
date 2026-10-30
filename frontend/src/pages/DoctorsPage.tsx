import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { type ColumnDef } from '@tanstack/react-table';
import { Plus, Search } from 'lucide-react';
import { api, apiMessage } from '../api/client';
import type { Department, Doctor, PageResponse, User } from '../types/api';
import { DataTable } from '../components/DataTable';
import { PageHeader } from '../components/PageHeader';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { Dialog, DialogContent } from '../components/ui/dialog';
import { StatusBadge } from '../components/StatusBadge';
import { MoneyText } from '../components/MoneyText';
import { FormField } from '../components/FormField';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';
import { useAuth } from '../hooks/use-auth';

const doctorSchema = z.object({
  userId: z.string().uuid('Please select a doctor user account.').optional(),
  departmentId: z.coerce.number().min(1, 'Please select a department.'),
  specialization: z.string().min(2, 'Specialization is required.').max(100),
  qualification: z.string().min(2, 'Qualification is required.').max(200),
  slmcRegNumber: z.string().min(3, 'SLMC registration number is required.').max(50),
  consultationFee: z.coerce.number().min(0, 'Fee must be 0 or higher.'),
  active: z.boolean().optional(),
});

type DoctorFormValues = z.infer<typeof doctorSchema>;

function DoctorForm({
  doctor,
  departments,
  doctorUsers,
  onDone,
}: {
  doctor?: Doctor;
  departments: Department[];
  doctorUsers: User[];
  onDone: () => void;
}) {
  const client = useQueryClient();
  const [error, setError] = useState('');
  const form = useForm<DoctorFormValues>({
    resolver: zodResolver(doctorSchema),
    defaultValues: doctor
      ? {
          departmentId: doctor.departmentId,
          specialization: doctor.specialization,
          qualification: doctor.qualification,
          slmcRegNumber: doctor.slmcRegNumber,
          consultationFee: doctor.consultationFee,
          active: doctor.active,
        }
      : {
          userId: doctorUsers[0]?.id ?? '',
          departmentId: departments[0]?.id ?? 1,
          specialization: '',
          qualification: 'MBBS, MD',
          slmcRegNumber: 'SLMC-',
          consultationFee: 2500,
          active: true,
        },
  });

  const mutation = useMutation({
    mutationFn: (values: DoctorFormValues) =>
      doctor ? api.put(`/doctors/${doctor.id}`, values) : api.post('/doctors', values),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['doctors'] });
      onDone();
    },
    onError: (err) => setError(apiMessage(err)),
  });

  return (
    <form
      className="grid gap-4 sm:grid-cols-2"
      onSubmit={form.handleSubmit((values) => mutation.mutate(values))}
    >
      {!doctor && (
        <FormField label="Staff User Account" error={form.formState.errors.userId?.message}>
          <select
            className="h-10 w-full rounded-md border bg-surface px-3 text-sm"
            {...form.register('userId')}
          >
            <option value="">Select doctor user…</option>
            {doctorUsers.map((u) => (
              <option key={u.id} value={u.id}>
                {u.firstName} {u.lastName} ({u.username})
              </option>
            ))}
          </select>
        </FormField>
      )}

      <FormField label="Assigned Department" error={form.formState.errors.departmentId?.message}>
        <select
          className="h-10 w-full rounded-md border bg-surface px-3 text-sm"
          {...form.register('departmentId')}
        >
          {departments.map((d) => (
            <option key={d.id} value={d.id}>
              {d.name} ({d.code})
            </option>
          ))}
        </select>
      </FormField>

      <FormField label="Specialization" error={form.formState.errors.specialization?.message}>
        <Input placeholder="e.g. Consultant Cardiologist" {...form.register('specialization')} />
      </FormField>

      <FormField label="SLMC Registration Number" error={form.formState.errors.slmcRegNumber?.message}>
        <Input
          placeholder="e.g. SLMC-24101"
          disabled={!!doctor}
          {...form.register('slmcRegNumber')}
        />
      </FormField>

      <div className="sm:col-span-2">
        <FormField label="Qualifications" error={form.formState.errors.qualification?.message}>
          <Input placeholder="e.g. MBBS (Colombo), MD, FRCP" {...form.register('qualification')} />
        </FormField>
      </div>

      <FormField label="Consultation Fee (LKR)" error={form.formState.errors.consultationFee?.message}>
        <Input type="number" step="50" min="0" {...form.register('consultationFee')} />
      </FormField>

      {doctor && (
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
          {mutation.isPending ? 'Saving…' : doctor ? 'Save changes' : 'Register doctor'}
        </Button>
      </div>
    </form>
  );
}

export function DoctorsPage() {
  const { has } = useAuth();
  const [q, setQ] = useState('');
  const [deptFilter, setDeptFilter] = useState('');
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<Doctor | null | undefined>(undefined);

  const departmentsQuery = useQuery({
    queryKey: ['departments', 'active'],
    queryFn: async () => (await api.get<Department[]>('/departments/active')).data,
  });

  const usersQuery = useQuery({
    queryKey: ['users', 'doctors'],
    queryFn: async () =>
      (
        await api.get<PageResponse<User>>('/users', {
          params: { role: 'DOCTOR', active: true, size: 50 },
        })
      ).data.content,
  });

  const query = useQuery({
    queryKey: ['doctors', q, deptFilter, page],
    queryFn: async () =>
      (
        await api.get<PageResponse<Doctor>>('/doctors', {
          params: {
            q: q || undefined,
            departmentId: deptFilter || undefined,
            page,
            size: 25,
            sort: 'user.lastName,asc',
          },
        })
      ).data,
  });

  const columns = useMemo<ColumnDef<Doctor>[]>(
    () => [
      {
        header: 'Physician',
        cell: ({ row }) => (
          <div>
            <p className="font-medium">{row.original.doctorName}</p>
            <p className="text-xs text-muted-foreground">{row.original.qualification}</p>
          </div>
        ),
      },
      {
        header: 'Department',
        cell: ({ row }) => row.original.departmentName,
      },
      {
        header: 'Specialization',
        cell: ({ row }) => row.original.specialization,
      },
      {
        header: 'SLMC Reg',
        cell: ({ row }) => (
          <span className="font-mono text-xs font-medium">{row.original.slmcRegNumber}</span>
        ),
      },
      {
        header: 'Fee',
        cell: ({ row }) => <MoneyText value={row.original.consultationFee} />,
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
              cell: ({ row }: { row: { original: Doctor } }) => (
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
        title="Doctors"
        description="Medical specialists, SLMC registrations, and consultation rates."
        action={
          has('USER_MANAGE') ? (
            <Button onClick={() => setEditing(null)}>
              <Plus className="h-4 w-4" />
              New doctor
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
            placeholder="Search by physician name, SLMC or specialty"
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
        <p className="text-sm text-muted-foreground">Loading doctors…</p>
      ) : query.isError ? (
        <p className="text-sm text-danger">{apiMessage(query.error)}</p>
      ) : (
        <>
          <DataTable
            data={query.data?.content ?? []}
            columns={columns}
            emptyTitle="No physicians match"
            emptyDetail="Try another department filter or physician name."
          />
          <div className="mt-3 flex items-center justify-between text-sm text-muted-foreground">
            <span>{query.data?.totalElements ?? 0} doctors</span>
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
          <DialogContent title={editing ? 'Edit Doctor Profile' : 'Register New Doctor'}>
            <DoctorForm
              doctor={editing ?? undefined}
              departments={departmentsQuery.data ?? []}
              doctorUsers={usersQuery.data ?? []}
              onDone={() => setEditing(undefined)}
            />
          </DialogContent>
        </Dialog>
      )}
    </>
  );
}

