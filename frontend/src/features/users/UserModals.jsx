import { useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Check, Copy } from 'lucide-react';
import { usersApi } from '../../api/admin';
import { useAuth } from '../../auth/AuthProvider';
import { Modal } from '../../components/ui/Modal';
import { Button } from '../../components/ui/Button';
import { CheckboxField, TextField } from '../../components/ui/Field';
import { Alert } from '../../components/ui/Alert';
import { focusFirstInvalid, requireFields, splitApiError } from '../../lib/forms';

export function UserFormModal({ user, onClose, onSaved }) {
  const isNew = !user;
  const { user: me } = useAuth();
  const roles = useQuery({ queryKey: ['roles'], queryFn: usersApi.roles, staleTime: 300_000 });
  const [form, setForm] = useState({
    username: user?.username ?? '',
    fullName: user?.fullName ?? '',
    email: user?.email ?? '',
    roles: user?.roles ?? [],
  });
  const [errors, setErrors] = useState({});
  const [general, setGeneral] = useState(null);
  const callerIsSuperAdmin = me?.roles?.includes('SUPER_ADMIN');

  const save = useMutation({
    mutationFn: () => (isNew
      ? usersApi.create({ username: form.username.trim(), fullName: form.fullName.trim(), email: form.email.trim() || null, roles: form.roles })
      : usersApi.update(user.id, { fullName: form.fullName.trim(), email: form.email.trim() || null, roles: form.roles, version: user.version })),
    onSuccess: (result) => onSaved(result, isNew),
    onError: (err) => {
      const { fields, general: g } = splitApiError(err);
      setErrors(fields);
      setGeneral(g);
    },
  });

  const set = (key) => (e) => { setForm((f) => ({ ...f, [key]: e.target.value })); setErrors((x) => ({ ...x, [key]: undefined })); };
  const toggleRole = (code) => {
    setForm((f) => ({ ...f, roles: f.roles.includes(code) ? f.roles.filter((r) => r !== code) : [...f.roles, code] }));
    setErrors((x) => ({ ...x, roles: undefined }));
  };

  const submit = (e) => {
    e.preventDefault();
    const errs = requireFields(form, isNew ? { username: 'Username', fullName: 'Full name', roles: 'At least one role' } : { fullName: 'Full name', roles: 'At least one role' });
    if (errs.roles) errs.roles = 'Assign at least one role.';
    setErrors(errs);
    setGeneral(null);
    if (Object.keys(errs).length === 0) save.mutate();
    else focusFirstInvalid();
  };

  return (
    <Modal
      open
      onClose={onClose}
      size="lg"
      title={isNew ? 'New user' : `Edit ${user.fullName}`}
      description={isNew ? 'A temporary password is generated. The user must change it at first sign-in.' : 'Changing roles signs the user out so the new access applies immediately.'}
      footer={(
        <>
          <Button variant="ghost" onClick={onClose}>Cancel</Button>
          <Button type="submit" form="user-form" loading={save.isPending}>{isNew ? 'Create user' : 'Save changes'}</Button>
        </>
      )}
    >
      <form id="user-form" onSubmit={submit} noValidate className="flex flex-col gap-4">
        {general && <Alert tone="danger" requestId={general.requestId}>{general.message}</Alert>}
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Username" required disabled={!isNew} value={form.username} onChange={set('username')} error={errors.username}
                     hint={isNew ? 'Letters, numbers, dot, underscore or hyphen.' : 'Usernames cannot be changed.'} autoCapitalize="none" spellCheck={false} data-autofocus />
          <TextField label="Full name" required value={form.fullName} onChange={set('fullName')} error={errors.fullName} />
          <TextField label="Email" type="email" value={form.email} onChange={set('email')} error={errors.email} className="sm:col-span-2" />
        </div>
        <fieldset>
          <legend className="text-[13px] font-medium">Roles <span className="text-danger" aria-hidden="true">*</span></legend>
          {roles.isLoading && <p className="mt-2 text-sm text-foreground-muted">Loading roles…</p>}
          <div className="mt-2 grid gap-3 sm:grid-cols-2">
            {roles.data?.map((r) => (
              <CheckboxField
                key={r.code}
                label={r.name}
                description={r.description}
                checked={form.roles.includes(r.code)}
                onChange={() => toggleRole(r.code)}
                disabled={r.code === 'SUPER_ADMIN' && !callerIsSuperAdmin}
              />
            ))}
          </div>
          {errors.roles && <p role="alert" className="mt-2 text-xs font-medium text-danger">{errors.roles}</p>}
        </fieldset>
      </form>
    </Modal>
  );
}

/** Shows a newly issued temporary password exactly once, with a copy button. */
export function TemporaryPasswordModal({ issued, onClose }) {
  const [copied, setCopied] = useState(false);
  if (!issued) return null;
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(issued.temporaryPassword);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  };
  return (
    <Modal
      open
      onClose={() => { setCopied(false); onClose(); }}
      size="sm"
      title="Temporary password issued"
      footer={<Button onClick={() => { setCopied(false); onClose(); }}>Done</Button>}
    >
      <div className="flex flex-col gap-4 text-sm">
        <p>Give this password to <span className="font-semibold">{issued.user.fullName}</span> ({issued.user.username}) in person or by a secure channel.</p>
        <div className="flex items-center gap-2 rounded-md border border-border-strong bg-primary-soft px-3 py-2">
          <code className="flex-1 select-all font-mono text-base tracking-wider text-primary-soft-foreground">{issued.temporaryPassword}</code>
          <Button variant="secondary" size="sm" icon={copied ? Check : Copy} onClick={copy}>{copied ? 'Copied' : 'Copy'}</Button>
        </div>
        <Alert tone="warning">This is the only time it is shown. The user must change it at first sign-in.</Alert>
      </div>
    </Modal>
  );
}
