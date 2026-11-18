import { act, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setServerOffset } from '../lib/api';
import { AuthContext, type AuthContextValue } from './AuthContext';
import { SessionTimeoutWarning } from './SessionTimeoutWarning';

function Where() {
  const loc = useLocation();
  const from = (loc.state as { from?: string } | null)?.from ?? '';
  return <div data-testid="where">{`${loc.pathname}${loc.search}|${from}`}</div>;
}

function setup(secondsLeft: number, refreshResult: () => Date | null = () => null) {
  const refresh = vi.fn().mockImplementation(() => Promise.resolve(refreshResult()));
  const logout = vi.fn().mockResolvedValue(undefined);
  const value: AuthContextValue = {
    user: { id: '1', username: 'a', fullName: 'A', role: 'ADMIN', mustChangePassword: false },
    expiresAt: new Date(Date.now() + secondsLeft * 1000),
    loading: false,
    expired: false,
    login: vi.fn(),
    logout,
    refresh,
    can: () => true,
  };
  render(
    <AuthContext.Provider value={value}>
      <MemoryRouter initialEntries={['/patients']}>
        <Routes>
          <Route
            path="*"
            element={
              <>
                <SessionTimeoutWarning />
                <Where />
              </>
            }
          />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>,
  );
  return { refresh, logout };
}

beforeEach(() => vi.useFakeTimers());
afterEach(() => {
  vi.useRealTimers();
  setServerOffset(0);
});

describe('SessionTimeoutWarning', () => {
  it('stays hidden while more than 60 seconds remain', () => {
    setup(120);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('opens with a live countdown when 59 seconds remain', () => {
    setup(59);
    expect(screen.getByRole('dialog', { name: /session expiring/i })).toBeInTheDocument();
    expect(screen.getByTestId('countdown')).toHaveTextContent('59');
    act(() => {
      vi.advanceTimersByTime(3000);
    });
    expect(screen.getByTestId('countdown')).toHaveTextContent('56');
  });

  it('Stay signed in refreshes the session', () => {
    const { refresh } = setup(59);
    fireEvent.click(screen.getByRole('button', { name: 'Stay signed in' }));
    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it('applies the server clock offset to the countdown', () => {
    setServerOffset(30_000); // the server clock is 30s ahead of ours: only 29s really remain
    setup(59);
    expect(screen.getByTestId('countdown')).toHaveTextContent('29');
  });

  it('does not warn early when the server clock is behind ours', () => {
    setServerOffset(-30_000); // 89s really remain
    setup(59);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('Log out logs out and goes to the login page', async () => {
    const { logout } = setup(59);
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Log out' }));
    });
    expect(logout).toHaveBeenCalled();
    expect(screen.getByTestId('where')).toHaveTextContent('/login');
  });

  it('at zero, a refresh that extends the session means no logout', async () => {
    const { logout, refresh } = setup(59, () => new Date(Date.now() + 15 * 60_000));
    await act(async () => {
      await vi.advanceTimersByTimeAsync(60_000);
    });
    expect(refresh).toHaveBeenCalled();
    expect(logout).not.toHaveBeenCalled();
    expect(screen.getByTestId('where')).toHaveTextContent('/patients');
  });

  it('at zero, a failed refresh logs out and goes to /login?reason=timeout, remembering the page', async () => {
    const { logout, refresh } = setup(59, () => null);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(60_000);
    });
    expect(refresh).toHaveBeenCalledTimes(1);
    expect(logout).toHaveBeenCalledTimes(1);
    expect(logout).toHaveBeenCalledWith({ reason: 'timeout' });
    expect(screen.getByTestId('where')).toHaveTextContent('/login?reason=timeout|/patients');
  });

  it('at zero, a refresh that still returns a past expiry logs out', async () => {
    const { logout } = setup(59, () => new Date(Date.now() - 1000));
    await act(async () => {
      await vi.advanceTimersByTimeAsync(60_000);
    });
    expect(logout).toHaveBeenCalledTimes(1);
  });
});
