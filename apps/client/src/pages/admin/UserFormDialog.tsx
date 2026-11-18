import { useState } from 'react';
import { useForm, type Resolver } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { ROLES, createUserSchema, updateUserSchema, type Role } from '@hms/shared';
import { Button } from '../../components/ui/Button';
import { Dialog } from '../../components/ui/Dialog';
import { FormField } from '../../components/ui/FormField';
import { Input } from '../../components/ui/Input';
import { Select } from '../../components/ui/Select';
import { useToast } from '../../components/ui/Toast';
import { roleLabel } from '../../layout/TopBar';
import { api, ApiError } from '../../lib/api';

export interface UserRow {
  id: string;
  username: string;
  fullName: string;
  email: string | null;
  role: Role;
  isActive: boolean;
  mustChangePassword: boolean;
  lastLoginAt: string | null;
}

interface FormValues {
  username: string;
  fullName: string;
  email?: string;
  role: Role;
}

const blankToUndefined = (v: FormValues) => ({ ...v, email: v.email?.trim() || undefined });

const createResolver: Resolver<FormValues> = (values, ctx, opts) =>
  zodResolver(createUserSchema)(blankToUndefined(values), ctx, opts as never) as ReturnType<Resolver<FormValues>>;

const editResolver: Resolver<FormValues> = (values, ctx, opts) => {
  const rest = { ...blankToUndefined(values), username: undefined };
  delete rest.username;
  return zodResolver(updateUserSchema)(rest, ctx, opts as never) as ReturnType<Resolver<FormValues>>;
};

interface Props {
  /** null = closed; 'create' = new user; a row = edit that user. */
  target: 'create' | UserRow | null;
  onClose: () => void;
  /** Called after a create succeeds with the one-time temporary password. */
  onCreated: (user: UserRow, temporaryPassword: string) => void;
}

export function UserFormDialog({ target, onClose, onCreated }: Props) {
  // Remount the form per target so defaults reset cleanly.
  return (
    <Dialog open={target !== null} title={target === 'create' ? 'Add user' : 'Edit user'} onClose={onClose}>
      {target !== null && (
        <UserForm key={target === 'create' ? 'create' : target.id} target={target} onClose={onClose} onCreated={onCreated} />
      )}
    </Dialog>
  );
}

function UserForm({
  target,
  onClose,
  onCreated,
}: {
  target: 'create' | UserRow;
  onClose: Props['onClose'];
  onCreated: Props['onCreated'];
}) {
  const isCreate = target === 'create';
  const queryClient = useQueryClient();
  const toast = useToast();
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: isCreate ? createResolver : editResolver,
    defaultValues: isCreate
      ? { username: '', fullName: '', email: '', role: 'RECEPTIONIST' }
      : { username: target.username, fullName: target.fullName, email: target.email ?? '', role: target.role },
  });

  const mutation = useMutation({
    mutationFn: async (v: FormValues) => {
      if (isCreate) {
        const body = blankToUndefined(v);
        return api<{ user: UserRow; temporaryPassword: string }>('/users', { method: 'POST', json: body });
      }
      const payload: Record<string, string> = {};
      if (v.fullName.trim() !== target.fullName) payload.fullName = v.fullName.trim();
      if (v.role !== target.role) payload.role = v.role;
      const email = (v.email ?? '').trim();
      if (email && email !== (target.email ?? '')) payload.email = email;
      return api<UserRow>(`/users/${target.id}`, { method: 'PATCH', json: payload });
    },
    onSuccess: (res) => {
      void queryClient.invalidateQueries({ queryKey: ['users'] });
      if (isCreate) {
        const created = res as { user: UserRow; temporaryPassword: string };
        onCreated(created.user, created.temporaryPassword);
      } else {
        toast('User updated', 'success');
      }
      onClose();
    },
    onError: (err) => {
      if (err instanceof ApiError && err.code === 'USERNAME_TAKEN') {
        setError('username', { message: err.message }, { shouldFocus: true });
      } else if (err instanceof ApiError && err.status < 500) {
        setFormError(err.message);
      } else {
        setFormError('Something went wrong. Please try again.');
      }
    },
  });

  return (
    <form
      onSubmit={handleSubmit((v) => {
        setFormError(null);
        mutation.mutate(v);
      })}
      noValidate
      className="flex flex-col gap-4"
    >
      {formError && (
        <div role="alert" className="rounded-control border border-danger bg-surface px-3 py-2 text-sm font-medium text-danger">
          {formError}
        </div>
      )}
      <FormField id="user-username" label="Username" error={errors.username?.message}>
        <Input autoComplete="off" autoCapitalize="none" spellCheck={false} disabled={!isCreate} {...register('username')} />
      </FormField>
      <FormField id="user-fullName" label="Full name" error={errors.fullName?.message}>
        <Input autoComplete="off" {...register('fullName')} />
      </FormField>
      <FormField id="user-email" label="Email (optional)" error={errors.email?.message}>
        <Input type="email" autoComplete="off" {...register('email')} />
      </FormField>
      <FormField id="user-role" label="Role" error={errors.role?.message}>
        <Select {...register('role')}>
          {ROLES.map((r) => (
            <option key={r} value={r}>
              {roleLabel(r)}
            </option>
          ))}
        </Select>
      </FormField>
      {isCreate && (
        <p className="text-xs text-muted">A temporary password is generated; the user must change it at first sign-in.</p>
      )}
      <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
        <Button variant="secondary" onClick={onClose} disabled={mutation.isPending}>
          Cancel
        </Button>
        <Button type="submit" loading={mutation.isPending}>
          {isCreate ? 'Create user' : 'Save changes'}
        </Button>
      </div>
    </form>
  );
}

/** Shows a generated password exactly once, with a copy button that degrades if the clipboard is blocked. */
export function TemporaryPasswordDialog({
  password,
  username,
  onClose,
}: {
  password: string | null;
  username: string;
  onClose: () => void;
}) {
  const [copy, setCopy] = useState<'idle' | 'copied' | 'failed'>('idle');
  const doCopy = async () => {
    try {
      await navigator.clipboard.writeText(password ?? '');
      setCopy('copied');
    } catch {
      setCopy('failed');
    }
  };
  const close = () => {
    setCopy('idle');
    onClose();
  };
  return (
    <Dialog open={password !== null} title="Temporary password" onClose={close}>
      <p className="text-sm text-text">
        Share this temporary password with <strong>{username}</strong>. It is shown only once and must be changed at
        first sign-in.
      </p>
      <div className="mt-4 flex items-center gap-2">
        <code className="min-h-11 flex-1 select-all break-all rounded-control border border-border bg-bg px-3 py-2.5 font-mono text-base text-text">
          {password}
        </code>
        <Button variant="secondary" onClick={() => void doCopy()}>
          Copy
        </Button>
      </div>
      <p role="status" className="mt-2 min-h-5 text-xs text-muted">
        {copy === 'copied' && 'Copied'}
        {copy === 'failed' && 'Could not copy automatically. Select the password and copy manually.'}
      </p>
      <div className="mt-4 flex justify-end">
        <Button onClick={close}>Done</Button>
      </div>
    </Dialog>
  );
}
