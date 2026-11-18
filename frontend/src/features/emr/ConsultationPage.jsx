import { useEffect, useId, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, CheckCircle2, Plus, Trash2 } from 'lucide-react';
import { appointmentsApi, emrApi } from '../../api/clinic';
import { toApiError } from '../../api/client';
import { Card, CardBody, CardHeader } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Alert } from '../../components/ui/Alert';
import { Badge } from '../../components/ui/Badge';
import { ConfirmDialog } from '../../components/ui/Modal';
import { SelectField, TextAreaField, TextField, controlClass } from '../../components/ui/Field';
import { Spinner } from '../../components/ui/Spinner';
import { useToast } from '../../components/ui/Toast';
import { focusFirstInvalid, splitApiError } from '../../lib/forms';
import { useAuth } from '../../auth/AuthProvider';
import { OrderLabCard, PatientLabPanel } from '../lab/LabPages';
import { AllergyBanner, NoteItem, PrescriptionList, VitalsDialog, VitalsTable, emrKey } from './parts';

/** The doctor's workspace for one consultation: everything writes to the open encounter. */
export default function ConsultationPage() {
  const { id } = useParams();
  const { hasPermission } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();
  const queryClient = useQueryClient();
  const appt = useQuery({ queryKey: ['appointment', id], queryFn: () => appointmentsApi.get(id) });
  const patientId = appt.data?.patientId;
  const emr = useQuery({ queryKey: emrKey(patientId), queryFn: () => emrApi.record(patientId), enabled: Boolean(patientId) });
  const [vitalsOpen, setVitalsOpen] = useState(false);
  const [completeError, setCompleteError] = useState(null);
  const [cancelling, setCancelling] = useState(null);

  const complete = useMutation({
    mutationFn: () => appointmentsApi.complete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['appointments'] });
      toast.success('Consultation completed.');
      navigate('/appointments');
    },
    onError: (err) => setCompleteError(toApiError(err).message),
  });
  const cancelRx = useMutation({
    mutationFn: (p) => emrApi.cancelPrescription(p.id),
    onSuccess: () => { setCancelling(null); queryClient.invalidateQueries({ queryKey: emrKey(patientId) }); toast.success('Prescription cancelled.'); },
  });

  if (appt.isLoading || emr.isLoading) return <Spinner label="Loading consultation" />;
  if (appt.isError || emr.isError) return <Alert tone="danger">{toApiError(appt.error ?? emr.error).message}</Alert>;
  const a = appt.data;
  const encounter = emr.data.encounters.find((e) => e.appointmentId === a.id);
  if (!encounter) return <Alert tone="warning">This consultation has not been started yet.</Alert>;
  const writable = encounter.canWrite;

  return (
    <>
      <Link to="/appointments" className="mb-3 inline-flex items-center gap-1 text-sm text-foreground-muted hover:text-foreground">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Appointments
      </Link>
      <div className="mb-5 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl">{a.patientName}</h1>
          <p className="mt-1 text-sm text-foreground-muted">
            <Link to={`/patients/${a.patientId}`} className="font-mono hover:text-primary hover:underline">{a.patientMrn}</Link>
            {' '}· {a.appointmentNo} · token {a.tokenNo} · <span className="font-mono">{encounter.encounterNo}</span>
            {a.reason ? ` · ${a.reason}` : ''}
          </p>
        </div>
        {writable && (
          <div className="flex flex-col items-end gap-1">
            <Button icon={CheckCircle2} loading={complete.isPending} onClick={() => { setCompleteError(null); complete.mutate(); }}>Complete consultation</Button>
            {completeError && <p role="alert" className="text-xs font-medium text-danger">{completeError}</p>}
          </div>
        )}
      </div>
      {!writable && <Alert tone="info" className="mb-4">This encounter is {encounter.status === 'CLOSED' ? 'closed' : 'not yours to edit'}; it is shown read-only.</Alert>}

      <div className="flex flex-col gap-6">
        <AllergyBanner allergies={emr.data.allergies} patientId={patientId} canWrite={writable} />
        <div className="grid gap-6 xl:grid-cols-5">
          <div className="flex flex-col gap-6 xl:col-span-3">
            <DiagnosisCard encounter={encounter} patientId={patientId} writable={writable} />
            <NotesCard encounter={encounter} patientId={patientId} writable={writable} />
            {writable && hasPermission('lab:request:create') && <OrderLabCard encounterId={encounter.id} />}
            <Card>
              <CardHeader title="Lab results" />
              <CardBody><PatientLabPanel patientId={patientId} /></CardBody>
            </Card>
          </div>
          <div className="flex flex-col gap-6 xl:col-span-2">
            <Card>
              <CardHeader title="Vital signs" action={writable && <Button size="sm" variant="secondary" onClick={() => setVitalsOpen(true)}>Record</Button>} />
              <CardBody><VitalsTable vitals={emr.data.recentVitals.slice(0, 5)} /></CardBody>
            </Card>
            <Card>
              <CardHeader title="Prescriptions" />
              <CardBody className="flex flex-col gap-5">
                <PrescriptionList prescriptions={encounter.prescriptions} onCancel={writable ? setCancelling : undefined} />
                {writable && <PrescriptionForm encounterId={encounter.id} patientId={patientId} />}
              </CardBody>
            </Card>
          </div>
        </div>
      </div>
      {vitalsOpen && <VitalsDialog patientId={patientId} encounterId={encounter.id} onClose={() => setVitalsOpen(false)} />}
      {cancelling && (
        <ConfirmDialog open title={`Cancel ${cancelling.prescriptionNo}?`} message="The pharmacy will no longer be able to dispense it."
                       confirmLabel="Cancel prescription" loading={cancelRx.isPending} onClose={() => setCancelling(null)}
                       onConfirm={() => cancelRx.mutate(cancelling)} />
      )}
    </>
  );
}

function DiagnosisCard({ encounter, patientId, writable }) {
  const queryClient = useQueryClient();
  const empty = { icd10Code: '', description: '', type: encounter.diagnoses.length ? 'SECONDARY' : 'PRIMARY', certainty: 'CONFIRMED' };
  const [form, setForm] = useState(empty);
  const [errors, setErrors] = useState({});
  useEffect(() => { setForm((f) => ({ ...f, type: encounter.diagnoses.length ? 'SECONDARY' : 'PRIMARY' })); }, [encounter.diagnoses.length]);
  const add = useMutation({
    mutationFn: () => emrApi.addDiagnosis(encounter.id, { ...form, icd10Code: form.icd10Code.trim().toUpperCase() }),
    onSuccess: () => { setForm(empty); setErrors({}); queryClient.invalidateQueries({ queryKey: emrKey(patientId) }); },
    onError: (err) => { setErrors(splitApiError(err).fields); focusFirstInvalid(); },
  });
  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));
  return (
    <Card>
      <CardHeader title="Diagnoses" description={writable ? 'At least one is needed to complete the consultation.' : undefined} />
      <CardBody className="flex flex-col gap-4">
        {encounter.diagnoses.length === 0 ? <p className="text-sm text-foreground-muted">None recorded yet.</p> : (
          <ul className="divide-y divide-border text-sm">
            {encounter.diagnoses.map((d) => (
              <li key={d.id} className="flex flex-wrap items-center gap-2 py-2">
                {d.icd10Code && <span className="font-mono text-xs text-foreground-muted">{d.icd10Code}</span>}
                <span className="font-medium">{d.description}</span>
                {d.type === 'PRIMARY' && <Badge tone="primary">Primary</Badge>}
                {d.certainty !== 'CONFIRMED' && <Badge>{d.certainty === 'PROVISIONAL' ? 'Provisional' : 'Ruled out'}</Badge>}
              </li>
            ))}
          </ul>
        )}
        {writable && (
          <form className="grid gap-3 sm:grid-cols-6" onSubmit={(e) => { e.preventDefault(); if (!form.description.trim()) { setErrors({ description: 'Describe the diagnosis.' }); focusFirstInvalid(); } else add.mutate(); }}>
            <TextField className="sm:col-span-1" label="ICD-10" value={form.icd10Code} onChange={set('icd10Code')} error={errors.icd10Code} placeholder="J45.9" inputClassName="font-mono uppercase" />
            <TextField className="sm:col-span-5" label="Diagnosis" required value={form.description} onChange={set('description')} error={errors.description} />
            <SelectField className="sm:col-span-2" label="Type" options={[{ value: 'PRIMARY', label: 'Primary' }, { value: 'SECONDARY', label: 'Secondary' }]} value={form.type} onChange={set('type')} />
            <SelectField className="sm:col-span-2" label="Certainty" options={[{ value: 'CONFIRMED', label: 'Confirmed' }, { value: 'PROVISIONAL', label: 'Provisional' }, { value: 'RULED_OUT', label: 'Ruled out' }]} value={form.certainty} onChange={set('certainty')} />
            <div className="flex items-end sm:col-span-2"><Button type="submit" variant="secondary" icon={Plus} loading={add.isPending} className="w-full">Add diagnosis</Button></div>
          </form>
        )}
      </CardBody>
    </Card>
  );
}

function NotesCard({ encounter, patientId, writable }) {
  const queryClient = useQueryClient();
  const [type, setType] = useState('DOCTOR_NOTE');
  const [content, setContent] = useState('');
  const add = useMutation({
    mutationFn: () => emrApi.addNote(encounter.id, { noteType: type, content }),
    onSuccess: () => { setContent(''); queryClient.invalidateQueries({ queryKey: emrKey(patientId) }); },
  });
  return (
    <Card>
      <CardHeader title="Clinical notes" description="Drafts stay editable until you sign them." />
      <CardBody className="flex flex-col gap-4">
        {encounter.notes.length === 0 ? <p className="text-sm text-foreground-muted">No notes yet.</p>
          : <ul className="divide-y divide-border">{encounter.notes.map((n) => <NoteItem key={n.id} note={n} patientId={patientId} />)}</ul>}
        {writable && (
          <div className="flex flex-col gap-3 border-t border-border pt-4">
            <div className="flex gap-3">
              <SelectField label="Note type" className="w-48" options={[{ value: 'DOCTOR_NOTE', label: 'Doctor note' }, { value: 'TREATMENT', label: 'Treatment plan' }]} value={type} onChange={(e) => setType(e.target.value)} />
            </div>
            <TextAreaField label="Note" rows={5} value={content} onChange={(e) => setContent(e.target.value)} placeholder="History, examination, assessment and plan" />
            {add.isError && <p role="alert" className="text-xs font-medium text-danger">{toApiError(add.error).message}</p>}
            <div><Button variant="secondary" loading={add.isPending} disabled={!content.trim()} onClick={() => add.mutate()}>Save draft</Button></div>
          </div>
        )}
      </CardBody>
    </Card>
  );
}

const ROUTES = ['ORAL', 'IV', 'IM', 'SC', 'TOPICAL', 'INHALED', 'SUBLINGUAL', 'RECTAL', 'OTHER'];
const blankItem = () => ({ key: Math.random(), medicine: null, dose: '', frequency: '', route: 'ORAL', durationDays: '5', quantity: '' });

/** Builds a prescription; an allergy conflict from the server turns into an explicit override step. */
function PrescriptionForm({ encounterId, patientId }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [items, setItems] = useState([blankItem()]);
  const [notes, setNotes] = useState('');
  const [conflict, setConflict] = useState(null);
  const [override, setOverride] = useState('');
  const [error, setError] = useState(null);

  const save = useMutation({
    mutationFn: (overrideReason) => emrApi.prescribe(encounterId, {
      notes: notes || null,
      allergyOverrideReason: overrideReason || null,
      items: items.map((i) => ({ medicineId: i.medicine.id, dose: i.dose, frequency: i.frequency, route: i.route,
        durationDays: Number(i.durationDays), quantity: Number(i.quantity) })),
    }),
    onSuccess: (p) => {
      setItems([blankItem()]); setNotes(''); setConflict(null); setOverride(''); setError(null);
      queryClient.invalidateQueries({ queryKey: emrKey(patientId) });
      toast.success(`${p.prescriptionNo} issued.`);
    },
    onError: (err) => {
      const e = toApiError(err);
      if (e.code === 'ALLERGY_CONFLICT') setConflict(e.message);
      else setError(e.message);
    },
  });

  const update = (key, patch) => setItems((list) => list.map((i) => (i.key === key ? { ...i, ...patch } : i)));
  const complete = items.every((i) => i.medicine && i.dose.trim() && i.frequency.trim() && Number(i.durationDays) > 0 && Number(i.quantity) > 0);

  return (
    <div className="flex flex-col gap-3 border-t border-border pt-4">
      <h3 className="text-sm">New prescription</h3>
      {items.map((i, idx) => (
        <fieldset key={i.key} className="grid gap-2 rounded-md border border-border p-3 sm:grid-cols-6">
          <legend className="sr-only">Medicine {idx + 1}</legend>
          <div className="sm:col-span-6"><MedicinePicker value={i.medicine} onChange={(m) => update(i.key, { medicine: m })} /></div>
          <TextField className="sm:col-span-2" label="Dose" value={i.dose} onChange={(e) => update(i.key, { dose: e.target.value })} placeholder="1 tablet" />
          <TextField className="sm:col-span-2" label="Frequency" value={i.frequency} onChange={(e) => update(i.key, { frequency: e.target.value })} placeholder="TDS" />
          <SelectField className="sm:col-span-2" label="Route" options={ROUTES.map((r) => ({ value: r, label: r.charAt(0) + r.slice(1).toLowerCase() }))} value={i.route} onChange={(e) => update(i.key, { route: e.target.value })} />
          <TextField className="sm:col-span-2" label="Days" type="number" min={1} value={i.durationDays} onChange={(e) => update(i.key, { durationDays: e.target.value })} />
          <TextField className="sm:col-span-2" label={`Quantity${i.medicine ? ` (${i.medicine.unit})` : ''}`} type="number" min={1} value={i.quantity} onChange={(e) => update(i.key, { quantity: e.target.value })} />
          <div className="flex items-end sm:col-span-2">
            {items.length > 1 && <Button variant="ghost" size="sm" icon={Trash2} onClick={() => setItems((l) => l.filter((x) => x.key !== i.key))}>Remove</Button>}
          </div>
        </fieldset>
      ))}
      <div><Button variant="ghost" size="sm" icon={Plus} onClick={() => setItems((l) => [...l, blankItem()])}>Add medicine</Button></div>
      <TextField label="Notes to pharmacy" value={notes} onChange={(e) => setNotes(e.target.value)} />
      {error && <Alert tone="danger">{error}</Alert>}
      {conflict ? (
        <div className="flex flex-col gap-3 rounded-md border border-border bg-danger-soft p-3">
          <p className="text-sm font-medium text-danger">{conflict}</p>
          <TextAreaField label="Clinical reason to override" required rows={2} value={override} onChange={(e) => setOverride(e.target.value)} />
          <div className="flex gap-2">
            <Button variant="danger" disabled={override.trim().length < 10} loading={save.isPending} onClick={() => save.mutate(override.trim())}>Prescribe with override</Button>
            <Button variant="ghost" onClick={() => setConflict(null)}>Change medicines</Button>
          </div>
        </div>
      ) : (
        <div><Button disabled={!complete} loading={save.isPending} onClick={() => save.mutate(null)}>Issue prescription</Button></div>
      )}
    </div>
  );
}

function MedicinePicker({ value, onChange }) {
  const inputId = useId();
  const [q, setQ] = useState('');
  const [term, setTerm] = useState('');
  useEffect(() => { const t = setTimeout(() => setTerm(q.trim()), 250); return () => clearTimeout(t); }, [q]);
  const results = useQuery({ queryKey: ['medicine-options', term], queryFn: () => emrApi.medicineOptions(term), enabled: term.length >= 2 });
  if (value) {
    return (
      <div className="flex items-center justify-between rounded-md bg-primary-soft px-3 py-2 text-sm text-primary-soft-foreground">
        <span className="font-medium">{value.name}</span>
        <button type="button" className="text-xs font-medium underline" onClick={() => onChange(null)}>Change</button>
      </div>
    );
  }
  return (
    <div>
      <label className="text-[13px] font-medium" htmlFor={inputId}>Medicine</label>
      <input id={inputId} className={controlClass(false, 'mt-1.5 h-10')} value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search generic or brand name" aria-autocomplete="list" />
      {results.data && (
        results.data.length === 0 ? <p className="mt-1 text-xs text-foreground-muted">No active medicine matches “{term}”.</p> : (
          <ul className="mt-1 max-h-48 overflow-y-auto rounded-md border border-border bg-surface shadow-sm">
            {results.data.map((m) => (
              <li key={m.id}>
                <button type="button" onClick={() => onChange(m)} className="flex w-full justify-between gap-2 px-3 py-2 text-left text-sm hover:bg-surface-muted">
                  <span>{m.name}</span><span className="text-xs text-foreground-subtle">{m.dosageForm.toLowerCase()}</span>
                </button>
              </li>
            ))}
          </ul>
        )
      )}
    </div>
  );
}
