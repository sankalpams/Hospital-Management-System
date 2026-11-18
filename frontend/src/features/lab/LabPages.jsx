import { useState } from 'react';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import clsx from 'clsx';
import { ArrowLeft, FlaskConical, Printer } from 'lucide-react';
import { labApi } from '../../api/lab';
import { toApiError } from '../../api/client';
import { PageHeader } from '../../components/layout/AppShell';
import { Card, CardBody, CardHeader } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Alert } from '../../components/ui/Alert';
import { CheckboxField, SelectField, TextField } from '../../components/ui/Field';
import { FilterSelect, SearchInput, Toolbar } from '../../components/ui/Toolbar';
import { Pagination } from '../../components/ui/DataTable';
import { Spinner } from '../../components/ui/Spinner';
import { useToast } from '../../components/ui/Toast';
import { formatDate, formatDateTime, formatLKR } from '../../lib/format';
import { useListParams } from '../../lib/useListParams';
import { LabRequestCard, range } from './LabRequestCard';

const STAGES = [
  { id: 'REQUESTED', label: 'To collect' },
  { id: 'SAMPLE_COLLECTED', label: 'Collected' },
  { id: 'PROCESSING', label: 'Processing' },
  { id: 'COMPLETED', label: 'Awaiting review' },
  { id: 'REVIEWED', label: 'Reviewed' },
  { id: '', label: 'All' },
];

function useLabTests() {
  return useQuery({ queryKey: ['lab', 'tests'], queryFn: labApi.tests, staleTime: 300_000 });
}

/** Lab staff worklist: one stage at a time, urgent work first. */
export function LabWorklistPage() {
  const [params, setParams] = useListParams({ status: 'REQUESTED', size: 25 });
  useLabTests();
  const list = useQuery({
    queryKey: ['lab', 'requests', params],
    queryFn: () => labApi.search(params),
    placeholderData: keepPreviousData,
    refetchInterval: 60_000,
  });
  return (
    <>
      <PageHeader title="Laboratory" description="Requests move left to right: collect, process, enter result, doctor review." />
      <div role="tablist" aria-label="Stage" className="mb-4 flex flex-wrap gap-1 rounded-md border border-border bg-surface p-1">
        {STAGES.map((s) => (
          <button key={s.id || 'all'} type="button" role="tab" aria-selected={(params.status ?? '') === s.id}
                  onClick={() => setParams({ status: s.id })}
                  className={clsx('rounded px-3 py-1.5 text-sm font-medium', (params.status ?? '') === s.id ? 'bg-primary text-primary-foreground' : 'text-foreground-muted hover:text-foreground')}>
            {s.label}
          </button>
        ))}
      </div>
      <Toolbar>
        <SearchInput value={params.q} onChange={(q) => setParams({ q })} placeholder="Patient name, MRN or LAB no." label="Search lab requests" />
        <FilterSelect label="Priority" value={params.priority ?? ''} onChange={(priority) => setParams({ priority })}
                      options={[{ value: '', label: 'Any' }, { value: 'STAT', label: 'STAT' }, { value: 'URGENT', label: 'Urgent' }, { value: 'ROUTINE', label: 'Routine' }]} />
      </Toolbar>
      {list.isLoading && <Spinner label="Loading worklist" />}
      {list.isError && <Alert tone="danger">{toApiError(list.error).message}</Alert>}
      {list.data?.content.length === 0 && (
        <div className="rounded-lg border border-border bg-surface px-4 py-12 text-center">
          <FlaskConical className="mx-auto h-8 w-8 text-foreground-subtle" aria-hidden="true" />
          <p className="mt-2 font-medium">Nothing at this stage</p>
          <p className="text-sm text-foreground-muted">New requests appear here as doctors order them.</p>
        </div>
      )}
      <div className="flex flex-col gap-4">
        {list.data?.content.map((r) => <LabRequestCard key={r.id} request={r} />)}
      </div>
      {list.data?.totalElements > 0 && (
        <div className="mt-4 overflow-hidden rounded-lg border border-border bg-surface">
          <Pagination page={list.data} onPageChange={(page) => setParams({ page }, { resetPage: false })} />
        </div>
      )}
    </>
  );
}

/** A patient's lab history (patient page tab and consultation). */
export function PatientLabPanel({ patientId }) {
  useLabTests();
  const list = useQuery({ queryKey: ['lab', 'patient', patientId], queryFn: () => labApi.search({ patientId, size: 50 }) });
  if (list.isLoading) return <Spinner label="Loading lab results" />;
  if (list.isError) return <Alert tone="danger">{toApiError(list.error).message}</Alert>;
  if (list.data.content.length === 0) return <p className="text-sm text-foreground-muted">No lab tests ordered.</p>;
  return <div className="flex flex-col gap-3">{[...list.data.content].reverse().map((r) => <LabRequestCard key={r.id} request={r} showPatient={false} />)}</div>;
}

/** Ordering card for the consultation workspace. */
export function OrderLabCard({ encounterId }) {
  const tests = useLabTests();
  const queryClient = useQueryClient();
  const toast = useToast();
  const [selected, setSelected] = useState([]);
  const [priority, setPriority] = useState('ROUTINE');
  const [notes, setNotes] = useState('');
  const order = useMutation({
    mutationFn: () => labApi.order(encounterId, { testIds: selected, priority, clinicalNotes: notes || null }),
    onSuccess: (r) => {
      setSelected([]); setNotes(''); setPriority('ROUTINE');
      queryClient.invalidateQueries({ queryKey: ['lab'] });
      toast.success(`${r.requestNo} sent to the laboratory.`);
    },
  });
  const byCategory = (tests.data ?? []).reduce((acc, t) => ({ ...acc, [t.category]: [...(acc[t.category] ?? []), t] }), {});
  const total = (tests.data ?? []).filter((t) => selected.includes(t.id)).reduce((s, t) => s + Number(t.price), 0);
  return (
    <Card>
      <CardHeader icon={FlaskConical} title="Order lab tests" />
      <CardBody className="flex flex-col gap-4">
        {tests.isLoading && <Spinner label="Loading tests" />}
        {Object.entries(byCategory).map(([cat, list]) => (
          <fieldset key={cat}>
            <legend className="mb-1.5 text-xs font-semibold uppercase tracking-wide text-foreground-subtle">{cat.toLowerCase()}</legend>
            <div className="grid gap-2 sm:grid-cols-2">
              {list.map((t) => (
                <CheckboxField key={t.id} label={t.name} description={`${range(t) ? `${range(t)} ${t.unit ?? ''} · ` : ''}${formatLKR(t.price)}`}
                               checked={selected.includes(t.id)}
                               onChange={(e) => setSelected((s) => (e.target.checked ? [...s, t.id] : s.filter((x) => x !== t.id)))} />
              ))}
            </div>
          </fieldset>
        ))}
        <div className="grid gap-3 sm:grid-cols-3">
          <SelectField label="Priority" value={priority} onChange={(e) => setPriority(e.target.value)}
                       options={[{ value: 'ROUTINE', label: 'Routine' }, { value: 'URGENT', label: 'Urgent' }, { value: 'STAT', label: 'STAT' }]} />
          <TextField className="sm:col-span-2" label="Clinical notes" value={notes} onChange={(e) => setNotes(e.target.value)} />
        </div>
        {order.isError && <Alert tone="danger">{toApiError(order.error).message}</Alert>}
        <div className="flex items-center gap-3">
          <Button disabled={!selected.length} loading={order.isPending} onClick={() => order.mutate()}>
            Order {selected.length || ''} test{selected.length === 1 ? '' : 's'}
          </Button>
          {selected.length > 0 && <span className="text-sm text-foreground-muted tabular">{formatLKR(total)}</span>}
        </div>
      </CardBody>
    </Card>
  );
}

/** Printable lab report (browser print; chrome hidden by print styles). */
export function LabReportPrintPage() {
  const { id } = useParams();
  const req = useQuery({ queryKey: ['lab', 'request', id], queryFn: () => labApi.get(id) });
  if (req.isLoading) return <Spinner label="Loading report" />;
  if (req.isError) return <Alert tone="danger">{toApiError(req.error).message}</Alert>;
  const r = req.data;
  const reported = r.items.filter((i) => i.result);
  return (
    <>
      <div className="no-print mb-4 flex items-center justify-between">
        <Link to="/lab" className="inline-flex items-center gap-1 text-sm text-foreground-muted hover:text-foreground"><ArrowLeft className="h-4 w-4" aria-hidden="true" /> Laboratory</Link>
        <Button icon={Printer} onClick={() => window.print()}>Print</Button>
      </div>
      <section className="print-sheet mx-auto max-w-3xl rounded-lg border border-border bg-surface p-8 shadow-sm">
        <h1 className="text-xl">Laboratory report</h1>
        <dl className="mt-4 grid grid-cols-2 gap-x-8 gap-y-1 text-sm">
          <div className="flex gap-2"><dt className="text-foreground-muted">Patient</dt><dd className="font-medium">{r.patientName}</dd></div>
          <div className="flex gap-2"><dt className="text-foreground-muted">MRN</dt><dd className="font-mono">{r.patientMrn}</dd></div>
          <div className="flex gap-2"><dt className="text-foreground-muted">Request</dt><dd className="font-mono">{r.requestNo}</dd></div>
          <div className="flex gap-2"><dt className="text-foreground-muted">Requested by</dt><dd>{r.doctorName}</dd></div>
          <div className="flex gap-2"><dt className="text-foreground-muted">Requested</dt><dd>{formatDateTime(r.requestedAt)}</dd></div>
          <div className="flex gap-2"><dt className="text-foreground-muted">Printed</dt><dd>{formatDate(new Date().toISOString().slice(0, 10))}</dd></div>
        </dl>
        <table className="mt-6 w-full text-left text-sm tabular">
          <thead className="border-b-2 border-border text-xs text-foreground-muted">
            <tr><th className="py-2">Test</th><th className="py-2">Result</th><th className="py-2">Unit</th><th className="py-2">Reference</th><th className="py-2">Flag</th></tr>
          </thead>
          <tbody>
            {reported.map((i) => (
              <tr key={i.id} className="border-b border-border align-top">
                <td className="py-2 pr-3 font-medium">{i.testName}</td>
                <td className="py-2 pr-3 font-semibold">{i.result.numericValue != null ? Number(i.result.numericValue).toString() : i.result.textValue}</td>
                <td className="py-2 pr-3">{i.result.unit ?? ''}</td>
                <td className="py-2 pr-3">{range(i.result)}</td>
                <td className="py-2">{i.result.flag && i.result.flag !== 'N' ? (i.result.flag === 'H' ? 'High' : 'Low') : ''}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {reported.some((i) => i.result.comments) && (
          <div className="mt-4 text-sm">
            {reported.filter((i) => i.result.comments).map((i) => <p key={i.id}><span className="font-medium">{i.testName}:</span> {i.result.comments}</p>)}
          </div>
        )}
        <p className="mt-8 text-xs text-foreground-subtle">
          Entered by {[...new Set(reported.map((i) => i.result.enteredByName))].join(', ')}.
          Flags compare results with the reference range only; interpretation is the clinician's.
        </p>
      </section>
    </>
  );
}
