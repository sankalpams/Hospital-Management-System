import { safeAppPath } from '../../lib/forms';
import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { CalendarCheck2, FlaskConical, LogIn, Pill, ReceiptText, ShieldCheck, Users } from 'lucide-react';
import { useAuth } from '../../auth/AuthProvider';
import { toApiError } from '../../api/client';
import { Button } from '../../components/ui/Button';
import { TextField } from '../../components/ui/Field';
import { Alert } from '../../components/ui/Alert';
import { Logo } from '../../components/ui/Logo';

const HIGHLIGHTS = [
  { icon: Users, text: 'Patient registration and electronic medical records' },
  { icon: CalendarCheck2, text: 'Appointments, admissions and bed management' },
  { icon: FlaskConical, text: 'Laboratory requests, results and review' },
  { icon: Pill, text: 'Pharmacy dispensing and batch-level stock' },
  { icon: ReceiptText, text: 'Billing, payments and receipts' },
];

export default function LoginPage() {
  const { login, notice } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [form, setForm] = useState({ username: '', password: '' });
  const [fieldErrors, setFieldErrors] = useState({});
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [capsLock, setCapsLock] = useState(false);

  const update = (key) => (e) => {
    setForm((f) => ({ ...f, [key]: e.target.value }));
    setFieldErrors((fe) => ({ ...fe, [key]: undefined }));
  };

  const onSubmit = async (e) => {
    e.preventDefault();
    const errs = {};
    if (!form.username.trim()) errs.username = 'Enter your username.';
    if (!form.password) errs.password = 'Enter your password.';
    setFieldErrors(errs);
    setError(null);
    if (Object.keys(errs).length) {
      document.getElementById(errs.username ? 'login-username' : 'login-password')?.focus();
      return;
    }
    setSubmitting(true);
    try {
      const me = await login(form.username.trim(), form.password);
      const target = me.mustChangePassword ? '/change-password' : safeAppPath(location.state?.from?.pathname);
      navigate(target, { replace: true });
    } catch (err) {
      const apiError = toApiError(err);
      setError(apiError);
      setForm((f) => ({ ...f, password: '' }));
      document.getElementById('login-password')?.focus();
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="flex min-h-screen bg-background">
      {/* Brand panel (desktop only) */}
      <aside className="relative hidden w-[44%] max-w-[640px] flex-col justify-between bg-sidebar px-12 py-10 text-sidebar-foreground lg:flex">
        <Logo inverted />
        <div className="max-w-md">
          <h1 className="text-3xl leading-tight text-white">Hospital operations, connected in one secure workspace.</h1>
          <ul className="mt-8 flex flex-col gap-4">
            {HIGHLIGHTS.map(({ icon: Icon, text }) => (
              <li key={text} className="flex items-center gap-3 text-[15px]">
                <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-sidebar-active text-white">
                  <Icon className="h-[18px] w-[18px]" aria-hidden="true" />
                </span>
                {text}
              </li>
            ))}
          </ul>
        </div>
        <p className="flex items-center gap-2 text-xs">
          <ShieldCheck className="h-4 w-4" aria-hidden="true" />
          Access is role-based and every action is recorded in the audit log.
        </p>
      </aside>

      {/* Form */}
      <main className="flex flex-1 items-center justify-center px-4 py-10 sm:px-8">
        <div className="w-full max-w-[400px]">
          <div className="mb-8 lg:hidden">
            <Logo />
          </div>
          <h2 className="text-2xl">Sign in</h2>
          <p className="mt-1 text-sm text-foreground-muted">Use the account issued by your hospital administrator.</p>

          <div className="mt-6 flex flex-col gap-3" aria-live="polite">
            {notice && !error && <Alert tone="info">{notice}</Alert>}
            {error && (
              <Alert tone={error.code === 'ACCOUNT_LOCKED' ? 'warning' : 'danger'} requestId={error.status >= 500 ? error.requestId : undefined}>
                {error.message}
              </Alert>
            )}
          </div>

          <form onSubmit={onSubmit} noValidate className="mt-4 flex flex-col gap-4">
            <TextField
              id="login-username"
              label="Username"
              autoComplete="username"
              autoCapitalize="none"
              spellCheck={false}
              value={form.username}
              onChange={update('username')}
              error={fieldErrors.username}
              required
              autoFocus
            />
            <TextField
              id="login-password"
              label="Password"
              type="password"
              autoComplete="current-password"
              value={form.password}
              onChange={update('password')}
              onKeyUp={(e) => setCapsLock(e.getModifierState?.('CapsLock') ?? false)}
              error={fieldErrors.password}
              hint={capsLock ? 'Caps Lock is on.' : undefined}
              required
            />
            <Button type="submit" size="lg" loading={submitting} icon={LogIn} className="mt-2 w-full">
              {submitting ? 'Signing in…' : 'Sign in'}
            </Button>
          </form>

          <p className="mt-6 text-xs text-foreground-subtle">
            Forgot your password? Ask a hospital administrator to issue a temporary one.
          </p>
          <p className="mt-3 text-sm text-foreground-muted">
            New to Prabodha Hospital?{' '}
            <Link to="/sign-up" className="font-medium text-primary underline-offset-4 hover:underline">Request an account</Link>
          </p>
        </div>
      </main>
    </div>
  );
}
