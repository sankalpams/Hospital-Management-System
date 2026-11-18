import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, Plus, Trash2, Users } from 'lucide-react';
import {
  BLOOD_GROUPS, DISTRICTS, GENDERS, MARITAL, NIC_PATTERN, PHONE_PATTERN, patientsApi,
} from '../../api/patients';
import { toApiError } from '../../api/client';
import { PageHeader } from '../../components/layout/AppShell';
import { Card, CardBody, CardHeader } from '../../components/ui/Card';
import { CheckboxField, SelectField, TextField } from '../../components/ui/Field';
import { Button } from '../../components/ui/Button';
import { Alert } from '../../components/ui/Alert';
import { Modal } from '../../components/ui/Modal';
import { Spinner } from '../../components/ui/Spinner';
import { useToast } from '../../components/ui/Toast';
import { formatDate, todayInHospital } from '../../lib/format';
import { focusFirstInvalid, requireFields, splitApiError } from '../../lib/forms';

const EMPTY = {
  firstName: '', lastName: '', nic: '', passportNo: '', gender: '', dateOfBirth: '', bloodGroup: '', maritalStatus: '',
  phone: '', email: '', addressLine1: '', addressLine2: '', city: '', district: '', occupation: '', emergencyContacts: [],
};
const EMPTY_CONTACT = { name: '', relationship: '', phone: '', altPhone: '', primary: false };

function fromPatient(p) {
  const out = { ...EMPTY };
  Object.keys(EMPTY).forEach((k) => { if (p[k] !== null && p[k] !== undefined) out[k] = p[k]; });
  out.emergencyContacts = (p.emergencyContacts ?? []).map((c) => ({ ...EMPTY_CONTACT, ...c, altPhone: c.altPhone ?? '' }));
  return out;
}

/** Client checks for instant feedback; the server repeats every one of them. */
function validate(form) {
  const errors = requireFields(form, {
    firstName: 'First name', lastName: 'Last name', gender: 'Sex', dateOfBirth: 'Date of birth', phone: 'Phone',
  });
  if (form.nic && !NIC_PATTERN.test(form.nic.trim())) errors.nic = 'Enter a valid NIC (e.g. 851234567V or 198512345678).';
  if (form.phone && !PHONE_PATTERN.test(form.phone.trim())) errors.phone = 'Use 0XXXXXXXXX or +94XXXXXXXXX.';
  if (form.dateOfBirth && form.dateOfBirth >= todayInHospital()) errors.dateOfBirth = 'Date of birth must be in the past.';
  form.emergencyContacts.forEach((c, i) => {
    if (!c.name.trim()) errors[`ec${i}.name`] = 'Name is required.';
    if (!c.relationship.trim()) errors[`ec${i}.relationship`] = 'Relationship is required.';
    if (!PHONE_PATTERN.test(c.phone.trim())) errors[`ec${i}.phone`] = 'Enter a valid phone number.';
    if (c.altPhone && !PHONE_PATTERN.test(c.altPhone.trim())) errors[`ec${i}.altPhone`] = 'Enter a valid phone number.';
  });
  return errors;
}

export default function PatientFormPage() {
  const { id } = useParams();
  const isNew = !id;
  const navigate = useNavigate();
  const toast = useToast();
  const queryClient = useQueryClient();

  const existing = useQuery({ queryKey: ['patient', id], queryFn: () => patientsApi.get(id), enabled: !isNew });
  const [form, setForm] = useState(EMPTY);
  const [errors, setErrors] = useState({});
  const [general, setGeneral] = useState(null);
  const [duplicates, setDuplicates] = useState(null);

  useEffect(() => { if (existing.data) setForm(fromPatient(existing.data)); }, [existing.data]);

  const set = (k) => (e) => { setForm((f) => ({ ...f, [k]: e.target.value })); setErrors((x) => ({ ...x, [k]: undefined })); };
  const setContact = (i, k) => (e) => {
    const value = e.target.type === 'checkbox' ? e.target.checked : e.target.value;
    setForm((f) => ({
      ...f,
      emergencyContacts: f.emergencyContacts.map((c, j) => {
        if (k === 'primary') return { ...c, primary: j === i ? value : false };
        return j === i ? { ...c, [k]: value } : c;
      }),
    }));
    setErrors((x) => ({ ...x, [`ec${i}.${k}`]: undefined }));
  };

  const payload = (confirmNotDuplicate) => ({
    ...form,
    nic: form.nic.trim() || null,
    passportNo: form.passportNo.trim() || null,
    bloodGroup: form.bloodGroup || null,
    maritalStatus: form.maritalStatus || null,
    email: form.email.trim() || null,
    district: form.district || null,
    emergencyContacts: form.emergencyContacts.map((c) => ({ ...c, altPhone: c.altPhone.trim() || null })),
    confirmNotDuplicate,
    version: existing.data?.version,
  });

  const save = useMutation({
    mutationFn: (confirm) => (isNew ? patientsApi.register(payload(confirm)) : patientsApi.update(id, payload(confirm))),
    onSuccess: (p) => {
      setDuplicates(null);
      queryClient.invalidateQueries({ queryKey: ['patients'] });
      queryClient.setQueryData(['patient', String(p.id)], p);
      toast.success(isNew ? `Registered ${p.fullName} as ${p.mrn}.` : 'Patient details saved.');
      navigate(`/patients/${p.id}`);
    },
    onError: (err) => {
      setDuplicates(null);
      const { fields, general: g } = splitApiError(err);
      const mapped = {};
      Object.entries(fields).forEach(([k, v]) => {
        const m = k.match(/^emergencyContacts\[(\d+)\]\.(\w+)$/);
        mapped[m ? `ec${m[1]}.${m[2]}` : k] = v;
      });
      setErrors(mapped);
      setGeneral(g);
      if (Object.keys(mapped).length) focusFirstInvalid();
      else window.scrollTo({ top: 0, behavior: 'smooth' });
    },
  });

  const submit = async (e) => {
    e.preventDefault();
    const errs = validate(form);
    setErrors(errs);
    setGeneral(null);
    if (Object.keys(errs).length) {
      focusFirstInvalid();
      return;
    }
    // Check for possible duplicates before saving, so the receptionist can compare records.
    try {
      const found = await patientsApi.duplicateCheck({
        firstName: form.firstName.trim(), lastName: form.lastName.trim(), dateOfBirth: form.dateOfBirth,
        phone: form.phone.trim(), nic: form.nic.trim() || null,
      });
      const others = found.filter((c) => String(c.id) !== String(id));
      if (others.length) {
        setDuplicates(others);
        return;
      }
    } catch (err) {
      setGeneral(toApiError(err));
      return;
    }
    save.mutate(false);
  };

  if (!isNew && existing.isLoading) return <Spinner label="Loading patient" />;
  if (!isNew && existing.isError) return <Alert tone="danger">{toApiError(existing.error).message}</Alert>;

  const nicTaken = duplicates?.some((d) => d.reason === 'Same NIC');

  return (
    <>
      <Link to={isNew ? '/patients' : `/patients/${id}`} className="mb-3 inline-flex items-center gap-1 text-sm text-foreground-muted hover:text-foreground">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" /> {isNew ? 'Patients' : 'Back to patient'}
      </Link>
      <PageHeader
        title={isNew ? 'Register patient' : `Edit ${existing.data.fullName}`}
        description={isNew ? 'An MRN is assigned automatically when you save.' : `MRN ${existing.data.mrn}`}
      />
      <form onSubmit={submit} noValidate className="flex flex-col gap-6">
        {general && <Alert tone="danger" requestId={general.requestId}>{general.message}</Alert>}

        <Card>
          <CardHeader title="Personal details" />
          <CardBody className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            <TextField label="First name" required value={form.firstName} onChange={set('firstName')} error={errors.firstName} autoComplete="off" />
            <TextField label="Last name" required value={form.lastName} onChange={set('lastName')} error={errors.lastName} autoComplete="off" />
            <SelectField label="Sex" required placeholder="Select" options={GENDERS} value={form.gender} onChange={set('gender')} error={errors.gender} />
            <TextField label="Date of birth" type="date" required max={todayInHospital()} value={form.dateOfBirth} onChange={set('dateOfBirth')} error={errors.dateOfBirth} />
            <TextField label="NIC" value={form.nic} onChange={set('nic')} error={errors.nic} hint="Old (9 digits + V/X) or new (12 digits) format." />
            <TextField label="Passport number" value={form.passportNo} onChange={set('passportNo')} error={errors.passportNo} hint="For foreign nationals." />
            <SelectField label="Blood group" placeholder="Unknown" options={BLOOD_GROUPS} value={form.bloodGroup} onChange={set('bloodGroup')} error={errors.bloodGroup} />
            <SelectField label="Marital status" placeholder="Not stated" options={MARITAL} value={form.maritalStatus} onChange={set('maritalStatus')} error={errors.maritalStatus} />
            <TextField label="Occupation" value={form.occupation} onChange={set('occupation')} error={errors.occupation} />
          </CardBody>
        </Card>

        <Card>
          <CardHeader title="Contact and address" />
          <CardBody className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            <TextField label="Mobile phone" type="tel" required value={form.phone} onChange={set('phone')} error={errors.phone} placeholder="07XXXXXXXX" />
            <TextField label="Email" type="email" value={form.email} onChange={set('email')} error={errors.email} />
            <span className="hidden lg:block" />
            <TextField label="Address line 1" value={form.addressLine1} onChange={set('addressLine1')} error={errors.addressLine1} />
            <TextField label="Address line 2" value={form.addressLine2} onChange={set('addressLine2')} error={errors.addressLine2} />
            <TextField label="City" value={form.city} onChange={set('city')} error={errors.city} />
            <SelectField label="District" placeholder="Select district" options={DISTRICTS} value={form.district} onChange={set('district')} error={errors.district} />
          </CardBody>
        </Card>

        <Card>
          <CardHeader
            icon={Users}
            title="Emergency contacts"
            description="Up to three. The primary contact is called first."
            action={form.emergencyContacts.length < 3 && (
              <Button variant="secondary" size="sm" icon={Plus}
                      onClick={() => setForm((f) => ({ ...f, emergencyContacts: [...f.emergencyContacts, { ...EMPTY_CONTACT, primary: f.emergencyContacts.length === 0 }] }))}>
                Add contact
              </Button>
            )}
          />
          <CardBody className="flex flex-col gap-5">
            {form.emergencyContacts.length === 0 && <p className="text-sm text-foreground-muted">No emergency contact recorded.</p>}
            {form.emergencyContacts.map((c, i) => (
              <fieldset key={i} className="grid gap-4 rounded-md border border-border p-4 sm:grid-cols-2 lg:grid-cols-4">
                <legend className="px-1 text-[13px] font-medium">Contact {i + 1}</legend>
                <TextField label="Name" required value={c.name} onChange={setContact(i, 'name')} error={errors[`ec${i}.name`]} />
                <TextField label="Relationship" required value={c.relationship} onChange={setContact(i, 'relationship')} error={errors[`ec${i}.relationship`]} placeholder="e.g. Spouse" />
                <TextField label="Phone" type="tel" required value={c.phone} onChange={setContact(i, 'phone')} error={errors[`ec${i}.phone`]} />
                <TextField label="Alternative phone" type="tel" value={c.altPhone} onChange={setContact(i, 'altPhone')} error={errors[`ec${i}.altPhone`]} />
                <div className="flex items-center justify-between gap-3 sm:col-span-2 lg:col-span-4">
                  <CheckboxField label="Primary contact" checked={c.primary} onChange={setContact(i, 'primary')} />
                  <Button variant="ghost" size="sm" icon={Trash2}
                          onClick={() => setForm((f) => ({ ...f, emergencyContacts: f.emergencyContacts.filter((_, j) => j !== i) }))}>
                    Remove
                  </Button>
                </div>
              </fieldset>
            ))}
          </CardBody>
        </Card>

        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button variant="ghost" onClick={() => navigate(isNew ? '/patients' : `/patients/${id}`)}>Cancel</Button>
          <Button type="submit" loading={save.isPending}>{isNew ? 'Register patient' : 'Save changes'}</Button>
        </div>
      </form>

      {duplicates && (
        <Modal
          open
          onClose={() => setDuplicates(null)}
          size="lg"
          title={nicTaken ? 'This NIC is already registered' : 'Possible existing record'}
          description={nicTaken
            ? 'A patient with this NIC already exists. Open that record instead of creating a new one.'
            : 'Compare these records with the person in front of you before continuing.'}
          footer={(
            <>
              <Button variant="ghost" onClick={() => setDuplicates(null)}>Go back and edit</Button>
              {!nicTaken && (
                <Button loading={save.isPending} onClick={() => save.mutate(true)}>
                  Not the same person, {isNew ? 'register' : 'save'}
                </Button>
              )}
            </>
          )}
        >
          <ul className="divide-y divide-border rounded-md border border-border">
            {duplicates.map((d) => (
              <li key={d.id} className="flex flex-col gap-1 px-4 py-3 text-sm sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <p className="font-medium">{d.fullName} <span className="font-mono text-xs text-foreground-subtle">{d.mrn}</span></p>
                  <p className="text-foreground-muted">Born {formatDate(d.dateOfBirth)} · {d.phone}{d.nic ? ` · NIC ${d.nic}` : ''}</p>
                  <p className="text-xs font-medium text-warning">{d.reason}</p>
                </div>
                <Link to={`/patients/${d.id}`} className="text-sm font-medium text-primary hover:underline">Open record</Link>
              </li>
            ))}
          </ul>
        </Modal>
      )}
    </>
  );
}
