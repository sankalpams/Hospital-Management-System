import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, CalendarCheck2 } from 'lucide-react';
import { appointmentsApi, doctorsApi } from '../../api/clinic';
import { departmentsApi } from '../../api/org';
import { patientsApi } from '../../api/patients';
import { toApiError } from '../../api/client';
import { PageHeader } from '../../components/layout/AppShell';
import { Card, CardBody, CardHeader } from '../../components/ui/Card';
import { CheckboxField, SelectField, TextField } from '../../components/ui/Field';
import { Button } from '../../components/ui/Button';
import { Alert } from '../../components/ui/Alert';
import { Spinner } from '../../components/ui/Spinner';
import { useToast } from '../../components/ui/Toast';
import { formatDate, formatLKR, todayInHospital } from '../../lib/format';
import { PatientPicker, SlotPicker } from './shared';

const TYPES = [{ value: 'CONSULTATION', label: 'Consultation' }, { value: 'FOLLOW_UP', label: 'Follow-up' }];

/** Book a new appointment, or (with :id) reschedule an existing one. */
export default function BookAppointmentPage() {
  const { id } = useParams();
  const rescheduling = Boolean(id);
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const toast = useToast();
  const queryClient = useQueryClient();

  const original = useQuery({ queryKey: ['appointment', id], queryFn: () => appointmentsApi.get(id), enabled: rescheduling });
  const preselectedPatientId = searchParams.get('patientId');
  const preselected = useQuery({ queryKey: ['patient', preselectedPatientId], queryFn: () => patientsApi.get(preselectedPatientId), enabled: Boolean(preselectedPatientId) && !rescheduling });

  const [patient, setPatient] = useState(null);
  const [departmentId, setDepartmentId] = useState('');
  const [doctorId, setDoctorId] = useState('');
  const [date, setDate] = useState(todayInHospital());
  const [time, setTime] = useState(null);
  const [type, setType] = useState('CONSULTATION');
  const [reason, setReason] = useState('');
  const [confirmNow, setConfirmNow] = useState(true);
  const [errors, setErrors] = useState({});
  const [general, setGeneral] = useState(null);

  useEffect(() => { if (preselected.data) setPatient(preselected.data); }, [preselected.data]);
  useEffect(() => {
    if (!original.data) return;
    const a = original.data;
    setPatient({ id: a.patientId, fullName: a.patientName, mrn: a.patientMrn, phone: a.patientPhone, age: '—' });
    setDepartmentId(String(a.departmentId));
    setDoctorId(String(a.doctorId));
    setReason(a.reason ?? '');
  }, [original.data]);
  useEffect(() => { setTime(null); }, [doctorId, date]);

  const departments = useQuery({ queryKey: ['department-options'], queryFn: departmentsApi.options, staleTime: 300_000 });
  const doctors = useQuery({ queryKey: ['doctor-options', departmentId], queryFn: () => doctorsApi.options(departmentId), staleTime: 60_000 });
  const doctor = doctors.data?.find((d) => String(d.id) === doctorId);

  const save = useMutation({
    mutationFn: () => (rescheduling
      ? appointmentsApi.reschedule(id, { doctorId: Number(doctorId), date, time, reason: reason || null })
      : appointmentsApi.book({ patientId: patient.id, doctorId: Number(doctorId), date, time, type, reason: reason || null, confirmNow })),
    onSuccess: (a) => {
      queryClient.invalidateQueries({ queryKey: ['appointments'] });
      queryClient.invalidateQueries({ queryKey: ['appointment-calendar'] });
      toast.success(`${a.appointmentNo} booked for ${formatDate(a.localDate)} at ${a.localTime.slice(0, 5)} (token ${a.tokenNo}).`);
      navigate(`/appointments?date=${a.localDate}`);
    },
    onError: (err) => {
      const e = toApiError(err);
      setGeneral(e);
      if (e.code === 'SLOT_TAKEN') {
        queryClient.invalidateQueries({ queryKey: ['availability', doctorId, date] });
        setTime(null);
      }
    },
  });

  const submit = (e) => {
    e.preventDefault();
    const errs = {};
    if (!patient) errs.patient = 'Select the patient.';
    if (!doctorId) errs.doctorId = 'Choose a doctor.';
    if (!date) errs.date = 'Choose a date.';
    if (!time) errs.time = 'Choose a free time.';
    setErrors(errs);
    setGeneral(null);
    if (!Object.keys(errs).length) save.mutate();
  };

  if (rescheduling && original.isLoading) return <Spinner label="Loading appointment" />;

  return (
    <>
      <Link to="/appointments" className="mb-3 inline-flex items-center gap-1 text-sm text-foreground-muted hover:text-foreground">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Appointments
      </Link>
      <PageHeader
        title={rescheduling ? `Reschedule ${original.data?.appointmentNo ?? ''}` : 'Book appointment'}
        description={rescheduling
          ? `Currently ${formatDate(original.data?.localDate)} at ${original.data?.localTime?.slice(0, 5)} with ${original.data?.doctorName}. The old booking is cancelled when the new one is saved.`
          : 'Only times inside the doctor’s sessions that are still free can be chosen.'}
      />
      <form onSubmit={submit} noValidate className="grid gap-6 lg:grid-cols-3">
        <div className="flex flex-col gap-6 lg:col-span-2">
          {general && <Alert tone="danger" requestId={general.requestId}>{general.message}</Alert>}
          <Card>
            <CardHeader title="1. Patient" />
            <CardBody>
              {rescheduling
                ? <p className="font-medium">{patient?.fullName} <span className="font-mono text-xs text-foreground-subtle">{patient?.mrn}</span></p>
                : <PatientPicker value={patient} onChange={setPatient} error={errors.patient} />}
            </CardBody>
          </Card>
          <Card>
            <CardHeader title="2. Doctor and date" />
            <CardBody className="grid gap-4 sm:grid-cols-3">
              <SelectField label="Department" placeholder="All departments" value={departmentId} onChange={(e) => { setDepartmentId(e.target.value); setDoctorId(''); }}
                           options={(departments.data ?? []).map((d) => ({ value: String(d.id), label: d.name }))} />
              <SelectField label="Doctor" required placeholder={doctors.isLoading ? 'Loading…' : 'Select doctor'} value={doctorId} onChange={(e) => setDoctorId(e.target.value)} error={errors.doctorId}
                           options={(doctors.data ?? []).map((d) => ({ value: String(d.id), label: `${d.displayName} · ${d.specialization}` }))} />
              <TextField label="Date" type="date" required min={todayInHospital()} value={date} onChange={(e) => setDate(e.target.value)} error={errors.date} />
            </CardBody>
          </Card>
          <Card>
            <CardHeader title="3. Time" description={errors.time ? undefined : 'Crossed-out times are booked or already past.'} />
            <CardBody>
              {errors.time && <p role="alert" className="mb-3 text-xs font-medium text-danger">{errors.time}</p>}
              <SlotPicker doctorId={doctorId} date={date} value={time} onChange={setTime} />
            </CardBody>
          </Card>
        </div>

        <div className="flex flex-col gap-6">
          <Card className="lg:sticky lg:top-20">
            <CardHeader icon={CalendarCheck2} title="Summary" />
            <CardBody className="flex flex-col gap-4">
              <dl className="grid gap-2 text-sm">
                <div><dt className="text-xs text-foreground-subtle">Patient</dt><dd className="font-medium">{patient?.fullName ?? '—'}</dd></div>
                <div><dt className="text-xs text-foreground-subtle">Doctor</dt><dd className="font-medium">{doctor?.displayName ?? '—'}</dd></div>
                <div><dt className="text-xs text-foreground-subtle">When</dt><dd className="font-medium">{time ? `${formatDate(date)} at ${time.slice(0, 5)}` : '—'}</dd></div>
                {doctor && <div><dt className="text-xs text-foreground-subtle">Consultation fee</dt><dd className="font-medium tabular">{formatLKR(doctor.consultationFee)}</dd></div>}
              </dl>
              {!rescheduling && <SelectField label="Type" options={TYPES} value={type} onChange={(e) => setType(e.target.value)} />}
              <TextField label="Reason for visit" value={reason} maxLength={255} onChange={(e) => setReason(e.target.value)} />
              {!rescheduling && <CheckboxField label="Confirm now" description="Untick to keep as pending (e.g. awaiting the patient's reply)." checked={confirmNow} onChange={(e) => setConfirmNow(e.target.checked)} />}
              <Button type="submit" loading={save.isPending} className="w-full">{rescheduling ? 'Reschedule' : 'Book appointment'}</Button>
            </CardBody>
          </Card>
        </div>
      </form>
    </>
  );
}
