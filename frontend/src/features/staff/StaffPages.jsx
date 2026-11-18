import { useEffect, useMemo, useState } from 'react';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import { CalendarCheck, Plus, Save } from 'lucide-react';
import { ATTENDANCE_STATUSES, STAFF_CATEGORIES, staffApi } from '../../api/staff';
import { EMPLOYEE_STATUSES, EMPLOYMENT_TYPES } from '../../api/clinic';
import { GENDERS, NIC_PATTERN, PHONE_PATTERN } from '../../api/patients';
import { departmentsApi } from '../../api/org';
import { usersApi } from '../../api/admin';
import { toApiError } from '../../api/client';
import { useAuth } from '../../auth/AuthProvider';
import { PageHeader } from '../../components/layout/AppShell';
import { Card, CardBody, CardHeader } from '../../components/ui/Card';
import { DataTable } from '../../components/ui/DataTable';
import { FilterSelect, SearchInput, Toolbar } from '../../components/ui/Toolbar';
import { Badge, StatusBadge, humanise } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Alert } from '../../components/ui/Alert';
import { Modal } from '../../components/ui/Modal';
import { CheckboxField, SelectField, TextAreaField, TextField } from '../../components/ui/Field';
import { Spinner } from '../../components/ui/Spinner';
import { useToast } from '../../components/ui/Toast';
import { formatDate, todayInHospital } from '../../lib/format';
import { focusFirstInvalid, requireFields, splitApiError } from '../../lib/forms';
import { useListParams } from '../../lib/useListParams';

const categoryLabel = (c) => STAFF_CATEGORIES.find((x) => x.value === c)?.label ?? humanise(c);
const LEAVE_TONE = { PENDING: 'neutral', APPROVED: 'success', REJECTED: 'danger', CANCELLED: 'neutral' };

function useInvalidate() {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: ['staff'] });
}

export function StaffPage() {
  const { hasPermission } = useAuth();
  const [params, setParams] = useListParams({ tab: 'employees' });
  const tabs = [
    { id: 'employees', label: 'Employees', show: hasPermission('employee:read') },
    { id: 'attendance', label: 'Attendance', show: hasPermission('attendance:manage') },
    { id: 'leave', label: 'Leave requests', show: hasPermission(['leave:approve', 'employee:manage']) },
  ].filter((t) => t.show);
  const tab = tabs.some((t) => t.id === params.tab) ? params.tab : tabs[0]?.id;
  return (
    <>
      <PageHeader title="Staff" description="Employee records, the daily attendance register and leave." />
      <div role="tablist" aria-label="Staff" className="mb-4 flex flex-wrap gap-1 rounded-md border border-border bg-surface p-1">
        {tabs.map((t) => (
          <button key={t.id} type="button" role="tab" aria-selected={tab === t.id} onClick={() => setParams({ tab: t.id, q: '', status: '' })}
                  className={clsx('rounded px-3 py-1.5 text-sm font-medium', tab === t.id ? 'bg-primary text-primary-foreground' : 'text-foreground-muted hover:text-foreground')}>
            {t.label}
          </button>
        ))}
      </div>
      {tab === 'employees' && <EmployeesTab params={params} setParams={setParams} />}
      {tab === 'attendance' && <AttendanceTab params={params} setParams={setParams} />}
      {tab === 'leave' && <LeaveTab params={params} setParams={setParams} />}
    </>
  );
}

// ---------------------------------------------------------------- employees

function EmployeesTab({ params, setParams }) {
  const { hasPermission } = useAuth();
  const [editing, setEditing] = useState(null);
  const departments = useQuery({ queryKey: ['department-options'], queryFn: departmentsApi.options, staleTime: 300_000 });
  const list = useQuery({
    queryKey: ['staff', 'employees', params],
    queryFn: () => staffApi.employees({ q: params.q, category: params.category, departmentId: params.departmentId, status: params.status, page: params.page }),
    placeholderData: keepPreviousData,
  });
  const columns = [
    { key: 'name', header: 'Employee', render: (e) => <div><p className="font-medium">{e.fullName}</p><p className="font-mono text-xs text-foreground-subtle">{e.employeeNo}</p></div> },
    { key: 'role', header: 'Designation', render: (e) => <div><p>{e.designation}</p><p className="text-xs text-foreground-muted">{categoryLabel(e.staffCategory)} · {humanise(e.employmentType)}</p></div> },
    { key: 'dept', header: 'Department', render: (e) => e.departmentName },
    { key: 'phone', header: 'Phone', render: (e) => <span className="tabular whitespace-nowrap">{e.phone}</span> },
    { key: 'joined', header: 'Joined', render: (e) => <span className="whitespace-nowrap">{formatDate(e.joinDate)}</span> },
    { key: 'status', header: 'Status', render: (e) => <StatusBadge status={e.status} /> },
  ];
  return (
    <>
      <Toolbar>
        <SearchInput value={params.q} onChange={(q) => setParams({ q })} placeholder="Name, EMP no., NIC or designation" label="Search employees" />
        <FilterSelect label="Category" value={params.category ?? ''} onChange={(category) => setParams({ category })} options={[{ value: '', label: 'All' }, ...STAFF_CATEGORIES]} />
        <FilterSelect label="Department" value={params.departmentId ?? ''} onChange={(departmentId) => setParams({ departmentId })}
                      options={[{ value: '', label: 'All' }, ...(departments.data ?? []).map((d) => ({ value: String(d.id), label: d.name }))]} />
        <FilterSelect label="Status" value={params.status ?? ''} onChange={(status) => setParams({ status })} options={[{ value: '', label: 'All' }, ...EMPLOYEE_STATUSES]} />
        <div className="flex-1" />
        {hasPermission('employee:manage') && <Button icon={Plus} onClick={() => setEditing('new')}>New employee</Button>}
      </Toolbar>
      <DataTable caption="Employees" columns={columns} page={list.data} loading={list.isFetching}
                 error={list.isError ? toApiError(list.error).message : null}
                 onPageChange={(page) => setParams({ page }, { resetPage: false })}
                 onRowClick={(e) => setEditing(e)} emptyTitle="No employees match" />
      {editing && <EmployeeDialog employee={editing === 'new' ? null : editing} departments={departments.data ?? []} onClose={() => setEditing(null)} />}
    </>
  );
}

const EMPTY = { firstName: '', lastName: '', gender: '', nic: '', dateOfBirth: '', phone: '', email: '', address: '', departmentId: '',
  designation: '', staffCategory: 'NURSE', employmentType: 'PERMANENT', joinDate: '', status: 'ACTIVE', userId: '' };

function EmployeeDialog({ employee, departments, onClose }) {
  const { hasPermission } = useAuth();
  const toast = useToast();
  const invalidate = useInvalidate();
  const isDoctor = employee?.staffCategory === 'DOCTOR';
  const readOnly = isDoctor || !hasPermission('employee:manage');
  const [form, setForm] = useState(() => (employee
    ? Object.fromEntries(Object.keys(EMPTY).map((k) => [k, employee[k] == null ? '' : String(employee[k])]))
    : { ...EMPTY, joinDate: todayInHospital() }));
  const [errors, setErrors] = useState({});
  const [general, setGeneral] = useState(null);
  const users = useQuery({ queryKey: ['users', 'active'], queryFn: () => usersApi.list({ status: 'ACTIVE', size: 100 }), enabled: !readOnly && hasPermission('user:read') });
  const set = (k) => (e) => { setForm((f) => ({ ...f, [k]: e.target.value })); setErrors((x) => ({ ...x, [k]: undefined })); };
  const save = useMutation({
    mutationFn: () => staffApi.save(employee?.id, { ...form, departmentId: Number(form.departmentId), userId: form.userId ? Number(form.userId) : null,
      dateOfBirth: form.dateOfBirth || null, nic: form.nic || null, email: form.email || null, address: form.address || null, version: employee?.version }),
    onSuccess: (e) => { invalidate(); toast.success(`${e.fullName} saved (${e.employeeNo}).`); onClose(); },
    onError: (err) => { const s = splitApiError(err); setErrors(s.fields); setGeneral(s.general); focusFirstInvalid(); },
  });
  const submit = () => {
    const errs = requireFields(form, { firstName: 'First name', lastName: 'Last name', gender: 'Gender', phone: 'Phone', departmentId: 'Department', designation: 'Designation', joinDate: 'Join date' });
    if (form.nic && !NIC_PATTERN.test(form.nic)) errs.nic = 'Enter a 12-digit NIC, or 9 digits followed by V or X.';
    if (form.phone && !PHONE_PATTERN.test(form.phone)) errs.phone = 'Enter a 10-digit number starting with 0, or +country code.';
    setErrors(errs);
    if (Object.keys(errs).length) focusFirstInvalid(); else save.mutate();
  };
  const title = employee ? `${employee.fullName} · ${employee.employeeNo}` : 'New employee';
  return (
    <Modal open onClose={onClose} title={title} size="lg"
           description={isDoctor ? 'Doctors are edited from the Doctors page, where their clinical profile lives.' : undefined}
           footer={readOnly ? <Button variant="ghost" onClick={onClose}>Close</Button>
             : <><Button variant="ghost" onClick={onClose}>Cancel</Button><Button loading={save.isPending} onClick={submit}>Save</Button></>}>
      <fieldset disabled={readOnly} className="grid gap-4 sm:grid-cols-2">
        {general && <Alert tone="danger" className="sm:col-span-2">{general.message}</Alert>}
        <TextField label="First name" required data-autofocus value={form.firstName} onChange={set('firstName')} error={errors.firstName} />
        <TextField label="Last name" required value={form.lastName} onChange={set('lastName')} error={errors.lastName} />
        <SelectField label="Gender" required placeholder="Choose" options={GENDERS} value={form.gender} onChange={set('gender')} error={errors.gender} />
        <TextField label="NIC" value={form.nic} onChange={set('nic')} error={errors.nic} inputClassName="font-mono uppercase" />
        <TextField label="Date of birth" type="date" max={todayInHospital()} value={form.dateOfBirth} onChange={set('dateOfBirth')} error={errors.dateOfBirth} />
        <TextField label="Phone" required inputMode="tel" value={form.phone} onChange={set('phone')} error={errors.phone} />
        <TextField label="Email" type="email" value={form.email} onChange={set('email')} error={errors.email} />
        <TextField label="Address" value={form.address} onChange={set('address')} error={errors.address} />
        <SelectField label="Department" required placeholder="Choose a department" value={form.departmentId} onChange={set('departmentId')} error={errors.departmentId}
                     options={departments.map((d) => ({ value: String(d.id), label: d.name }))} />
        <TextField label="Designation" required value={form.designation} onChange={set('designation')} error={errors.designation} placeholder="e.g. Nursing Officer" />
        <SelectField label="Staff category" required value={form.staffCategory} onChange={set('staffCategory')} error={errors.staffCategory}
                     options={STAFF_CATEGORIES.filter((c) => c.value !== 'DOCTOR' || isDoctor)} />
        <SelectField label="Employment type" required options={EMPLOYMENT_TYPES} value={form.employmentType} onChange={set('employmentType')} error={errors.employmentType} />
        <TextField label="Join date" type="date" required max={todayInHospital()} value={form.joinDate} onChange={set('joinDate')} error={errors.joinDate} />
        {employee && <SelectField label="Status" required options={EMPLOYEE_STATUSES} value={form.status} onChange={set('status')} error={errors.status} />}
        {!readOnly && hasPermission('user:read') && (
          <SelectField label="Login account" placeholder="Not linked" value={form.userId} onChange={set('userId')} error={errors.userId}
                       hint="Linking lets this person apply for their own leave."
                       options={(users.data?.content ?? []).map((u) => ({ value: String(u.id), label: `${u.fullName} (${u.username})` }))} />
        )}
      </fieldset>
    </Modal>
  );
}

// ---------------------------------------------------------------- attendance

function AttendanceTab({ params, setParams }) {
  const toast = useToast();
  const queryClient = useQueryClient();
  const today = todayInHospital();
  const date = params.date || today;
  const register = useQuery({ queryKey: ['staff', 'attendance', date], queryFn: () => staffApi.register(date) });
  const [edits, setEdits] = useState({});
  useEffect(() => setEdits({}), [date]);
  const rows = register.data ?? [];
  const value = (r, k) => (edits[r.employeeId]?.[k] ?? (r[k] ?? '')).toString().slice(0, k === 'status' ? undefined : 5);
  const change = (r, k, v) => setEdits((e) => ({ ...e, [r.employeeId]: { ...e[r.employeeId], [k]: v } }));
  const dirty = Object.keys(edits).length;
  const save = useMutation({
    mutationFn: () => staffApi.saveRegister(date, Object.keys(edits).map((id) => {
      const r = rows.find((x) => String(x.employeeId) === id);
      const status = value(r, 'status');
      const worked = status === 'PRESENT' || status === 'HALF_DAY';
      return { employeeId: Number(id), status, clockIn: worked ? value(r, 'clockIn') || null : null, clockOut: worked ? value(r, 'clockOut') || null : null, remarks: value(r, 'remarks') || null };
    })),
    onSuccess: (data) => { queryClient.setQueryData(['staff', 'attendance', date], data); setEdits({}); toast.success(`Attendance for ${formatDate(date)} saved.`); },
    onError: (err) => toast.error(toApiError(err).message),
  });
  const markRest = () => setEdits((e) => {
    const next = { ...e };
    rows.filter((r) => !r.status && !value(r, 'status')).forEach((r) => { next[r.employeeId] = { ...next[r.employeeId], status: r.onLeave ? 'ON_LEAVE' : 'PRESENT' }; });
    return next;
  });
  const unrecorded = rows.filter((r) => !value(r, 'status')).length;
  const missingStatus = Object.keys(edits).some((id) => !value(rows.find((x) => String(x.employeeId) === id) ?? {}, 'status'));

  return (
    <>
      <Toolbar>
        <TextField label="Date" type="date" className="w-44" max={today} value={date} onChange={(e) => setParams({ date: e.target.value })} />
        <p className="text-sm text-foreground-muted tabular">{rows.length} on roll · {unrecorded} not recorded</p>
        <div className="flex-1" />
        <Button variant="secondary" icon={CalendarCheck} disabled={!unrecorded} onClick={markRest}>Mark unrecorded present</Button>
        <Button icon={Save} disabled={!dirty || missingStatus} loading={save.isPending} onClick={() => save.mutate()}>Save{dirty ? ` ${dirty} change${dirty === 1 ? '' : 's'}` : ''}</Button>
      </Toolbar>
      {register.isLoading && <Spinner label="Loading register" />}
      {register.isError && <Alert tone="danger">{toApiError(register.error).message}</Alert>}
      {register.data && (
        <div className="relative overflow-x-auto rounded-lg border border-border bg-surface shadow-sm">
          <table className="w-full min-w-[820px] text-left text-sm">
            <caption className="sr-only">Attendance register for {formatDate(date)}</caption>
            <thead className="bg-surface-muted text-xs text-foreground-muted"><tr>
              {['Employee', 'Department', 'Status', 'In', 'Out', 'Remarks'].map((h) => <th key={h} className="px-3 py-2 font-medium">{h}</th>)}
            </tr></thead>
            <tbody className="divide-y divide-border">
              {rows.length === 0 && <tr><td colSpan={6} className="px-3 py-8 text-center text-foreground-muted">No employees on the roll for this date.</td></tr>}
              {rows.map((r) => {
                const status = value(r, 'status');
                const worked = status === 'PRESENT' || status === 'HALF_DAY';
                const label = `${r.fullName}`;
                return (
                  <tr key={r.employeeId} className={clsx(edits[r.employeeId] && 'bg-primary-soft')}>
                    <td className="px-3 py-2"><p className="font-medium">{r.fullName}</p><p className="font-mono text-xs text-foreground-subtle">{r.employeeNo}</p>
                      {r.onLeave && <Badge tone="info" className="mt-1">{r.onLeave}</Badge>}</td>
                    <td className="px-3 py-2 text-xs"><p>{r.departmentName}</p><p className="text-foreground-muted">{r.designation}</p></td>
                    <td className="px-3 py-2">
                      <select aria-label={`Status for ${label}`} value={status} onChange={(e) => change(r, 'status', e.target.value)}
                              className="h-9 rounded-md border border-border bg-surface px-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">
                        <option value="">Not recorded</option>
                        {ATTENDANCE_STATUSES.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
                      </select>
                    </td>
                    {['clockIn', 'clockOut'].map((k) => (
                      <td key={k} className="px-3 py-2">
                        <input type="time" aria-label={`${k === 'clockIn' ? 'Clock in' : 'Clock out'} for ${label}`} disabled={!worked} value={worked ? value(r, k) : ''}
                               onChange={(e) => change(r, k, e.target.value)}
                               className="tabular h-9 rounded-md border border-border bg-surface px-2 text-sm disabled:opacity-40 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring" />
                      </td>
                    ))}
                    <td className="px-3 py-2">
                      <input aria-label={`Remarks for ${label}`} value={value(r, 'remarks')} maxLength={255} onChange={(e) => change(r, 'remarks', e.target.value)}
                             className="h-9 w-full min-w-[10rem] rounded-md border border-border bg-surface px-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring" />
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}

// ---------------------------------------------------------------- leave approvals

function LeaveTab({ params, setParams }) {
  const { hasPermission } = useAuth();
  const [deciding, setDeciding] = useState(null);
  const status = params.status ?? 'PENDING';
  const list = useQuery({
    queryKey: ['staff', 'leave', status, params.page],
    queryFn: () => staffApi.leave({ status, page: params.page }),
    placeholderData: keepPreviousData,
  });
  const columns = [
    { key: 'who', header: 'Employee', render: (l) => <div><p className="font-medium">{l.employeeName}</p><p className="text-xs text-foreground-muted">{l.departmentName}</p></div> },
    { key: 'type', header: 'Leave', render: (l) => <div><p>{l.leaveType}</p><p className="max-w-xs truncate text-xs text-foreground-muted">{l.reason}</p></div> },
    { key: 'dates', header: 'Dates', render: (l) => <span className="whitespace-nowrap">{formatDate(l.startDate)}{l.endDate !== l.startDate && ` – ${formatDate(l.endDate)}`}</span> },
    { key: 'days', header: 'Days', align: 'right', render: (l) => <span className="tabular">{Number(l.days)}</span> },
    { key: 'status', header: 'Status', render: (l) => <Badge tone={LEAVE_TONE[l.status]}>{humanise(l.status)}</Badge> },
    { key: 'act', header: <span className="sr-only">Actions</span>, align: 'right', render: (l) => l.status === 'PENDING' && hasPermission('leave:approve') && (
      <Button size="sm" variant="secondary" onClick={(e) => { e.stopPropagation(); setDeciding(l); }}>Decide</Button>
    ) },
  ];
  return (
    <>
      <Toolbar>
        <FilterSelect label="Status" value={status} onChange={(s) => setParams({ status: s })}
                      options={[...['PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'].map((s) => ({ value: s, label: humanise(s) })), { value: '', label: 'All' }]} />
      </Toolbar>
      <DataTable caption="Leave requests" columns={columns} page={list.data} loading={list.isFetching}
                 error={list.isError ? toApiError(list.error).message : null}
                 onPageChange={(page) => setParams({ page }, { resetPage: false })}
                 emptyTitle={status === 'PENDING' ? 'No leave waiting for a decision' : 'No leave requests match'} />
      {deciding && <DecideDialog leave={deciding} onClose={() => setDeciding(null)} />}
    </>
  );
}

function DecideDialog({ leave, onClose }) {
  const toast = useToast();
  const invalidate = useInvalidate();
  const [note, setNote] = useState('');
  const [error, setError] = useState(null);
  const year = Number(leave.startDate.slice(0, 4));
  const balances = useQuery({ queryKey: ['staff', 'balances', leave.employeeId, year], queryFn: () => staffApi.balances(leave.employeeId, year) });
  const balance = balances.data?.find((b) => b.leaveTypeId === leave.leaveTypeId);
  const decide = useMutation({
    mutationFn: (approve) => (approve ? staffApi.approve(leave.id, note) : staffApi.reject(leave.id, note)),
    onSuccess: (l) => { invalidate(); toast.success(`Leave ${l.status.toLowerCase()} for ${l.employeeName}.`); onClose(); },
    onError: (err) => setError(toApiError(err).message),
  });
  return (
    <Modal open onClose={onClose} title={`${leave.leaveType} for ${leave.employeeName}`}
           description={`${formatDate(leave.startDate)} – ${formatDate(leave.endDate)} · ${Number(leave.days)} day(s)`}
           footer={<>
             <Button variant="ghost" onClick={onClose}>Cancel</Button>
             <Button variant="danger" disabled={!note.trim()} loading={decide.isPending && decide.variables === false} onClick={() => decide.mutate(false)}>Reject</Button>
             <Button loading={decide.isPending && decide.variables === true} onClick={() => decide.mutate(true)}>Approve</Button>
           </>}>
      <div className="flex flex-col gap-4 text-sm">
        {error && <Alert tone="danger">{error}</Alert>}
        <p><span className="text-foreground-muted">Reason:</span> {leave.reason}</p>
        {balance && (
          <p className="tabular text-foreground-muted">
            {balance.entitlement == null ? 'No yearly limit for this leave type.'
              : <>{year}: {Number(balance.approved)} approved, {Number(balance.pending)} pending (including this) of {balance.entitlement} days.</>}
          </p>
        )}
        <TextAreaField label="Note to the employee" data-autofocus rows={2} maxLength={255} value={note} onChange={(e) => setNote(e.target.value)}
                       hint="Required when rejecting." />
      </div>
    </Modal>
  );
}

// ---------------------------------------------------------------- my leave (self-service)

export function MyLeavePage() {
  const toast = useToast();
  const invalidate = useInvalidate();
  const [applying, setApplying] = useState(false);
  const mine = useQuery({ queryKey: ['staff', 'me'], queryFn: staffApi.myLeave, retry: false });
  const cancel = useMutation({
    mutationFn: (id) => staffApi.cancel(id),
    onSuccess: () => { invalidate(); toast.success('Leave cancelled.'); },
    onError: (err) => toast.error(toApiError(err).message),
  });
  const today = todayInHospital();
  return (
    <>
      <PageHeader title="My leave" description="Your leave balance for this year and your requests."
                  actions={mine.data && <Button icon={Plus} onClick={() => setApplying(true)}>Apply for leave</Button>} />
      {mine.isLoading && <Spinner label="Loading your leave" />}
      {mine.isError && <Alert tone="info">{toApiError(mine.error).message}</Alert>}
      {mine.data && (
        <div className="flex flex-col gap-6">
          <Card>
            <CardHeader title="Balance this year" />
            <CardBody>
              <dl className="grid gap-3 sm:grid-cols-3 lg:grid-cols-5">
                {mine.data.balances.map((b) => (
                  <div key={b.leaveTypeId} className="rounded-md border border-border px-3 py-2">
                    <dt className="text-xs text-foreground-muted">{b.leaveType}</dt>
                    <dd className="tabular text-lg font-semibold">{b.remaining == null ? 'No limit' : `${Number(b.remaining)} left`}</dd>
                    <dd className="tabular text-xs text-foreground-muted">{Number(b.approved)} taken{Number(b.pending) > 0 && ` · ${Number(b.pending)} pending`}</dd>
                  </div>
                ))}
              </dl>
            </CardBody>
          </Card>
          <Card>
            <CardHeader title="Requests" />
            <CardBody>
              {mine.data.requests.length === 0 && <p className="text-sm text-foreground-muted">You have not applied for leave yet.</p>}
              <ul className="divide-y divide-border">
                {mine.data.requests.map((l) => (
                  <li key={l.id} className="flex flex-wrap items-center justify-between gap-3 py-3 text-sm">
                    <div>
                      <p className="font-medium">{l.leaveType} · {formatDate(l.startDate)}{l.endDate !== l.startDate && ` – ${formatDate(l.endDate)}`} <span className="tabular text-foreground-muted">({Number(l.days)} day{Number(l.days) === 1 ? '' : 's'})</span></p>
                      <p className="text-xs text-foreground-muted">{l.reason}{l.decisionNote && ` · ${l.decidedByName}: ${l.decisionNote}`}</p>
                    </div>
                    <div className="flex items-center gap-2">
                      <Badge tone={LEAVE_TONE[l.status]}>{humanise(l.status)}</Badge>
                      {['PENDING', 'APPROVED'].includes(l.status) && l.startDate > today && (
                        <Button size="sm" variant="ghost" loading={cancel.isPending && cancel.variables === l.id} onClick={() => cancel.mutate(l.id)}>Cancel</Button>
                      )}
                    </div>
                  </li>
                ))}
              </ul>
            </CardBody>
          </Card>
        </div>
      )}
      {applying && <ApplyDialog onClose={() => setApplying(false)} />}
    </>
  );
}

function ApplyDialog({ onClose }) {
  const toast = useToast();
  const invalidate = useInvalidate();
  const types = useQuery({ queryKey: ['staff', 'leave-types'], queryFn: staffApi.leaveTypes, staleTime: 300_000 });
  const [form, setForm] = useState({ leaveTypeId: '', startDate: '', endDate: '', halfDay: false, reason: '' });
  const [errors, setErrors] = useState({});
  const [general, setGeneral] = useState(null);
  const set = (k) => (e) => { setForm((f) => ({ ...f, [k]: e.target.value })); setErrors((x) => ({ ...x, [k]: undefined })); };
  const days = useMemo(() => {
    if (form.halfDay) return 0.5;
    if (!form.startDate || !form.endDate || form.endDate < form.startDate) return null;
    return Math.round((Date.parse(form.endDate) - Date.parse(form.startDate)) / 86_400_000) + 1;
  }, [form]);
  const save = useMutation({
    mutationFn: () => staffApi.apply({ ...form, leaveTypeId: Number(form.leaveTypeId), endDate: form.halfDay ? form.startDate : form.endDate }),
    onSuccess: () => { invalidate(); toast.success('Leave requested. You will be notified of the decision.'); onClose(); },
    onError: (err) => { const s = splitApiError(err); setErrors(s.fields); setGeneral(s.general); focusFirstInvalid(); },
  });
  const submit = () => {
    const errs = requireFields(form, form.halfDay ? { leaveTypeId: 'Leave type', startDate: 'Date', reason: 'Reason' } : { leaveTypeId: 'Leave type', startDate: 'First day', endDate: 'Last day', reason: 'Reason' });
    if (!form.halfDay && form.startDate && form.endDate && form.endDate < form.startDate) errs.endDate = 'The last day must be on or after the first day.';
    setErrors(errs);
    if (Object.keys(errs).length) focusFirstInvalid(); else save.mutate();
  };
  return (
    <Modal open onClose={onClose} title="Apply for leave"
           footer={<><Button variant="ghost" onClick={onClose}>Cancel</Button><Button loading={save.isPending} onClick={submit}>Apply{days ? ` for ${days} day${days === 1 ? '' : 's'}` : ''}</Button></>}>
      <div className="grid gap-4 sm:grid-cols-2">
        {general && <Alert tone="danger" className="sm:col-span-2">{general.message}</Alert>}
        <SelectField label="Leave type" required data-autofocus placeholder="Choose" className="sm:col-span-2" value={form.leaveTypeId} onChange={set('leaveTypeId')} error={errors.leaveTypeId}
                     options={(types.data ?? []).map((t) => ({ value: String(t.id), label: `${t.name}${t.daysPerYear ? ` (${t.daysPerYear} days a year)` : ''}${t.paid ? '' : ' · unpaid'}` }))} />
        <TextField label={form.halfDay ? 'Date' : 'First day'} type="date" required value={form.startDate} onChange={set('startDate')} error={errors.startDate} />
        {!form.halfDay && <TextField label="Last day" type="date" required min={form.startDate || undefined} value={form.endDate} onChange={set('endDate')} error={errors.endDate} />}
        <CheckboxField label="Half day" className="sm:col-span-2" checked={form.halfDay} onChange={(e) => setForm((f) => ({ ...f, halfDay: e.target.checked }))} />
        <TextField label="Reason" required className="sm:col-span-2" maxLength={255} value={form.reason} onChange={set('reason')} error={errors.reason} />
        <p className="text-xs text-foreground-muted sm:col-span-2">Days are counted as calendar days, including weekends.</p>
      </div>
    </Modal>
  );
}
