import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import App from '../App';

let authState;
vi.mock('../auth/AuthProvider', () => ({
  useAuth: () => authState,
}));

const baseUser = { id: 1, username: 'nimal', fullName: 'Nimal Perera', roles: ['RECEPTIONIST'], permissions: [] };

function renderAt(path) {
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={[path]} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <App />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

const auth = (user) => ({
  status: 'authenticated', user, notice: null,
  hasPermission: (p) => !p || user.permissions.includes(p),
  login: vi.fn(), logout: vi.fn(), changePassword: vi.fn(), setNotice: vi.fn(),
});

describe('routing', () => {
  // Regression: a user with a temporary password landing on "/" used to get a blank page.
  it.each(['/', '/patients', '/anything/deep'])(
    'sends a user with a temporary password from %s to the change-password screen',
    async (path) => {
      authState = auth({ ...baseUser, mustChangePassword: true });
      renderAt(path);
      expect(await screen.findByRole('heading', { name: /set your own password/i })).toBeInTheDocument();
      expect(screen.queryByRole('navigation', { name: /main navigation/i })).not.toBeInTheDocument();
    },
  );

  it('shows the dashboard inside the app shell for a normal user', async () => {
    authState = auth({ ...baseUser, mustChangePassword: false });
    renderAt('/');
    expect(await screen.findByRole('heading', { name: /nimal/i })).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: /main navigation/i })).toBeInTheDocument();
  });

  it('sends anonymous visitors to sign-in', async () => {
    authState = { ...auth(baseUser), status: 'anonymous', user: null };
    renderAt('/patients');
    expect(await screen.findByRole('heading', { name: /sign in/i })).toBeInTheDocument();
  });

  it('shows a not-found page for unknown routes', async () => {
    authState = auth({ ...baseUser, mustChangePassword: false });
    renderAt('/no-such-page');
    expect(await screen.findByRole('heading', { name: /page not found/i })).toBeInTheDocument();
  });
});
