import { useRef, useState, type FormEvent } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { FormField } from '../components/ui/FormField';
import { Input } from '../components/ui/Input';
import { ApiError } from '../lib/api';

interface FieldErrors {
  username?: string;
  password?: string;
}

export function LoginPage() {
  const { user, loading, login } = useAuth();
  const location = useLocation();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const userRef = useRef<HTMLInputElement>(null);
  const passRef = useRef<HTMLInputElement>(null);

  const from = (location.state as { from?: string } | null)?.from ?? '/';
  if (!loading && user) return <Navigate to={from} replace />;

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    const next: FieldErrors = {};
    if (!username.trim()) next.username = 'Enter your username';
    if (!password) next.password = 'Enter your password';
    setErrors(next);
    setFormError(null);
    if (next.username) return userRef.current?.focus();
    if (next.password) return passRef.current?.focus();

    setSubmitting(true);
    try {
      await login(username.trim(), password);
    } catch (err) {
      if (err instanceof ApiError && (err.status === 401 || err.status === 423 || err.status === 429)) {
        setFormError(err.message);
      } else {
        setFormError('Something went wrong. Please try again.');
      }
      setPassword('');
      passRef.current?.focus();
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <main className="flex min-h-screen items-center justify-center bg-bg p-4">
      <Card className="w-full max-w-sm">
        <h1 className="text-2xl font-semibold text-text">Sign in</h1>
        <p className="mt-1 text-sm text-muted">Hospital Management System</p>
        <form onSubmit={onSubmit} noValidate className="mt-6 flex flex-col gap-4">
          {formError && (
            <div
              role="alert"
              className="rounded-control border border-danger bg-surface px-3 py-2 text-sm font-medium text-danger"
            >
              {formError}
            </div>
          )}
          <FormField id="username" label="Username" error={errors.username}>
            <Input
              ref={userRef}
              autoComplete="username"
              autoCapitalize="none"
              spellCheck={false}
              autoFocus
              value={username}
              onChange={(e) => setUsername(e.target.value)}
            />
          </FormField>
          <FormField id="password" label="Password" error={errors.password}>
            <Input
              ref={passRef}
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </FormField>
          <Button type="submit" loading={submitting} className="w-full">
            Sign in
          </Button>
        </form>
      </Card>
    </main>
  );
}
