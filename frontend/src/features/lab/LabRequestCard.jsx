import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Printer } from 'lucide-react';
import clsx from 'clsx';
import { labApi } from '../../api/lab';
import { toApiError } from '../../api/client';
import { useAuth } from '../../auth/AuthProvider';
import { Badge, StatusBadge } from '../../components/ui/Badge';
import { Button, LinkButton } from '../../components/ui/Button';
import { ConfirmDialog, Modal } from '../../components/ui/Modal';
import { TextAreaField, TextField } from '../../components/ui/Field';
import { Alert } from '../../components/ui/Alert';
import { useToast } from '../../components/ui/Toast';
import { formatDateTime } from '../../lib/format';

export const PRIORITY_TONE = { STAT: 'danger', URGENT: 'warning', ROUTINE: 'neutral' };
const FLAG = { H: { label: 'High', tone: 'danger' }, L: { label: 'Low', tone: 'warning' }, N: { label: 'Normal', tone: 'success' } };

export function range(r) {
  if (!r) return '';
  if (r.refRangeLow != null && r.refRangeHigh != null) return `${num(r.refRangeLow)}–${num(r.refRangeHigh)}`;
  if (r.refRangeHigh != null) return `≤ ${num(r.refRangeHigh)}`;
  if (r.refRangeLow != null) return `≥ ${num(r.refRangeLow)}`;
  return '';
}
const num = (v) => Number(v).toString();

/** One request with per-test actions. The buttons shown depend on the item's stage and the user's permissions. */
export function LabRequestCard({ request: r, showPatient = true }) {
  const { hasPermission } = useAuth();
  const queryClient = useQueryClient();
  const toast = useToast();
  const [entering, setEntering] = useState(null);
  const [cancelling, setCancelling] = useState(null);

  const act = useMutation({
    mutationFn: ({ kind, item, reason }) => ({
      collect: () => labApi.collect(item.id), start: () => labApi.start(item.id),
      review: () => labApi.review(item.id), cancel: () => labApi.cancel(item.id, reason),
    })[kind](),
    onSuccess: () => { setCancelling(null); queryClient.invalidateQueries({ queryKey: ['lab'] }); },
    onError: (err) => toast.error(toApiError(err).message),
  });

  const actionFor = (item) => {
    const busy = act.isPending && act.variables?.item.id === item.id;
    switch (item.status) {
      case 'REQUESTED':
        return (
          <>
            {hasPermission('lab:sample:collect') && <Button size="sm" variant="secondary" loading={busy} onClick={() => act.mutate({ kind: 'collect', item })}>Collect sample</Button>}
            {hasPermission('lab:request:create') && <Button size="sm" variant="ghost" onClick={() => setCancelling(item)}>Cancel</Button>}
          </>
        );
      case 'SAMPLE_COLLECTED':
        return hasPermission('lab:sample:collect') && <Button size="sm" variant="secondary" loading={busy} onClick={() => act.mutate({ kind: 'start', item })}>Start processing</Button>;
      case 'PROCESSING':
        return hasPermission('lab:result:enter') && <Button size="sm" onClick={() => setEntering(item)}>Enter result</Button>;
      case 'COMPLETED':
        return hasPermission('lab:result:review') && <Button size="sm" variant="secondary" loading={busy} onClick={() => act.mutate({ kind: 'review', item })}>Mark reviewed</Button>;
      default:
        return null;
    }
  };

  return (
    <article className="rounded-lg border border-border bg-surface shadow-sm">
      <header className="flex flex-wrap items-start justify-between gap-3 border-b border-border px-4 py-3">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            {showPatient && <span className="font-medium">{r.patientName}</span>}
            {showPatient && <span className="font-mono text-xs text-foreground-subtle">{r.patientMrn}</span>}
            {r.priority !== 'ROUTINE' && <Badge tone={PRIORITY_TONE[r.priority]}>{r.priority}</Badge>}
          </div>
          <p className="mt-0.5 text-xs text-foreground-muted">
            <span className="font-mono">{r.requestNo}</span> · {r.doctorName} · {formatDateTime(r.requestedAt)}
          </p>
          {r.clinicalNotes && <p className="mt-1 text-xs text-foreground-muted">Clinical notes: {r.clinicalNotes}</p>}
        </div>
        <div className="flex items-center gap-2">
          <StatusBadge status={r.status} />
          {r.items.some((i) => i.result) && <LinkButton size="sm" variant="ghost" icon={Printer} to={`/lab/requests/${r.id}/print`}>Report</LinkButton>}
        </div>
      </header>
      <ul className="divide-y divide-border">
        {r.items.map((item) => (
          <li key={item.id} className="flex flex-col gap-2 px-4 py-2.5 sm:flex-row sm:items-center sm:justify-between">
            <div className="min-w-0 text-sm">
              <p>
                <span className="font-medium">{item.testName}</span>
                <span className="ml-2 text-xs text-foreground-subtle">{item.sampleType.toLowerCase()}{item.sampleBarcode ? ` · ${item.sampleBarcode}` : ''}</span>
              </p>
              {item.result && (
                <p className="mt-0.5 tabular">
                  <span className={clsx('font-semibold', item.result.flag === 'H' && 'text-danger', item.result.flag === 'L' && 'text-warning')}>
                    {item.result.numericValue != null ? num(item.result.numericValue) : item.result.textValue}
                  </span>
                  {item.result.unit && <span className="text-foreground-muted"> {item.result.unit}</span>}
                  {range(item.result) && <span className="text-xs text-foreground-subtle"> (ref {range(item.result)})</span>}
                  {item.result.flag && <Badge tone={FLAG[item.result.flag].tone} className="ml-2">{FLAG[item.result.flag].label}</Badge>}
                  {item.result.comments && <span className="block text-xs text-foreground-muted">{item.result.comments}</span>}
                </p>
              )}
              {item.cancelReason && <p className="text-xs text-foreground-muted">Cancelled: {item.cancelReason}</p>}
            </div>
            <div className="flex shrink-0 items-center gap-2">
              <StatusBadge status={item.status} />
              {actionFor(item)}
            </div>
          </li>
        ))}
      </ul>
      {entering && <ResultDialog item={entering} onClose={() => setEntering(null)} />}
      {cancelling && (
        <ConfirmDialog open title={`Cancel ${cancelling.testName}?`} message="The test is removed from the patient's bill if it has not been invoiced."
                       confirmLabel="Cancel test" requireReason loading={act.isPending} onClose={() => setCancelling(null)}
                       onConfirm={(reason) => act.mutate({ kind: 'cancel', item: cancelling, reason })} />
      )}
    </article>
  );
}

function ResultDialog({ item, onClose }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [value, setValue] = useState('');
  const [comments, setComments] = useState('');
  const [error, setError] = useState(null);
  const tests = queryClient.getQueryData(['lab', 'tests']) ?? [];
  const test = tests.find((t) => t.id === item.labTestId);
  const numeric = (test?.resultType ?? 'NUMERIC') === 'NUMERIC';
  const save = useMutation({
    mutationFn: () => labApi.result(item.id, numeric ? { numericValue: value, comments: comments || null } : { textValue: value, comments: comments || null }),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['lab'] }); toast.success(`${item.testName} result saved.`); onClose(); },
    onError: (err) => setError(toApiError(err).message),
  });
  return (
    <Modal open onClose={onClose} title={`Result: ${item.testName}`} size="sm"
           description={item.sampleBarcode ? `Sample ${item.sampleBarcode}` : undefined}
           footer={<><Button variant="ghost" onClick={onClose}>Cancel</Button><Button disabled={!value.trim()} loading={save.isPending} onClick={() => save.mutate()}>Save result</Button></>}>
      <div className="flex flex-col gap-4">
        {error && <Alert tone="danger">{error}</Alert>}
        {numeric ? (
          <TextField label={`Value${test?.unit ? ` (${test.unit})` : ''}`} type="number" step="any" required data-autofocus inputClassName="tabular"
                     value={value} onChange={(e) => setValue(e.target.value)} hint={range(test) ? `Reference range ${range(test)}` : undefined} />
        ) : (
          <TextAreaField label="Result" required rows={3} data-autofocus value={value} onChange={(e) => setValue(e.target.value)}
                         hint={test?.refRangeText ? `Normal: ${test.refRangeText}` : undefined} />
        )}
        <TextField label="Comments" value={comments} onChange={(e) => setComments(e.target.value)} />
      </div>
    </Modal>
  );
}
