import { http, HttpResponse } from 'msw';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { server } from '../../test/server';
import { renderWithProviders } from '../../test/render';
import { App } from '../../App';

const user = (role: string) => ({ id: 'a1', username: 'x', fullName: 'X', role, mustChangePassword: false });
const settings = {
  hospitalName: 'City Hospital',
  hospitalAddress: 'Colombo',
  hospitalPhone: '0112',
  currencyCode: 'LKR',
  currencySymbol: 'Rs.',
  taxRatePercent: 5,
  sessionTimeoutMinutes: 30,
  expiryAlertDays: 90,
};

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

function signIn(role: string) {
  server.use(
    http.get('*/api/v1/auth/me', () => HttpResponse.json({ user: user(role), expiresAt: null })),
    http.get('*/api/v1/settings/public', () => HttpResponse.json({ hospitalName: 'H' })),
  );
}

describe('route guards', () => {
  it('sends a nurse away from admin pages', async () => {
    signIn('NURSE');
    renderWithProviders(<App />, '/admin/users');
    await waitFor(() => expect(screen.queryByRole('table', { name: 'Users' })).not.toBeInTheDocument());
    expect(await screen.findByText(/do not have access/i)).toBeInTheDocument();
  });
});

describe('SettingsPage', () => {
  it('loads every field, validates, and saves', async () => {
    signIn('ADMIN');
    let saved: Record<string, unknown> = {};
    server.use(
      http.get('*/api/v1/settings', () => HttpResponse.json(settings)),
      http.put('*/api/v1/settings', async ({ request }) => {
        saved = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(saved);
      }),
    );
    renderWithProviders(<App />, '/admin/settings');
    const name = await screen.findByLabelText('Hospital name');
    await waitFor(() => expect(name).toHaveValue('City Hospital'));
    for (const l of [
      'Address',
      'Phone',
      'Currency code',
      'Currency symbol',
      'Tax rate (%)',
      'Session timeout (minutes)',
      'Expiry alert window (days)',
    ]) {
      expect(screen.getByLabelText(l)).toBeInTheDocument();
    }
    const timeout = screen.getByLabelText('Session timeout (minutes)');
    await userEvent.clear(timeout);
    await userEvent.type(timeout, '2');
    await userEvent.click(screen.getByRole('button', { name: 'Save settings' }));
    expect(await screen.findByText(/greater than or equal to 5|at least 5/i)).toBeInTheDocument();
    await userEvent.clear(timeout);
    await userEvent.type(timeout, '45');
    await userEvent.click(screen.getByRole('button', { name: 'Save settings' }));
    await waitFor(() => expect(saved).toMatchObject({ sessionTimeoutMinutes: 45, taxRatePercent: 5 }));
  });
});

describe('AuditLogPage', () => {
  it('filters via query params and expands before/after JSON', async () => {
    signIn('ADMIN');
    const seen: URLSearchParams[] = [];
    server.use(
      http.get('*/api/v1/users', () => HttpResponse.json({ items: [], page: 1, pageSize: 200, total: 0 })),
      http.get('*/api/v1/audit-logs', ({ request }) => {
        seen.push(new URL(request.url).searchParams);
        return HttpResponse.json({
          items: [
            {
              id: 'l1',
              userId: 'a1',
              action: 'UPDATE',
              entityType: 'User',
              entityId: 'u1',
              before: { role: 'NURSE' },
              after: { role: 'DOCTOR' },
              ip: '1.2.3.4',
              createdAt: '2026-11-18T05:00:00Z',
              user: { username: 'admin', fullName: 'Admin' },
            },
          ],
          page: 1,
          pageSize: 25,
          total: 1,
        });
      }),
    );
    renderWithProviders(<App />, '/admin/audit');
    expect(await screen.findByRole('table', { name: 'Audit log' })).toBeInTheDocument();
    const toggle = await screen.findByRole('button', { name: 'Show changes' });
    expect(toggle).toHaveAttribute('aria-expanded', 'false');
    await userEvent.click(toggle);
    expect(screen.getByText(/"role": "DOCTOR"/)).toBeInTheDocument();
    expect(screen.getByText(/"role": "NURSE"/)).toBeInTheDocument();

    await userEvent.selectOptions(screen.getByLabelText('Action'), 'UPDATE');
    await userEvent.type(screen.getByLabelText('Entity type'), 'User');
    await userEvent.type(screen.getByLabelText('From date'), '2026-11-01');
    await userEvent.type(screen.getByLabelText('To date'), '2026-11-18');
    await waitFor(() => {
      const last = seen[seen.length - 1];
      expect(last?.get('action')).toBe('UPDATE');
      expect(last?.get('entityType')).toBe('User');
      expect(last?.get('from')).toBe('2026-11-01');
      expect(last?.get('to')).toBe('2026-11-18');
    });
  });

  it('flags an inverted date range', async () => {
    signIn('ADMIN');
    server.use(
      http.get('*/api/v1/users', () => HttpResponse.json({ items: [], page: 1, pageSize: 200, total: 0 })),
      http.get('*/api/v1/audit-logs', () => HttpResponse.json({ items: [], page: 1, pageSize: 25, total: 0 })),
    );
    renderWithProviders(<App />, '/admin/audit');
    await userEvent.type(await screen.findByLabelText('From date'), '2026-11-18');
    await userEvent.type(screen.getByLabelText('To date'), '2026-11-01');
    expect(await screen.findByText(/start date must be on or before/i)).toBeInTheDocument();
  });

  it('debounces the entity type filter', async () => {
    signIn('ADMIN');
    const seen: (string | null)[] = [];
    server.use(
      http.get('*/api/v1/users', () => HttpResponse.json({ items: [], page: 1, pageSize: 200, total: 0 })),
      http.get('*/api/v1/audit-logs', ({ request }) => {
        seen.push(new URL(request.url).searchParams.get('entityType'));
        return HttpResponse.json({ items: [], page: 1, pageSize: 25, total: 0 });
      }),
    );
    renderWithProviders(<App />, '/admin/audit');
    await userEvent.type(await screen.findByLabelText('Entity type'), 'User');
    await waitFor(() => expect(seen).toContain('User'));
    expect(seen.filter((v) => v !== null && v !== 'User')).toEqual([]);
  });

  it('only sets aria-controls on the toggle while the details are shown', async () => {
    signIn('ADMIN');
    server.use(
      http.get('*/api/v1/users', () => HttpResponse.json({ items: [], page: 1, pageSize: 200, total: 0 })),
      http.get('*/api/v1/audit-logs', () =>
        HttpResponse.json({
          items: [
            {
              id: 'l1', userId: null, action: 'UPDATE', entityType: 'User', entityId: 'u1',
              before: { a: 1 }, after: { a: 2 }, ip: null, createdAt: '2026-11-18T05:00:00Z', user: null,
            },
          ],
          page: 1, pageSize: 25, total: 1,
        }),
      ),
    );
    renderWithProviders(<App />, '/admin/audit');
    const toggle = await screen.findByRole('button', { name: 'Show changes' });
    expect(toggle).not.toHaveAttribute('aria-controls');
    await userEvent.click(toggle);
    expect(screen.getByRole('button', { name: 'Hide changes' })).toHaveAttribute('aria-controls', 'audit-detail-l1');
  });
});
