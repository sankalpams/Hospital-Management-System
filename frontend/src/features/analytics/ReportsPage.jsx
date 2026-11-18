import { keepPreviousData, useQuery } from '@tanstack/react-query';
import clsx from 'clsx';
import { Download, Info } from 'lucide-react';
import { REPORTS, analyticsApi } from '../../api/analytics';
import { departmentsApi } from '../../api/org';
import { toApiError } from '../../api/client';
import { useAuth } from '../../auth/AuthProvider';
import { PageHeader } from '../../components/layout/AppShell';
import { FilterSelect, Toolbar } from '../../components/ui/Toolbar';
import { buttonClasses } from '../../components/ui/Button';
import { Alert } from '../../components/ui/Alert';
import { TextField } from '../../components/ui/Field';
import { Spinner } from '../../components/ui/Spinner';
import { addDays, formatDate, todayInHospital } from '../../lib/format';
import { useListParams } from '../../lib/useListParams';
import { BreakdownChart, ReportTable, TrendChart } from './charts';
import { KpiGrid, trendGroups } from './figures';

const PRESETS = [
  { value: '30d', label: 'Last 30 days', days: 30 },
  { value: '90d', label: 'Last 3 months', days: 91 },
  { value: '12m', label: 'Last 12 months', days: 365 },
  { value: 'custom', label: 'Custom range' },
];

export function ReportBody({ report }) {
  const groups = trendGroups(report.trends);
  return (
    <div className="flex flex-col gap-4">
      <KpiGrid kpis={report.kpis} />
      {groups.map((g) => (
        <TrendChart key={g.group} title={g.series.map((s) => s.label).join(' and ')} series={g.series} granularity={report.granularity}
                    description={`By ${report.granularity}, ${formatDate(report.from)} to ${formatDate(report.to)}. The latest ${report.granularity} may be incomplete.`} />
      ))}
      {report.breakdowns.length > 0 && (
        <div className="grid gap-4 lg:grid-cols-2">
          {report.breakdowns.map((b) => <BreakdownChart key={b.key} title={b.label} rows={b.rows} unit={b.unit} />)}
        </div>
      )}
      {report.tables.map((t) => <ReportTable key={t.key} table={t} />)}
      {report.notes.length > 0 && (
        <ul className="flex flex-col gap-1 text-xs text-foreground-muted">
          {report.notes.map((n) => <li key={n} className="flex gap-1.5"><Info className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />{n}</li>)}
        </ul>
      )}
    </div>
  );
}

export default function ReportsPage() {
  const { hasPermission } = useAuth();
  const reports = REPORTS.filter((r) => hasPermission(r.permission));
  const [params, setParams] = useListParams({ report: reports[0]?.id, range: '12m' });
  const report = reports.some((r) => r.id === params.report) ? params.report : reports[0]?.id;
  const today = todayInHospital();
  const preset = PRESETS.find((p) => p.value === params.range) ?? PRESETS[2];
  // "Last 12 months" starts on the 1st so every month but the current one is whole
  const from = preset.value === '12m' ? `${addDays(today, -334).slice(0, 7)}-01`
    : preset.days ? addDays(today, -(preset.days - 1)) : params.from || addDays(today, -29);
  const to = preset.days ? today : params.to || today;
  const filter = { from, to, departmentId: params.departmentId, granularity: params.granularity };

  const departments = useQuery({ queryKey: ['department-options'], queryFn: departmentsApi.options, staleTime: 300_000 });
  const data = useQuery({
    queryKey: ['analytics', report, filter],
    queryFn: () => analyticsApi.report(report, filter),
    enabled: !!report,
    placeholderData: keepPreviousData,
  });

  return (
    <>
      <PageHeader title="Reports" description="Every figure is calculated from the hospital's records for the dates you choose."
                  actions={report && (
                    <a href={analyticsApi.exportUrl(report, filter)} download className={buttonClasses({ variant: 'secondary' })}>
                      <Download className="h-4 w-4" aria-hidden="true" /> Export CSV
                    </a>
                  )} />
      <div role="tablist" aria-label="Reports" className="mb-4 flex flex-wrap gap-1 rounded-md border border-border bg-surface p-1">
        {reports.map((r) => (
          <button key={r.id} type="button" role="tab" aria-selected={report === r.id} onClick={() => setParams({ report: r.id })}
                  className={clsx('rounded px-3 py-1.5 text-sm font-medium', report === r.id ? 'bg-primary text-primary-foreground' : 'text-foreground-muted hover:text-foreground')}>
            {r.label}
          </button>
        ))}
      </div>
      <Toolbar>
        <FilterSelect label="Period" value={preset.value} onChange={(range) => setParams({ range, granularity: '' })} options={PRESETS} />
        {preset.value === 'custom' && (
          <>
            <TextField label="From" type="date" className="w-40" max={to} value={from} onChange={(e) => setParams({ from: e.target.value })} />
            <TextField label="To" type="date" className="w-40" min={from} max={today} value={to} onChange={(e) => setParams({ to: e.target.value })} />
          </>
        )}
        <FilterSelect label="Group by" value={params.granularity ?? ''} onChange={(granularity) => setParams({ granularity })}
                      options={[{ value: '', label: 'Automatic' }, { value: 'day', label: 'Day' }, { value: 'week', label: 'Week' }, { value: 'month', label: 'Month' }]} />
        <FilterSelect label="Department" value={params.departmentId ?? ''} onChange={(departmentId) => setParams({ departmentId })}
                      options={[{ value: '', label: 'All departments' }, ...(departments.data ?? []).map((d) => ({ value: String(d.id), label: d.name }))]} />
        {data.isFetching && <Spinner className="h-4 w-4" label="Updating" />}
      </Toolbar>
      {!report && <Alert tone="info">Your role has no reports.</Alert>}
      {data.isError && <Alert tone="danger">{toApiError(data.error).message}</Alert>}
      {data.isLoading && <Spinner label="Calculating" />}
      {data.data && <ReportBody report={data.data} />}
    </>
  );
}
