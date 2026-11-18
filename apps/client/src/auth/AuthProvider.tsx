import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { can as roleCan, type Permission } from '@hms/shared';
import { api, ApiError, setApiHandlers } from '../lib/api';
import { AuthContext, type AuthContextValue, type AuthUser } from './AuthContext';

interface MeResponse {
  user: AuthUser;
  expiresAt: string | null;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [expiresAt, setExpiresAt] = useState<Date | null>(null);
  const [loading, setLoading] = useState(true);
  const queryClient = useQueryClient();

  useEffect(() => {
    setApiHandlers({
      onUnauthenticated: () => {
        setUser(null);
        setExpiresAt(null);
        queryClient.clear();
      },
      onSessionExpiry: setExpiresAt,
    });
    return () => setApiHandlers({});
  }, [queryClient]);

  const refresh = useCallback(async () => {
    try {
      const me = await api<MeResponse>('/auth/me');
      setUser(me.user);
      if (me.expiresAt) setExpiresAt(new Date(me.expiresAt));
    } catch (err) {
      // 401 is the normal signed-out state; the api layer has already cleared the user.
      if (!(err instanceof ApiError) || err.status !== 401) setUser(null);
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
    setUser(res.user);
    return res.user;
  }, []);

  const logout = useCallback(async () => {
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
    () => ({ user, expiresAt, loading, login, logout, refresh, can }),
    [user, expiresAt, loading, login, logout, refresh, can],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
