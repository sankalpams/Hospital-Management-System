import { useMemo, useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import clsx from 'clsx';
import { CalendarDays, CalendarPlus, ChevronLeft, ChevronRight, DoorOpen, List } from 'lucide-react';
import { appointmentsApi, doctorsApi } from '../../api/clinic';
import { departmentsApi } from '../../api/org';
import { toApiError } from '../../api/client';
import { useAuth } from '../../auth/AuthProvider';
import { PageHeader } from '../../components/layout/AppShell';
import { Alert } from '../../components/ui/Alert';
import { DataTable } from '../../components/ui/DataTable';
import { FilterSelect, SearchInput, Toolbar } from '../../components/ui/Toolbar';
import { Badge, StatusBadge, humanise } from '../../components/ui/Badge';
import { Button, LinkButton } from '../../components/ui/Button';
import { CheckboxField, controlClass } from '../../components/ui/Field';
import { addDays, formatDate, todayInHospital } from '../../lib/format';
import { useListParams } from '../../lib/useListParams';
import { AppointmentActions } from './shared';
import { WalkInDialog } from './WalkInDialog';

const STATUS_OPTIONS = ['PENDING', 'CONFIRMED', 'CHECKED_IN', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'NO_SHOW']
  .map((s) => ({ value: s, label: humanise(s) }));

export default function AppointmentsPage() {
  const { hasPermission, user } = useAuth();
  // "my patients" needs a doctor profile linked to this login (doctorId from /auth/me)
  const isDoctor = user?.doctorId != null;
  const unlinkedDoctor = !isDoctor && user?.roles?.includes('DOCTOR');
  const [params, setParams] = useListParams({ view: 'day', date: todayInHospital(), size: 50, mine: isDoctor ? 'true' : '' });
  const [walkIn, setWalkIn] = useState(false);
  const date = params.date ?? todayInHospital();

  const departments = useQuery({ queryKey: ['department-options'], queryFn: departmentsApi.options, staleTime: 300_000 });
  const doctors = useQuery({ queryKey: ['doctor-options', params.departmentId ?? ''], queryFn: () => doctorsApi.options(params.departmentId), staleTime: 60_000 });

  const filters = (
    <Toolbar>
      {params.view === 'day' && <SearchInput value={params.q} onChange={(q) => setParams({ q })} placeholder="Patient name, MRN or APT no." label="Search appointments" />}
      {params.mine !== 'true' && (
        <>
          <FilterSelect label="Department" value={params.departmentId ?? ''} onChange={(departmentId) => setParams({ departmentId, doctorId: '' })}
                        options={[{ value: '', label: 'All' }, ...(departments.data ?? []).map((d) => ({ value: String(d.id), label: d.name }))]} />
          <FilterSelect label="Doctor" value={params.doctorId ?? ''} onChange={(doctorId) => setParams({ doctorId })}
                        options={[{ value: '', label: 'All' }, ...(doctors.data ?? []).map((d) => ({ value: String(d.id), label: d.displayName }))]} />
        </>
      )}
      {params.view === 'day' && (
        <FilterSelect label="Status" value={params.status ?? ''} onChange={(status) => setParams({ status })}
                      options={[{ value: '', label: 'Any' }, { value: 'PENDING,CONFIRMED,CHECKED_IN,IN_PROGRESS', label: 'Open' }, ...STATUS_OPTIONS]} />
      )}
      {isDoctor && <CheckboxField label="Only my patients" checked={params.mine === 'true'} onChange={(e) => setParams({ mine: e.target.checked ? 'true' : 'false' })} />}
    </Toolbar>
  );

  return (
    <>
      <PageHeader
        title="Appointments"
        description="Book, check in and track outpatient appointments."
        actions={(
          <>
            {hasPermission(['appointment:create']) && hasPermission('appointment:checkin') && (
              <Button variant="secondary" icon={DoorOpen} onClick={() => setWalkIn(true)}>Walk-in</Button>
            )}
            {hasPermission('appointment:create') && <LinkButton to="/appointments/new" icon={CalendarPlus}>Book appointment</LinkButton>}
          </>
        )}
      />
      {unlinkedDoctor && (
        <Alert tone="info" className="mb-4">
          Your login is not linked to a doctor profile yet, so you see all appointments and cannot start consultations. Ask an administrator to link it on the Doctors page.
        </Alert>
      )}
      <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div role="tablist" aria-label="View" className="inline-flex rounded-md border border-border bg-surface p-1">
          {[{ id: 'day', label: 'Day', Icon: List }, { id: 'month', label: 'Month', Icon: CalendarDays }].map(({ id, label, Icon }) => (
            <button key={id} type="button" role="tab" aria-selected={params.view === id} onClick={() => setParams({ view: id })}
                    className={clsx('inline-flex items-center gap-1.5 rounded px-3 py-1.5 text-sm font-medium', params.view === id ? 'bg-primary text-primary-foreground' : 'text-foreground-muted hover:text-foreground')}>
              <Icon className="h-4 w-4" aria-hidden="true" /> {label}
            </button>
          ))}
        </div>
        <DateNavigator view={params.view} date={date} onChange={(d) => setParams({ date: d })} />
      </div>
      {filters}
      {params.view === 'month'
        ? <MonthView date={date} params={params} onPickDay={(d) => setParams({ view: 'day', date: d })} />
        : <DayView date={date} params={params} setParams={setParams} />}
      {walkIn && <WalkInDialog onClose={() => setWalkIn(false)} />}
    </>
  );
}

function DateNavigator({ view, date, onChange }) {
  const step = (dir) => {
    if (view === 'day') return onChange(addDays(date, dir));
    const [y, m] = date.split('-').map(Number);
    const next = new Date(Date.UTC(y, m - 1 + dir, 1));
    return onChange(next.toISOString().slice(0, 10));
  };
  const label = view === 'day'
    ? new Intl.DateTimeFormat('en-GB', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' }).format(new Date(`${date}T00:00:00Z`))
    : new Intl.DateTimeFormat('en-GB', { month: 'long', year: 'numeric', timeZone: 'UTC' }).format(new Date(`${date}T00:00:00Z`));
  return (
    <div className="flex flex-wrap items-center gap-2">
      <Button variant="ghost" size="sm" onClick={() => step(-1)} aria-label={view === 'day' ? 'Previous day' : 'Previous month'}><ChevronLeft className="h-4 w-4" aria-hidden="true" /></Button>
      <p className="min-w-[12rem] text-center text-sm font-semibold" aria-live="polite">{label}</p>
      <Button variant="ghost" size="sm" onClick={() => step(1)} aria-label={view === 'day' ? 'Next day' : 'Next month'}><ChevronRight className="h-4 w-4" aria-hidden="true" /></Button>
      <Button variant="secondary" size="sm" onClick={() => onChange(todayInHospital())}>Today</Button>
      <label className="sr-only" htmlFor="appt-date">Go to date</label>
      <input id="appt-date" type="date" value={date} onChange={(e) => e.target.value && onChange(e.target.value)} className={controlClass(false, 'h-8 w-auto text-sm')} />
    </div>
  );
}

function DayView({ date, params, setParams }) {
  const query = useQuery({
    queryKey: ['appointments', 'day', date, params],
    queryFn: () => appointmentsApi.search({
      from: date, to: date, doctorId: params.doctorId, departmentId: params.departmentId, status: params.status,
      q: params.q, mine: params.mine === 'true', page: params.page, size: params.size,
    }),
    placeholderData: keepPreviousData,
    refetchInterval: 60_000,   // front-desk queue view stays current
  });

  const columns = [
    { key: 'time', header: 'Time', render: (a) => (
      <div className="tabular"><p className="font-medium">{a.localTime.slice(0, 5)}</p><p className="text-xs text-foreground-subtle">Token {a.tokenNo}</p></div>
    ) },
    { key: 'patient', header: 'Patient', render: (a) => (
      <div>
        <Link to={`/patients/${a.patientId}`} onClick={(e) => e.stopPropagation()} className="font-medium hover:text-primary hover:underline">{a.patientName}</Link>
        <p className="font-mono text-xs text-foreground-subtle">{a.patientMrn}</p>
      </div>
    ) },
    { key: 'doctor', header: 'Doctor', render: (a) => (
      <div><p>{a.doctorName}</p><p className="text-xs text-foreground-subtle">{a.departmentName}</p></div>
    ) },
    { key: 'status', header: 'Status', render: (a) => (
      <div className="flex flex-wrap gap-1">
        <StatusBadge status={a.status} />
        {a.type === 'WALK_IN' && <Badge tone="neutral">Walk-in</Badge>}
        {a.type === 'FOLLOW_UP' && <Badge tone="neutral">Follow-up</Badge>}
      </div>
    ) },
    { key: 'actions', header: <span className="sr-only">Actions</span>, align: 'right', render: (a) => <AppointmentActions appointment={a} /> },
  ];

  return (
    <DataTable
      caption={`Appointments on ${formatDate(date)}`}
      rowKey="id"
      columns={columns}
      page={query.data}
      loading={query.isFetching}
      error={query.isError ? toApiError(query.error).message : null}
      onPageChange={(page) => setParams({ page }, { resetPage: false })}
      emptyTitle="No appointments on this day"
      emptyMessage="Change the date or filters, or book a new appointment."
    />
  );
}

function MonthView({ date, params, onPickDay }) {
  const [y, m] = date.split('-').map(Number);
  const first = `${y}-${String(m).padStart(2, '0')}-01`;
  const daysInMonth = new Date(Date.UTC(y, m, 0)).getUTCDate();
  const last = `${y}-${String(m).padStart(2, '0')}-${String(daysInMonth).padStart(2, '0')}`;
  const offset = (new Date(`${first}T00:00:00Z`).getUTCDay() + 6) % 7;   // Monday-first grid
  const today = todayInHospital();

  const query = useQuery({
    queryKey: ['appointment-calendar', first, params.doctorId, params.departmentId],
    queryFn: () => appointmentsApi.calendar({ from: first, to: last, doctorId: params.doctorId, departmentId: params.departmentId }),
  });
  const byDay = useMemo(() => Object.fromEntries((query.data ?? []).map((d) => [d.date, d])), [query.data]);

  if (query.isError) return <p role="alert" className="text-sm text-danger">{toApiError(query.error).message}</p>;
  return (
    <div className="overflow-hidden rounded-lg border border-border bg-surface shadow-sm">
      <div className="grid grid-cols-7 border-b border-border bg-surface-muted text-center text-xs font-medium text-foreground-muted">
        {['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'].map((d) => <div key={d} className="py-2">{d}</div>)}
      </div>
      <div className="grid grid-cols-7">
        {[...Array(offset)].map((_, i) => <div key={`pad-${i}`} className="min-h-[5.5rem] border-b border-r border-border bg-surface-muted" />)}
        {[...Array(daysInMonth)].map((_, i) => {
          const day = `${y}-${String(m).padStart(2, '0')}-${String(i + 1).padStart(2, '0')}`;
          const info = byDay[day];
          const open = info ? (info.byStatus.PENDING ?? 0) + (info.byStatus.CONFIRMED ?? 0) + (info.byStatus.CHECKED_IN ?? 0) + (info.byStatus.IN_PROGRESS ?? 0) : 0;
          const cancelled = info ? (info.byStatus.CANCELLED ?? 0) + (info.byStatus.NO_SHOW ?? 0) : 0;
          return (
            <button key={day} type="button" onClick={() => onPickDay(day)}
                    aria-label={`${formatDate(day)}: ${info ? `${info.total} appointments` : 'no appointments'}`}
                    className={clsx('flex min-h-[5.5rem] flex-col items-start gap-1 border-b border-r border-border p-2 text-left hover:bg-info-soft', day === today && 'bg-info-soft')}>
              <span className={clsx('text-sm tabular', day === today ? 'font-bold text-primary' : 'text-foreground')}>{i + 1}</span>
              {info && (
                <span className="flex flex-col gap-0.5 text-[11px] leading-tight sm:text-xs">
                  <span className="font-semibold text-foreground">{info.total} total</span>
                  {open > 0 && <span className="text-info">{open} open</span>}
                  {(info.byStatus.COMPLETED ?? 0) > 0 && <span className="text-success">{info.byStatus.COMPLETED} done</span>}
                  {cancelled > 0 && <span className="text-danger">{cancelled} cancelled/no-show</span>}
                </span>
              )}
            </button>
          );
        })}
      </div>
      {query.isFetching && <p className="px-4 py-2 text-xs text-foreground-subtle">Updating…</p>}
    </div>
  );
}
