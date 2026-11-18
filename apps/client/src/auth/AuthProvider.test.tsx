import { http, HttpResponse } from 'msw';
import { act, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import userEvent from '@testing-library/user-event';
import { server } from '../test/server';
import { AuthProvider } from './AuthProvider';
import { RequireAuth } from './RequireAuth';
import { useAuth } from './useAuth';
import { LoginPage } from '../pages/LoginPage';
import { api } from '../lib/api';

const adminUser = { id: '1', username: 'admin', fullName: 'Admin', role: 'ADMIN', mustChangePassword: false };

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

function Probe() {
  const { user, logout } = useAuth();
  return (
    <div>
      <span>{user ? 'signed-in' : 'signed-out'}</span>
      <button onClick={() => void logout()}>do-logout</button>
    </div>
  );
}

function mount(client: QueryClient) {
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <AuthProvider>
          <Probe />
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('AuthProvider cache hygiene', () => {
  it('clears the query cache on logout', async () => {
    server.use(
      http.get('*/api/v1/auth/me', () => HttpResponse.json({ user: adminUser, expiresAt: null })),
      http.post('*/api/v1/auth/logout', () => new HttpResponse(null, { status: 204 })),
    );
    const client = new QueryClient();
    client.setQueryData(['patients'], [{ id: 'p1' }]);
    mount(client);
    await screen.findByText('signed-in');
    await userEvent.click(screen.getByText('do-logout'));
    await screen.findByText('signed-out');
    expect(client.getQueryCache().getAll()).toHaveLength(0);
  });

  it('clears the query cache when a request returns 401', async () => {
    server.use(
      http.get('*/api/v1/auth/me', () => HttpResponse.json({ user: adminUser, expiresAt: null })),
      http.get('*/api/v1/other', () =>
        HttpResponse.json({ error: { code: 'UNAUTHENTICATED', message: 'no' } }, { status: 401 }),
      ),
    );
    const client = new QueryClient();
    client.setQueryData(['patients'], [{ id: 'p1' }]);
    mount(client);
    await screen.findByText('signed-in');
    await act(async () => {
      await api('/other').catch(() => undefined);
    });
    await waitFor(() => expect(screen.getByText('signed-out')).toBeInTheDocument());
    expect(client.getQueryCache().getAll()).toHaveLength(0);
  });
});

function Where() {
  const l = useLocation();
  return <p>at:{l.pathname + l.search + l.hash}</p>;
}

describe('RequireAuth redirect back', () => {
  it('returns to pathname + search + hash after login', async () => {
    server.use(
      http.get('*/api/v1/auth/me', () =>
        HttpResponse.json({ error: { code: 'UNAUTHENTICATED', message: 'no' } }, { status: 401 }),
      ),
      http.post('*/api/v1/auth/login', () => HttpResponse.json({ user: adminUser })),
    );
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter initialEntries={['/secret?tab=2#sec']}>
          <AuthProvider>
            <Routes>
              <Route path="/login" element={<LoginPage />} />
              <Route
                path="/secret"
                element={
                  <RequireAuth>
                    <Where />
                  </RequireAuth>
                }
              />
            </Routes>
          </AuthProvider>
        </MemoryRouter>
      </QueryClientProvider>,
    );
    await userEvent.type(await screen.findByLabelText('Username'), 'admin');
    await userEvent.type(screen.getByLabelText('Password'), 'Password1!');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByText('at:/secret?tab=2#sec')).toBeInTheDocument();
  });
});

function ExpiredProbe() {
  const { user, expired, refresh } = useAuth();
  return (
    <div>
      <span>{user ? 'signed-in' : 'signed-out'}</span>
      <span>{expired ? 'is-expired' : 'not-expired'}</span>
      <button onClick={() => void refresh()}>do-refresh</button>
    </div>
  );
}

describe('AuthProvider expired flag', () => {
  it('resets expired to false when a refresh succeeds', async () => {
    let meCalls = 0;
    server.use(
      http.get('*/api/v1/auth/me', () => {
        meCalls += 1;
        return HttpResponse.json({
          user: adminUser,
          expiresAt: new Date(Date.now() - 60_000).toISOString(),
        });
      }),
      http.get('*/api/v1/other', () =>
        HttpResponse.json({ error: { code: 'UNAUTHENTICATED', message: 'no' } }, { status: 401 }),
      ),
    );
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <AuthProvider>
            <ExpiredProbe />
          </AuthProvider>
        </MemoryRouter>
      </QueryClientProvider>,
    );
    await screen.findByText('signed-in');
    await act(async () => {
      await api('/other').catch(() => undefined);
    });
    await screen.findByText('is-expired');
    await userEvent.click(screen.getByText('do-refresh'));
    await screen.findByText('not-expired');
    expect(meCalls).toBeGreaterThanOrEqual(2);
  });
});
