import { useState } from 'react';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, Ban, FilePlus2, Printer, ReceiptText, Send, Wallet } from 'lucide-react';
import { PAYMENT_METHODS, billingApi } from '../../api/billing';
import { toApiError } from '../../api/client';
import { useAuth } from '../../auth/AuthProvider';
import { PageHeader } from '../../components/layout/AppShell';
import { Card, CardBody, CardHeader } from '../../components/ui/Card';
import { DataTable } from '../../components/ui/DataTable';
import { FilterSelect, SearchInput, Toolbar } from '../../components/ui/Toolbar';
import { StatusBadge, humanise } from '../../components/ui/Badge';
import { Button, LinkButton } from '../../components/ui/Button';
import { Alert } from '../../components/ui/Alert';
import { ConfirmDialog, Modal } from '../../components/ui/Modal';
import { SelectField, TextField } from '../../components/ui/Field';
import { Spinner } from '../../components/ui/Spinner';
import { useToast } from '../../components/ui/Toast';
import { formatDate, formatDateTime, formatLKR } from '../../lib/format';
import { focusFirstInvalid, splitApiError } from '../../lib/forms';
import { useListParams } from '../../lib/useListParams';

const STATUS_OPTIONS = [{ value: '', label: 'All' }, ...['DRAFT', 'ISSUED', 'PARTIALLY_PAID', 'PAID', 'VOID'].map((s) => ({ value: s, label: humanise(s) }))];
const money = (n) => Number(n ?? 0);

function useInvalidate() {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: ['billing'] });
}

// ---------------------------------------------------------------- invoice list

const invoiceColumns = [
  { key: 'no', header: 'Invoice', render: (i) => <span className="font-mono text-xs">{i.invoiceNo}</span> },
  { key: 'patient', header: 'Patient', render: (i) => <div><p className="font-medium">{i.patientName}</p><p className="font-mono text-xs text-foreground-subtle">{i.patientMrn}</p></div> },
  { key: 'date', header: 'Date', render: (i) => <span className="whitespace-nowrap">{formatDateTime(i.issuedAt ?? i.createdAt)}</span> },
  { key: 'total', header: 'Total', align: 'right', render: (i) => <span className="tabular">{formatLKR(i.totalAmount)}</span> },
  { key: 'balance', header: 'Balance due', align: 'right', render: (i) => <span className="tabular font-medium">{i.status === 'VOID' ? '—' : formatLKR(i.balanceDue)}</span> },
  { key: 'status', header: 'Status', render: (i) => <StatusBadge status={i.status} /> },
];

export function BillingPage() {
  const navigate = useNavigate();
  const [params, setParams] = useListParams({});
  const list = useQuery({
    queryKey: ['billing', 'invoices', params],
    queryFn: () => billingApi.invoices({ q: params.q, status: params.status, from: params.from, to: params.to, page: params.page }),
    placeholderData: keepPreviousData,
  });
  return (
    <>
      <PageHeader title="Billing" description="Invoices, payments and refunds. New invoices are made from a patient's Billing tab, where their charges collect." />
      <Toolbar>
        <SearchInput value={params.q} onChange={(q) => setParams({ q })} placeholder="Invoice no., MRN or name" label="Search invoices" />
        <FilterSelect label="Status" value={params.status ?? ''} onChange={(status) => setParams({ status })} options={STATUS_OPTIONS} />
        <TextField label="From" type="date" className="w-40" value={params.from ?? ''} onChange={(e) => setParams({ from: e.target.value })} />
        <TextField label="To" type="date" className="w-40" value={params.to ?? ''} onChange={(e) => setParams({ to: e.target.value })} />
      </Toolbar>
      <DataTable caption="Invoices" columns={invoiceColumns} page={list.data} loading={list.isFetching}
                 error={list.isError ? toApiError(list.error).message : null}
                 onPageChange={(page) => setParams({ page }, { resetPage: false })}
                 onRowClick={(i) => navigate(`/billing/invoices/${i.id}`)} emptyTitle="No invoices match" />
    </>
  );
}

// ---------------------------------------------------------------- patient billing tab

export function PatientBillingPanel({ patientId }) {
  const { hasPermission } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();
  const invalidate = useInvalidate();
  const [selected, setSelected] = useState(null); // null = all
  const [discount, setDiscount] = useState({ amount: '', reason: '' });
  const [error, setError] = useState(null);
  const [addingCharge, setAddingCharge] = useState(false);
  const charges = useQuery({ queryKey: ['billing', 'unbilled', patientId], queryFn: () => billingApi.unbilled(patientId) });
  const invoices = useQuery({ queryKey: ['billing', 'invoices', { patientId }], queryFn: () => billingApi.invoices({ patientId, size: 50 }) });
  const rows = charges.data ?? [];
  const chosen = selected ?? new Set(rows.map((c) => c.id));
  const subtotal = rows.filter((c) => chosen.has(c.id)).reduce((s, c) => s + money(c.amount), 0);
  const create = useMutation({
    mutationFn: () => billingApi.create({ patientId, chargeIds: [...chosen], discountAmount: discount.amount || null, discountReason: discount.reason || null }),
    onSuccess: (inv) => { invalidate(); toast.success(`Draft ${inv.invoiceNo} created. Review it, then issue.`); navigate(`/billing/invoices/${inv.id}`); },
    onError: (err) => setError(toApiError(err).message),
  });
  const toggle = (id) => setSelected(() => { const n = new Set(chosen); if (n.has(id)) n.delete(id); else n.add(id); return n; });
  const discountInvalid = money(discount.amount) > 0 && discount.reason.trim().length < 3;

  return (
    <div className="flex flex-col gap-6">
      <Card>
        <CardHeader title="Charges not yet invoiced" icon={ReceiptText}
                    action={hasPermission('charge:create') && <Button size="sm" variant="secondary" onClick={() => setAddingCharge(true)}>Add service charge</Button>} />
        <CardBody>
          {charges.isLoading && <Spinner label="Loading charges" />}
          {charges.isError && <Alert tone="danger">{toApiError(charges.error).message}</Alert>}
          {charges.data && rows.length === 0 && <p className="text-sm text-foreground-muted">Nothing to bill. Consultations, lab tests, medicines and bed-days appear here as they happen.</p>}
          {rows.length > 0 && (
            <>
              <div className="relative overflow-x-auto">
                <table className="w-full min-w-[560px] text-left text-sm">
                  <thead className="text-xs text-foreground-muted"><tr className="border-b border-border">
                    <th className="w-8 py-2"><span className="sr-only">Include</span></th>
                    <th className="py-2 pr-3 font-medium">Item</th><th className="py-2 pr-3 font-medium">Date</th>
                    <th className="py-2 pr-3 text-right font-medium">Qty</th><th className="py-2 pr-3 text-right font-medium">Rate</th><th className="py-2 text-right font-medium">Amount</th>
                  </tr></thead>
                  <tbody className="tabular">
                    {rows.map((c) => (
                      <tr key={c.id} className="border-b border-border last:border-0">
                        <td className="py-2"><input type="checkbox" aria-label={`Include ${c.description}`} checked={chosen.has(c.id)} onChange={() => toggle(c.id)} className="h-4 w-4 accent-[var(--color-primary)]" /></td>
                        <td className="py-2 pr-3 font-sans"><span className="font-medium">{c.description}</span> <span className="text-xs text-foreground-muted">{humanise(c.category)}</span></td>
                        <td className="whitespace-nowrap py-2 pr-3 text-xs">{formatDateTime(c.serviceDate)}</td>
                        <td className="py-2 pr-3 text-right">{money(c.quantity)}</td>
                        <td className="py-2 pr-3 text-right">{formatLKR(c.unitPrice)}</td>
                        <td className="py-2 text-right">{formatLKR(c.amount)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              {hasPermission('invoice:create') && (
                <div className="mt-4 flex flex-col gap-3 border-t border-border pt-4 sm:flex-row sm:items-end">
                  <TextField label="Discount (LKR)" inputMode="decimal" className="sm:w-40" inputClassName="tabular" value={discount.amount} onChange={(e) => setDiscount((d) => ({ ...d, amount: e.target.value }))} />
                  <TextField label="Discount reason" className="flex-1" value={discount.reason} required={money(discount.amount) > 0}
                             error={discountInvalid ? 'Give a reason for the discount.' : undefined} onChange={(e) => setDiscount((d) => ({ ...d, reason: e.target.value }))} />
                  <div className="flex items-center gap-3">
                    <p className="tabular text-sm"><span className="text-foreground-muted">Selected</span> <span className="font-semibold">{formatLKR(subtotal)}</span></p>
                    <Button icon={FilePlus2} disabled={chosen.size === 0 || discountInvalid} loading={create.isPending} onClick={() => { setError(null); create.mutate(); }}>Create invoice</Button>
                  </div>
                </div>
              )}
              {error && <Alert tone="danger" className="mt-3">{error}</Alert>}
            </>
          )}
        </CardBody>
      </Card>
      <section>
        <h2 className="mb-2 text-base">Invoices</h2>
        <DataTable caption="Patient invoices" columns={invoiceColumns.filter((c) => c.key !== 'patient')} page={invoices.data} loading={invoices.isFetching}
                   error={invoices.isError ? toApiError(invoices.error).message : null} onPageChange={() => {}}
                   onRowClick={(i) => navigate(`/billing/invoices/${i.id}`)} emptyTitle="No invoices yet" />
      </section>
      {addingCharge && <ServiceChargeDialog patientId={patientId} onClose={() => setAddingCharge(false)} />}
    </div>
  );
}

function ServiceChargeDialog({ patientId, onClose }) {
  const toast = useToast();
  const invalidate = useInvalidate();
  const services = useQuery({ queryKey: ['billing', 'services'], queryFn: billingApi.services, staleTime: 300_000 });
  const [form, setForm] = useState({ serviceId: '', quantity: '1' });
  const [error, setError] = useState(null);
  const save = useMutation({
    mutationFn: () => billingApi.addCharge({ patientId, serviceId: Number(form.serviceId), quantity: form.quantity }),
    onSuccess: (c) => { invalidate(); toast.success(`${c.description} added (${formatLKR(c.amount)}).`); onClose(); },
    onError: (err) => setError(toApiError(err).message),
  });
  return (
    <Modal open onClose={onClose} title="Add service charge" description="For services that are not charged automatically, such as procedures or imaging."
           footer={<><Button variant="ghost" onClick={onClose}>Cancel</Button><Button disabled={!form.serviceId || !(money(form.quantity) > 0)} loading={save.isPending} onClick={() => save.mutate()}>Add charge</Button></>}>
      <div className="flex flex-col gap-4">
        {error && <Alert tone="danger">{error}</Alert>}
        <SelectField label="Service" required data-autofocus placeholder="Choose a service" value={form.serviceId} onChange={(e) => setForm((f) => ({ ...f, serviceId: e.target.value }))}
                     options={(services.data ?? []).map((s) => ({ value: String(s.id), label: `${s.name} · ${formatLKR(s.price)}` }))} />
        <TextField label="Quantity" type="number" min={1} step="1" className="w-32" inputClassName="tabular" value={form.quantity} onChange={(e) => setForm((f) => ({ ...f, quantity: e.target.value }))} />
      </div>
    </Modal>
  );
}

// ---------------------------------------------------------------- invoice detail (printable)

export function InvoicePage() {
  const { id } = useParams();
  const { hasPermission } = useAuth();
  const toast = useToast();
  const invalidate = useInvalidate();
  const [paying, setPaying] = useState(false);
  const [voiding, setVoiding] = useState(false);
  const [refunding, setRefunding] = useState(null);
  const invoice = useQuery({ queryKey: ['billing', 'invoice', id], queryFn: () => billingApi.invoice(id) });
  const issue = useMutation({
    mutationFn: () => billingApi.issue(id),
    onSuccess: (i) => { invalidate(); toast.success(`${i.invoiceNo} issued.`); },
    onError: (err) => toast.error(toApiError(err).message),
  });
  const voidInv = useMutation({
    mutationFn: (reason) => billingApi.void(id, reason),
    onSuccess: () => { invalidate(); toast.success('Invoice voided. Its charges can be billed again.'); setVoiding(false); },
  });
  if (invoice.isLoading) return <Spinner label="Loading invoice" />;
  if (invoice.isError) return <Alert tone="danger">{toApiError(invoice.error).message}</Alert>;
  const inv = invoice.data;
  const payable = ['ISSUED', 'PARTIALLY_PAID'].includes(inv.status);

  return (
    <>
      <div className="no-print mb-4 flex flex-wrap items-center justify-between gap-2">
        <Link to="/billing" className="inline-flex items-center gap-1 text-sm text-foreground-muted hover:text-foreground"><ArrowLeft className="h-4 w-4" aria-hidden="true" /> Billing</Link>
        <div className="flex flex-wrap gap-2">
          {inv.status === 'DRAFT' && hasPermission('invoice:create') && <Button icon={Send} loading={issue.isPending} onClick={() => issue.mutate()}>Issue invoice</Button>}
          {payable && hasPermission('payment:record') && <Button icon={Wallet} onClick={() => setPaying(true)}>Record payment</Button>}
          {inv.status !== 'VOID' && inv.status !== 'PAID' && hasPermission('invoice:void') && <Button variant="ghost" icon={Ban} onClick={() => setVoiding(true)}>Void</Button>}
          <Button variant="secondary" icon={Printer} onClick={() => window.print()}>Print</Button>
        </div>
      </div>
      <section className="print-sheet mx-auto max-w-3xl rounded-lg border border-border bg-surface p-8 shadow-sm">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <h1 className="text-xl">{inv.status === 'DRAFT' ? 'Draft invoice' : 'Invoice'} <span className="font-mono text-base font-normal">{inv.invoiceNo}</span></h1>
            <p className="mt-1 text-sm text-foreground-muted">{inv.issuedAt ? `Issued ${formatDateTime(inv.issuedAt)}` : `Created ${formatDateTime(inv.createdAt)}`}</p>
          </div>
          <StatusBadge status={inv.status} />
        </div>
        <dl className="mt-4 grid grid-cols-2 gap-x-8 gap-y-1 text-sm">
          <div className="flex gap-2"><dt className="text-foreground-muted">Patient</dt><dd className="font-medium"><Link to={`/patients/${inv.patientId}`} className="hover:underline">{inv.patientName}</Link></dd></div>
          <div className="flex gap-2"><dt className="text-foreground-muted">MRN</dt><dd className="font-mono">{inv.patientMrn}</dd></div>
        </dl>
        {inv.voidReason && <Alert tone="danger" className="mt-4">Voided: {inv.voidReason}</Alert>}
        <table className="mt-6 w-full text-left text-sm tabular">
          <thead className="border-b-2 border-border text-xs text-foreground-muted">
            <tr><th className="py-2 pr-3 font-medium">Item</th><th className="py-2 pr-3 text-right font-medium">Qty</th><th className="py-2 pr-3 text-right font-medium">Rate</th><th className="py-2 text-right font-medium">Amount</th></tr>
          </thead>
          <tbody>
            {inv.items.map((l) => (
              <tr key={l.id} className="border-b border-border align-top">
                <td className="py-2 pr-3 font-sans">{l.description} <span className="text-xs text-foreground-muted">{humanise(l.category)}</span></td>
                <td className="py-2 pr-3 text-right">{money(l.quantity)}</td>
                <td className="py-2 pr-3 text-right">{formatLKR(l.unitPrice)}</td>
                <td className="py-2 text-right">{formatLKR(l.lineTotal)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <dl className="ml-auto mt-4 w-full max-w-xs text-sm tabular">
          <Row label="Subtotal" value={formatLKR(inv.subtotal)} />
          {money(inv.discountAmount) > 0 && <Row label={`Discount${inv.discountReason ? ` (${inv.discountReason})` : ''}`} value={`− ${formatLKR(inv.discountAmount)}`} />}
          {money(inv.taxAmount) > 0 && <Row label="Tax" value={formatLKR(inv.taxAmount)} />}
          <Row label="Total" value={formatLKR(inv.totalAmount)} strong />
          <Row label="Paid" value={formatLKR(inv.amountPaid)} />
          <Row label="Balance due" value={formatLKR(inv.balanceDue)} strong />
        </dl>
        {inv.payments.length > 0 && (
          <>
            <h2 className="mt-8 text-base">Payments</h2>
            <table className="mt-2 w-full text-left text-sm tabular">
              <thead className="border-b border-border text-xs text-foreground-muted">
                <tr><th className="py-2 pr-3 font-medium">Receipt</th><th className="py-2 pr-3 font-medium">Date</th><th className="py-2 pr-3 font-medium">Method</th><th className="py-2 pr-3 text-right font-medium">Amount</th><th className="no-print py-2"><span className="sr-only">Actions</span></th></tr>
              </thead>
              <tbody>
                {inv.payments.map((p) => (
                  <tr key={p.id} className="border-b border-border last:border-0">
                    <td className="py-2 pr-3 font-mono text-xs">{p.receiptNo}</td>
                    <td className="whitespace-nowrap py-2 pr-3 text-xs">{formatDateTime(p.receivedAt)}</td>
                    <td className="py-2 pr-3 font-sans">{p.refundOfPaymentId ? 'Refund' : humanise(p.method)}{p.referenceNo && <span className="text-xs text-foreground-muted"> · {p.referenceNo}</span>}</td>
                    <td className="py-2 pr-3 text-right">{formatLKR(p.amount)}</td>
                    <td className="no-print py-2 text-right">
                      <div className="flex justify-end gap-1">
                        <LinkButton size="sm" variant="ghost" to={`/billing/invoices/${inv.id}/receipts/${p.id}`}>Receipt</LinkButton>
                        {!p.refundOfPaymentId && money(p.refundable) > 0 && hasPermission('payment:refund') && <Button size="sm" variant="ghost" onClick={() => setRefunding(p)}>Refund</Button>}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </>
        )}
      </section>
      {paying && <PaymentDialog invoice={inv} onClose={() => setPaying(false)} />}
      {refunding && <RefundDialog payment={refunding} onClose={() => setRefunding(null)} />}
      <ConfirmDialog open={voiding} onClose={() => setVoiding(false)} onConfirm={(r) => voidInv.mutate(r)} loading={voidInv.isPending}
                     error={voidInv.isError ? toApiError(voidInv.error).message : null} requireReason title={`Void ${inv.invoiceNo}`}
                     message="The invoice stays on record as void and its charges return to the patient's unbilled list." confirmLabel="Void invoice" />
    </>
  );
}

function Row({ label, value, strong }) {
  return (
    <div className={strong ? 'flex justify-between border-t border-border py-1.5 font-semibold' : 'flex justify-between py-1'}>
      <dt className={strong ? '' : 'text-foreground-muted'}>{label}</dt><dd>{value}</dd>
    </div>
  );
}

function PaymentDialog({ invoice, onClose }) {
  const toast = useToast();
  const invalidate = useInvalidate();
  const [form, setForm] = useState({ amount: String(invoice.balanceDue), method: 'CASH', referenceNo: '', notes: '' });
  const [errors, setErrors] = useState({});
  const [general, setGeneral] = useState(null);
  const set = (k) => (e) => { setForm((f) => ({ ...f, [k]: e.target.value })); setErrors((x) => ({ ...x, [k]: undefined })); };
  const save = useMutation({
    mutationFn: () => billingApi.pay(invoice.id, form),
    onSuccess: (inv) => { invalidate(); toast.success(`Payment recorded. Balance due ${formatLKR(inv.balanceDue)}.`); onClose(); },
    onError: (err) => { const s = splitApiError(err); setErrors(s.fields); setGeneral(s.general); focusFirstInvalid(); },
  });
  const submit = () => {
    const errs = {};
    if (!(money(form.amount) > 0)) errs.amount = 'Enter an amount.';
    else if (money(form.amount) > money(invoice.balanceDue)) errs.amount = `No more than the balance due (${formatLKR(invoice.balanceDue)}).`;
    if (form.method !== 'CASH' && !form.referenceNo.trim()) errs.referenceNo = 'Card, transfer and cheque payments need a reference.';
    setErrors(errs);
    if (Object.keys(errs).length) focusFirstInvalid(); else save.mutate();
  };
  return (
    <Modal open onClose={onClose} title={`Record payment for ${invoice.invoiceNo}`} description={`Balance due ${formatLKR(invoice.balanceDue)}`}
           footer={<><Button variant="ghost" onClick={onClose}>Cancel</Button><Button icon={Wallet} loading={save.isPending} onClick={submit}>Record payment</Button></>}>
      <div className="grid gap-4 sm:grid-cols-2">
        {general && <Alert tone="danger" className="sm:col-span-2">{general.message}</Alert>}
        <TextField label="Amount (LKR)" required data-autofocus inputMode="decimal" inputClassName="tabular" value={form.amount} onChange={set('amount')} error={errors.amount} />
        <SelectField label="Method" required value={form.method} onChange={set('method')} options={PAYMENT_METHODS} />
        <TextField label="Reference" required={form.method !== 'CASH'} value={form.referenceNo} onChange={set('referenceNo')} error={errors.referenceNo}
                   hint={form.method === 'CARD' ? 'Approval code or last four digits only.' : undefined} maxLength={60} />
        <TextField label="Notes" value={form.notes} onChange={set('notes')} maxLength={255} />
      </div>
    </Modal>
  );
}

function RefundDialog({ payment, onClose }) {
  const toast = useToast();
  const invalidate = useInvalidate();
  const [form, setForm] = useState({ amount: String(payment.refundable), reason: '' });
  const [error, setError] = useState(null);
  const save = useMutation({
    mutationFn: () => billingApi.refund(payment.id, form),
    onSuccess: () => { invalidate(); toast.success('Refund recorded.'); onClose(); },
    onError: (err) => setError(toApiError(err).message),
  });
  const ok = money(form.amount) > 0 && money(form.amount) <= money(payment.refundable) && form.reason.trim().length >= 3;
  return (
    <Modal open onClose={onClose} title={`Refund ${payment.receiptNo}`} description={`Up to ${formatLKR(payment.refundable)} can be refunded.`} size="sm"
           footer={<><Button variant="ghost" onClick={onClose}>Cancel</Button><Button variant="danger" disabled={!ok} loading={save.isPending} onClick={() => save.mutate()}>Record refund</Button></>}>
      <div className="flex flex-col gap-4">
        {error && <Alert tone="danger">{error}</Alert>}
        <TextField label="Amount (LKR)" required data-autofocus inputMode="decimal" inputClassName="tabular" value={form.amount} onChange={(e) => setForm((f) => ({ ...f, amount: e.target.value }))} />
        <TextField label="Reason" required value={form.reason} onChange={(e) => setForm((f) => ({ ...f, reason: e.target.value }))} maxLength={255} />
      </div>
    </Modal>
  );
}

// ---------------------------------------------------------------- receipt

export function ReceiptPrintPage() {
  const { id, paymentId } = useParams();
  const invoice = useQuery({ queryKey: ['billing', 'invoice', id], queryFn: () => billingApi.invoice(id) });
  if (invoice.isLoading) return <Spinner label="Loading receipt" />;
  if (invoice.isError) return <Alert tone="danger">{toApiError(invoice.error).message}</Alert>;
  const inv = invoice.data;
  const p = inv.payments.find((x) => String(x.id) === paymentId);
  if (!p) return <Alert tone="danger">This payment is not on invoice {inv.invoiceNo}.</Alert>;
  const refund = !!p.refundOfPaymentId;
  return (
    <>
      <div className="no-print mb-4 flex items-center justify-between">
        <Link to={`/billing/invoices/${inv.id}`} className="inline-flex items-center gap-1 text-sm text-foreground-muted hover:text-foreground"><ArrowLeft className="h-4 w-4" aria-hidden="true" /> {inv.invoiceNo}</Link>
        <Button icon={Printer} onClick={() => window.print()}>Print</Button>
      </div>
      <section className="print-sheet mx-auto max-w-md rounded-lg border border-border bg-surface p-8 shadow-sm">
        <h1 className="text-xl">{refund ? 'Refund receipt' : 'Payment receipt'}</h1>
        <p className="font-mono text-sm">{p.receiptNo}</p>
        <dl className="mt-4 flex flex-col gap-1 text-sm">
          <Row label="Date" value={formatDateTime(p.receivedAt)} />
          <Row label="Patient" value={`${inv.patientName} (${inv.patientMrn})`} />
          <Row label="Invoice" value={inv.invoiceNo} />
          <Row label="Method" value={refund ? 'Refund' : humanise(p.method)} />
          {p.referenceNo && <Row label="Reference" value={p.referenceNo} />}
          <Row label={refund ? 'Amount refunded' : 'Amount received'} value={formatLKR(Math.abs(money(p.amount)))} strong />
          <Row label="Invoice balance today" value={formatLKR(inv.balanceDue)} />
          {p.receivedByName && <Row label="Received by" value={p.receivedByName} />}
        </dl>
        <p className="mt-6 text-xs text-foreground-subtle">Printed {formatDate(new Date().toISOString().slice(0, 10))}. Please keep this receipt for your records.</p>
      </section>
    </>
  );
}
