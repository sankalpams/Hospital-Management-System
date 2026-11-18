import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Activity, ShieldAlert } from 'lucide-react';
import { emrApi } from '../../api/clinic';
import { toApiError } from '../../api/client';
import { useAuth } from '../../auth/AuthProvider';
import { Card, CardBody, CardHeader } from '../../components/ui/Card';
import { Button, LinkButton } from '../../components/ui/Button';
import { Alert } from '../../components/ui/Alert';
import { Badge, StatusBadge } from '../../components/ui/Badge';
import { TextAreaField } from '../../components/ui/Field';
import { Spinner } from '../../components/ui/Spinner';
import { formatDateTime } from '../../lib/format';
import { AllergyBanner, NoteItem, PrescriptionList, VitalsDialog, VitalsTable, emrKey } from './parts';

/** The patient's medical record. Without a care relationship the server asks for a break-the-glass reason. */
export function EmrTab({ patientId }) {
  const { hasPermission } = useAuth();
  const [reason, setReason] = useState(null);
  const [draft, setDraft] = useState('');
  const [recording, setRecording] = useState(false);
  const emr = useQuery({ queryKey: [...emrKey(patientId), reason], queryFn: () => emrApi.record(patientId, reason), retry: false });

  if (emr.isLoading) return <Spinner label="Loading medical record" />;
  if (emr.isError) {
    const e = toApiError(emr.error);
    if (e.code !== 'BREAK_GLASS_REQUIRED') return <Alert tone="danger" requestId={e.requestId}>{e.message}</Alert>;
    return (
      <Card className="max-w-xl">
        <CardHeader icon={ShieldAlert} title="Restricted record" description="You are not recorded as caring for this patient." />
        <CardBody className="flex flex-col gap-3">
          <p className="text-sm text-foreground-muted">
            You can still open the record in an emergency. Your name, the time and your reason are written to the audit log and reviewed.
          </p>
          <TextAreaField label="Reason for access" required rows={2} value={draft} onChange={(ev) => setDraft(ev.target.value)}
                         hint="At least 10 characters, e.g. “Covering for Dr. Silva in A&E”." />
          <div><Button variant="danger" disabled={draft.trim().length < 10} onClick={() => setReason(draft.trim())}>Open record</Button></div>
        </CardBody>
      </Card>
    );
  }

  const r = emr.data;
  return (
    <div className="flex flex-col gap-6">
      <AllergyBanner allergies={r.allergies} patientId={patientId} canWrite={hasPermission('allergy:write')} />
      <Card>
        <CardHeader icon={Activity} title="Vital signs" description="Most recent first"
                    action={hasPermission('vitals:record') && <Button size="sm" variant="secondary" onClick={() => setRecording(true)}>Record vitals</Button>} />
        <CardBody><VitalsTable vitals={r.recentVitals} /></CardBody>
      </Card>
      <section aria-labelledby="enc-heading">
        <h2 id="enc-heading" className="mb-3 text-base">Encounters</h2>
        {r.encounters.length === 0 && <p className="text-sm text-foreground-muted">No encounters yet.</p>}
        <ol className="flex flex-col gap-4">
          {r.encounters.map((e) => (
            <li key={e.id}>
              <Card>
                <div className="flex flex-wrap items-start justify-between gap-3 border-b border-border px-5 py-3">
                  <div>
                    <p className="font-medium">{formatDateTime(e.startedAt)} · {e.doctorName}</p>
                    <p className="text-xs text-foreground-muted"><span className="font-mono">{e.encounterNo}</span> · {e.departmentName}{e.chiefComplaint ? ` · ${e.chiefComplaint}` : ''}</p>
                  </div>
                  <div className="flex items-center gap-2">
                    <StatusBadge status={e.status === 'OPEN' ? 'IN_PROGRESS' : 'COMPLETED'} />
                    {e.canWrite && e.appointmentId && <LinkButton size="sm" to={`/appointments/${e.appointmentId}/consult`}>Continue</LinkButton>}
                  </div>
                </div>
                <CardBody className="grid gap-5 lg:grid-cols-2">
                  <div>
                    <h3 className="mb-2 text-sm">Diagnoses</h3>
                    {e.diagnoses.length === 0 ? <p className="text-sm text-foreground-muted">None recorded.</p> : (
                      <ul className="flex flex-col gap-1 text-sm">
                        {e.diagnoses.map((d) => (
                          <li key={d.id}>
                            {d.icd10Code && <span className="mr-1.5 font-mono text-xs text-foreground-muted">{d.icd10Code}</span>}
                            <span className="font-medium">{d.description}</span>
                            <span className="ml-2 inline-flex gap-1">{d.type === 'PRIMARY' && <Badge tone="primary">Primary</Badge>}{d.certainty !== 'CONFIRMED' && <Badge>{d.certainty === 'PROVISIONAL' ? 'Provisional' : 'Ruled out'}</Badge>}</span>
                          </li>
                        ))}
                      </ul>
                    )}
                    <h3 className="mb-2 mt-5 text-sm">Prescriptions</h3>
                    <PrescriptionList prescriptions={e.prescriptions} />
                  </div>
                  <div>
                    <h3 className="text-sm">Notes</h3>
                    {e.notes.length === 0 ? <p className="mt-2 text-sm text-foreground-muted">No notes.</p> : (
                      <ul className="divide-y divide-border">{e.notes.map((n) => <NoteItem key={n.id} note={n} patientId={patientId} />)}</ul>
                    )}
                  </div>
                </CardBody>
              </Card>
            </li>
          ))}
        </ol>
      </section>
      {recording && <VitalsDialog patientId={patientId} onClose={() => setRecording(false)} />}
    </div>
  );
}
