import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import clsx from 'clsx';
import { CheckCircle2, Search, X } from 'lucide-react';
import { ACTION_LABEL, ACTION_PERMISSION, appointmentsApi, doctorsApi } from '../../api/clinic';
import { patientsApi } from '../../api/patients';
import { toApiError } from '../../api/client';
import { useAuth } from '../../auth/AuthProvider';
import { Button, LinkButton } from '../../components/ui/Button';
import { ConfirmDialog } from '../../components/ui/Modal';
import { Spinner } from '../../components/ui/Spinner';
import { useToast } from '../../components/ui/Toast';
import { controlClass } from '../../components/ui/Field';
import { formatDate } from '../../lib/format';
import { humanise } from '../../components/ui/Badge';

/** Type-ahead patient search (MRN, NIC, phone or name). Only ACTIVE patients can be selected. */
export function PatientPicker({ value, onChange, error }) {
  const [q, setQ] = useState('');
  const [term, setTerm] = useState('');
  const results = useQuery({
    queryKey: ['patient-picker', term],
    queryFn: () => patientsApi.search({ q: term, size: 8, status: 'ACTIVE' }),
    enabled: term.length >= 2,
  });

  if (value) {
    return (
      <div className="flex items-center justify-between gap-3 rounded-md border border-border-strong bg-primary-soft px-3 py-2.5">
        <div>
          <p className="font-medium text-primary-soft-foreground">{value.fullName}</p>
          <p className="text-xs text-primary-soft-foreground"><span className="font-mono">{value.mrn}</span> · {value.age} yrs · {value.phone}</p>
        </div>
        <button type="button" onClick={() => onChange(null)} className="rounded p-1 text-primary-soft-foreground hover:bg-surface" aria-label="Change patient">
          <X className="h-4 w-4" aria-hidden="true" />
        </button>
      </div>
    );
  }
  return (
    <div className="flex flex-col gap-2">
      <form onSubmit={(e) => { e.preventDefault(); setTerm(q.trim()); }} className="flex gap-2">
        <label htmlFor="patient-picker" className="sr-only">Find patient</label>
        <div className="relative flex-1">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-foreground-subtle" aria-hidden="true" />
          <input id="patient-picker" value={q} onChange={(e) => setQ(e.target.value)} placeholder="MRN, NIC, phone or name"
                 aria-invalid={error ? true : undefined} className={controlClass(error, 'h-10 pl-9')} />
        </div>
        <Button type="submit" variant="secondary">Find</Button>
      </form>
      {error && <p className="text-xs font-medium text-danger">{error}</p>}
      {results.isFetching && <Spinner label="Searching" />}
      {results.data && (
        results.data.content.length === 0
          ? <p className="text-sm text-foreground-muted">No active patient matches “{term}”.</p>
          : (
            <ul className="divide-y divide-border rounded-md border border-border">
              {results.data.content.map((p) => (
                <li key={p.id}>
                  <button type="button" onClick={() => onChange(p)} className="flex w-full items-center justify-between gap-3 px-3 py-2 text-left hover:bg-surface-muted">
                    <span>
                      <span className="block text-sm font-medium">{p.fullName}</span>
                      <span className="block text-xs text-foreground-subtle"><span className="font-mono">{p.mrn}</span> · born {formatDate(p.dateOfBirth)} · {p.phone}</span>
                    </span>
                    <span className="text-xs font-medium text-primary">Select</span>
                  </button>
                </li>
              ))}
            </ul>
          )
      )}
    </div>
  );
}

/** Slot grid for one doctor and date, from the availability API (never computed in the browser). */
export function SlotPicker({ doctorId, date, value, onChange }) {
  const availability = useQuery({
    queryKey: ['availability', doctorId, date],
    queryFn: () => doctorsApi.availability(doctorId, date),
    enabled: Boolean(doctorId && date),
  });
  if (!doctorId || !date) return <p className="text-sm text-foreground-muted">Choose a doctor and a date to see free times.</p>;
  if (availability.isLoading) return <Spinner label="Loading free times" />;
  if (availability.isError) return <p className="text-sm text-danger">{toApiError(availability.error).message}</p>;
  const a = availability.data;
  if (a.blockedReason) return <p className="rounded-md bg-surface-muted px-3 py-2.5 text-sm text-foreground-muted">{a.blockedReason}.</p>;

  return (
    <div className="flex flex-col gap-4">
      {a.sessions.map((s) => (
        <div key={s.scheduleId}>
          <p className="mb-2 text-[13px] font-medium">
            {s.startTime.slice(0, 5)}–{s.endTime.slice(0, 5)}{s.roomLabel ? ` · ${s.roomLabel}` : ''}
            <span className="ml-2 font-normal text-foreground-subtle">
              {s.slots.filter((x) => x.available).length} bookable · {s.booked} of {s.capacity} booked
            </span>
          </p>
          <div role="radiogroup" aria-label={`Times ${s.startTime.slice(0, 5)} to ${s.endTime.slice(0, 5)}`} className="grid grid-cols-4 gap-2 sm:grid-cols-6 lg:grid-cols-8">
            {s.slots.map((slot) => {
              const selected = value === slot.time;
              return (
                <button
                  key={slot.time}
                  type="button"
                  role="radio"
                  aria-checked={selected}
                  disabled={!slot.available}
                  title={slot.unavailableReason ?? undefined}
                  onClick={() => onChange(slot.time)}
                  className={clsx(
                    'h-10 rounded-md border text-sm font-medium tabular transition-colors duration-fast',
                    selected && 'border-primary bg-primary text-primary-foreground',
                    !selected && slot.available && 'border-border bg-surface hover:border-primary hover:bg-primary-soft',
                    !slot.available && 'cursor-not-allowed border-border bg-surface-muted text-foreground-subtle line-through',
                  )}
                >
                  {slot.time.slice(0, 5)}
                  <span className="sr-only">{slot.available ? '' : ` (${slot.unavailableReason})`}</span>
                </button>
              );
            })}
          </div>
        </div>
      ))}
    </div>
  );
}

/**
 * Buttons for the actions the server says are possible now (allowedActions), filtered by the
 * user's permissions. Destructive ones ask for confirmation; cancel requires a reason.
 */
export function AppointmentActions({ appointment, size = 'sm', onChanged }) {
  const { hasPermission } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();
  const queryClient = useQueryClient();
  const [confirming, setConfirming] = useState(null);
  const [error, setError] = useState(null);

  const run = useMutation({
    mutationFn: ({ action, reason }) => {
      const id = appointment.id;
      switch (action) {
        case 'confirm': return appointmentsApi.confirm(id);
        case 'checkIn': return appointmentsApi.checkIn(id);
        case 'start': return appointmentsApi.start(id);
        case 'complete': return appointmentsApi.complete(id);
        case 'noShow': return appointmentsApi.noShow(id);
        case 'cancel': return appointmentsApi.cancel(id, reason);
        default: throw new Error(`Unknown action ${action}`);
      }
    },
    onSuccess: (updated, { action }) => {
      setConfirming(null);
      if (action === 'start') {
        navigate(`/appointments/${updated.id}/consult`);
        return;
      }
      queryClient.invalidateQueries({ queryKey: ['appointments'] });
      queryClient.invalidateQueries({ queryKey: ['appointment-calendar'] });
      toast.success(`${updated.appointmentNo}: ${humanise(updated.status)}.`);
      onChanged?.(updated);
    },
    onError: (err) => {
      const message = toApiError(err).message;
      if (confirming) setError(message);
      else toast.error(message);
    },
  });

  const actions = appointment.allowedActions.filter((a) => hasPermission(ACTION_PERMISSION[a]));
  if (actions.length === 0) return null;

  const primary = ['checkIn', 'start', 'complete', 'confirm'];
  const needsConfirm = { cancel: true, noShow: true };

  return (
    <div className="flex flex-wrap justify-end gap-1">
      {actions.map((action) => (action === 'complete' ? (
        <LinkButton key={action} size={size} variant="secondary" to={`/appointments/${appointment.id}/consult`} onClick={(e) => e.stopPropagation()}>
          Open consultation
        </LinkButton>
      ) : (
        <Button
          key={action}
          size={size}
          variant={primary.includes(action) ? 'secondary' : 'ghost'}
          icon={action === 'complete' ? CheckCircle2 : undefined}
          loading={run.isPending && run.variables?.action === action}
          onClick={(e) => {
            e.stopPropagation();
            if (action === 'reschedule') navigate(`/appointments/${appointment.id}/reschedule`);
            else if (needsConfirm[action]) { setError(null); setConfirming(action); }
            else run.mutate({ action });
          }}
        >
          {ACTION_LABEL[action]}
        </Button>
      )))}
      {confirming && (
        <ConfirmDialog
          open
          title={confirming === 'cancel' ? `Cancel ${appointment.appointmentNo}?` : 'Mark as no-show?'}
          message={confirming === 'cancel'
            ? `${appointment.patientName}'s appointment with ${appointment.doctorName} will be cancelled and the slot released.`
            : `${appointment.patientName} did not attend. The slot is released and the record is kept for no-show analysis.`}
          confirmLabel={confirming === 'cancel' ? 'Cancel appointment' : 'Mark no-show'}
          requireReason={confirming === 'cancel'}
          reasonLabel="Cancellation reason"
          loading={run.isPending}
          error={error}
          onClose={() => setConfirming(null)}
          onConfirm={(reason) => run.mutate({ action: confirming, reason })}
        />
      )}
    </div>
  );
}
