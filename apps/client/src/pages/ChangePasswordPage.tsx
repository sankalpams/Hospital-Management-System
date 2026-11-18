import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useNavigate } from 'react-router-dom';
import { z } from 'zod';
import { passwordSchema } from '@hms/shared';
import { useAuth } from '../auth/useAuth';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { FormField } from '../components/ui/FormField';
import { Input } from '../components/ui/Input';
import { api, ApiError } from '../lib/api';

const formSchema = z
  .object({
    currentPassword: z.string().min(1, 'Enter your current password'),
    newPassword: passwordSchema,
    confirmPassword: z.string().min(1, 'Confirm your new password'),
  })
  .refine((v) => v.newPassword === v.confirmPassword, {
    path: ['confirmPassword'],
    message: 'Passwords do not match',
  });
type FormValues = z.infer<typeof formSchema>;

export function ChangePasswordPage() {
  const { user, refresh, logout } = useAuth();
  const navigate = useNavigate();
  const forced = user?.mustChangePassword ?? false;
  const {
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(formSchema),
    defaultValues: { currentPassword: '', newPassword: '', confirmPassword: '' },
  });

  const onSubmit = handleSubmit(async ({ currentPassword, newPassword }) => {
    try {
      await api('/auth/change-password', { method: 'POST', json: { currentPassword, newPassword } });
    } catch (err) {
      if (err instanceof ApiError && err.code === 'INVALID_CURRENT_PASSWORD') {
        setError('currentPassword', { message: err.message }, { shouldFocus: true });
      } else if (err instanceof ApiError && err.status < 500) {
        setError('root', { message: err.message });
      } else {
        setError('root', { message: 'Something went wrong. Please try again.' });
      }
      return;
    }
    reset();
    await refresh();
    navigate('/', { replace: true });
  });

  const onLogout = async () => {
    try {
      await logout();
    } catch {
      /* the session is cleared client-side regardless */
    }
    navigate('/login', { replace: true });
  };

  return (
    <main className="flex min-h-screen items-center justify-center bg-bg p-4">
      <Card className="w-full max-w-md">
        <h1 className="text-2xl font-semibold text-text">Change password</h1>
        <p className="mt-1 text-sm text-muted">
          {forced
            ? 'You must set a new password before you can continue.'
            : 'Choose a new password for your account.'}
        </p>
        <form onSubmit={onSubmit} noValidate className="mt-6 flex flex-col gap-4">
          {errors.root && (
            <div
              role="alert"
              className="rounded-control border border-danger bg-surface px-3 py-2 text-sm font-medium text-danger"
            >
              {errors.root.message}
            </div>
          )}
          <FormField id="currentPassword" label="Current password" error={errors.currentPassword?.message}>
            <Input type="password" autoComplete="current-password" autoFocus {...register('currentPassword')} />
          </FormField>
          <FormField
            id="newPassword"
            label="New password"
            hint="At least 8 characters, with a letter and a digit."
            error={errors.newPassword?.message}
          >
            <Input type="password" autoComplete="new-password" {...register('newPassword')} />
          </FormField>
          <FormField id="confirmPassword" label="Confirm new password" error={errors.confirmPassword?.message}>
            <Input type="password" autoComplete="new-password" {...register('confirmPassword')} />
          </FormField>
          <div className="flex flex-col gap-2 sm:flex-row-reverse">
            <Button type="submit" loading={isSubmitting}>
              Change password
            </Button>
            {forced ? (
              <Button variant="secondary" onClick={() => void onLogout()}>
                Log out
              </Button>
            ) : (
              <Button variant="secondary" onClick={() => navigate('/')}>
                Cancel
              </Button>
            )}
          </div>
        </form>
      </Card>
    </main>
  );
}
