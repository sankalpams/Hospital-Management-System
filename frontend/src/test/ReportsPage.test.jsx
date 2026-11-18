import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import ReportsPage from '../features/analytics/ReportsPage';

let permissions = [];
vi.mock('../auth/AuthProvider', () => ({
  useAuth: () => ({ hasPermission: (p) => !p || [].concat(p).some((x) => permissions.includes(x)) }),
}));
vi.mock('../api/org', () => ({ departmentsApi: { options: () => Promise.resolve([]) } }));

const report = (name) => ({
  report: name, from: '2026-11-01', to: '2026-11-18', granularity: 'day', departmentId: 3,
  kpis: [{ key: 'billed', label: 'Billed', value: 1234567.5, unit: 'LKR' }],
  trends: [], breakdowns: [], tables: [], notes: ['Collections are hospital-wide.'],
});
const reportSpy = vi.fn((name) => Promise.resolve(report(name)));
vi.mock('../api/analytics', async (orig) => {
  const real = await orig();
  return { ...real, analyticsApi: { ...real.analyticsApi, report: (...a) => reportSpy(...a) } };
});

function renderAt(url) {
  return render(
    <MemoryRouter initialEntries={[url]}>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ReportsPage />
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

describe('ReportsPage', () => {
  it('offers only the reports the role may open', async () => {
    permissions = ['report:financial'];   // accountant
    renderAt('/reports');
    expect(screen.getAllByRole('tab').map((t) => t.textContent)).toEqual(['Revenue']);
    expect(await screen.findByText('Billed')).toBeInTheDocument();
    expect(reportSpy).toHaveBeenLastCalledWith('revenue', expect.objectContaining({ to: expect.any(String) }));
  });

  it('exports exactly what is on screen, and shows the report notes', async () => {
    permissions = ['report:operational', 'report:financial', 'report:staff'];
    renderAt('/reports?report=revenue&range=custom&from=2026-11-01&to=2026-11-18&departmentId=3&granularity=day');
    expect(screen.getAllByRole('tab')).toHaveLength(9);
    const link = screen.getByRole('link', { name: /export csv/i });
    expect(link.getAttribute('href')).toBe('/api/v1/analytics/revenue/export.csv?from=2026-11-01&to=2026-11-18&departmentId=3&granularity=day');
    expect(await screen.findByText('Collections are hospital-wide.')).toBeInTheDocument();
    expect(reportSpy).toHaveBeenLastCalledWith('revenue', { from: '2026-11-01', to: '2026-11-18', departmentId: '3', granularity: 'day' });
  });
});
