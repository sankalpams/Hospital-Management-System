import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { authApi } from '../api/auth';
import { AUTH_EVENT } from '../api/client';

const AuthContext = createContext(null);

/**
 * Holds the signed-in user (from GET /auth/me). Permissions here drive what the UI *shows*;
 * the backend independently enforces every permission on every request.
 */
export function AuthProvider({ children }) {
  const navigate = useNavigate();
  const [user, setUser] = useState(null);
  const [status, setStatus] = useState('loading'); // loading | authenticated | anonymous
  const [notice, setNotice] = useState(null);       // e.g. session-expired message for the login page

  const loadSession = useCallback(async () => {
    try {
      await authApi.csrf();                 // ensures the XSRF-TOKEN cookie exists before any POST
      const me = await authApi.me();
      setUser(me);
      setStatus('authenticated');
    } catch {
      setUser(null);
      setStatus('anonymous');
    }
  }, []);

  useEffect(() => {
    loadSession();
  }, [loadSession]);

  useEffect(() => {
    const onAuthEvent = (event) => {
      const { type, code } = event.detail ?? {};
      if (type === 'unauthenticated') {
        setUser(null);
        setStatus('anonymous');
        setNotice(code === 'SESSION_EXPIRED'
          ? 'Your session expired after a period of inactivity. Please sign in again.'
          : 'Please sign in to continue.');
        navigate('/login', { replace: true });
      } else if (type === 'password-change-required') {
        setUser((u) => (u ? { ...u, mustChangePassword: true } : u));
        navigate('/change-password', { replace: true });
      }
    };
    window.addEventListener(AUTH_EVENT, onAuthEvent);
    return () => window.removeEventListener(AUTH_EVENT, onAuthEvent);
  }, [navigate]);

  const login = useCallback(async (username, password) => {
    const me = await authApi.login(username, password);
    setUser(me);
    setStatus('authenticated');
    setNotice(null);
    return me;
  }, []);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } finally {
      setUser(null);
      setStatus('anonymous');
      setNotice('You have signed out.');
      await authApi.csrf().catch(() => {});   // fresh token for the next sign-in
      navigate('/login', { replace: true });
    }
  }, [navigate]);

  const changePassword = useCallback(async (currentPassword, newPassword) => {
    const me = await authApi.changePassword(currentPassword, newPassword);
    setUser(me);
    return me;
  }, []);

  const hasPermission = useCallback(
    (permission) => {
      if (!permission) return true;
      const list = Array.isArray(permission) ? permission : [permission];
      return list.some((p) => user?.permissions?.includes(p));
    },
    [user],
  );

  const value = useMemo(
    () => ({ user, status, notice, setNotice, login, logout, changePassword, hasPermission }),
    [user, status, notice, login, logout, changePassword, hasPermission],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}
