import { createContext } from 'react';
import type { Permission, Role } from '@hms/shared';

export interface AuthUser {
  id: string;
  username: string;
  fullName: string;
  role: Role;
  mustChangePassword: boolean;
}

export interface AuthContextValue {
  user: AuthUser | null;
  expiresAt: Date | null;
  /** True until the initial GET /auth/me has settled. */
  loading: boolean;
  login: (username: string, password: string) => Promise<AuthUser>;
  /** True after a timeout logout, so the login redirect can explain why. */
  expired: boolean;
  logout: (opts?: { reason?: 'timeout' }) => Promise<void>;
  /** GET /auth/me; resolves to the new session expiry, or null when there is no session. */
  refresh: () => Promise<Date | null>;
  can: (perm: Permission) => boolean;
}

export const AuthContext = createContext<AuthContextValue | null>(null);
