import { useEffect } from 'react';
import { useForm, type FieldPath } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { settingsSchema, type Settings } from '@hms/shared';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { FormField } from '../../components/ui/FormField';
import { Input } from '../../components/ui/Input';
import { Spinner } from '../../components/ui/Spinner';
import { useToast } from '../../components/ui/Toast';
import { api, ApiError } from '../../lib/api';

interface FieldDef {
  name: FieldPath<Settings>;
  label: string;
  hint?: string;
  type?: 'number';
  step?: string;
}

const GROUPS: { title: string; fields: FieldDef[] }[] = [
  {
    title: 'Hospital',
    fields: [
      { name: 'hospitalName', label: 'Hospital name' },
      { name: 'hospitalAddress', label: 'Address' },
      { name: 'hospitalPhone', label: 'Phone' },
    ],
  },
  {
    title: 'Billing',
    fields: [
      { name: 'currencyCode', label: 'Currency code', hint: 'Three-letter ISO code, e.g. LKR.' },
      { name: 'currencySymbol', label: 'Currency symbol' },
      { name: 'taxRatePercent', label: 'Tax rate (%)', type: 'number', step: '0.01', hint: '0 to 100.' },
    ],
  },
  {
    title: 'System',
    fields: [
      {
        name: 'sessionTimeoutMinutes',
        label: 'Session timeout (minutes)',
        type: 'number',
        hint: 'Inactivity before sign-out, 5 to 120.',
      },
      {
        name: 'expiryAlertDays',
        label: 'Expiry alert window (days)',
        type: 'number',
        hint: 'How early to flag expiring stock, 1 to 365.',
      },
    ],
  },
];

export function SettingsPage() {
  const toast = useToast();
  const queryClient = useQueryClient();
  const query = useQuery({ queryKey: ['settings', 'all'], queryFn: () => api<Settings>('/settings') });
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isDirty },
  } = useForm<Settings>({ resolver: zodResolver(settingsSchema) });

  useEffect(() => {
    if (query.data) reset(query.data);
  }, [query.data, reset]);

  const save = useMutation({
    mutationFn: (v: Settings) => api<Settings>('/settings', { method: 'PUT', json: v }),
    onSuccess: (saved) => {
      reset(saved);
      toast('Settings saved', 'success');
      void queryClient.invalidateQueries({ queryKey: ['settings'] });
    },
    onError: (err) =>
      toast(err instanceof ApiError && err.status < 500 ? err.message : 'Could not save settings.', 'danger'),
  });

  if (query.isLoading) return <Spinner label="Loading settings" />;
  if (query.isError) {
    return (
      <div role="alert" className="rounded-control border border-danger bg-surface px-3 py-2 text-sm text-danger">
        Could not load settings.{' '}
        <button type="button" className="font-medium underline" onClick={() => void query.refetch()}>
          Try again
        </button>
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit((v) => save.mutate(v))} noValidate className="flex max-w-2xl flex-col gap-6">
      <h1 className="text-2xl font-semibold text-text">Settings</h1>
      {GROUPS.map((g) => (
        <Card key={g.title}>
          <fieldset className="flex flex-col gap-4">
            <legend className="mb-2 text-lg font-semibold text-text">{g.title}</legend>
            {g.fields.map((f) => (
              <FormField key={f.name} id={`setting-${f.name}`} label={f.label} hint={f.hint} error={errors[f.name]?.message}>
                <Input
                  type={f.type ?? 'text'}
                  step={f.step}
                  inputMode={f.type === 'number' ? 'decimal' : undefined}
                  {...register(f.name, f.type === 'number' ? { valueAsNumber: true } : undefined)}
                />
              </FormField>
            ))}
          </fieldset>
        </Card>
      ))}
      <div>
        <Button type="submit" loading={save.isPending} disabled={!isDirty}>
          Save settings
        </Button>
      </div>
    </form>
  );
}
