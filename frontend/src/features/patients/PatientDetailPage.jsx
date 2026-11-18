import { useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import { ArrowLeft, BedSingle, CalendarPlus, Download, FileText, Pencil, Phone, Upload, UserCog } from 'lucide-react';
import { DOCUMENT_CATEGORIES, patientsApi } from '../../api/patients';
import { toApiError } from '../../api/client';
import { useAuth } from '../../auth/AuthProvider';
import { Card, CardBody, CardHeader } from '../../components/ui/Card';
import { Badge, StatusBadge, humanise } from '../../components/ui/Badge';
import { Button, LinkButton } from '../../components/ui/Button';
import { Alert } from '../../components/ui/Alert';
import { Modal } from '../../components/ui/Modal';
import { SelectField, TextAreaField, TextField } from '../../components/ui/Field';
import { Spinner } from '../../components/ui/Spinner';
import { useToast } from '../../components/ui/Toast';
import { formatDate, formatDateTime, todayInHospital } from '../../lib/format';
import { PatientAppointments } from '../appointments/PatientAppointments';
import { EmrTab } from '../emr/EmrTab';
import { PatientLabPanel } from '../lab/LabPages';
import { RequestAdmissionDialog } from '../inpatient/InpatientPage';
import { PatientBillingPanel } from '../billing/BillingPages';

function Detail({ label, children }) {
  return (
    <div>
      <dt className="text-xs text-foreground-subtle">{label}</dt>
      <dd className="mt-0.5 text-sm">{children || <span className="text-foreground-subtle">—</span>}</dd>
    </div>
  );
}

export default function PatientDetailPage() {
  const { id } = useParams();
  const { hasPermission } = useAuth();
  const [searchParams] = useSearchParams();
  const [tab, setTab] = useState(searchParams.get('tab') ?? 'overview');
  const [statusOpen, setStatusOpen] = useState(false);
  const [admitOpen, setAdmitOpen] = useState(false);
  const patient = useQuery({ queryKey: ['patient', id], queryFn: () => patientsApi.get(id) });

  if (patient.isLoading) return <Spinner label="Loading patient" />;
  if (patient.isError) return <Alert tone="danger">{toApiError(patient.error).message}</Alert>;
  const p = patient.data;

  const tabs = [
    { id: 'overview', label: 'Overview', show: true },
    { id: 'emr', label: 'Medical record', show: hasPermission('emr:read') },
    { id: 'lab', label: 'Lab results', show: hasPermission('lab:request:read') },
    { id: 'appointments', label: 'Appointments', show: hasPermission('appointment:read') },
    { id: 'documents', label: 'Documents', show: hasPermission('patient:document:read') },
    { id: 'billing', label: 'Billing', show: hasPermission('billing:read') },
  ].filter((t) => t.show);

  return (
    <>
      <Link to="/patients" className="mb-3 inline-flex items-center gap-1 text-sm text-foreground-muted hover:text-foreground">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Patients
      </Link>

      <Card className="mb-6">
        <div className="flex flex-col gap-4 px-5 py-5 sm:flex-row sm:items-start sm:justify-between">
          <div className="flex items-start gap-4">
            <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-primary-soft font-heading text-lg font-semibold text-primary-soft-foreground">
              {p.firstName[0]}{p.lastName[0]}
            </span>
            <div>
              <div className="flex flex-wrap items-center gap-2">
                <h1 className="text-2xl">{p.fullName}</h1>
                <StatusBadge status={p.status} />
              </div>
              <p className="mt-1 text-sm text-foreground-muted">
                <span className="font-mono">{p.mrn}</span> · {p.age} years · {humanise(p.gender)}
                {p.bloodGroup && <> · Blood group <span className="font-medium text-foreground">{p.bloodGroup}</span></>}
              </p>
              <p className="mt-1 inline-flex items-center gap-1.5 text-sm text-foreground-muted">
                <Phone className="h-3.5 w-3.5" aria-hidden="true" /> {p.phone}
              </p>
            </div>
          </div>
          <div className="flex flex-wrap gap-2">
            {hasPermission('appointment:create') && p.status === 'ACTIVE' && (
              <LinkButton to={`/appointments/new?patientId=${p.id}`} variant="secondary" icon={CalendarPlus}>Book appointment</LinkButton>
            )}
            {hasPermission('admission:request') && p.status === 'ACTIVE' && (
              <Button variant="secondary" icon={BedSingle} onClick={() => setAdmitOpen(true)}>Request admission</Button>
            )}
            {hasPermission('patient:update') && p.status !== 'DECEASED' && (
              <>
                <Button variant="ghost" icon={UserCog} onClick={() => setStatusOpen(true)}>Change status</Button>
                <LinkButton to={`/patients/${p.id}/edit`} icon={Pencil}>Edit</LinkButton>
              </>
            )}
          </div>
        </div>
        <div role="tablist" aria-label="Patient record" className="flex gap-1 overflow-x-auto overflow-y-hidden border-t border-border px-3">
          {tabs.map((t) => (
            <button key={t.id} type="button" role="tab" aria-selected={tab === t.id} onClick={() => setTab(t.id)}
                    className={clsx('-mb-px whitespace-nowrap border-b-2 px-3 py-3 text-sm font-medium',
                      tab === t.id ? 'border-primary text-primary' : 'border-transparent text-foreground-muted hover:text-foreground')}>
              {t.label}
            </button>
          ))}
        </div>
      </Card>

      {admitOpen && <RequestAdmissionDialog patient={p} onClose={() => setAdmitOpen(false)} />}
      {tab === 'overview' && <Overview p={p} />}
      {tab === 'emr' && <EmrTab patientId={p.id} />}
      {tab === 'lab' && <PatientLabPanel patientId={p.id} />}
      {tab === 'appointments' && <PatientAppointments patientId={p.id} />}
      {tab === 'billing' && <PatientBillingPanel patientId={p.id} />}
      {tab === 'documents' && <Documents patientId={p.id} canUpload={hasPermission('patient:document:upload')} />}
      {statusOpen && <StatusDialog patient={p} onClose={() => setStatusOpen(false)} />}
    </>
  );
}

function Overview({ p }) {
  const address = [p.addressLine1, p.addressLine2, p.city, p.district].filter(Boolean).join(', ');
  return (
    <div className="grid gap-6 lg:grid-cols-3">
      <Card className="lg:col-span-2">
        <CardHeader title="Demographics" />
        <CardBody>
          <dl className="grid gap-4 sm:grid-cols-3">
            <Detail label="Date of birth">{formatDate(p.dateOfBirth)}</Detail>
            <Detail label="NIC">{p.nic && <span className="font-mono">{p.nic}</span>}</Detail>
            <Detail label="Passport">{p.passportNo}</Detail>
            <Detail label="Marital status">{p.maritalStatus && humanise(p.maritalStatus)}</Detail>
            <Detail label="Occupation">{p.occupation}</Detail>
            <Detail label="Email">{p.email}</Detail>
            <div className="sm:col-span-3"><Detail label="Address">{address}</Detail></div>
            {p.deceasedAt && <Detail label="Date of death">{formatDate(p.deceasedAt)}</Detail>}
            <Detail label="Registered">{formatDateTime(p.createdAt)}</Detail>
            <Detail label="Last updated">{formatDateTime(p.updatedAt)}</Detail>
          </dl>
        </CardBody>
      </Card>
      <Card>
        <CardHeader title="Emergency contacts" />
        <CardBody>
          {p.emergencyContacts.length === 0 ? (
            <p className="text-sm text-foreground-muted">None recorded.</p>
          ) : (
            <ul className="flex flex-col gap-4">
              {p.emergencyContacts.map((c) => (
                <li key={c.id} className="text-sm">
                  <p className="font-medium">{c.name} {c.primary && <Badge tone="primary" className="ml-1">Primary</Badge>}</p>
                  <p className="text-foreground-muted">{c.relationship}</p>
                  <p className="tabular">{c.phone}{c.altPhone ? ` · ${c.altPhone}` : ''}</p>
                </li>
              ))}
            </ul>
          )}
        </CardBody>
      </Card>
    </div>
  );
}

function Documents({ patientId, canUpload }) {
  const [uploading, setUploading] = useState(false);
  const docs = useQuery({ queryKey: ['patient-documents', patientId], queryFn: () => patientsApi.documents(patientId) });
  const categoryLabel = (c) => DOCUMENT_CATEGORIES.find((x) => x.value === c)?.label ?? c;

  return (
    <Card>
      <CardHeader
        icon={FileText}
        title="Documents"
        description="PDF, JPEG or PNG, up to 10 MB. Downloads are recorded in the audit log."
        action={canUpload && <Button size="sm" icon={Upload} onClick={() => setUploading(true)}>Upload</Button>}
      />
      <CardBody>
        {docs.isLoading && <Spinner label="Loading documents" />}
        {docs.isError && <Alert tone="danger">{toApiError(docs.error).message}</Alert>}
        {docs.data?.length === 0 && <p className="text-sm text-foreground-muted">No documents uploaded.</p>}
        {docs.data?.length > 0 && (
          <ul className="divide-y divide-border">
            {docs.data.map((d) => (
              <li key={d.id} className="flex flex-col gap-2 py-3 sm:flex-row sm:items-center sm:justify-between">
                <div className="min-w-0">
                  <p className="font-medium">{d.title}</p>
                  <p className="truncate text-xs text-foreground-subtle">
                    {categoryLabel(d.category)} · {d.originalName} · {(d.sizeBytes / 1024).toFixed(0)} KB · {formatDateTime(d.createdAt)}
                  </p>
                </div>
                <a href={patientsApi.downloadUrl(d.id)} className="inline-flex items-center gap-1.5 text-sm font-medium text-primary hover:underline">
                  <Download className="h-4 w-4" aria-hidden="true" /> Download
                </a>
              </li>
            ))}
          </ul>
        )}
      </CardBody>
      {uploading && <UploadDialog patientId={patientId} onClose={() => setUploading(false)} />}
    </Card>
  );
}

function UploadDialog({ patientId, onClose }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [form, setForm] = useState({ category: 'REFERRAL', title: '', file: null });
  const [errors, setErrors] = useState({});
  const [general, setGeneral] = useState(null);

  const upload = useMutation({
    mutationFn: () => patientsApi.upload(patientId, form),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['patient-documents', patientId] });
      toast.success('Document uploaded.');
      onClose();
    },
    onError: (err) => setGeneral(toApiError(err)),
  });

  const submit = (e) => {
    e.preventDefault();
    const errs = {};
    if (!form.title.trim()) errs.title = 'Title is required.';
    if (!form.file) errs.file = 'Choose a file.';
    else if (form.file.size > 10 * 1024 * 1024) errs.file = 'Files must be 10 MB or smaller.';
    setErrors(errs);
    setGeneral(null);
    if (!Object.keys(errs).length) upload.mutate();
  };

  return (
    <Modal open onClose={onClose} title="Upload document"
           footer={<><Button variant="ghost" onClick={onClose}>Cancel</Button><Button type="submit" form="upload-form" loading={upload.isPending}>Upload</Button></>}>
      <form id="upload-form" onSubmit={submit} noValidate className="flex flex-col gap-4">
        {general && <Alert tone="danger" requestId={general.requestId}>{general.message}</Alert>}
        <SelectField label="Category" required options={DOCUMENT_CATEGORIES} value={form.category} onChange={(e) => setForm((f) => ({ ...f, category: e.target.value }))} />
        <TextField label="Title" required maxLength={150} value={form.title} error={errors.title} data-autofocus
                   onChange={(e) => setForm((f) => ({ ...f, title: e.target.value }))} placeholder="e.g. GP referral letter" />
        <TextField label="File" type="file" required accept=".pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png" error={errors.file}
                   inputClassName="py-1.5" onChange={(e) => setForm((f) => ({ ...f, file: e.target.files?.[0] ?? null }))} />
      </form>
    </Modal>
  );
}

function StatusDialog({ patient, onClose }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const options = [
    { value: 'ACTIVE', label: 'Active' },
    { value: 'INACTIVE', label: 'Inactive (e.g. moved away, merged record)' },
    { value: 'DECEASED', label: 'Deceased' },
  ].filter((o) => o.value !== patient.status);
  const [form, setForm] = useState({ status: options[0].value, deceasedAt: '', reason: '' });
  const [error, setError] = useState(null);

  const save = useMutation({
    mutationFn: () => patientsApi.changeStatus(patient.id, { ...form, deceasedAt: form.deceasedAt || null }),
    onSuccess: (p) => {
      queryClient.setQueryData(['patient', String(p.id)], p);
      queryClient.invalidateQueries({ queryKey: ['patients'] });
      toast.success(`Status changed to ${humanise(p.status)}.`);
      onClose();
    },
    onError: (err) => setError(toApiError(err).message),
  });

  const invalid = form.reason.trim().length < 3 || (form.status === 'DECEASED' && !form.deceasedAt);
  return (
    <Modal open onClose={onClose} title="Change patient status" size="sm"
           description={form.status === 'DECEASED' ? 'Recording a death cannot be undone in the system.' : undefined}
           footer={<><Button variant="ghost" onClick={onClose}>Cancel</Button>
             <Button variant={form.status === 'DECEASED' ? 'danger' : 'primary'} disabled={invalid} loading={save.isPending} onClick={() => save.mutate()}>Save status</Button></>}>
      <div className="flex flex-col gap-4">
        {error && <Alert tone="danger">{error}</Alert>}
        <SelectField label="New status" options={options} value={form.status} onChange={(e) => setForm((f) => ({ ...f, status: e.target.value }))} />
        {form.status === 'DECEASED' && (
          <TextField label="Date of death" type="date" required max={todayInHospital()} min={patient.dateOfBirth}
                     value={form.deceasedAt} onChange={(e) => setForm((f) => ({ ...f, deceasedAt: e.target.value }))} />
        )}
        <TextAreaField label="Reason" required value={form.reason} onChange={(e) => setForm((f) => ({ ...f, reason: e.target.value }))}
                       hint="Stored in the audit log." maxLength={255} />
      </div>
    </Modal>
  );
}
