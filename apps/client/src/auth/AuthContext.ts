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
  logout: () => Promise<void>;
  refresh: () => Promise<void>;
  can: (perm: Permission) => boolean;
}

export const AuthContext = createContext<AuthContextValue | null>(null);
