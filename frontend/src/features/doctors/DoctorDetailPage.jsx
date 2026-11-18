import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, CalendarOff, CalendarRange, Pencil, Plus, Trash2 } from 'lucide-react';
import { WEEKDAYS, doctorsApi } from '../../api/clinic';
import { toApiError } from '../../api/client';
import { useAuth } from '../../auth/AuthProvider';
import { Card, CardBody, CardHeader } from '../../components/ui/Card';
import { Badge, StatusBadge } from '../../components/ui/Badge';
import { Button, LinkButton } from '../../components/ui/Button';
import { Alert } from '../../components/ui/Alert';
import { ConfirmDialog, Modal } from '../../components/ui/Modal';
import { CheckboxField, SelectField, TextField } from '../../components/ui/Field';
import { Spinner } from '../../components/ui/Spinner';
import { useToast } from '../../components/ui/Toast';
import { formatDate, formatLKR, todayInHospital } from '../../lib/format';
import { splitApiError } from '../../lib/forms';

const hhmm = (t) => (t ? t.slice(0, 5) : '');

export default function DoctorDetailPage() {
  const { id } = useParams();
  const { hasPermission } = useAuth();
  const canSchedule = hasPermission('schedule:manage');
  const doctor = useQuery({ queryKey: ['doctor', id], queryFn: () => doctorsApi.get(id) });
  const schedules = useQuery({ queryKey: ['doctor-schedules', id], queryFn: () => doctorsApi.schedules(id) });
  const exceptions = useQuery({ queryKey: ['doctor-exceptions', id], queryFn: () => doctorsApi.exceptions(id) });
  const [editingSession, setEditingSession] = useState(null);
  const [addingLeave, setAddingLeave] = useState(false);
  const [removing, setRemoving] = useState(null);
  const queryClient = useQueryClient();
  const toast = useToast();

  const remove = useMutation({
    mutationFn: (x) => doctorsApi.removeException(id, x.id),
    onSuccess: () => { setRemoving(null); queryClient.invalidateQueries({ queryKey: ['doctor-exceptions', id] }); toast.success('Unavailability removed.'); },
  });

  if (doctor.isLoading) return <Spinner label="Loading doctor" />;
  if (doctor.isError) return <Alert tone="danger">{toApiError(doctor.error).message}</Alert>;
  const d = doctor.data;

  return (
    <>
      <Link to="/doctors" className="mb-3 inline-flex items-center gap-1 text-sm text-foreground-muted hover:text-foreground">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Doctors
      </Link>
      <Card className="mb-6">
        <div className="flex flex-col gap-4 px-5 py-5 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="text-2xl">{d.displayName}</h1>
              <StatusBadge status={d.status} />
              {d.status === 'ACTIVE' && !d.acceptingPatients && <Badge tone="warning">Not taking bookings</Badge>}
            </div>
            <p className="mt-1 text-sm text-foreground-muted">{d.specialization} · {d.departmentName}{d.qualifications ? ` · ${d.qualifications}` : ''}</p>
            <p className="mt-1 text-sm text-foreground-muted">
              SLMC <span className="font-mono">{d.registrationNo}</span> · {d.employeeNo} · Fee <span className="font-medium text-foreground">{formatLKR(d.consultationFee)}</span>
            </p>
            {!d.userId && <p className="mt-2 text-xs text-warning">No login account linked: this doctor cannot start consultations in the system yet.</p>}
          </div>
          {hasPermission('doctor:manage') && <LinkButton to={`/doctors/${d.id}/edit`} icon={Pencil}>Edit</LinkButton>}
        </div>
      </Card>

      <div className="grid gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader icon={CalendarRange} title="Weekly consultation sessions" description="Appointments can only be booked inside these sessions."
                      action={canSchedule && <Button size="sm" icon={Plus} onClick={() => setEditingSession('new')}>Add session</Button>} />
          <CardBody>
            {schedules.isLoading && <Spinner label="Loading sessions" />}
            {schedules.data?.length === 0 && <p className="text-sm text-foreground-muted">No sessions yet. Add one so patients can be booked.</p>}
            {schedules.data?.length > 0 && (
              <ul className="divide-y divide-border">
                {schedules.data.map((s) => (
                  <li key={s.id} className="flex flex-col gap-2 py-3 sm:flex-row sm:items-center sm:justify-between">
                    <div>
                      <p className="font-medium">
                        {WEEKDAYS.find((w) => w.value === s.dayOfWeek)?.label} · {hhmm(s.startTime)}–{hhmm(s.endTime)}
                        {!s.active && <Badge tone="neutral" className="ml-2">Inactive</Badge>}
                      </p>
                      <p className="text-xs text-foreground-subtle">
                        {s.slotMinutes}-min slots · up to {s.slotsPerSession} patients{s.roomLabel ? ` · ${s.roomLabel}` : ''} · from {formatDate(s.effectiveFrom)}{s.effectiveTo ? ` to ${formatDate(s.effectiveTo)}` : ''}
                      </p>
                    </div>
                    {canSchedule && <Button variant="ghost" size="sm" icon={Pencil} onClick={() => setEditingSession(s)}>Edit</Button>}
                  </li>
                ))}
              </ul>
            )}
          </CardBody>
        </Card>

        <Card>
          <CardHeader icon={CalendarOff} title="Unavailable dates" action={canSchedule && <Button size="sm" variant="secondary" icon={Plus} onClick={() => setAddingLeave(true)}>Add</Button>} />
          <CardBody>
            {exceptions.data?.length === 0 && <p className="text-sm text-foreground-muted">None planned.</p>}
            <ul className="flex flex-col gap-3">
              {exceptions.data?.map((x) => (
                <li key={x.id} className="flex items-start justify-between gap-2 text-sm">
                  <div>
                    <p className="font-medium">{formatDate(x.dateFrom)}{x.dateTo !== x.dateFrom ? ` – ${formatDate(x.dateTo)}` : ''}</p>
                    <p className="text-foreground-muted">{x.reason}</p>
                  </div>
                  {canSchedule && (
                    <button type="button" onClick={() => setRemoving(x)} className="rounded p-1 text-foreground-subtle hover:text-danger" aria-label={`Remove unavailability ${x.dateFrom}`}>
                      <Trash2 className="h-4 w-4" aria-hidden="true" />
                    </button>
                  )}
                </li>
              ))}
            </ul>
          </CardBody>
        </Card>
      </div>

      {editingSession && <SessionDialog doctorId={id} session={editingSession === 'new' ? null : editingSession} onClose={() => setEditingSession(null)} />}
      {addingLeave && <LeaveDialog doctorId={id} onClose={() => setAddingLeave(false)} />}
      {removing && (
        <ConfirmDialog open title="Remove unavailability?" confirmLabel="Remove"
                       message={`The doctor will be bookable again on ${formatDate(removing.dateFrom)}${removing.dateTo !== removing.dateFrom ? ` – ${formatDate(removing.dateTo)}` : ''}.`}
                       loading={remove.isPending} onClose={() => setRemoving(null)} onConfirm={() => remove.mutate(removing)} />
      )}
    </>
  );
}

function SessionDialog({ doctorId, session, onClose }) {
  const isNew = !session;
  const queryClient = useQueryClient();
  const toast = useToast();
  const [form, setForm] = useState({
    dayOfWeek: String(session?.dayOfWeek ?? 1), startTime: hhmm(session?.startTime) || '09:00', endTime: hhmm(session?.endTime) || '12:00',
    slotMinutes: String(session?.slotMinutes ?? 15), maxPatients: String(session?.maxPatients ?? 12), roomLabel: session?.roomLabel ?? '',
    effectiveFrom: session?.effectiveFrom ?? todayInHospital(), effectiveTo: session?.effectiveTo ?? '', active: session?.active ?? true,
  });
  const [errors, setErrors] = useState({});
  const [general, setGeneral] = useState(null);
  const set = (k) => (e) => { const v = e.target.type === 'checkbox' ? e.target.checked : e.target.value; setForm((f) => ({ ...f, [k]: v })); setErrors((x) => ({ ...x, [k]: undefined })); };

  const save = useMutation({
    mutationFn: () => {
      const body = { ...form, dayOfWeek: Number(form.dayOfWeek), slotMinutes: Number(form.slotMinutes), maxPatients: Number(form.maxPatients),
        effectiveTo: form.effectiveTo || null, roomLabel: form.roomLabel || null };
      return isNew ? doctorsApi.addSchedule(doctorId, body) : doctorsApi.updateSchedule(doctorId, session.id, body);
    },
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['doctor-schedules', doctorId] }); toast.success('Session saved.'); onClose(); },
    onError: (err) => { const { fields, general: g } = splitApiError(err); setErrors(fields); setGeneral(g); },
  });

  const slots = (() => {
    const [sh, sm] = form.startTime.split(':').map(Number);
    const [eh, em] = form.endTime.split(':').map(Number);
    const minutes = (eh * 60 + em) - (sh * 60 + sm);
    return minutes > 0 && Number(form.slotMinutes) > 0 ? Math.min(Math.floor(minutes / Number(form.slotMinutes)), Number(form.maxPatients) || 0) : 0;
  })();

  return (
    <Modal open onClose={onClose} title={isNew ? 'Add consultation session' : 'Edit session'} size="lg"
           footer={<><Button variant="ghost" onClick={onClose}>Cancel</Button><Button onClick={() => save.mutate()} loading={save.isPending}>Save session</Button></>}>
      <div className="grid gap-4 sm:grid-cols-3">
        {general && <Alert tone="danger" className="sm:col-span-3">{general.message}</Alert>}
        <SelectField label="Day" required options={WEEKDAYS.map((w) => ({ value: String(w.value), label: w.label }))} value={form.dayOfWeek} onChange={set('dayOfWeek')} />
        <TextField label="Starts" type="time" required value={form.startTime} onChange={set('startTime')} error={errors.startTime} />
        <TextField label="Ends" type="time" required value={form.endTime} onChange={set('endTime')} error={errors.endTime} />
        <TextField label="Slot length (minutes)" type="number" min={5} max={120} required value={form.slotMinutes} onChange={set('slotMinutes')} error={errors.slotMinutes} />
        <TextField label="Maximum patients" type="number" min={1} max={200} required value={form.maxPatients} onChange={set('maxPatients')} error={errors.maxPatients} />
        <TextField label="Room" value={form.roomLabel} onChange={set('roomLabel')} error={errors.roomLabel} placeholder="e.g. OPD Room 4" />
        <TextField label="Effective from" type="date" required value={form.effectiveFrom} onChange={set('effectiveFrom')} error={errors.effectiveFrom} />
        <TextField label="Effective to" type="date" value={form.effectiveTo} onChange={set('effectiveTo')} error={errors.effectiveTo} hint="Leave empty for no end date." />
        <CheckboxField className="self-end" label="Active" checked={form.active} onChange={set('active')} />
        <p className="text-sm text-foreground-muted sm:col-span-3">This session offers <span className="font-semibold text-foreground">{slots}</span> appointment slot{slots === 1 ? '' : 's'}.</p>
      </div>
    </Modal>
  );
}

function LeaveDialog({ doctorId, onClose }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [form, setForm] = useState({ dateFrom: todayInHospital(), dateTo: todayInHospital(), reason: '' });
  const [error, setError] = useState(null);
  const save = useMutation({
    mutationFn: () => doctorsApi.addException(doctorId, form),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['doctor-exceptions', doctorId] }); toast.success('Unavailability added.'); onClose(); },
    onError: (err) => setError(toApiError(err).message),
  });
  return (
    <Modal open onClose={onClose} title="Add unavailable dates" size="sm"
           description="Existing appointments on these dates are not cancelled automatically; review and reschedule them."
           footer={<><Button variant="ghost" onClick={onClose}>Cancel</Button><Button onClick={() => save.mutate()} loading={save.isPending} disabled={form.reason.trim().length < 3}>Save</Button></>}>
      <div className="grid gap-4 sm:grid-cols-2">
        {error && <Alert tone="danger" className="sm:col-span-2">{error}</Alert>}
        <TextField label="From" type="date" required min={todayInHospital()} value={form.dateFrom} onChange={(e) => setForm((f) => ({ ...f, dateFrom: e.target.value }))} />
        <TextField label="To" type="date" required min={form.dateFrom} value={form.dateTo} onChange={(e) => setForm((f) => ({ ...f, dateTo: e.target.value }))} />
        <TextField className="sm:col-span-2" label="Reason" required value={form.reason} onChange={(e) => setForm((f) => ({ ...f, reason: e.target.value }))} placeholder="e.g. Annual leave, conference" />
      </div>
    </Modal>
  );
}
