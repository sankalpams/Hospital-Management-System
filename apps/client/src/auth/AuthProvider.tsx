import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { can as roleCan, type Permission } from '@hms/shared';
import { api, ApiError, serverNow, setApiHandlers } from '../lib/api';
import { AuthContext, type AuthContextValue, type AuthUser } from './AuthContext';

interface MeResponse {
  user: AuthUser;
  expiresAt: string | null;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [expiresAt, setExpiresAt] = useState<Date | null>(null);
  const [loading, setLoading] = useState(true);
  const [expired, setExpired] = useState(false);
  const queryClient = useQueryClient();
  const expiresAtRef = useRef<Date | null>(null);
  useEffect(() => {
    expiresAtRef.current = expiresAt;
  }, [expiresAt]);

  useEffect(() => {
    setApiHandlers({
      onUnauthenticated: () => {
        // A 401 after the known expiry is a timeout, not a plain sign-out.
        const known = expiresAtRef.current;
        if (known && known.getTime() <= serverNow()) setExpired(true);
        setUser(null);
        setExpiresAt(null);
        queryClient.clear();
      },
      onSessionExpiry: setExpiresAt,
      // RequireAuth then routes the user to /change-password.
      onPasswordChangeRequired: () =>
        setUser((u) => (u && !u.mustChangePassword ? { ...u, mustChangePassword: true } : u)),
    });
    return () => setApiHandlers({});
  }, [queryClient]);

  const refresh = useCallback(async (): Promise<Date | null> => {
    try {
      const me = await api<MeResponse>('/auth/me');
      setUser(me.user);
      setExpired(false);
      if (!me.expiresAt) return null;
      const next = new Date(me.expiresAt);
      setExpiresAt(next);
      return next;
    } catch (err) {
      // 401 is the normal signed-out state; the api layer has already cleared the user.
      if (!(err instanceof ApiError) || err.status !== 401) setUser(null);
      return null;
    }
  }, []);

  useEffect(() => {
    let active = true;
    void refresh().finally(() => {
      if (active) setLoading(false);
    });
    return () => {
      active = false;
    };
  }, [refresh]);

  const login = useCallback(async (username: string, password: string) => {
    const res = await api<{ user: AuthUser }>('/auth/login', {
      method: 'POST',
      json: { username, password },
    });
    setExpired(false);
    setUser(res.user);
    return res.user;
  }, []);

  const logout = useCallback(async (opts?: { reason?: 'timeout' }) => {
    if (opts?.reason === 'timeout') setExpired(true);
    try {
      await api('/auth/logout', { method: 'POST' });
    } finally {
      setUser(null);
      setExpiresAt(null);
      queryClient.clear();
    }
  }, [queryClient]);

  const can = useCallback((perm: Permission) => (user ? roleCan(user.role, perm) : false), [user]);

  const value = useMemo<AuthContextValue>(
    () => ({ user, expiresAt, loading, expired, login, logout, refresh, can }),
    [user, expiresAt, loading, expired, login, logout, refresh, can],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
