import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { AlertTriangle, PenLine, ShieldCheck } from 'lucide-react';
import clsx from 'clsx';
import { emrApi } from '../../api/clinic';
import { toApiError } from '../../api/client';
import { useAuth } from '../../auth/AuthProvider';
import { Modal } from '../../components/ui/Modal';
import { Button } from '../../components/ui/Button';
import { Alert } from '../../components/ui/Alert';
import { Badge, humanise } from '../../components/ui/Badge';
import { SelectField, TextAreaField, TextField } from '../../components/ui/Field';
import { useToast } from '../../components/ui/Toast';
import { formatDateTime } from '../../lib/format';
import { focusFirstInvalid, splitApiError } from '../../lib/forms';

export const emrKey = (patientId) => ['emr', String(patientId)];

/** Active allergies stay visible above every clinical view; severe ones in danger tone. */
export function AllergyBanner({ allergies, patientId, canWrite }) {
  const [adding, setAdding] = useState(false);
  const active = allergies.filter((a) => a.status === 'ACTIVE');
  const severe = active.some((a) => a.severity === 'SEVERE');
  return (
    <div className={clsx('flex flex-col gap-2 rounded-lg border px-4 py-3 sm:flex-row sm:items-center sm:justify-between',
      active.length === 0 ? 'border-border bg-surface' : severe ? 'border-border bg-danger-soft' : 'border-border bg-warning-soft')}>
      <div className="flex min-w-0 items-start gap-2.5">
        {active.length === 0
          ? <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-foreground-subtle" aria-hidden="true" />
          : <AlertTriangle className={clsx('mt-0.5 h-4 w-4 shrink-0', severe ? 'text-danger' : 'text-warning')} aria-hidden="true" />}
        <div className="text-sm">
          <p className={clsx('font-semibold', active.length ? (severe ? 'text-danger' : 'text-warning') : 'text-foreground')}>
            {active.length === 0 ? 'No known allergies recorded' : `Allergies (${active.length})`}
          </p>
          {active.length > 0 && (
            <ul className="mt-0.5 flex flex-wrap gap-x-4 gap-y-0.5 text-foreground">
              {active.map((a) => (
                <li key={a.id}>
                  <span className="font-medium">{a.allergen}</span>
                  <span className="text-foreground-muted"> · {humanise(a.allergenType)} · {humanise(a.severity)}{a.reaction ? ` · ${a.reaction}` : ''}</span>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
      {canWrite && <Button size="sm" variant="secondary" onClick={() => setAdding(true)}>Record allergy</Button>}
      {adding && <AllergyDialog patientId={patientId} onClose={() => setAdding(false)} />}
    </div>
  );
}

function AllergyDialog({ patientId, onClose }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [form, setForm] = useState({ allergenType: 'DRUG', allergen: '', reaction: '', severity: 'MODERATE' });
  const [errors, setErrors] = useState({});
  const [general, setGeneral] = useState(null);
  const save = useMutation({
    mutationFn: () => emrApi.addAllergy(patientId, form),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: emrKey(patientId) }); toast.success('Allergy recorded.'); onClose(); },
    onError: (err) => { const s = splitApiError(err); setErrors(s.fields); setGeneral(s.general); },
  });
  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));
  return (
    <Modal open onClose={onClose} title="Record allergy" size="sm"
           footer={<><Button variant="ghost" onClick={onClose}>Cancel</Button>
             <Button loading={save.isPending} onClick={() => { if (!form.allergen.trim()) { setErrors({ allergen: 'Name the allergen.' }); focusFirstInvalid(); } else save.mutate(); }}>Save</Button></>}>
      <div className="grid gap-4 sm:grid-cols-2">
        {general && <Alert tone="danger" className="sm:col-span-2">{general.message}</Alert>}
        <SelectField label="Type" options={['DRUG', 'FOOD', 'ENVIRONMENT', 'OTHER'].map((v) => ({ value: v, label: humanise(v) }))} value={form.allergenType} onChange={set('allergenType')} />
        <SelectField label="Severity" options={['MILD', 'MODERATE', 'SEVERE'].map((v) => ({ value: v, label: humanise(v) }))} value={form.severity} onChange={set('severity')} />
        <TextField className="sm:col-span-2" label="Allergen" required data-autofocus value={form.allergen} onChange={set('allergen')} error={errors.allergen}
                   hint={form.allergenType === 'DRUG' ? 'Use the generic name (e.g. amoxicillin) so prescriptions are checked against it.' : undefined} />
        <TextField className="sm:col-span-2" label="Reaction" value={form.reaction} onChange={set('reaction')} placeholder="e.g. Urticaria, anaphylaxis" />
      </div>
    </Modal>
  );
}

const VITAL_FIELDS = [
  ['temperatureC', 'Temp (°C)', '0.1'], ['pulseBpm', 'Pulse (bpm)', '1'], ['systolicMmhg', 'Systolic (mmHg)', '1'],
  ['diastolicMmhg', 'Diastolic (mmHg)', '1'], ['respiratoryRate', 'Resp. rate (/min)', '1'], ['spo2Percent', 'SpO₂ (%)', '1'],
  ['weightKg', 'Weight (kg)', '0.1'], ['heightCm', 'Height (cm)', '0.1'], ['bloodGlucoseMmol', 'Glucose (mmol/L)', '0.1'],
];

export function VitalsDialog({ patientId, encounterId, onClose }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [form, setForm] = useState({});
  const [errors, setErrors] = useState({});
  const [general, setGeneral] = useState(null);
  const save = useMutation({
    mutationFn: () => emrApi.recordVitals(patientId, {
      encounterId, ...Object.fromEntries(Object.entries(form).filter(([, v]) => v !== '').map(([k, v]) => [k, k === 'notes' ? v : Number(v)])),
    }),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: emrKey(patientId) }); toast.success('Vital signs recorded.'); onClose(); },
    onError: (err) => { const s = splitApiError(err); setErrors(s.fields); setGeneral(s.general); focusFirstInvalid(); },
  });
  return (
    <Modal open onClose={onClose} title="Record vital signs" size="lg"
           footer={<><Button variant="ghost" onClick={onClose}>Cancel</Button><Button loading={save.isPending} onClick={() => save.mutate()}>Save</Button></>}>
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
        {general && <Alert tone="danger" className="col-span-2 sm:col-span-3">{general.message}</Alert>}
        {VITAL_FIELDS.map(([k, label, step], i) => (
          <TextField key={k} label={label} type="number" step={step} inputMode="decimal" value={form[k] ?? ''} error={errors[k]}
                     data-autofocus={i === 0 ? true : undefined}
                     onChange={(e) => setForm((f) => ({ ...f, [k]: e.target.value }))} inputClassName="tabular" />
        ))}
        <TextField className="col-span-2 sm:col-span-3" label="Notes" value={form.notes ?? ''} onChange={(e) => setForm((f) => ({ ...f, notes: e.target.value }))} />
      </div>
    </Modal>
  );
}

export function VitalsTable({ vitals }) {
  if (vitals.length === 0) return <p className="text-sm text-foreground-muted">No vital signs recorded.</p>;
  const bp = (v) => (v.systolicMmhg ? `${v.systolicMmhg}/${v.diastolicMmhg}` : '—');
  return (
    <div className="relative overflow-x-auto">
      <table className="w-full min-w-[620px] text-left text-sm tabular">
        <caption className="sr-only">Recent vital signs</caption>
        <thead className="text-xs text-foreground-muted">
          <tr className="border-b border-border">
            {['Recorded', 'Temp °C', 'Pulse', 'BP', 'RR', 'SpO₂', 'Weight', 'BMI', 'By'].map((h) => <th key={h} scope="col" className="py-2 pr-4 font-medium">{h}</th>)}
          </tr>
        </thead>
        <tbody>
          {vitals.map((v) => (
            <tr key={v.id} className="border-b border-border last:border-0">
              <td className="whitespace-nowrap py-2 pr-4">{formatDateTime(v.recordedAt)}</td>
              <td className="py-2 pr-4">{v.temperatureC ?? '—'}</td>
              <td className="py-2 pr-4">{v.pulseBpm ?? '—'}</td>
              <td className="py-2 pr-4">{bp(v)}</td>
              <td className="py-2 pr-4">{v.respiratoryRate ?? '—'}</td>
              <td className="py-2 pr-4">{v.spo2Percent != null ? `${v.spo2Percent}%` : '—'}</td>
              <td className="py-2 pr-4">{v.weightKg != null ? `${v.weightKg} kg` : '—'}</td>
              <td className="py-2 pr-4">{v.bmi ?? '—'}</td>
              <td className="py-2 pr-4 font-sans text-foreground-muted">{v.recordedByName}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

const NOTE_LABEL = { DOCTOR_NOTE: 'Doctor note', TREATMENT: 'Treatment', NURSING_NOTE: 'Nursing note', ADDENDUM: 'Addendum', DISCHARGE_SUMMARY: 'Discharge summary' };

/** A note with its edit / sign / addendum controls. Signed notes are read-only. */
export function NoteItem({ note, patientId }) {
  const { hasPermission } = useAuth();
  const queryClient = useQueryClient();
  const toast = useToast();
  const [mode, setMode] = useState(null);   // 'edit' | 'addendum'
  const [text, setText] = useState('');
  const [error, setError] = useState(null);
  const refresh = () => queryClient.invalidateQueries({ queryKey: emrKey(patientId) });
  const act = useMutation({
    mutationFn: ({ kind }) => (kind === 'sign' ? emrApi.signNote(note.id)
      : kind === 'edit' ? emrApi.editNote(note.id, text) : emrApi.addAddendum(note.id, text)),
    onSuccess: (_, { kind }) => { setMode(null); refresh(); toast.success(kind === 'sign' ? 'Note signed.' : kind === 'edit' ? 'Note updated.' : 'Addendum added.'); },
    onError: (err) => setError(toApiError(err).message),
  });
  const canAddend = note.signedAt && note.noteType !== 'ADDENDUM' && hasPermission(['emr:write', 'nursing-note:write']);

  return (
    <li className={clsx('py-3', note.parentNoteId && 'ml-6 border-l border-border pl-4')}>
      <div className="flex flex-wrap items-center gap-2 text-xs text-foreground-muted">
        <span className="font-semibold text-foreground">{NOTE_LABEL[note.noteType] ?? humanise(note.noteType)}</span>
        <span>{note.authorName} · {formatDateTime(note.createdAt)}</span>
        {note.signedAt ? <Badge tone="success">Signed</Badge> : <Badge tone="warning">Draft</Badge>}
      </div>
      {mode === 'edit' ? null : <p className="mt-1 whitespace-pre-wrap text-sm leading-relaxed">{note.content}</p>}
      {mode && (
        <div className="mt-2 flex flex-col gap-2">
          <TextAreaField label={mode === 'edit' ? 'Edit note' : 'Addendum'} rows={4} value={text} onChange={(e) => setText(e.target.value)} data-autofocus />
          {error && <p role="alert" className="text-xs font-medium text-danger">{error}</p>}
          <div className="flex gap-2">
            <Button size="sm" loading={act.isPending} disabled={!text.trim()} onClick={() => act.mutate({ kind: mode })}>Save</Button>
            <Button size="sm" variant="ghost" onClick={() => setMode(null)}>Cancel</Button>
          </div>
        </div>
      )}
      {!mode && (note.editable || canAddend) && (
        <div className="mt-1.5 flex gap-1">
          {note.editable && (
            <>
              <Button size="sm" variant="ghost" icon={PenLine} onClick={() => { setText(note.content); setError(null); setMode('edit'); }}>Edit</Button>
              <Button size="sm" variant="ghost" loading={act.isPending} onClick={() => act.mutate({ kind: 'sign' })}>Sign</Button>
            </>
          )}
          {canAddend && <Button size="sm" variant="ghost" onClick={() => { setText(''); setError(null); setMode('addendum'); }}>Add addendum</Button>}
        </div>
      )}
    </li>
  );
}

export function PrescriptionList({ prescriptions, onCancel }) {
  if (prescriptions.length === 0) return <p className="text-sm text-foreground-muted">No prescriptions.</p>;
  return (
    <ul className="flex flex-col gap-3">
      {prescriptions.map((p) => (
        <li key={p.id} className="rounded-md border border-border">
          <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border px-3 py-2 text-xs text-foreground-muted">
            <span><span className="font-mono text-foreground">{p.prescriptionNo}</span> · {p.doctorName} · {formatDateTime(p.createdAt)}</span>
            <span className="flex items-center gap-2">
              <Badge tone={p.status === 'CANCELLED' ? 'danger' : p.status === 'DISPENSED' ? 'success' : 'info'}>{humanise(p.status)}</Badge>
              {onCancel && p.status === 'ACTIVE' && <Button size="sm" variant="ghost" onClick={() => onCancel(p)}>Cancel</Button>}
            </span>
          </div>
          <ul className="divide-y divide-border text-sm">
            {p.items.map((i) => (
              <li key={i.id} className="px-3 py-2">
                <span className="font-medium">{i.medicineName}</span>
                <span className="text-foreground-muted"> · {i.dose} {i.frequency} · {humanise(i.route)} · {i.durationDays} days · qty {i.quantityPrescribed}</span>
                {i.instructions && <span className="block text-xs text-foreground-muted">{i.instructions}</span>}
              </li>
            ))}
          </ul>
          {p.allergyOverrideReason && <p className="border-t border-border px-3 py-2 text-xs text-warning">Allergy override: {p.allergyOverrideReason}</p>}
        </li>
      ))}
    </ul>
  );
}
