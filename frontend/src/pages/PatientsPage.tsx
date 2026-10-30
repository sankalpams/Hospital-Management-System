import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { type ColumnDef } from '@tanstack/react-table';
import { AlertCircle, AlertTriangle, ArrowRight, Plus, Search } from 'lucide-react';
import { Link, useNavigate } from 'react-router-dom';
import { api, apiMessage } from '../api/client';
import type { DuplicateMatch, PageResponse, Patient, PatientSummary } from '../types/api';
import { DataTable } from '../components/DataTable';
import { PageHeader } from '../components/PageHeader';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { Dialog, DialogContent } from '../components/ui/dialog';
import { StatusBadge } from '../components/StatusBadge';
import { FormField } from '../components/FormField';
import { useForm, useFieldArray } from 'react-hook-form';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';
import { useAuth } from '../hooks/use-auth';

const SRI_LANKAN_DISTRICTS = [
  'Colombo', 'Gampaha', 'Kalutara', 'Kandy', 'Matale', 'Nuwara Eliya',
  'Galle', 'Matara', 'Hambantota', 'Jaffna', 'Kilinochchi', 'Mannar',
  'Vavuniya', 'Mullaitivu', 'Batticaloa', 'Ampara', 'Trincomalee',
  'Kurunegala', 'Puttalam', 'Anuradhapura', 'Polonnaruwa', 'Badulla',
  'Monaragala', 'Ratnapura', 'Kegalle'
] as const;

const BLOOD_GROUPS = ['A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-'] as const;

const patientFormSchema = z.object({
  nic: z.string().regex(/^$|^[0-9]{9}[vVxX]$|^[0-9]{12}$|^[a-zA-Z0-9]{6,12}$/, 'Valid Sri Lankan NIC (e.g. 123456789V or 199012345678) or passport number required.').optional(),
  firstName: z.string().min(1, 'First name is required.').max(100),
  lastName: z.string().min(1, 'Last name is required.').max(100),
  dateOfBirth: z.string().min(1, 'Date of birth is required.'),
  sex: z.enum(['MALE', 'FEMALE', 'OTHER']),
  phone: z.string().regex(/^$|^\+94[0-9]{9}$/, 'Use +94XXXXXXXXX format.').optional(),
  email: z.string().email('Invalid email address.').optional().or(z.literal('')),
  addressLine1: z.string().max(255).optional(),
  city: z.string().max(100).optional(),
  district: z.string().min(1, 'District is required.'),
  guardianName: z.string().max(100).optional(),
  guardianRelationship: z.string().max(50).optional(),
  guardianPhone: z.string().regex(/^$|^\+94[0-9]{9}$/, 'Use +94XXXXXXXXX format.').optional(),
  bloodGroup: z.string().optional(),
  chronicConditions: z.string().optional(),
  insuranceProvider: z.string().max(100).optional(),
  insurancePolicyNumber: z.string().max(100).optional(),
  allergies: z.array(z.object({
    allergen: z.string().min(1, 'Allergen required'),
    reaction: z.string().min(1, 'Reaction required'),
    severity: z.enum(['MILD', 'MODERATE', 'SEVERE']),
    notes: z.string().optional(),
  })).optional(),
});

type PatientFormValues = z.infer<typeof patientFormSchema>;

function PatientRegistrationForm({
  patient,
  onDone,
}: {
  patient?: Patient;
  onDone: (newId?: string) => void;
}) {
  const client = useQueryClient();
  const [error, setError] = useState('');
  const [duplicates, setDuplicates] = useState<DuplicateMatch[]>([]);
  const [checkingDupes, setCheckingDupes] = useState(false);

  const form = useForm<PatientFormValues>({
    resolver: zodResolver(patientFormSchema),
    defaultValues: patient
      ? {
          nic: patient.nic ?? '',
          firstName: patient.firstName,
          lastName: patient.lastName,
          dateOfBirth: patient.dateOfBirth,
          sex: patient.sex as 'MALE' | 'FEMALE' | 'OTHER',
          phone: patient.phone ?? '',
          email: patient.email ?? '',
          addressLine1: patient.addressLine1 ?? '',
          city: patient.city ?? '',
          district: patient.district,
          guardianName: patient.guardianName ?? '',
          guardianRelationship: patient.guardianRelationship ?? '',
          guardianPhone: patient.guardianPhone ?? '',
          bloodGroup: patient.bloodGroup ?? '',
          chronicConditions: patient.chronicConditions ?? '',
          insuranceProvider: patient.insuranceProvider ?? '',
          insurancePolicyNumber: patient.insurancePolicyNumber ?? '',
          allergies: [],
        }
      : {
          nic: '',
          firstName: '',
          lastName: '',
          dateOfBirth: '',
          sex: 'MALE',
          phone: '',
          email: '',
          addressLine1: '',
          city: '',
          district: 'Colombo',
          guardianName: '',
          guardianRelationship: '',
          guardianPhone: '',
          bloodGroup: 'O+',
          chronicConditions: '',
          insuranceProvider: '',
          insurancePolicyNumber: '',
          allergies: [],
        },
  });

  const { fields, append, remove } = useFieldArray({
    control: form.control,
    name: 'allergies',
  });

  const nicValue = form.watch('nic');
  const firstNameValue = form.watch('firstName');
  const lastNameValue = form.watch('lastName');
  const dobValue = form.watch('dateOfBirth');
  const phoneValue = form.watch('phone');

  // Real-time duplicate check with debounce
  useEffect(() => {
    if (patient) return; // Skip duplicate checks when editing existing patient
    if (!nicValue && (!dobValue || (!phoneValue && !lastNameValue))) {
      setDuplicates([]);
      return;
    }

    const timer = setTimeout(async () => {
      try {
        setCheckingDupes(true);
        const { data } = await api.post<DuplicateMatch[]>('/patients/check-duplicates', {
          nic: nicValue || undefined,
          firstName: firstNameValue || undefined,
          lastName: lastNameValue || undefined,
          dateOfBirth: dobValue || undefined,
          phone: phoneValue || undefined,
        });
        setDuplicates(data);
      } catch {
        // silent check failure
      } finally {
        setCheckingDupes(false);
      }
    }, 500);

    return () => clearTimeout(timer);
  }, [nicValue, firstNameValue, lastNameValue, dobValue, phoneValue, patient]);

  const mutation = useMutation({
    mutationFn: async (values: PatientFormValues) => {
      if (patient) {
        return (await api.put<Patient>(`/patients/${patient.id}`, values)).data;
      } else {
        return (await api.post<Patient>('/patients', values)).data;
      }
    },
    onSuccess: (saved) => {
      void client.invalidateQueries({ queryKey: ['patients'] });
      onDone(saved.id);
    },
    onError: (err) => setError(apiMessage(err)),
  });

  return (
    <form
      className="space-y-6 max-h-[80vh] overflow-y-auto px-1"
      onSubmit={form.handleSubmit((values) => mutation.mutate(values))}
    >
      {duplicates.length > 0 && !patient && (
        <div className="rounded-md border border-amber-300 bg-amber-50 p-4 dark:border-amber-800 dark:bg-amber-950/40">
          <div className="flex items-start gap-2.5">
            <AlertTriangle className="h-5 w-5 text-amber-600 dark:text-amber-400 shrink-0 mt-0.5" />
            <div className="text-sm">
              <p className="font-semibold text-amber-900 dark:text-amber-200">
                Possible duplicate patient record detected ({duplicates.length})
              </p>
              <p className="text-xs text-amber-800 dark:text-amber-300 mt-0.5">
                Review existing registered patients before creating a new MRN.
              </p>
              <div className="mt-2.5 space-y-1.5 divide-y divide-amber-200 dark:divide-amber-800/60">
                {duplicates.map((d) => (
                  <div key={d.id} className="pt-1.5 flex items-center justify-between text-xs">
                    <div>
                      <span className="font-mono font-medium">{d.mrn}</span> —{' '}
                      <span className="font-medium">{d.fullName}</span> (DOB: {d.dateOfBirth}, Phone: {d.phone ?? 'N/A'})
                      <p className="text-muted-foreground">{d.reason}</p>
                    </div>
                    <Link
                      to={`/patients/${d.id}`}
                      target="_blank"
                      className="ml-3 inline-flex items-center gap-1 font-semibold text-primary hover:underline"
                    >
                      View <ArrowRight className="h-3 w-3" />
                    </Link>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Demographics section */}
      <div>
        <h3 className="text-sm font-semibold tracking-wide uppercase text-muted-foreground mb-3">
          Demographics & Identity
        </h3>
        <div className="grid gap-4 sm:grid-cols-3">
          <FormField label="NIC / Passport" error={form.formState.errors.nic?.message}>
            <Input
              placeholder="e.g. 199012345678 or 123456789V"
              {...form.register('nic')}
            />
          </FormField>

          <FormField label="First Name" error={form.formState.errors.firstName?.message}>
            <Input placeholder="e.g. Sunil" {...form.register('firstName')} />
          </FormField>

          <FormField label="Last / Family Name" error={form.formState.errors.lastName?.message}>
            <Input placeholder="e.g. Perera" {...form.register('lastName')} />
          </FormField>

          <FormField label="Date of Birth" error={form.formState.errors.dateOfBirth?.message}>
            <Input type="date" {...form.register('dateOfBirth')} />
          </FormField>

          <FormField label="Sex" error={form.formState.errors.sex?.message}>
            <select
              className="h-10 w-full rounded-md border bg-surface px-3 text-sm"
              {...form.register('sex')}
            >
              <option value="MALE">Male</option>
              <option value="FEMALE">Female</option>
              <option value="OTHER">Other</option>
            </select>
          </FormField>

          <FormField label="Blood Group" error={form.formState.errors.bloodGroup?.message}>
            <select
              className="h-10 w-full rounded-md border bg-surface px-3 text-sm"
              {...form.register('bloodGroup')}
            >
              {BLOOD_GROUPS.map((bg) => (
                <option key={bg} value={bg}>
                  {bg}
                </option>
              ))}
            </select>
          </FormField>
        </div>
      </div>

      {/* Contact & Address */}
      <div>
        <h3 className="text-sm font-semibold tracking-wide uppercase text-muted-foreground mb-3">
          Contact & Location
        </h3>
        <div className="grid gap-4 sm:grid-cols-3">
          <FormField label="Mobile Telephone" error={form.formState.errors.phone?.message}>
            <Input placeholder="+94771234567" {...form.register('phone')} />
          </FormField>

          <FormField label="Email" error={form.formState.errors.email?.message}>
            <Input type="email" placeholder="patient@mail.lk" {...form.register('email')} />
          </FormField>

          <FormField label="District" error={form.formState.errors.district?.message}>
            <select
              className="h-10 w-full rounded-md border bg-surface px-3 text-sm"
              {...form.register('district')}
            >
              {SRI_LANKAN_DISTRICTS.map((d) => (
                <option key={d} value={d}>
                  {d}
                </option>
              ))}
            </select>
          </FormField>

          <div className="sm:col-span-2">
            <FormField label="Residential Address" error={form.formState.errors.addressLine1?.message}>
              <Input placeholder="Street name and house number" {...form.register('addressLine1')} />
            </FormField>
          </div>

          <FormField label="City / Town" error={form.formState.errors.city?.message}>
            <Input placeholder="e.g. Nugegoda" {...form.register('city')} />
          </FormField>
        </div>
      </div>

      {/* Emergency Contact & Insurance */}
      <div>
        <h3 className="text-sm font-semibold tracking-wide uppercase text-muted-foreground mb-3">
          Emergency Contact & Insurance
        </h3>
        <div className="grid gap-4 sm:grid-cols-3">
          <FormField label="Guardian / Contact Name" error={form.formState.errors.guardianName?.message}>
            <Input placeholder="e.g. Mrs. K. Perera" {...form.register('guardianName')} />
          </FormField>

          <FormField label="Relationship" error={form.formState.errors.guardianRelationship?.message}>
            <Input placeholder="e.g. Spouse, Parent" {...form.register('guardianRelationship')} />
          </FormField>

          <FormField label="Emergency Contact Phone" error={form.formState.errors.guardianPhone?.message}>
            <Input placeholder="+94771234567" {...form.register('guardianPhone')} />
          </FormField>

          <FormField label="Insurance Provider" error={form.formState.errors.insuranceProvider?.message}>
            <Input placeholder="e.g. Sri Lanka Insurance" {...form.register('insuranceProvider')} />
          </FormField>

          <FormField label="Insurance Policy Number" error={form.formState.errors.insurancePolicyNumber?.message}>
            <Input placeholder="e.g. POL-984210" {...form.register('insurancePolicyNumber')} />
          </FormField>

          <FormField label="Chronic Conditions" error={form.formState.errors.chronicConditions?.message}>
            <Input placeholder="e.g. Hypertension, Asthma" {...form.register('chronicConditions')} />
          </FormField>
        </div>
      </div>

      {/* Structured Allergies (registration time) */}
      {!patient && (
        <div>
          <div className="flex items-center justify-between mb-2">
            <h3 className="text-sm font-semibold tracking-wide uppercase text-muted-foreground">
              Documented Allergies
            </h3>
            <Button
              type="button"
              variant="secondary"
              size="sm"
              onClick={() => append({ allergen: '', reaction: '', severity: 'MODERATE', notes: '' })}
            >
              <Plus className="h-3.5 w-3.5" /> Add Allergy
            </Button>
          </div>

          {fields.length === 0 ? (
            <p className="text-xs text-muted-foreground border border-dashed p-3 text-center">
              No known drug or environmental allergies (NKDA).
            </p>
          ) : (
            <div className="space-y-3">
              {fields.map((field, index) => (
                <div
                  key={field.id}
                  className="grid gap-2 border p-3 bg-muted/20 sm:grid-cols-4 items-end"
                >
                  <FormField label="Allergen">
                    <Input
                      placeholder="e.g. Penicillin"
                      {...form.register(`allergies.${index}.allergen`)}
                    />
                  </FormField>
                  <FormField label="Reaction">
                    <Input
                      placeholder="e.g. Anaphylaxis, Rash"
                      {...form.register(`allergies.${index}.reaction`)}
                    />
                  </FormField>
                  <FormField label="Severity">
                    <select
                      className="h-10 w-full rounded-md border bg-surface px-3 text-sm"
                      {...form.register(`allergies.${index}.severity`)}
                    >
                      <option value="MILD">Mild</option>
                      <option value="MODERATE">Moderate</option>
                      <option value="SEVERE">Severe</option>
                    </select>
                  </FormField>
                  <div className="flex items-center gap-2">
                    <Input
                      placeholder="Clinical notes"
                      {...form.register(`allergies.${index}.notes`)}
                    />
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      className="text-danger"
                      onClick={() => remove(index)}
                    >
                      Remove
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {error && <p className="text-sm text-danger">{error}</p>}

      <div className="flex justify-end gap-2 pt-2 border-t">
        <Button type="button" variant="secondary" onClick={() => onDone()}>
          Cancel
        </Button>
        <Button type="submit" disabled={mutation.isPending}>
          {mutation.isPending ? 'Saving…' : patient ? 'Save changes' : 'Register patient'}
        </Button>
      </div>
    </form>
  );
}

export function PatientsPage() {
  const { has } = useAuth();
  const navigate = useNavigate();
  const [q, setQ] = useState('');
  const [districtFilter, setDistrictFilter] = useState('');
  const [bloodFilter, setBloodFilter] = useState('');
  const [page, setPage] = useState(0);
  const [registering, setRegistering] = useState(false);

  // Keyboard shortcut N to register new patient
  useEffect(() => {
    const listener = (event: KeyboardEvent) => {
      if (event.key.toLowerCase() === 'n' && has('PATIENT_WRITE') && document.activeElement?.tagName !== 'INPUT') {
        event.preventDefault();
        setRegistering(true);
      }
    };
    window.addEventListener('keydown', listener);
    return () => window.removeEventListener('keydown', listener);
  }, [has]);

  const query = useQuery({
    queryKey: ['patients', q, districtFilter, bloodFilter, page],
    queryFn: async () =>
      (
        await api.get<PageResponse<PatientSummary>>('/patients', {
          params: {
            q: q || undefined,
            district: districtFilter || undefined,
            bloodGroup: bloodFilter || undefined,
            page,
            size: 25,
            sort: 'lastName,asc',
          },
        })
      ).data,
  });

  const columns = useMemo<ColumnDef<PatientSummary>[]>(
    () => [
      {
        header: 'MRN',
        cell: ({ row }) => (
          <Link
            to={`/patients/${row.original.id}`}
            className="font-mono text-xs font-semibold text-primary hover:underline"
          >
            {row.original.mrn}
          </Link>
        ),
      },
      {
        header: 'Patient Name',
        cell: ({ row }) => (
          <div>
            <Link
              to={`/patients/${row.original.id}`}
              className="font-medium hover:underline block"
            >
              {row.original.fullName}
            </Link>
            <p className="text-xs text-muted-foreground font-mono">
              {row.original.nic ?? 'No NIC on file'}
            </p>
          </div>
        ),
      },
      {
        header: 'Age / Sex',
        cell: ({ row }) => `${row.original.age}y / ${row.original.sex}`,
      },
      {
        header: 'Contact',
        cell: ({ row }) => (
          <div>
            <p className="font-mono text-xs">{row.original.phone ?? '—'}</p>
            <p className="text-xs text-muted-foreground">{row.original.district}</p>
          </div>
        ),
      },
      {
        header: 'Blood',
        cell: ({ row }) => (
          <span className="inline-flex rounded bg-muted px-2 py-0.5 font-mono text-xs font-semibold">
            {row.original.bloodGroup ?? '—'}
          </span>
        ),
      },
      {
        header: 'Status',
        cell: ({ row }) => <StatusBadge status={row.original.active} />,
      },
      {
        id: 'actions',
        header: '',
        cell: ({ row }) => (
          <Link to={`/patients/${row.original.id}`}>
            <Button size="sm" variant="ghost">
              Open File
            </Button>
          </Link>
        ),
      },
    ],
    []
  );

  return (
    <>
      <PageHeader
        title="Patients"
        description="Master Patient Index (MPI), demographic records, and hospital registry."
        action={
          has('PATIENT_WRITE') ? (
            <Button onClick={() => setRegistering(true)}>
              <Plus className="h-4 w-4" />
              Register patient (N)
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
            placeholder="Search by MRN, NIC, Name, or Phone ( / )"
          />
        </div>

        <select
          className="h-10 rounded-md border bg-surface px-3 text-sm"
          value={districtFilter}
          onChange={(e) => {
            setDistrictFilter(e.target.value);
            setPage(0);
          }}
        >
          <option value="">All Districts</option>
          {SRI_LANKAN_DISTRICTS.map((d) => (
            <option key={d} value={d}>
              {d}
            </option>
          ))}
        </select>

        <select
          className="h-10 rounded-md border bg-surface px-3 text-sm"
          value={bloodFilter}
          onChange={(e) => {
            setBloodFilter(e.target.value);
            setPage(0);
          }}
        >
          <option value="">All Blood Groups</option>
          {BLOOD_GROUPS.map((bg) => (
            <option key={bg} value={bg}>
              {bg}
            </option>
          ))}
        </select>
      </div>

      {query.isLoading ? (
        <p className="text-sm text-muted-foreground">Searching patient index…</p>
      ) : query.isError ? (
        <p className="text-sm text-danger">{apiMessage(query.error)}</p>
      ) : (
        <>
          <DataTable
            data={query.data?.content ?? []}
            columns={columns}
            emptyTitle="No patient records match"
            emptyDetail="Try searching by full MRN (e.g. MRN-2026-000123) or National Identity Card number."
          />
          <div className="mt-3 flex items-center justify-between text-sm text-muted-foreground">
            <span>{query.data?.totalElements ?? 0} registered patients</span>
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

      {registering && (
        <Dialog open onOpenChange={(open) => !open && setRegistering(false)}>
          <DialogContent title="New Patient Registration">
            <PatientRegistrationForm
              onDone={(newId) => {
                setRegistering(false);
                if (newId) {
                  navigate(`/patients/${newId}`);
                }
              }}
            />
          </DialogContent>
        </Dialog>
      )}
    </>
  );
}

