import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft } from 'lucide-react';
import { EMPLOYEE_STATUSES, EMPLOYMENT_TYPES, doctorsApi } from '../../api/clinic';
import { departmentsApi } from '../../api/org';
import { usersApi } from '../../api/admin';
import { GENDERS, NIC_PATTERN, PHONE_PATTERN } from '../../api/patients';
import { toApiError } from '../../api/client';
import { useAuth } from '../../auth/AuthProvider';
import { PageHeader } from '../../components/layout/AppShell';
import { Card, CardBody, CardHeader } from '../../components/ui/Card';
import { CheckboxField, SelectField, TextField } from '../../components/ui/Field';
import { Button } from '../../components/ui/Button';
import { Alert } from '../../components/ui/Alert';
import { Spinner } from '../../components/ui/Spinner';
import { useToast } from '../../components/ui/Toast';
import { todayInHospital } from '../../lib/format';
import { focusFirstInvalid, requireFields, splitApiError } from '../../lib/forms';

const EMPTY = {
  firstName: '', lastName: '', gender: '', nic: '', phone: '', email: '', departmentId: '', employmentType: 'PERMANENT',
  joinDate: '', registrationNo: '', specialization: '', qualifications: '', consultationFee: '', acceptingPatients: true,
  status: 'ACTIVE', userId: '',
};

export default function DoctorFormPage() {
  const { id } = useParams();
  const isNew = !id;
  const navigate = useNavigate();
  const toast = useToast();
  const queryClient = useQueryClient();
  const { hasPermission } = useAuth();

  const existing = useQuery({ queryKey: ['doctor', id], queryFn: () => doctorsApi.get(id), enabled: !isNew });
  const departments = useQuery({ queryKey: ['department-options'], queryFn: departmentsApi.options, staleTime: 300_000 });
  const doctorUsers = useQuery({
    queryKey: ['users', { role: 'DOCTOR', size: 100, status: 'ACTIVE' }],
    queryFn: () => usersApi.list({ role: 'DOCTOR', size: 100, status: 'ACTIVE' }),
    enabled: hasPermission('user:read'),
  });

  const [form, setForm] = useState(EMPTY);
  const [errors, setErrors] = useState({});
  const [general, setGeneral] = useState(null);

  useEffect(() => {
    if (!existing.data) return;
    const d = existing.data;
    setForm({
      ...EMPTY, ...Object.fromEntries(Object.keys(EMPTY).map((k) => [k, d[k] ?? EMPTY[k]])),
      departmentId: String(d.departmentId), consultationFee: String(d.consultationFee), userId: d.userId ? String(d.userId) : '',
    });
  }, [existing.data]);

  const set = (k) => (e) => {
    const value = e.target.type === 'checkbox' ? e.target.checked : e.target.value;
    setForm((f) => ({ ...f, [k]: value }));
    setErrors((x) => ({ ...x, [k]: undefined }));
  };

  const save = useMutation({
    mutationFn: () => {
      const body = {
        ...form,
        nic: form.nic.trim() || null,
        email: form.email.trim() || null,
        departmentId: Number(form.departmentId),
        consultationFee: form.consultationFee,
        userId: form.userId ? Number(form.userId) : null,
        status: isNew ? null : form.status,
        version: existing.data?.version,
      };
      return isNew ? doctorsApi.create(body) : doctorsApi.update(id, body);
    },
    onSuccess: (d) => {
      queryClient.invalidateQueries({ queryKey: ['doctors'] });
      queryClient.setQueryData(['doctor', String(d.id)], d);
      toast.success(isNew ? `${d.displayName} added. Now set up the weekly sessions.` : 'Doctor saved.');
      navigate(`/doctors/${d.id}`);
    },
    onError: (err) => { const { fields, general: g } = splitApiError(err); setErrors(fields); setGeneral(g); focusFirstInvalid(); },
  });

  const submit = (e) => {
    e.preventDefault();
    const errs = requireFields(form, {
      firstName: 'First name', lastName: 'Last name', gender: 'Sex', phone: 'Phone', departmentId: 'Department',
      joinDate: 'Join date', registrationNo: 'SLMC registration', specialization: 'Specialisation', consultationFee: 'Consultation fee',
    });
    if (form.nic && !NIC_PATTERN.test(form.nic.trim())) errs.nic = 'Enter a valid NIC.';
    if (form.phone && !PHONE_PATTERN.test(form.phone.trim())) errs.phone = 'Use 0XXXXXXXXX or +94XXXXXXXXX.';
    if (form.consultationFee && !/^\d{1,7}(\.\d{1,2})?$/.test(form.consultationFee)) errs.consultationFee = 'Enter an amount in LKR, e.g. 2500 or 2500.00.';
    setErrors(errs);
    setGeneral(null);
    if (Object.keys(errs).length) focusFirstInvalid();
    else save.mutate();
  };

  if (!isNew && existing.isLoading) return <Spinner label="Loading doctor" />;
  if (!isNew && existing.isError) return <Alert tone="danger">{toApiError(existing.error).message}</Alert>;

  const deptOptions = (departments.data ?? []).map((d) => ({ value: String(d.id), label: d.name }));
  const userOptions = (doctorUsers.data?.content ?? []).map((u) => ({ value: String(u.id), label: `${u.fullName} (${u.username})` }));

  return (
    <>
      <Link to={isNew ? '/doctors' : `/doctors/${id}`} className="mb-3 inline-flex items-center gap-1 text-sm text-foreground-muted hover:text-foreground">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" /> {isNew ? 'Doctors' : 'Back to doctor'}
      </Link>
      <PageHeader title={isNew ? 'Add doctor' : `Edit ${existing.data.displayName}`} description={isNew ? 'An employee number is assigned automatically.' : existing.data.employeeNo} />
      <form onSubmit={submit} noValidate className="flex flex-col gap-6">
        {general && <Alert tone="danger" requestId={general.requestId}>{general.message}</Alert>}
        <Card>
          <CardHeader title="Personal details" />
          <CardBody className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            <TextField label="First name" required value={form.firstName} onChange={set('firstName')} error={errors.firstName} />
            <TextField label="Last name" required value={form.lastName} onChange={set('lastName')} error={errors.lastName} />
            <SelectField label="Sex" required placeholder="Select" options={GENDERS} value={form.gender} onChange={set('gender')} error={errors.gender} />
            <TextField label="NIC" value={form.nic} onChange={set('nic')} error={errors.nic} />
            <TextField label="Phone" type="tel" required value={form.phone} onChange={set('phone')} error={errors.phone} />
            <TextField label="Email" type="email" value={form.email} onChange={set('email')} error={errors.email} />
          </CardBody>
        </Card>
        <Card>
          <CardHeader title="Professional details" />
          <CardBody className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            <TextField label="SLMC registration no." required value={form.registrationNo} onChange={set('registrationNo')} error={errors.registrationNo} />
            <TextField label="Specialisation" required value={form.specialization} onChange={set('specialization')} error={errors.specialization} placeholder="e.g. Cardiology" />
            <TextField label="Qualifications" value={form.qualifications} onChange={set('qualifications')} error={errors.qualifications} placeholder="e.g. MBBS, MD" />
            <SelectField label="Department" required placeholder="Select department" options={deptOptions} value={form.departmentId} onChange={set('departmentId')} error={errors.departmentId} />
            <TextField label="Consultation fee (LKR)" required inputMode="decimal" value={form.consultationFee} onChange={set('consultationFee')} error={errors.consultationFee} />
            <SelectField label="Employment type" required options={EMPLOYMENT_TYPES} value={form.employmentType} onChange={set('employmentType')} error={errors.employmentType} />
            <TextField label="Join date" type="date" required max={todayInHospital()} value={form.joinDate} onChange={set('joinDate')} error={errors.joinDate} />
            {!isNew && <SelectField label="Employment status" options={EMPLOYEE_STATUSES} value={form.status} onChange={set('status')} error={errors.status} />}
            {hasPermission('user:read') && (
              <SelectField label="Login account" placeholder="Not linked" options={userOptions} value={form.userId} onChange={set('userId')}
                           error={errors.userId} hint="A user with the Doctor role. Needed to start consultations." />
            )}
            <CheckboxField className="sm:col-span-2 lg:col-span-3" label="Accepting new appointments" checked={form.acceptingPatients} onChange={set('acceptingPatients')}
                           description="Turn off to stop new bookings without changing existing appointments." />
          </CardBody>
        </Card>
        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button variant="ghost" onClick={() => navigate(isNew ? '/doctors' : `/doctors/${id}`)}>Cancel</Button>
          <Button type="submit" loading={save.isPending}>{isNew ? 'Add doctor' : 'Save changes'}</Button>
        </div>
      </form>
    </>
  );
}
