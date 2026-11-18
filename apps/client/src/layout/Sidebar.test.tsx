import { http, HttpResponse } from 'msw';
import { screen } from '@testing-library/react';
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { server } from '../test/server';
import { renderWithProviders } from '../test/render';
import { Sidebar } from './Sidebar';

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

function asRole(role: string) {
  server.use(
    http.get('*/api/v1/auth/me', () =>
      HttpResponse.json({
        user: { id: '1', username: role.toLowerCase(), fullName: 'Test', role, mustChangePassword: false },
        expiresAt: null,
      }),
    ),
  );
}

describe('Sidebar', () => {
  it('shows Users for ADMIN', async () => {
    asRole('ADMIN');
    renderWithProviders(<Sidebar />);
    expect(await screen.findByRole('link', { name: 'Users' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Audit Log' })).toBeInTheDocument();
  });

  it('hides Users for DOCTOR but shows Home', async () => {
    asRole('DOCTOR');
    renderWithProviders(<Sidebar />);
    expect(await screen.findByRole('link', { name: 'Home' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Users' })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Settings' })).not.toBeInTheDocument();
  });
});
