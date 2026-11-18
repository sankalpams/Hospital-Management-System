import { act, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthContext, type AuthContextValue } from './AuthContext';
import { SessionTimeoutWarning } from './SessionTimeoutWarning';

function Where() {
  const loc = useLocation();
  return <div data-testid="where">{`${loc.pathname}${loc.search}`}</div>;
}

function setup(secondsLeft: number) {
  const refresh = vi.fn().mockResolvedValue(undefined);
  const logout = vi.fn().mockResolvedValue(undefined);
  const value: AuthContextValue = {
    user: { id: '1', username: 'a', fullName: 'A', role: 'ADMIN', mustChangePassword: false },
    expiresAt: new Date(Date.now() + secondsLeft * 1000),
    loading: false,
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
afterEach(() => vi.useRealTimers());

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

  it('Log out logs out and goes to the login page', async () => {
    const { logout } = setup(59);
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Log out' }));
    });
    expect(logout).toHaveBeenCalled();
    expect(screen.getByTestId('where')).toHaveTextContent('/login');
  });

  it('logs out and navigates to /login?reason=timeout at zero', async () => {
    const { logout } = setup(59);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(60_000);
    });
    expect(logout).toHaveBeenCalledTimes(1);
    expect(screen.getByTestId('where')).toHaveTextContent('/login?reason=timeout');
  });
});
