import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Check, Circle, KeyRound, LogOut } from 'lucide-react';
import clsx from 'clsx';
import { useAuth } from '../../auth/AuthProvider';
import { toApiError } from '../../api/client';
import { evaluatePassword, isPasswordAcceptable } from '../../lib/passwordRules';
import { focusFirstInvalid } from '../../lib/forms';
import { Button } from '../../components/ui/Button';
import { TextField } from '../../components/ui/Field';
import { Alert } from '../../components/ui/Alert';
import { Card, CardBody, CardHeader } from '../../components/ui/Card';
import { Logo } from '../../components/ui/Logo';

/**
 * Used both for the forced first-login change (standalone layout, no navigation) and for a
 * voluntary change from the user menu (inside the app shell).
 */
export default function ChangePasswordPage() {
  const { user, changePassword, logout } = useAuth();
  const navigate = useNavigate();
  const forced = Boolean(user?.mustChangePassword);

  const [form, setForm] = useState({ current: '', next: '', confirm: '' });
  const [errors, setErrors] = useState({});
  const [apiError, setApiError] = useState(null);
  const [done, setDone] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const rules = useMemo(() => evaluatePassword(form.next, user?.username), [form.next, user?.username]);

  const update = (key) => (e) => {
    setForm((f) => ({ ...f, [key]: e.target.value }));
    setErrors((er) => ({ ...er, [key]: undefined }));
    setDone(false);
  };

  const onSubmit = async (e) => {
    e.preventDefault();
    const errs = {};
    if (!form.current) errs.current = 'Enter your current password.';
    if (!isPasswordAcceptable(form.next, user?.username)) errs.next = 'The new password does not meet all the requirements.';
    else if (form.next === form.current) errs.next = 'Choose a password different from your current one.';
    if (form.confirm !== form.next) errs.confirm = 'The passwords do not match.';
    setErrors(errs);
    setApiError(null);
    if (Object.keys(errs).length) {
      focusFirstInvalid();
      return;
    }

    setSubmitting(true);
    try {
      await changePassword(form.current, form.next);
      setForm({ current: '', next: '', confirm: '' });
      if (forced) {
        navigate('/', { replace: true });
      } else {
        setDone(true);
      }
    } catch (err) {
      const e2 = toApiError(err);
      if (e2.code === 'CURRENT_PASSWORD_INCORRECT') setErrors({ current: e2.message });
      else if (e2.code === 'WEAK_PASSWORD' || e2.code === 'PASSWORD_REUSED') setErrors({ next: e2.message });
      else setApiError(e2);
    } finally {
      setSubmitting(false);
    }
  };

  const formCard = (
    <Card className="w-full max-w-[480px]">
      <CardHeader
        icon={KeyRound}
        title={forced ? 'Set your own password' : 'Change password'}
        description={forced
          ? 'You signed in with a temporary password. Choose a new one to continue.'
          : 'Other devices signed in to your account will be signed out.'}
      />
      <CardBody>
        <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
          {apiError && <Alert tone="danger" requestId={apiError.requestId}>{apiError.message}</Alert>}
          {done && <Alert tone="success">Your password has been changed.</Alert>}
          <TextField label="Current password" type="password" autoComplete="current-password"
                     value={form.current} onChange={update('current')} error={errors.current} required />
          <TextField label="New password" type="password" autoComplete="new-password"
                     value={form.next} onChange={update('next')} error={errors.next} required />
          <ul className="grid gap-1.5 rounded-md bg-surface-muted px-3 py-2.5 text-[13px]" aria-label="Password requirements">
            {rules.map((r) => (
              <li key={r.id} className={clsx('flex items-center gap-2', r.passed ? 'text-success' : 'text-foreground-muted')}>
                {r.passed
                  ? <Check className="h-3.5 w-3.5" aria-hidden="true" />
                  : <Circle className="h-3.5 w-3.5" aria-hidden="true" />}
                <span>{r.label}</span>
                <span className="sr-only">{r.passed ? '(met)' : '(not met)'}</span>
              </li>
            ))}
          </ul>
          <TextField label="Confirm new password" type="password" autoComplete="new-password"
                     value={form.confirm} onChange={update('confirm')} error={errors.confirm} required />
          <div className="mt-1 flex flex-col-reverse gap-2 sm:flex-row sm:justify-between">
            {forced ? (
              <Button variant="ghost" icon={LogOut} onClick={logout}>Sign out</Button>
            ) : <span />}
            <Button type="submit" loading={submitting}>
              {forced ? 'Save and continue' : 'Change password'}
            </Button>
          </div>
        </form>
      </CardBody>
    </Card>
  );

  if (forced) {
    return (
      <div className="flex min-h-screen flex-col items-center justify-center gap-8 bg-background px-4 py-10">
        <Logo />
        {formCard}
      </div>
    );
  }
  return <div className="flex justify-center pt-2">{formCard}</div>;
}
