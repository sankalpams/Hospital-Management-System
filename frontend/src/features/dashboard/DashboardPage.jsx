import { Suspense, lazy } from 'react';
import { useQuery } from '@tanstack/react-query';
import { ShieldCheck, UserRound } from 'lucide-react';
import { analyticsApi } from '../../api/analytics';
import { toApiError } from '../../api/client';
import { Alert } from '../../components/ui/Alert';
import { Spinner } from '../../components/ui/Spinner';
import { KpiGrid, trendGroups } from '../analytics/figures';

const TrendChart = lazy(() => import('../analytics/charts').then((m) => ({ default: m.TrendChart })));
import { useAuth } from '../../auth/AuthProvider';
import { PageHeader } from '../../components/layout/AppShell';
import { Card, CardBody, CardHeader } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { roleLabel } from '../../lib/roles';

/** Groups permission codes ("patient:create") by module for a readable access summary. */
function groupPermissions(permissions = []) {
  const groups = {};
  permissions.forEach((code) => {
    const [module, ...rest] = code.split(':');
    (groups[module] ??= []).push(rest.join(':'));
  });
  return Object.entries(groups).sort(([a], [b]) => a.localeCompare(b));
}

function greeting(date = new Date()) {
  const h = date.getHours();
  if (h < 12) return 'Good morning';
  if (h < 17) return 'Good afternoon';
  return 'Good evening';
}

/**
 * Home: today's figures for the modules the user's role works with (from /analytics/dashboard,
 * computed from live records), the last two weeks' trend, then the account and access summary.
 */
export default function DashboardPage() {
  const { user } = useAuth();
  const groups = groupPermissions(user?.permissions);
  const firstName = user?.fullName?.split(' ')[0] ?? '';
  const today = useQuery({ queryKey: ['analytics', 'dashboard'], queryFn: analyticsApi.dashboard, refetchInterval: 120_000 });

  return (
    <>
      <PageHeader
        title={`${greeting()}, ${firstName}`}
        description="Today at the hospital, for the work your role covers."
      />
      {today.isLoading && <Spinner label="Loading today's figures" />}
      {today.isError && <Alert tone="danger" className="mb-6">{toApiError(today.error).message}</Alert>}
      {today.data && (
        <div className="mb-6 flex flex-col gap-4">
          <KpiGrid kpis={today.data.kpis} />
          {today.data.trends.length > 0 && (
            <Suspense fallback={<Spinner label="Loading charts" />}><div className="grid gap-4 xl:grid-cols-2">
              {trendGroups(today.data.trends).map((g) => (
                <TrendChart key={g.group} title={`${g.series.map((s) => s.label).join(' and ')}, last 14 days`} series={g.series} granularity="day" height={200} />
              ))}
            </div></Suspense>
          )}
        </div>
      )}
      <div className="grid gap-6 lg:grid-cols-3">
        <Card>
          <CardHeader icon={UserRound} title="Your account" />
          <CardBody>
            <dl className="grid gap-3 text-sm">
              <div>
                <dt className="text-foreground-subtle">Name</dt>
                <dd className="font-medium">{user?.fullName}</dd>
              </div>
              <div>
                <dt className="text-foreground-subtle">Username</dt>
                <dd className="font-mono text-[13px]">{user?.username}</dd>
              </div>
              <div>
                <dt className="text-foreground-subtle">Role{user?.roles?.length > 1 ? 's' : ''}</dt>
                <dd className="mt-1 flex flex-wrap gap-1.5">
                  {user?.roles?.map((r) => <Badge key={r} tone="primary">{roleLabel(r)}</Badge>)}
                </dd>
              </div>
            </dl>
          </CardBody>
        </Card>

        <Card className="lg:col-span-2">
          <CardHeader
            icon={ShieldCheck}
            title="Access"
            description={`${user?.permissions?.length ?? 0} permissions granted through your role. Every request is also checked by the server.`}
          />
          <CardBody>
            {groups.length === 0 ? (
              <p className="text-sm text-foreground-muted">Your role has no module permissions yet. Contact an administrator.</p>
            ) : (
              <dl className="grid gap-x-6 gap-y-4 sm:grid-cols-2">
                {groups.map(([module, actions]) => (
                  <div key={module}>
                    <dt className="text-[13px] font-semibold capitalize">{module.replace(/-/g, ' ')}</dt>
                    <dd className="mt-1.5 flex flex-wrap gap-1.5">
                      {actions.map((a) => <Badge key={a}>{a.replace(/[-:]/g, ' ')}</Badge>)}
                    </dd>
                  </div>
                ))}
              </dl>
            )}
          </CardBody>
        </Card>
      </div>
    </>
  );
}
