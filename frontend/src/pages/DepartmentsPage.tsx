import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { type ColumnDef } from '@tanstack/react-table';
import { Plus, Search } from 'lucide-react';
import { api, apiMessage } from '../api/client';
import type { Department, PageResponse } from '../types/api';
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

const departmentSchema = z.object({
  code: z.string().min(2, 'Code must be at least 2 characters.').max(30).regex(/^[A-Z0-9_-]+$/, 'Uppercase letters, digits, underscores, or hyphens only.'),
  name: z.string().min(2, 'Name is required.').max(100),
  description: z.string().max(255).optional(),
  phone: z.string().regex(/^$|^\+94\d{9}$/, 'Use +94XXXXXXXXX format.').optional(),
  location: z.string().max(100).optional(),
  active: z.boolean().optional(),
});

type DepartmentFormValues = z.infer<typeof departmentSchema>;

function DepartmentForm({ department, onDone }: { department?: Department; onDone: () => void }) {
  const client = useQueryClient();
  const [error, setError] = useState('');
  const form = useForm<DepartmentFormValues>({
    resolver: zodResolver(departmentSchema),
    defaultValues: department
      ? {
          code: department.code,
          name: department.name,
          description: department.description ?? '',
          phone: department.phone ?? '',
          location: department.location ?? '',
          active: department.active,
        }
      : {
          code: '',
          name: '',
          description: '',
          phone: '',
          location: '',
          active: true,
        },
  });

  const mutation = useMutation({
    mutationFn: (values: DepartmentFormValues) =>
      department
        ? api.put(`/departments/${department.id}`, values)
        : api.post('/departments', values),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['departments'] });
      onDone();
    },
    onError: (err) => setError(apiMessage(err)),
  });

  return (
    <form
      className="grid gap-4 sm:grid-cols-2"
      onSubmit={form.handleSubmit((values) => mutation.mutate(values))}
    >
      <FormField label="Department Code" error={form.formState.errors.code?.message}>
        <Input
          placeholder="e.g. CARD, GEN_MED"
          disabled={!!department}
          {...form.register('code')}
        />
      </FormField>

      <FormField label="Department Name" error={form.formState.errors.name?.message}>
        <Input placeholder="e.g. Cardiology" {...form.register('name')} />
      </FormField>

      <FormField label="Direct Telephone" error={form.formState.errors.phone?.message}>
        <Input placeholder="+94112345001" {...form.register('phone')} />
      </FormField>

      <FormField label="Hospital Location" error={form.formState.errors.location?.message}>
        <Input placeholder="e.g. Building B, Floor 3" {...form.register('location')} />
      </FormField>

      <div className="sm:col-span-2">
        <FormField label="Description" error={form.formState.errors.description?.message}>
          <Input placeholder="Clinical scope and services" {...form.register('description')} />
        </FormField>
      </div>

      {department && (
        <div className="sm:col-span-2">
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
        </div>
      )}

      {error && <p className="sm:col-span-2 text-sm text-danger">{error}</p>}

      <div className="sm:col-span-2 flex justify-end gap-2">
        <Button type="button" variant="secondary" onClick={onDone}>
          Cancel
        </Button>
        <Button type="submit" disabled={mutation.isPending}>
          {mutation.isPending ? 'Saving…' : department ? 'Save changes' : 'Create department'}
        </Button>
      </div>
    </form>
  );
}

export function DepartmentsPage() {
  const { has } = useAuth();
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<Department | null | undefined>(undefined);

  const query = useQuery({
    queryKey: ['departments', q, page],
    queryFn: async () =>
      (
        await api.get<PageResponse<Department>>('/departments', {
          params: { q: q || undefined, page, size: 25, sort: 'name,asc' },
        })
      ).data,
  });

  const columns = useMemo<ColumnDef<Department>[]>(
    () => [
      {
        header: 'Code',
        cell: ({ row }) => <span className="font-mono text-xs font-semibold">{row.original.code}</span>,
      },
      {
        header: 'Name',
        cell: ({ row }) => (
          <div>
            <p className="font-medium">{row.original.name}</p>
            {row.original.description && (
              <p className="text-xs text-muted-foreground">{row.original.description}</p>
            )}
          </div>
        ),
      },
      {
        header: 'Location',
        cell: ({ row }) => row.original.location ?? '—',
      },
      {
        header: 'Contact',
        cell: ({ row }) => (
          <span className="font-mono text-xs">{row.original.phone ?? '—'}</span>
        ),
      },
      {
        header: 'Status',
        cell: ({ row }) => <StatusBadge status={row.original.active} />,
      },
      ...(has('SETTINGS_MANAGE') || has('USER_MANAGE')
        ? [
            {
              id: 'actions',
              header: '',
              cell: ({ row }: { row: { original: Department } }) => (
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
        title="Departments"
        description="Clinical and administrative units across the hospital."
        action={
          has('SETTINGS_MANAGE') || has('USER_MANAGE') ? (
            <Button onClick={() => setEditing(null)}>
              <Plus className="h-4 w-4" />
              New department
            </Button>
          ) : undefined
        }
      />

      <div className="mb-4 flex max-w-md items-center gap-2">
        <Search className="h-4 w-4 text-muted-foreground" />
        <Input
          value={q}
          onChange={(e) => {
            setQ(e.target.value);
            setPage(0);
          }}
          placeholder="Search by department name or code"
        />
      </div>

      {query.isLoading ? (
        <p className="text-sm text-muted-foreground">Loading departments…</p>
      ) : query.isError ? (
        <p className="text-sm text-danger">{apiMessage(query.error)}</p>
      ) : (
        <>
          <DataTable
            data={query.data?.content ?? []}
            columns={columns}
            emptyTitle="No departments found"
            emptyDetail="Try adjusting your search criteria."
          />
          <div className="mt-3 flex items-center justify-between text-sm text-muted-foreground">
            <span>{query.data?.totalElements ?? 0} departments</span>
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
          <DialogContent title={editing ? 'Edit Department' : 'Create Department'}>
            <DepartmentForm department={editing ?? undefined} onDone={() => setEditing(undefined)} />
          </DialogContent>
        </Dialog>
      )}
    </>
  );
}

