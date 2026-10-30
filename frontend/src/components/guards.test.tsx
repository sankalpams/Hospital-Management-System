import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { RequireAuth, RequirePermission, RequirePasswordChange } from './guards';
import * as useAuthModule from '../hooks/use-auth';
import type { Session } from '../types/api';

vi.mock('../hooks/use-auth');

describe('Route & Permission Guards', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  const mockSession: Session = {
    accessToken: 'valid-access-token',
    refreshToken: 'valid-refresh-token',
    forcePasswordChange: false,
    user: {
      id: '123',
      username: 'doctor',
      email: 'doctor@hms.local',
      firstName: 'Nimal',
      lastName: 'Fernando',
      role: 'DOCTOR',
      active: true,
      forcePasswordChange: false,
      createdAt: '2026-10-31T00:00:00Z',
      version: 0,
    },
  };

  it('RequireAuth redirects unauthenticated user to /login', () => {
    vi.spyOn(useAuthModule, 'useAuth').mockReturnValue({
      session: null,
      user: null,
      login: vi.fn(),
      logout: vi.fn(),
      refreshProfile: vi.fn(),
      has: vi.fn(),
    });

    render(
      <MemoryRouter initialEntries={['/protected']}>
        <Routes>
          <Route element={<RequireAuth />}>
            <Route path="/protected" element={<div>Protected Content</div>} />
          </Route>
          <Route path="/login" element={<div>Login Screen</div>} />
        </Routes>
      </MemoryRouter>
    );

    expect(screen.getByText('Login Screen')).toBeInTheDocument();
    expect(screen.queryByText('Protected Content')).not.toBeInTheDocument();
  });

  it('RequireAuth allows authenticated user to access protected route', () => {
    vi.spyOn(useAuthModule, 'useAuth').mockReturnValue({
      session: mockSession,
      user: mockSession.user,
      login: vi.fn(),
      logout: vi.fn(),
      refreshProfile: vi.fn(),
      has: vi.fn(),
    });

    render(
      <MemoryRouter initialEntries={['/protected']}>
        <Routes>
          <Route element={<RequireAuth />}>
            <Route path="/protected" element={<div>Protected Content</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    );

    expect(screen.getByText('Protected Content')).toBeInTheDocument();
  });

  it('RequirePermission renders children when user has permission', () => {
    vi.spyOn(useAuthModule, 'useAuth').mockReturnValue({
      session: mockSession,
      user: mockSession.user,
      login: vi.fn(),
      logout: vi.fn(),
      refreshProfile: vi.fn(),
      has: (perm: string) => perm === 'EMR_READ',
    });

    render(
      <MemoryRouter initialEntries={['/clinical']}>
        <Routes>
          <Route
            path="/clinical"
            element={
              <RequirePermission permission="EMR_READ">
                <div>Clinical Notes Allowed</div>
              </RequirePermission>
            }
          />
          <Route path="/forbidden" element={<div>Access Forbidden</div>} />
        </Routes>
      </MemoryRouter>
    );

    expect(screen.getByText('Clinical Notes Allowed')).toBeInTheDocument();
  });

  it('RequirePermission redirects to /forbidden when user lacks permission', () => {
    vi.spyOn(useAuthModule, 'useAuth').mockReturnValue({
      session: mockSession,
      user: mockSession.user,
      login: vi.fn(),
      logout: vi.fn(),
      refreshProfile: vi.fn(),
      has: (_perm: string) => false,
    });

    render(
      <MemoryRouter initialEntries={['/admin-settings']}>
        <Routes>
          <Route
            path="/admin-settings"
            element={
              <RequirePermission permission="USER_MANAGE">
                <div>Admin Secret</div>
              </RequirePermission>
            }
          />
          <Route path="/forbidden" element={<div>Access Forbidden</div>} />
        </Routes>
      </MemoryRouter>
    );

    expect(screen.getByText('Access Forbidden')).toBeInTheDocument();
    expect(screen.queryByText('Admin Secret')).not.toBeInTheDocument();
  });

  it('RequirePasswordChange redirects to /change-password if forced flag is set', () => {
    const forcedSession: Session = {
      ...mockSession,
      forcePasswordChange: true,
      user: {
        ...mockSession.user,
        forcePasswordChange: true,
      },
    };

    vi.spyOn(useAuthModule, 'useAuth').mockReturnValue({
      session: forcedSession,
      user: forcedSession.user,
      login: vi.fn(),
      logout: vi.fn(),
      refreshProfile: vi.fn(),
      has: vi.fn(),
    });

    render(
      <MemoryRouter initialEntries={['/dashboard']}>
        <Routes>
          <Route
            path="/dashboard"
            element={
              <RequirePasswordChange>
                <div>Dashboard View</div>
              </RequirePasswordChange>
            }
          />
          <Route path="/change-password" element={<div>Forced Password Change</div>} />
        </Routes>
      </MemoryRouter>
    );

    expect(screen.getByText('Forced Password Change')).toBeInTheDocument();
    expect(screen.queryByText('Dashboard View')).not.toBeInTheDocument();
  });
});
