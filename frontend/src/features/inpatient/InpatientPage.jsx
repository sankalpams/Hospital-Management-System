import { useState } from 'react';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import clsx from 'clsx';
import { BedDouble, BedSingle, LogOut, Plus } from 'lucide-react';
import { ADMISSION_TYPES, DISCHARGE_TYPES, GENDER_POLICIES, ROOM_TYPES, WARD_TYPES, inpatientApi } from '../../api/inpatient';
import { departmentsApi } from '../../api/org';
import { doctorsApi } from '../../api/clinic';
import { toApiError } from '../../api/client';
import { useAuth } from '../../auth/AuthProvider';
import { PageHeader } from '../../components/layout/AppShell';
import { DataTable } from '../../components/ui/DataTable';
import { FilterSelect, SearchInput, Toolbar } from '../../components/ui/Toolbar';
import { Badge, StatusBadge, humanise } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Alert } from '../../components/ui/Alert';
import { ConfirmDialog, Modal } from '../../components/ui/Modal';
import { SelectField, TextAreaField, TextField } from '../../components/ui/Field';
import { Spinner } from '../../components/ui/Spinner';
import { useToast } from '../../components/ui/Toast';
import { formatDateTime, formatLKR } from '../../lib/format';
import { focusFirstInvalid, requireFields, splitApiError } from '../../lib/forms';
import { useListParams } from '../../lib/useListParams';

const BED_TONE = { AVAILABLE: 'success', OCCUPIED: 'primary', CLEANING: 'warning', MAINTENANCE: 'neutral' };

function useInvalidate() {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: ['inpatient'] });
}

export default function InpatientPage() {
  const { hasPermission } = useAuth();
  const [params, setParams] = useListParams({ tab: 'board', status: 'REQUESTED,ADMITTED' });
  const tabs = [
    { id: 'board', label: 'Bed board', show: true },
    { id: 'admissions', label: 'Admissions', show: hasPermission('admission:read') },
    { id: 'setup', label: 'Ward setup', show: hasPermission('ward:manage') },
  ].filter((t) => t.show);
  const tab = tabs.some((t) => t.id === params.tab) ? params.tab : 'board';
  return (
    <>
      <PageHeader title="Inpatients" description="Bed occupancy, admissions, transfers and discharges." />
      <div role="tablist" aria-label="Inpatients" className="mb-4 flex flex-wrap gap-1 rounded-md border border-border bg-surface p-1">
        {tabs.map((t) => (
          <button key={t.id} type="button" role="tab" aria-selected={tab === t.id} onClick={() => setParams({ tab: t.id, q: '' })}
                  className={clsx('rounded px-3 py-1.5 text-sm font-medium', tab === t.id ? 'bg-primary text-primary-foreground' : 'text-foreground-muted hover:text-foreground')}>
            {t.label}
          </button>
        ))}
      </div>
      {tab === 'board' && <BedBoard />}
      {tab === 'admissions' && <AdmissionsTab params={params} setParams={setParams} />}
      {tab === 'setup' && <WardSetup />}
    </>
  );
}

// ---------------------------------------------------------------- bed board

function useWards() {
  return useQuery({ queryKey: ['inpatient', 'wards'], queryFn: inpatientApi.wards, refetchInterval: 60_000 });
}

function BedBoard() {
  const { hasPermission } = useAuth();
  const wards = useWards();
  const [bed, setBed] = useState(null);
  if (wards.isLoading) return <Spinner label="Loading bed board" />;
  if (wards.isError) return <Alert tone="danger">{toApiError(wards.error).message}</Alert>;
  if (wards.data.length === 0) {
    return (
      <div className="rounded-lg border border-border bg-surface px-4 py-12 text-center">
        <BedDouble className="mx-auto h-8 w-8 text-foreground-subtle" aria-hidden="true" />
        <p className="mt-2 font-medium">No wards set up yet</p>
        <p className="text-sm text-foreground-muted">{hasPermission('ward:manage') ? 'Add wards, rooms and beds under Ward setup.' : 'An administrator needs to add wards first.'}</p>
      </div>
    );
  }
  return (
    <div className="flex flex-col gap-4">
      {wards.data.map((w) => (
        <section key={w.id} aria-labelledby={`ward-${w.id}`} className="rounded-lg border border-border bg-surface shadow-sm">
          <header className="flex flex-wrap items-baseline justify-between gap-2 border-b border-border px-4 py-3">
            <div>
              <h2 id={`ward-${w.id}`} className="text-base">{w.name}</h2>
              <p className="text-xs text-foreground-muted">{w.departmentName} · {humanise(w.wardType)}{w.floor && ` · Floor ${w.floor}`} · {humanise(w.genderPolicy)}</p>
            </div>
            <p className="tabular text-sm text-foreground-muted">
              <span className="font-semibold text-foreground">{w.occupied}</span> of {w.totalBeds} occupied · {w.available} available
            </p>
          </header>
          {w.rooms.length === 0 && <p className="px-4 py-4 text-sm text-foreground-muted">No rooms in this ward yet.</p>}
          <div className="divide-y divide-border">
            {w.rooms.map((r) => (
              <div key={r.id} className="flex flex-col gap-2 px-4 py-3 sm:flex-row sm:items-start">
                <p className="w-32 shrink-0 text-sm"><span className="font-medium">Room {r.roomNo}</span><br />
                  <span className="text-xs text-foreground-muted">{humanise(r.roomType)}<br />{formatLKR(r.dailyRate)}/day</span></p>
                <ul className="grid flex-1 grid-cols-[repeat(auto-fill,minmax(9.5rem,1fr))] gap-2">
                  {r.beds.map((b) => (
                    <li key={b.id}>
                      <button type="button" onClick={() => setBed({ ...b, wardName: w.name, roomNo: r.roomNo })}
                              className="flex h-full w-full flex-col items-start gap-1 rounded-md border border-border px-3 py-2 text-left hover:border-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">
                        <span className="inline-flex items-center gap-1.5 whitespace-nowrap text-sm font-medium"><BedSingle className="h-4 w-4 text-foreground-subtle" aria-hidden="true" />{r.roomNo}-{b.bedNo}</span>
                        <Badge tone={BED_TONE[b.status]}>{humanise(b.status)}</Badge>
                        {b.patientName
                          ? <span className="w-full min-w-0 text-xs"><span className="block truncate font-medium">{b.patientName}</span><span className="font-mono text-foreground-subtle">{b.patientMrn}</span></span>
                          : <span className="text-xs text-foreground-subtle">No patient</span>}
                      </button>
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </div>
        </section>
      ))}
      {bed && <BedDialog bed={bed} onClose={() => setBed(null)} />}
    </div>
  );
}

function BedDialog({ bed, onClose }) {
  const { hasPermission } = useAuth();
  const toast = useToast();
  const invalidate = useInvalidate();
  const [admitting, setAdmitting] = useState(false);
  const [error, setError] = useState(null);
  const status = useMutation({
    mutationFn: (s) => inpatientApi.setBedStatus(bed.id, s),
    onSuccess: (_, s) => { invalidate(); toast.success(`Bed ${bed.roomNo}-${bed.bedNo} marked ${humanise(s).toLowerCase()}.`); onClose(); },
    onError: (err) => setError(toApiError(err).message),
  });
  const canSetStatus = hasPermission(['ward:manage', 'admission:manage']) && bed.status !== 'OCCUPIED';
  if (admitting) return <AdmitDialog bed={bed} onClose={onClose} />;
  return (
    <Modal open onClose={onClose} title={`Bed ${bed.roomNo}-${bed.bedNo}`} description={`${bed.wardName} · ${humanise(bed.status)}`} size="sm"
           footer={<Button variant="ghost" onClick={onClose}>Close</Button>}>
      <div className="flex flex-col gap-3 text-sm">
        {error && <Alert tone="danger">{error}</Alert>}
        {bed.patientName && (
          <p>Occupied by <Link to={`/patients/${bed.patientId}`} className="font-medium text-primary hover:underline">{bed.patientName}</Link>{' '}
            <span className="font-mono text-xs text-foreground-subtle">{bed.patientMrn}</span>. Transfer or discharge from the Admissions tab.</p>
        )}
        {bed.status === 'AVAILABLE' && hasPermission('admission:manage') && (
          <Button icon={BedSingle} onClick={() => setAdmitting(true)}>Admit a waiting patient here</Button>
        )}
        {canSetStatus && (
          <div className="flex flex-wrap gap-2">
            {['AVAILABLE', 'CLEANING', 'MAINTENANCE'].filter((s) => s !== bed.status).map((s) => (
              <Button key={s} variant="secondary" size="sm" loading={status.isPending && status.variables === s} onClick={() => status.mutate(s)}>
                Mark {humanise(s).toLowerCase()}
              </Button>
            ))}
          </div>
        )}
      </div>
    </Modal>
  );
}

/** Picks a REQUESTED admission for a fixed bed. */
function AdmitDialog({ bed, onClose }) {
  const toast = useToast();
  const invalidate = useInvalidate();
  const [admissionId, setAdmissionId] = useState('');
  const [error, setError] = useState(null);
  const waiting = useQuery({ queryKey: ['inpatient', 'admissions', 'waiting'], queryFn: () => inpatientApi.admissions({ status: 'REQUESTED', size: 100 }) });
  const save = useMutation({
    mutationFn: () => inpatientApi.admit(admissionId, bed.id),
    onSuccess: (a) => { invalidate(); toast.success(`${a.patientName} admitted to ${a.currentBed}.`); onClose(); },
    onError: (err) => setError(toApiError(err).message),
  });
  const list = waiting.data?.content ?? [];
  return (
    <Modal open onClose={onClose} title={`Admit to bed ${bed.roomNo}-${bed.bedNo}`} description={bed.wardName}
           footer={<><Button variant="ghost" onClick={onClose}>Cancel</Button><Button disabled={!admissionId} loading={save.isPending} onClick={() => save.mutate()}>Admit</Button></>}>
      <div className="flex flex-col gap-3">
        {error && <Alert tone="danger">{error}</Alert>}
        {waiting.isLoading && <Spinner label="Loading requests" />}
        {waiting.data && list.length === 0 && <p className="text-sm text-foreground-muted">No admission requests are waiting. A doctor or the front desk requests admission from the patient's record.</p>}
        {list.length > 0 && (
          <SelectField label="Admission request" required data-autofocus placeholder="Choose a patient" value={admissionId} onChange={(e) => setAdmissionId(e.target.value)}
                       options={list.map((a) => ({ value: String(a.id), label: `${a.patientName} (${a.patientMrn}) · ${humanise(a.admissionType)} · ${a.doctorName}` }))} />
        )}
      </div>
    </Modal>
  );
}

/** Picks an AVAILABLE bed for a fixed admission (admit or transfer). */
function BedPickerDialog({ admission, mode, onClose }) {
  const toast = useToast();
  const invalidate = useInvalidate();
  const wards = useWards();
  const [bedId, setBedId] = useState('');
  const [error, setError] = useState(null);
  const save = useMutation({
    mutationFn: () => (mode === 'transfer' ? inpatientApi.transfer(admission.id, bedId) : inpatientApi.admit(admission.id, bedId)),
    onSuccess: (a) => { invalidate(); toast.success(`${a.patientName} ${mode === 'transfer' ? 'moved' : 'admitted'} to ${a.currentBed}.`); onClose(); },
    onError: (err) => setError(toApiError(err).message),
  });
  const options = (wards.data ?? []).flatMap((w) => w.rooms.flatMap((r) => r.beds.filter((b) => b.status === 'AVAILABLE').map((b) => ({
    value: String(b.id), label: `${w.name} · ${r.roomNo}-${b.bedNo} · ${humanise(r.roomType)} · ${formatLKR(r.dailyRate)}/day${w.genderPolicy !== 'MIXED' ? ` · ${humanise(w.genderPolicy)} only` : ''}`,
  }))));
  return (
    <Modal open onClose={onClose} title={mode === 'transfer' ? `Transfer ${admission.patientName}` : `Admit ${admission.patientName}`}
           description={mode === 'transfer' ? `Now in ${admission.currentBed}. The old bed goes to cleaning.` : `${admission.admissionNo} · ${admission.reason}`}
           footer={<><Button variant="ghost" onClick={onClose}>Cancel</Button><Button disabled={!bedId} loading={save.isPending} onClick={() => save.mutate()}>{mode === 'transfer' ? 'Transfer' : 'Admit'}</Button></>}>
      <div className="flex flex-col gap-3">
        {error && <Alert tone="danger">{error}</Alert>}
        {wards.isLoading && <Spinner label="Loading beds" />}
        {wards.data && options.length === 0 && <Alert tone="warning">No bed is available right now. Beds being cleaned can be released from the bed board.</Alert>}
        {options.length > 0 && (
          <SelectField label="Available bed" required data-autofocus placeholder="Choose a bed" value={bedId} onChange={(e) => setBedId(e.target.value)} options={options}
                       hint={`Patient is ${humanise(admission.patientGender).toLowerCase()}.`} />
        )}
      </div>
    </Modal>
  );
}

// ---------------------------------------------------------------- admissions

const STATUS_FILTERS = [
  { value: 'REQUESTED,ADMITTED', label: 'Open' },
  { value: 'REQUESTED', label: 'Requested' },
  { value: 'ADMITTED', label: 'Admitted' },
  { value: 'DISCHARGED', label: 'Discharged' },
  { value: 'CANCELLED', label: 'Cancelled' },
  { value: '', label: 'All' },
];

function AdmissionsTab({ params, setParams }) {
  const { hasPermission } = useAuth();
  const toast = useToast();
  const invalidate = useInvalidate();
  const [picking, setPicking] = useState(null);
  const [discharging, setDischarging] = useState(null);
  const [cancelling, setCancelling] = useState(null);
  const list = useQuery({
    queryKey: ['inpatient', 'admissions', params.status, params.q, params.page],
    queryFn: () => inpatientApi.admissions({ status: params.status, q: params.q, page: params.page }),
    placeholderData: keepPreviousData,
  });
  const cancel = useMutation({
    mutationFn: (reason) => inpatientApi.cancel(cancelling.id, reason),
    onSuccess: () => { invalidate(); toast.success('Admission request cancelled.'); setCancelling(null); },
  });
  const manage = hasPermission('admission:manage');
  const columns = [
    { key: 'patient', header: 'Patient', render: (a) => (
      <div><Link to={`/patients/${a.patientId}`} onClick={(e) => e.stopPropagation()} className="font-medium hover:text-primary hover:underline">{a.patientName}</Link>
        <p className="font-mono text-xs text-foreground-subtle">{a.patientMrn}</p><p className="font-mono text-xs text-foreground-subtle">{a.admissionNo}</p></div>
    ) },
    { key: 'reason', header: 'Reason', render: (a) => <div className="max-w-xs"><p className="truncate">{a.reason}</p><p className="text-xs text-foreground-muted">{humanise(a.admissionType)} · {a.doctorName}</p></div> },
    { key: 'bed', header: 'Bed', render: (a) => (a.currentBed ? <span className="whitespace-nowrap">{a.currentBed}</span> : <span className="text-foreground-subtle">—</span>) },
    { key: 'when', header: 'Since', render: (a) => <span className="tabular whitespace-nowrap text-xs">{formatDateTime(a.dischargedAt ?? a.admittedAt ?? a.requestedAt)}</span> },
    { key: 'status', header: 'Status', render: (a) => <StatusBadge status={a.status} /> },
    { key: 'actions', header: <span className="sr-only">Actions</span>, align: 'right', render: (a) => manage && (
      <div className="flex justify-end gap-1" onClick={(e) => e.stopPropagation()}>
        {a.status === 'REQUESTED' && <>
          <Button size="sm" onClick={() => setPicking({ admission: a, mode: 'admit' })}>Admit</Button>
          <Button size="sm" variant="ghost" onClick={() => setCancelling(a)}>Cancel</Button>
        </>}
        {a.status === 'ADMITTED' && <>
          <Button size="sm" variant="secondary" onClick={() => setPicking({ admission: a, mode: 'transfer' })}>Transfer</Button>
          <Button size="sm" variant="secondary" icon={LogOut} onClick={() => setDischarging(a)}>Discharge</Button>
        </>}
      </div>
    ) },
  ];
  return (
    <>
      <Toolbar>
        <SearchInput value={params.q} onChange={(q) => setParams({ q })} placeholder="Name, MRN or admission no." label="Search admissions" />
        <FilterSelect label="Status" value={params.status ?? ''} onChange={(status) => setParams({ status })} options={STATUS_FILTERS} />
      </Toolbar>
      <DataTable caption="Admissions" columns={columns} page={list.data} loading={list.isFetching}
                 error={list.isError ? toApiError(list.error).message : null}
                 onPageChange={(page) => setParams({ page }, { resetPage: false })}
                 emptyTitle="No admissions match" />
      {picking && <BedPickerDialog {...picking} onClose={() => setPicking(null)} />}
      {discharging && <DischargeDialog admission={discharging} onClose={() => setDischarging(null)} />}
      <ConfirmDialog open={!!cancelling} onClose={() => setCancelling(null)} onConfirm={(r) => cancel.mutate(r)} loading={cancel.isPending}
                     error={cancel.isError ? toApiError(cancel.error).message : null} requireReason title="Cancel admission request"
                     message={cancelling ? `${cancelling.patientName} will no longer be waiting for a bed.` : ''} confirmLabel="Cancel request" />
    </>
  );
}

function DischargeDialog({ admission, onClose }) {
  const toast = useToast();
  const invalidate = useInvalidate();
  const [form, setForm] = useState({ dischargeType: 'ROUTINE', summary: '' });
  const [errors, setErrors] = useState({});
  const [general, setGeneral] = useState(null);
  const save = useMutation({
    mutationFn: () => inpatientApi.discharge(admission.id, form),
    onSuccess: (a) => { invalidate(); toast.success(`${a.patientName} discharged. Bed charges are ready for billing.`); onClose(); },
    onError: (err) => { const s = splitApiError(err); setErrors(s.fields); setGeneral(s.general); focusFirstInvalid(); },
  });
  const submit = () => {
    const errs = form.summary.trim().length < 10 ? { summary: 'Write a discharge summary (at least 10 characters).' } : {};
    setErrors(errs);
    if (Object.keys(errs).length) focusFirstInvalid(); else save.mutate();
  };
  return (
    <Modal open onClose={onClose} title={`Discharge ${admission.patientName}`} size="lg"
           description={`${admission.admissionNo} · ${admission.currentBed} · admitted ${formatDateTime(admission.admittedAt)}`}
           footer={<><Button variant="ghost" onClick={onClose}>Cancel</Button><Button icon={LogOut} loading={save.isPending} onClick={submit}>Discharge</Button></>}>
      <div className="flex flex-col gap-4">
        {general && <Alert tone="danger">{general.message}</Alert>}
        <SelectField label="Discharge type" required value={form.dischargeType} onChange={(e) => setForm((f) => ({ ...f, dischargeType: e.target.value }))} options={DISCHARGE_TYPES} />
        <TextAreaField label="Discharge summary" required data-autofocus rows={6} value={form.summary} error={errors.summary}
                       hint="Condition at discharge, treatment given, medication and follow-up."
                       onChange={(e) => { setForm((f) => ({ ...f, summary: e.target.value })); setErrors({}); }} />
        <p className="text-xs text-foreground-muted">The bed goes to cleaning and each stay is charged per started day at its room rate.</p>
      </div>
    </Modal>
  );
}

/** Used from the patient record: a doctor or the front desk asks for a bed. */
export function RequestAdmissionDialog({ patient, onClose }) {
  const toast = useToast();
  const invalidate = useInvalidate();
  const doctors = useQuery({ queryKey: ['doctor-options', ''], queryFn: () => doctorsApi.options(), staleTime: 60_000 });
  const [form, setForm] = useState({ attendingDoctorId: '', admissionType: 'EMERGENCY', reason: '' });
  const [errors, setErrors] = useState({});
  const [general, setGeneral] = useState(null);
  const set = (k) => (e) => { setForm((f) => ({ ...f, [k]: e.target.value })); setErrors((x) => ({ ...x, [k]: undefined })); };
  const save = useMutation({
    mutationFn: () => inpatientApi.request({ ...form, patientId: patient.id }),
    onSuccess: (a) => { invalidate(); toast.success(`Admission ${a.admissionNo} requested. The ward will assign a bed.`); onClose(); },
    onError: (err) => { const s = splitApiError(err); setErrors(s.fields); setGeneral(s.general); focusFirstInvalid(); },
  });
  const submit = () => {
    const errs = requireFields(form, { attendingDoctorId: 'Attending doctor', reason: 'Reason' });
    setErrors(errs);
    if (Object.keys(errs).length) focusFirstInvalid(); else save.mutate();
  };
  return (
    <Modal open onClose={onClose} title="Request admission" description={`${patient.fullName} · ${patient.mrn}`}
           footer={<><Button variant="ghost" onClick={onClose}>Cancel</Button><Button icon={BedSingle} loading={save.isPending} onClick={submit}>Request admission</Button></>}>
      <div className="flex flex-col gap-4">
        {general && <Alert tone="danger">{general.message}</Alert>}
        <SelectField label="Attending doctor" required data-autofocus placeholder="Choose a doctor" value={form.attendingDoctorId} onChange={set('attendingDoctorId')} error={errors.attendingDoctorId}
                     options={(doctors.data ?? []).map((d) => ({ value: String(d.id), label: `${d.displayName} · ${d.departmentName}` }))} />
        <SelectField label="Admission type" required value={form.admissionType} onChange={set('admissionType')} options={ADMISSION_TYPES} />
        <TextAreaField label="Reason for admission" required rows={3} maxLength={500} value={form.reason} onChange={set('reason')} error={errors.reason} />
      </div>
    </Modal>
  );
}

// ---------------------------------------------------------------- ward setup

function WardSetup() {
  const wards = useWards();
  const [creating, setCreating] = useState(false);
  const [addingRoom, setAddingRoom] = useState(null);
  return (
    <>
      <Toolbar>
        <p className="text-sm text-foreground-muted">Rooms are added with their beds; occupancy is managed from the bed board.</p>
        <div className="flex-1" />
        <Button icon={Plus} onClick={() => setCreating(true)}>New ward</Button>
      </Toolbar>
      {wards.isLoading && <Spinner label="Loading wards" />}
      {wards.isError && <Alert tone="danger">{toApiError(wards.error).message}</Alert>}
      <div className="flex flex-col gap-3">
        {(wards.data ?? []).map((w) => (
          <section key={w.id} className="rounded-lg border border-border bg-surface px-4 py-3 shadow-sm">
            <div className="flex flex-wrap items-start justify-between gap-2">
              <div>
                <h2 className="text-base">{w.name} <span className="font-mono text-xs font-normal text-foreground-subtle">{w.code}</span></h2>
                <p className="text-xs text-foreground-muted">{w.departmentName} · {humanise(w.wardType)} · {humanise(w.genderPolicy)} · {w.totalBeds} beds</p>
              </div>
              <Button size="sm" variant="secondary" icon={Plus} onClick={() => setAddingRoom(w)}>Add room</Button>
            </div>
            {w.rooms.length > 0 && (
              <ul className="mt-2 flex flex-wrap gap-2 text-xs">
                {w.rooms.map((r) => (
                  <li key={r.id} className="rounded border border-border px-2 py-1 tabular">Room {r.roomNo} · {humanise(r.roomType)} · {r.beds.length} beds · {formatLKR(r.dailyRate)}/day</li>
                ))}
              </ul>
            )}
          </section>
        ))}
      </div>
      {creating && <WardDialog onClose={() => setCreating(false)} />}
      {addingRoom && <RoomDialog ward={addingRoom} onClose={() => setAddingRoom(null)} />}
    </>
  );
}

function WardDialog({ onClose }) {
  const toast = useToast();
  const invalidate = useInvalidate();
  const departments = useQuery({ queryKey: ['department-options'], queryFn: departmentsApi.options, staleTime: 300_000 });
  const [form, setForm] = useState({ code: '', name: '', departmentId: '', wardType: 'GENERAL', genderPolicy: 'MIXED', floor: '' });
  const [errors, setErrors] = useState({});
  const [general, setGeneral] = useState(null);
  const set = (k) => (e) => { setForm((f) => ({ ...f, [k]: e.target.value })); setErrors((x) => ({ ...x, [k]: undefined })); };
  const save = useMutation({
    mutationFn: () => inpatientApi.createWard(form),
    onSuccess: (w) => { invalidate(); toast.success(`${w.name} created. Add its rooms next.`); onClose(); },
    onError: (err) => { const s = splitApiError(err); setErrors(s.fields); setGeneral(s.general); focusFirstInvalid(); },
  });
  const submit = () => {
    const errs = requireFields(form, { code: 'Code', name: 'Name', departmentId: 'Department' });
    setErrors(errs);
    if (Object.keys(errs).length) focusFirstInvalid(); else save.mutate();
  };
  return (
    <Modal open onClose={onClose} title="New ward" size="lg"
           footer={<><Button variant="ghost" onClick={onClose}>Cancel</Button><Button loading={save.isPending} onClick={submit}>Create ward</Button></>}>
      <div className="grid gap-4 sm:grid-cols-2">
        {general && <Alert tone="danger" className="sm:col-span-2">{general.message}</Alert>}
        <TextField label="Name" required data-autofocus value={form.name} onChange={set('name')} error={errors.name} placeholder="e.g. Medical Ward A" />
        <TextField label="Code" required value={form.code} onChange={set('code')} error={errors.code} inputClassName="font-mono uppercase" placeholder="e.g. MWA" />
        <SelectField label="Department" required placeholder="Choose a department" value={form.departmentId} onChange={set('departmentId')} error={errors.departmentId}
                     options={(departments.data ?? []).map((d) => ({ value: String(d.id), label: d.name }))} />
        <TextField label="Floor" value={form.floor} onChange={set('floor')} error={errors.floor} />
        <SelectField label="Ward type" required value={form.wardType} onChange={set('wardType')} options={WARD_TYPES} />
        <SelectField label="Gender policy" required value={form.genderPolicy} onChange={set('genderPolicy')} options={GENDER_POLICIES} />
      </div>
    </Modal>
  );
}

function RoomDialog({ ward, onClose }) {
  const toast = useToast();
  const invalidate = useInvalidate();
  const [form, setForm] = useState({ roomNo: '', roomType: 'SHARED', dailyRate: '', beds: '4' });
  const [errors, setErrors] = useState({});
  const [general, setGeneral] = useState(null);
  const set = (k) => (e) => { setForm((f) => ({ ...f, [k]: e.target.value })); setErrors((x) => ({ ...x, [k]: undefined })); };
  const save = useMutation({
    mutationFn: () => inpatientApi.addRoom(ward.id, { ...form, beds: Number(form.beds) }),
    onSuccess: () => { invalidate(); toast.success(`Room ${form.roomNo.toUpperCase()} added with ${form.beds} beds.`); onClose(); },
    onError: (err) => { const s = splitApiError(err); setErrors(s.fields); setGeneral(s.general); focusFirstInvalid(); },
  });
  const submit = () => {
    const errs = requireFields(form, { roomNo: 'Room number', dailyRate: 'Daily rate', beds: 'Beds' });
    setErrors(errs);
    if (Object.keys(errs).length) focusFirstInvalid(); else save.mutate();
  };
  return (
    <Modal open onClose={onClose} title={`Add room to ${ward.name}`} description="Beds are numbered A, B, C and start available."
           footer={<><Button variant="ghost" onClick={onClose}>Cancel</Button><Button loading={save.isPending} onClick={submit}>Add room</Button></>}>
      <div className="grid gap-4 sm:grid-cols-2">
        {general && <Alert tone="danger" className="sm:col-span-2">{general.message}</Alert>}
        <TextField label="Room number" required data-autofocus value={form.roomNo} onChange={set('roomNo')} error={errors.roomNo} inputClassName="uppercase" />
        <SelectField label="Room type" required value={form.roomType} onChange={set('roomType')} options={ROOM_TYPES} />
        <TextField label="Daily rate (LKR)" required inputMode="decimal" value={form.dailyRate} onChange={set('dailyRate')} error={errors.dailyRate} inputClassName="tabular" />
        <TextField label="Beds" required type="number" min={1} max={20} value={form.beds} onChange={set('beds')} error={errors.beds} inputClassName="tabular" />
      </div>
    </Modal>
  );
}
