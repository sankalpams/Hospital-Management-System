import { useEffect, useState } from 'react';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import clsx from 'clsx';
import { AlertTriangle, PackagePlus, Pill, Plus } from 'lucide-react';
import { DOSAGE_FORMS, pharmacyApi } from '../../api/pharmacy';
import { toApiError } from '../../api/client';
import { useAuth } from '../../auth/AuthProvider';
import { PageHeader } from '../../components/layout/AppShell';
import { Card, CardBody, CardHeader } from '../../components/ui/Card';
import { DataTable } from '../../components/ui/DataTable';
import { FilterSelect, SearchInput, Toolbar } from '../../components/ui/Toolbar';
import { Badge, humanise } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Alert } from '../../components/ui/Alert';
import { ConfirmDialog, Modal } from '../../components/ui/Modal';
import { CheckboxField, SelectField, TextField } from '../../components/ui/Field';
import { Spinner } from '../../components/ui/Spinner';
import { useToast } from '../../components/ui/Toast';
import { formatDate, formatDateTime, formatLKR, todayInHospital } from '../../lib/format';
import { focusFirstInvalid, requireFields, splitApiError } from '../../lib/forms';
import { useListParams } from '../../lib/useListParams';

const EXPIRY_TONE = { OK: 'success', EXPIRING: 'warning', EXPIRED: 'danger' };

export default function PharmacyPage() {
  const { hasPermission } = useAuth();
  const [params, setParams] = useListParams({ tab: hasPermission('prescription:read') ? 'queue' : 'stock' });
  const tabs = [
    { id: 'queue', label: 'Dispensing queue', show: hasPermission('prescription:read') },
    { id: 'stock', label: 'Stock', show: hasPermission('medicine:read') },
    { id: 'alerts', label: 'Alerts', show: hasPermission('medicine:read') },
    { id: 'ledger', label: 'Stock ledger', show: hasPermission('medicine:read') },
    { id: 'suppliers', label: 'Suppliers', show: hasPermission('supplier:manage') },
  ].filter((t) => t.show);
  const tab = tabs.some((t) => t.id === params.tab) ? params.tab : tabs[0]?.id;

  return (
    <>
      <PageHeader title="Pharmacy" description="Dispense prescriptions, receive stock and watch expiry dates." />
      <div role="tablist" aria-label="Pharmacy" className="mb-4 flex flex-wrap gap-1 rounded-md border border-border bg-surface p-1">
        {tabs.map((t) => (
          <button key={t.id} type="button" role="tab" aria-selected={tab === t.id} onClick={() => setParams({ tab: t.id, q: '', lowStock: '' })}
                  className={clsx('rounded px-3 py-1.5 text-sm font-medium', tab === t.id ? 'bg-primary text-primary-foreground' : 'text-foreground-muted hover:text-foreground')}>
            {t.label}
          </button>
        ))}
      </div>
      {tab === 'queue' && <QueueTab />}
      {tab === 'stock' && <StockTab params={params} setParams={setParams} />}
      {tab === 'alerts' && <AlertsTab />}
      {tab === 'ledger' && <LedgerTab params={params} setParams={setParams} />}
      {tab === 'suppliers' && <SuppliersTab />}
    </>
  );
}

// ---------------------------------------------------------------- queue

function QueueTab() {
  const { hasPermission } = useAuth();
  const [dispensing, setDispensing] = useState(null);
  const queue = useQuery({ queryKey: ['pharmacy', 'queue'], queryFn: () => pharmacyApi.queue(), refetchInterval: 60_000 });
  if (queue.isLoading) return <Spinner label="Loading queue" />;
  if (queue.isError) return <Alert tone="danger">{toApiError(queue.error).message}</Alert>;
  if (queue.data.length === 0) {
    return (
      <div className="rounded-lg border border-border bg-surface px-4 py-12 text-center">
        <Pill className="mx-auto h-8 w-8 text-foreground-subtle" aria-hidden="true" />
        <p className="mt-2 font-medium">No prescriptions waiting</p>
        <p className="text-sm text-foreground-muted">Prescriptions appear here as soon as a doctor issues them.</p>
      </div>
    );
  }
  return (
    <div className="flex flex-col gap-4">
      {queue.data.map((rx) => (
        <article key={rx.id} className="rounded-lg border border-border bg-surface shadow-sm">
          <header className="flex flex-wrap items-start justify-between gap-3 border-b border-border px-4 py-3">
            <div>
              <p className="font-medium">
                <Link to={`/patients/${rx.patientId}`} className="hover:text-primary hover:underline">{rx.patientName}</Link>
                <span className="ml-2 font-mono text-xs text-foreground-subtle">{rx.patientMrn}</span>
              </p>
              <p className="text-xs text-foreground-muted"><span className="font-mono">{rx.prescriptionNo}</span> · {rx.doctorName} · {formatDateTime(rx.createdAt)}</p>
              {rx.notes && <p className="mt-1 text-xs text-foreground-muted">Note: {rx.notes}</p>}
              {rx.allergyOverrideReason && <p className="mt-1 text-xs font-medium text-warning">Prescribed despite recorded allergy: {rx.allergyOverrideReason}</p>}
            </div>
            <div className="flex items-center gap-2">
              {rx.status === 'PARTIALLY_DISPENSED' && <Badge tone="warning">Partly dispensed</Badge>}
              {hasPermission('pharmacy:dispense') && <Button size="sm" onClick={() => setDispensing(rx)}>Dispense</Button>}
            </div>
          </header>
          <ul className="divide-y divide-border text-sm">
            {rx.items.map((i) => (
              <li key={i.id} className="flex flex-wrap items-center justify-between gap-2 px-4 py-2">
                <span>
                  <span className="font-medium">{i.medicineName}</span>
                  <span className="text-foreground-muted"> · {i.dose} {i.frequency} · {i.durationDays} days</span>
                </span>
                <span className="tabular text-xs text-foreground-muted">
                  {i.remaining} of {i.quantityPrescribed} {i.unit} to give ·{' '}
                  <span className={clsx(i.stockAvailable < i.remaining && 'font-semibold text-danger')}>{i.stockAvailable} in stock</span>
                </span>
              </li>
            ))}
          </ul>
        </article>
      ))}
      {dispensing && <DispenseDialog rx={dispensing} onClose={() => setDispensing(null)} />}
    </div>
  );
}

function DispenseDialog({ rx, onClose }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const open = rx.items.filter((i) => i.remaining > 0);
  const [qty, setQty] = useState(() => Object.fromEntries(open.map((i) => [i.id, String(Math.min(i.remaining, i.stockAvailable))])));
  const [error, setError] = useState(null);
  const lines = open.map((i) => ({ prescriptionItemId: i.id, quantity: Number(qty[i.id] || 0) })).filter((l) => l.quantity > 0);
  const save = useMutation({
    mutationFn: () => pharmacyApi.dispense(rx.id, { items: lines }),
    onSuccess: (r) => {
      queryClient.invalidateQueries({ queryKey: ['pharmacy'] });
      toast.success(`${r.dispensationNo}: ${r.lines.join(', ')}`);
      onClose();
    },
    onError: (err) => setError(toApiError(err).message),
  });
  return (
    <Modal open onClose={onClose} title={`Dispense ${rx.prescriptionNo}`} description={`${rx.patientName} · ${rx.patientMrn}`} size="lg"
           footer={<><Button variant="ghost" onClick={onClose}>Cancel</Button><Button disabled={!lines.length} loading={save.isPending} onClick={() => save.mutate()}>Dispense</Button></>}>
      <div className="flex flex-col gap-3">
        {error && <Alert tone="danger">{error}</Alert>}
        <p className="text-sm text-foreground-muted">Stock is taken from the batch that expires first. Expired batches are never used.</p>
        {open.map((i) => (
          <div key={i.id} className="grid items-end gap-3 rounded-md border border-border p-3 sm:grid-cols-3">
            <div className="sm:col-span-2 text-sm">
              <p className="font-medium">{i.medicineName}</p>
              <p className="text-xs text-foreground-muted tabular">{i.remaining} {i.unit} remaining · {i.stockAvailable} usable in stock</p>
            </div>
            <TextField label="Quantity now" type="number" min={0} max={Math.min(i.remaining, i.stockAvailable)} inputClassName="tabular"
                       value={qty[i.id]} onChange={(e) => setQty((q) => ({ ...q, [i.id]: e.target.value }))} />
          </div>
        ))}
      </div>
    </Modal>
  );
}

// ---------------------------------------------------------------- stock

function StockTab({ params, setParams }) {
  const { hasPermission } = useAuth();
  const [viewing, setViewing] = useState(null);
  const [receiving, setReceiving] = useState(null);
  const [editing, setEditing] = useState(null);
  const categories = useQuery({ queryKey: ['pharmacy', 'categories'], queryFn: pharmacyApi.categories, staleTime: 300_000 });
  const list = useQuery({
    queryKey: ['pharmacy', 'medicines', params],
    queryFn: () => pharmacyApi.medicines({ q: params.q, categoryId: params.categoryId, lowStock: params.lowStock === 'true', page: params.page }),
    placeholderData: keepPreviousData,
  });
  const columns = [
    { key: 'name', header: 'Medicine', render: (m) => (
      <div><p className="font-medium">{m.name}</p><p className="font-mono text-xs text-foreground-subtle">{m.code} · {humanise(m.dosageForm)}</p></div>
    ) },
    { key: 'category', header: 'Category', render: (m) => m.categoryName },
    { key: 'price', header: 'Unit price', align: 'right', render: (m) => formatLKR(m.unitPrice) },
    { key: 'stock', header: 'Usable stock', align: 'right', render: (m) => (
      <span className="inline-flex items-center gap-2">
        {m.lowStock && <Badge tone={m.stockOnHand === 0 ? 'danger' : 'warning'}>{m.stockOnHand === 0 ? 'Out' : 'Low'}</Badge>}
        <span className="tabular">{m.stockOnHand} {m.unit}</span>
      </span>
    ) },
    { key: 'reorder', header: 'Reorder at', align: 'right', render: (m) => m.reorderLevel },
    { key: 'actions', header: <span className="sr-only">Actions</span>, align: 'right', render: (m) => (
      <div className="flex justify-end gap-1">
        {hasPermission('stock:manage') && <Button size="sm" variant="secondary" onClick={(e) => { e.stopPropagation(); setReceiving(m); }}>Receive</Button>}
        {hasPermission('medicine:manage') && <Button size="sm" variant="ghost" onClick={(e) => { e.stopPropagation(); setEditing(m); }}>Edit</Button>}
      </div>
    ) },
  ];
  return (
    <>
      <Toolbar>
        <SearchInput value={params.q} onChange={(q) => setParams({ q })} placeholder="Generic, brand or code" label="Search medicines" />
        <FilterSelect label="Category" value={params.categoryId ?? ''} onChange={(categoryId) => setParams({ categoryId })}
                      options={[{ value: '', label: 'All' }, ...(categories.data ?? []).map((c) => ({ value: String(c.id), label: c.name }))]} />
        <CheckboxField label="Low stock only" checked={params.lowStock === 'true'} onChange={(e) => setParams({ lowStock: e.target.checked ? 'true' : '' })} />
        <div className="flex-1" />
        {hasPermission('medicine:manage') && <Button icon={Plus} onClick={() => setEditing('new')}>New medicine</Button>}
      </Toolbar>
      <DataTable caption="Medicines" columns={columns} page={list.data} loading={list.isFetching}
                 error={list.isError ? toApiError(list.error).message : null}
                 onPageChange={(page) => setParams({ page }, { resetPage: false })} onRowClick={setViewing}
                 emptyTitle={params.lowStock === 'true' ? 'No medicine is below its reorder level' : 'No medicines found'} />
      {viewing && <BatchesDialog medicine={viewing} onClose={() => setViewing(null)} />}
      {receiving && <ReceiveDialog medicine={receiving} onClose={() => setReceiving(null)} />}
      {editing && <MedicineDialog medicine={editing === 'new' ? null : editing} categories={categories.data ?? []} onClose={() => setEditing(null)} />}
    </>
  );
}

function BatchesDialog({ medicine, onClose }) {
  const { hasPermission } = useAuth();
  const [adjusting, setAdjusting] = useState(null);
  const batches = useQuery({ queryKey: ['pharmacy', 'batches', medicine.id], queryFn: () => pharmacyApi.batches(medicine.id) });
  return (
    <Modal open onClose={onClose} title={medicine.name} description={`${medicine.stockOnHand} ${medicine.unit} usable · reorder at ${medicine.reorderLevel}`} size="lg">
      {batches.isLoading && <Spinner label="Loading batches" />}
      {batches.data?.length === 0 && <p className="text-sm text-foreground-muted">No stock received yet.</p>}
      {batches.data?.length > 0 && (
        <div className="relative overflow-x-auto">
          <table className="w-full min-w-[560px] text-left text-sm tabular">
            <thead className="text-xs text-foreground-muted"><tr className="border-b border-border">
              {['Batch', 'Expiry', 'Received', 'On hand', 'Supplier', ''].map((h) => <th key={h} className="py-2 pr-3 font-medium">{h}</th>)}
            </tr></thead>
            <tbody>
              {batches.data.map((b) => (
                <tr key={b.id} className="border-b border-border last:border-0">
                  <td className="py-2 pr-3 font-mono text-xs">{b.batchNo}</td>
                  <td className="py-2 pr-3">{formatDate(b.expiryDate)} <Badge tone={EXPIRY_TONE[b.expiryStatus]} className="ml-1">{humanise(b.expiryStatus)}</Badge></td>
                  <td className="py-2 pr-3">{formatDate(b.receivedDate)}</td>
                  <td className="py-2 pr-3">{b.quantityOnHand} / {b.quantityReceived}</td>
                  <td className="py-2 pr-3 font-sans">{b.supplierName ?? '—'}</td>
                  <td className="py-2 text-right">{hasPermission('stock:manage') && <Button size="sm" variant="ghost" onClick={() => setAdjusting(b)}>Adjust</Button>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {adjusting && <AdjustDialog batch={adjusting} medicineId={medicine.id} onClose={() => setAdjusting(null)} />}
    </Modal>
  );
}

function AdjustDialog({ batch, medicineId, onClose }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [qty, setQty] = useState('');
  const [reason, setReason] = useState('');
  const [error, setError] = useState(null);
  const save = useMutation({
    mutationFn: () => pharmacyApi.adjust({ batchId: batch.id, quantity: Number(qty), reason }),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['pharmacy'] }); toast.success('Stock adjusted.'); onClose(); },
    onError: (err) => setError(toApiError(err).message),
  });
  return (
    <Modal open onClose={onClose} title={`Adjust batch ${batch.batchNo}`} size="sm" description={`${batch.quantityOnHand} on hand. Use a negative number to remove stock.`}
           footer={<><Button variant="ghost" onClick={onClose}>Cancel</Button><Button disabled={!Number(qty) || reason.trim().length < 3} loading={save.isPending} onClick={() => save.mutate()}>Save</Button></>}>
      <div className="flex flex-col gap-4">
        {error && <Alert tone="danger">{error}</Alert>}
        <TextField label="Change in quantity" type="number" data-autofocus value={qty} onChange={(e) => setQty(e.target.value)} placeholder="e.g. -2" inputClassName="tabular" />
        <TextField label="Reason" required value={reason} onChange={(e) => setReason(e.target.value)} placeholder="e.g. Damaged strip, stock count correction" />
      </div>
    </Modal>
  );
}

function ReceiveDialog({ medicine, onClose }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const suppliers = useQuery({ queryKey: ['pharmacy', 'suppliers'], queryFn: pharmacyApi.suppliers });
  const [form, setForm] = useState({ supplierId: '', batchNo: '', manufactureDate: '', expiryDate: '', quantity: '', costPrice: '' });
  const [errors, setErrors] = useState({});
  const [general, setGeneral] = useState(null);
  const set = (k) => (e) => { setForm((f) => ({ ...f, [k]: e.target.value })); setErrors((x) => ({ ...x, [k]: undefined })); };
  const save = useMutation({
    mutationFn: () => pharmacyApi.receive({ ...form, medicineId: medicine.id, supplierId: form.supplierId || null, manufactureDate: form.manufactureDate || null }),
    onSuccess: (b) => { queryClient.invalidateQueries({ queryKey: ['pharmacy'] }); toast.success(`Received ${b.quantityReceived} ${medicine.unit} (batch ${b.batchNo}).`); onClose(); },
    onError: (err) => { const s = splitApiError(err); setErrors(s.fields); setGeneral(s.general); focusFirstInvalid(); },
  });
  const submit = () => {
    const errs = requireFields(form, { batchNo: 'Batch number', expiryDate: 'Expiry date', quantity: 'Quantity', costPrice: 'Cost price' });
    setErrors(errs);
    if (Object.keys(errs).length) focusFirstInvalid(); else save.mutate();
  };
  return (
    <Modal open onClose={onClose} title={`Receive ${medicine.name}`} size="lg"
           footer={<><Button variant="ghost" onClick={onClose}>Cancel</Button><Button icon={PackagePlus} loading={save.isPending} onClick={submit}>Receive stock</Button></>}>
      <div className="grid gap-4 sm:grid-cols-3">
        {general && <Alert tone="danger" className="sm:col-span-3">{general.message}</Alert>}
        <TextField label="Batch number" required data-autofocus value={form.batchNo} onChange={set('batchNo')} error={errors.batchNo} inputClassName="font-mono uppercase" />
        <TextField label="Expiry date" type="date" required min={todayInHospital()} value={form.expiryDate} onChange={set('expiryDate')} error={errors.expiryDate} />
        <TextField label="Manufactured" type="date" max={todayInHospital()} value={form.manufactureDate} onChange={set('manufactureDate')} error={errors.manufactureDate} />
        <TextField label={`Quantity (${medicine.unit})`} type="number" min={1} required value={form.quantity} onChange={set('quantity')} error={errors.quantity} inputClassName="tabular" />
        <TextField label="Cost per unit (LKR)" inputMode="decimal" required value={form.costPrice} onChange={set('costPrice')} error={errors.costPrice} inputClassName="tabular" />
        <SelectField label="Supplier" placeholder="Not recorded" value={form.supplierId} onChange={set('supplierId')}
                     options={(suppliers.data ?? []).filter((s) => s.active).map((s) => ({ value: String(s.id), label: s.name }))} />
      </div>
    </Modal>
  );
}

function MedicineDialog({ medicine, categories, onClose }) {
  const isNew = !medicine;
  const queryClient = useQueryClient();
  const toast = useToast();
  const [form, setForm] = useState({
    code: medicine?.code ?? '', genericName: medicine?.genericName ?? '', brandName: medicine?.brandName ?? '',
    categoryId: medicine ? String(medicine.categoryId) : '', dosageForm: medicine?.dosageForm ?? 'TABLET', strength: medicine?.strength ?? '',
    unit: medicine?.unit ?? 'tablet', unitPrice: medicine ? String(medicine.unitPrice) : '', reorderLevel: medicine ? String(medicine.reorderLevel) : '0',
    prescriptionRequired: medicine?.prescriptionRequired ?? true, active: medicine?.active ?? true,
  });
  const [newCategory, setNewCategory] = useState('');
  const [errors, setErrors] = useState({});
  const [general, setGeneral] = useState(null);
  const set = (k) => (e) => { const v = e.target.type === 'checkbox' ? e.target.checked : e.target.value; setForm((f) => ({ ...f, [k]: v })); setErrors((x) => ({ ...x, [k]: undefined })); };
  const save = useMutation({
    mutationFn: async () => {
      let categoryId = form.categoryId;
      if (categoryId === 'new') categoryId = (await pharmacyApi.addCategory({ name: newCategory })).id;
      const body = { ...form, categoryId: Number(categoryId), brandName: form.brandName || null, reorderLevel: Number(form.reorderLevel), version: medicine?.version };
      return isNew ? pharmacyApi.createMedicine(body) : pharmacyApi.updateMedicine(medicine.id, body);
    },
    onSuccess: (m) => { queryClient.invalidateQueries({ queryKey: ['pharmacy'] }); toast.success(`${m.name} saved.`); onClose(); },
    onError: (err) => { const s = splitApiError(err); setErrors(s.fields); setGeneral(s.general); focusFirstInvalid(); },
  });
  useEffect(() => { if (isNew && categories.length && !form.categoryId) setForm((f) => ({ ...f, categoryId: String(categories[0].id) })); }, [categories, isNew, form.categoryId]);
  return (
    <Modal open onClose={onClose} title={isNew ? 'New medicine' : `Edit ${medicine.name}`} size="lg"
           footer={<><Button variant="ghost" onClick={onClose}>Cancel</Button><Button loading={save.isPending} onClick={() => save.mutate()}>Save</Button></>}>
      <div className="grid gap-4 sm:grid-cols-3">
        {general && <Alert tone="danger" className="sm:col-span-3">{general.message}</Alert>}
        <TextField label="Code" required disabled={!isNew} value={form.code} onChange={set('code')} error={errors.code} inputClassName="font-mono uppercase" data-autofocus />
        <TextField label="Generic name" required value={form.genericName} onChange={set('genericName')} error={errors.genericName} hint="Used for allergy checks." />
        <TextField label="Brand name" value={form.brandName} onChange={set('brandName')} error={errors.brandName} />
        <SelectField label="Category" required value={form.categoryId} onChange={set('categoryId')} error={errors.categoryId}
                     options={[...categories.map((c) => ({ value: String(c.id), label: c.name })), { value: 'new', label: '+ New category…' }]} />
        {form.categoryId === 'new' && <TextField label="New category name" required value={newCategory} onChange={(e) => setNewCategory(e.target.value)} />}
        <SelectField label="Dosage form" value={form.dosageForm} onChange={set('dosageForm')} options={DOSAGE_FORMS.map((v) => ({ value: v, label: humanise(v) }))} />
        <TextField label="Strength" required value={form.strength} onChange={set('strength')} error={errors.strength} placeholder="500 mg" />
        <TextField label="Dispensing unit" required value={form.unit} onChange={set('unit')} error={errors.unit} placeholder="tablet, ml, vial" />
        <TextField label="Selling price per unit (LKR)" required inputMode="decimal" value={form.unitPrice} onChange={set('unitPrice')} error={errors.unitPrice} inputClassName="tabular" />
        <TextField label="Reorder level" type="number" min={0} value={form.reorderLevel} onChange={set('reorderLevel')} error={errors.reorderLevel} inputClassName="tabular" />
        <CheckboxField label="Prescription required" checked={form.prescriptionRequired} onChange={set('prescriptionRequired')} />
        <CheckboxField label="Active" description="Inactive medicines cannot be prescribed." checked={form.active} onChange={set('active')} />
      </div>
    </Modal>
  );
}

// ---------------------------------------------------------------- alerts

function AlertsTab() {
  const { hasPermission } = useAuth();
  const queryClient = useQueryClient();
  const toast = useToast();
  const [confirm, setConfirm] = useState(false);
  const alerts = useQuery({ queryKey: ['pharmacy', 'alerts'], queryFn: pharmacyApi.alerts });
  const writeOff = useMutation({
    mutationFn: pharmacyApi.writeOffExpired,
    onSuccess: (r) => { setConfirm(false); queryClient.invalidateQueries({ queryKey: ['pharmacy'] }); toast.success(`${r.batches} expired batch(es) written off.`); },
  });
  if (alerts.isLoading) return <Spinner label="Loading alerts" />;
  if (alerts.isError) return <Alert tone="danger">{toApiError(alerts.error).message}</Alert>;
  const a = alerts.data;
  const batchList = (list, empty) => (list.length === 0 ? <p className="text-sm text-foreground-muted">{empty}</p> : (
    <ul className="divide-y divide-border text-sm">
      {list.map((b) => (
        <li key={b.id} className="flex flex-wrap justify-between gap-2 py-2">
          <span>{b.medicineName} <span className="font-mono text-xs text-foreground-subtle">{b.batchNo}</span></span>
          <span className="tabular">{b.quantityOnHand} left · expires {formatDate(b.expiryDate)}</span>
        </li>
      ))}
    </ul>
  ));
  return (
    <div className="grid gap-6 lg:grid-cols-3">
      <Card>
        <CardHeader icon={AlertTriangle} title="Low stock" description="At or below the reorder level" />
        <CardBody>
          {a.lowStock.length === 0 ? <p className="text-sm text-foreground-muted">All medicines are above their reorder level.</p> : (
            <ul className="divide-y divide-border text-sm">
              {a.lowStock.map((m) => (
                <li key={m.id} className="flex justify-between gap-2 py-2">
                  <span>{m.name}</span>
                  <span className={clsx('tabular', m.stockOnHand === 0 ? 'font-semibold text-danger' : 'text-warning')}>{m.stockOnHand} / {m.reorderLevel}</span>
                </li>
              ))}
            </ul>
          )}
        </CardBody>
      </Card>
      <Card>
        <CardHeader title={`Expiring within ${a.expiryWindowDays} days`} />
        <CardBody>{batchList(a.expiringSoon, 'Nothing expires soon.')}</CardBody>
      </Card>
      <Card>
        <CardHeader title="Expired, still on the shelf"
                    action={hasPermission('stock:manage') && a.expiredWithStock.length > 0 && <Button size="sm" variant="danger" onClick={() => setConfirm(true)}>Write off</Button>} />
        <CardBody>{batchList(a.expiredWithStock, 'No expired stock.')}</CardBody>
      </Card>
      {confirm && (
        <ConfirmDialog open title="Write off all expired stock?" confirmLabel="Write off" loading={writeOff.isPending}
                       message={`${a.expiredWithStock.length} batch(es) will be set to zero and recorded in the stock ledger. This cannot be undone.`}
                       onClose={() => setConfirm(false)} onConfirm={() => writeOff.mutate()} />
      )}
    </div>
  );
}

// ---------------------------------------------------------------- ledger & suppliers

function LedgerTab({ params, setParams }) {
  const tx = useQuery({ queryKey: ['pharmacy', 'ledger', params], queryFn: () => pharmacyApi.transactions({ type: params.type, page: params.page }), placeholderData: keepPreviousData });
  const columns = [
    { key: 'when', header: 'When', render: (t) => <span className="tabular whitespace-nowrap">{formatDateTime(t.createdAt)}</span> },
    { key: 'medicine', header: 'Medicine', render: (t) => <div><p>{t.medicineName}</p><p className="font-mono text-xs text-foreground-subtle">{t.batchNo}</p></div> },
    { key: 'type', header: 'Movement', render: (t) => <Badge tone={t.quantity > 0 ? 'success' : 'neutral'}>{humanise(t.type)}</Badge> },
    { key: 'qty', header: 'Quantity', align: 'right', render: (t) => (t.quantity > 0 ? `+${t.quantity}` : t.quantity) },
    { key: 'balance', header: 'Balance', align: 'right', render: (t) => t.balanceAfter },
    { key: 'by', header: 'By', render: (t) => <span className="text-foreground-muted">{t.performedByName ?? 'system'}{t.note ? ` · ${t.note}` : ''}</span> },
  ];
  return (
    <>
      <Toolbar>
        <FilterSelect label="Movement" value={params.type ?? ''} onChange={(type) => setParams({ type })}
                      options={[{ value: '', label: 'All' }, ...['RECEIPT', 'DISPENSE', 'ADJUSTMENT_IN', 'ADJUSTMENT_OUT', 'RETURN', 'EXPIRED_WRITE_OFF'].map((v) => ({ value: v, label: humanise(v) }))]} />
      </Toolbar>
      <DataTable caption="Stock ledger" columns={columns} page={tx.data} loading={tx.isFetching}
                 error={tx.isError ? toApiError(tx.error).message : null}
                 onPageChange={(page) => setParams({ page }, { resetPage: false })} emptyTitle="No stock movements yet" />
    </>
  );
}

function SuppliersTab() {
  const [editing, setEditing] = useState(null);
  const suppliers = useQuery({ queryKey: ['pharmacy', 'suppliers'], queryFn: pharmacyApi.suppliers });
  const columns = [
    { key: 'name', header: 'Supplier', render: (s) => <span className="font-medium">{s.name}</span> },
    { key: 'contact', header: 'Contact', render: (s) => <span className="text-foreground-muted">{[s.contactPerson, s.phone, s.email].filter(Boolean).join(' · ')}</span> },
    { key: 'reg', header: 'Registration', render: (s) => s.registrationNo ?? '—' },
    { key: 'status', header: 'Status', render: (s) => (s.active ? <Badge tone="success">Active</Badge> : <Badge>Inactive</Badge>) },
  ];
  const page = suppliers.data && { content: suppliers.data, page: 0, size: suppliers.data.length, totalElements: suppliers.data.length, totalPages: 1 };
  return (
    <>
      <Toolbar><div className="flex-1" /><Button icon={Plus} onClick={() => setEditing('new')}>New supplier</Button></Toolbar>
      <DataTable caption="Suppliers" columns={columns} page={page} loading={suppliers.isFetching} onRowClick={setEditing}
                 error={suppliers.isError ? toApiError(suppliers.error).message : null} emptyTitle="No suppliers yet" />
      {editing && <SupplierDialog supplier={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />}
    </>
  );
}

function SupplierDialog({ supplier, onClose }) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState({
    name: supplier?.name ?? '', contactPerson: supplier?.contactPerson ?? '', phone: supplier?.phone ?? '', email: supplier?.email ?? '',
    address: supplier?.address ?? '', registrationNo: supplier?.registrationNo ?? '', active: supplier?.active ?? true,
  });
  const [errors, setErrors] = useState({});
  const [general, setGeneral] = useState(null);
  const set = (k) => (e) => { const v = e.target.type === 'checkbox' ? e.target.checked : e.target.value; setForm((f) => ({ ...f, [k]: v })); };
  const save = useMutation({
    mutationFn: () => pharmacyApi.saveSupplier(supplier?.id, { ...form, email: form.email || null }),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['pharmacy', 'suppliers'] }); onClose(); },
    onError: (err) => { const s = splitApiError(err); setErrors(s.fields); setGeneral(s.general); focusFirstInvalid(); },
  });
  return (
    <Modal open onClose={onClose} title={supplier ? `Edit ${supplier.name}` : 'New supplier'}
           footer={<><Button variant="ghost" onClick={onClose}>Cancel</Button><Button loading={save.isPending} onClick={() => save.mutate()}>Save</Button></>}>
      <div className="grid gap-4 sm:grid-cols-2">
        {general && <Alert tone="danger" className="sm:col-span-2">{general.message}</Alert>}
        <TextField className="sm:col-span-2" label="Name" required data-autofocus value={form.name} onChange={set('name')} error={errors.name} />
        <TextField label="Contact person" value={form.contactPerson} onChange={set('contactPerson')} />
        <TextField label="Phone" required value={form.phone} onChange={set('phone')} error={errors.phone} />
        <TextField label="Email" type="email" value={form.email} onChange={set('email')} error={errors.email} />
        <TextField label="Registration no." value={form.registrationNo} onChange={set('registrationNo')} hint="NMRA or business registration" />
        <TextField className="sm:col-span-2" label="Address" value={form.address} onChange={set('address')} />
        <CheckboxField label="Active" checked={form.active} onChange={set('active')} />
      </div>
    </Modal>
  );
}
