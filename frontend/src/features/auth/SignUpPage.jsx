import { useState } from 'react';
import { Link } from 'react-router-dom';
import { CircleCheck, UserPlus } from 'lucide-react';
import { authApi } from '../../api/auth';
import { toApiError } from '../../api/client';
import { Button, LinkButton } from '../../components/ui/Button';
import { SelectField, TextField } from '../../components/ui/Field';
import { Alert } from '../../components/ui/Alert';
import { Logo } from '../../components/ui/Logo';
import { ROLE_LABELS } from '../../lib/roles';

// Administrator roles are never requestable; the server enforces the same list.
const ROLE_OPTIONS = ['DOCTOR', 'NURSE', 'RECEPTIONIST', 'LAB_STAFF', 'PHARMACIST', 'ACCOUNTANT', 'HR_MANAGER']
  .map((value) => ({ value, label: ROLE_LABELS[value] }));

const EMPTY = { fullName: '', username: '', email: '', role: '', password: '', confirm: '' };

export default function SignUpPage() {
  const [form, setForm] = useState(EMPTY);
  const [fieldErrors, setFieldErrors] = useState({});
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [sent, setSent] = useState(false);

  const update = (key) => (e) => {
    setForm((f) => ({ ...f, [key]: e.target.value }));
    setFieldErrors((fe) => ({ ...fe, [key]: undefined }));
  };

  const onSubmit = async (e) => {
    e.preventDefault();
    const errs = {};
    if (!form.fullName.trim()) errs.fullName = 'Enter your full name.';
    if (!form.username.trim()) errs.username = 'Choose a username.';
    if (!form.role) errs.role = 'Choose the role you work in.';
    if (form.password.length < 10) errs.password = 'Use at least 10 characters, with a letter and a number.';
    if (form.confirm !== form.password) errs.confirm = 'The passwords do not match.';
    setFieldErrors(errs);
    setError(null);
    const first = Object.keys(errs)[0];
    if (first) {
      document.getElementById(`signup-${first}`)?.focus();
      return;
    }
    setSubmitting(true);
    try {
      await authApi.signUp({
        fullName: form.fullName.trim(),
        username: form.username.trim(),
        email: form.email.trim() || null,
        role: form.role,
        password: form.password,
      });
      setSent(true);
    } catch (err) {
      setError(toApiError(err));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="flex min-h-screen items-start justify-center bg-background px-4 py-10 sm:items-center sm:px-8">
      <div className="w-full max-w-[440px]">
        <Link to="/login" className="mb-8 inline-block rounded-md" aria-label="Back to sign in">
          <Logo />
        </Link>

        {sent ? (
          <div role="status">
            <CircleCheck className="h-8 w-8 text-success" aria-hidden="true" />
            <h1 className="mt-3 text-2xl">Request sent</h1>
            <p className="mt-2 text-foreground-muted">
              An administrator will check your details and approve the account. Until then, signing in shows that your
              request is waiting.
            </p>
            <LinkButton to="/login" size="lg" className="mt-6 w-full">Go to sign in</LinkButton>
          </div>
        ) : (
          <>
            <h1 className="text-2xl">Request an account</h1>
            <p className="mt-1 text-sm text-foreground-muted">
              Staff accounts are approved by a hospital administrator before they can be used.
            </p>

            <div className="mt-6" aria-live="polite">
              {error && <Alert tone="danger">{error.message}</Alert>}
            </div>

            <form onSubmit={onSubmit} noValidate className="mt-4 flex flex-col gap-4">
              <TextField id="signup-fullName" label="Full name" autoComplete="name" value={form.fullName}
                onChange={update('fullName')} error={fieldErrors.fullName} required />
              <TextField id="signup-username" label="Username" autoComplete="username" autoCapitalize="none"
                spellCheck={false} value={form.username} onChange={update('username')} error={fieldErrors.username}
                hint="Letters, numbers, dot, underscore or hyphen." required />
              <TextField id="signup-email" label="Email" type="email" autoComplete="email" value={form.email}
                onChange={update('email')} hint="Optional." />
              <SelectField id="signup-role" label="Role" value={form.role} onChange={update('role')}
                options={ROLE_OPTIONS} placeholder="Choose your role" error={fieldErrors.role} required />
              <TextField id="signup-password" label="Password" type="password" autoComplete="new-password"
                value={form.password} onChange={update('password')} error={fieldErrors.password}
                hint="At least 10 characters, with a letter and a number." required />
              <TextField id="signup-confirm" label="Confirm password" type="password" autoComplete="new-password"
                value={form.confirm} onChange={update('confirm')} error={fieldErrors.confirm} required />
              <Button type="submit" size="lg" loading={submitting} icon={UserPlus} className="mt-2 w-full">
                {submitting ? 'Sending request…' : 'Send request'}
              </Button>
            </form>

            <p className="mt-6 text-sm text-foreground-muted">
              Already have an account?{' '}
              <Link to="/login" className="font-medium text-primary underline-offset-4 hover:underline">Sign in</Link>
            </p>
          </>
        )}
      </div>
    </div>
  );
}
