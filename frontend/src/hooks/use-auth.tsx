import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api, sessionStore } from '../api/client';
import type { Session, User } from '../types/api';

type AuthContextValue = { session: Session | null; user: User | null; login: (identity: string, password: string) => Promise<Session>; logout: () => Promise<void>; refreshProfile: () => Promise<void>; has: (permission: string) => boolean };
const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(() => sessionStore.get());
  useEffect(() => sessionStore.subscribe(setSession), []);
  const value = useMemo<AuthContextValue>(() => ({
    session, user: session?.user ?? null,
    login: async (identity, password) => { const { data } = await api.post<Session>('/auth/login', { identity, password }); sessionStore.set(data); return data; },
    logout: async () => { const current = sessionStore.get(); try { if (current) await api.post('/auth/logout', { refreshToken: current.refreshToken }); } finally { sessionStore.clear(); } },
    refreshProfile: async () => { const current = sessionStore.get(); if (!current) return; const { data } = await api.get<User>('/auth/me'); sessionStore.set({ ...current, user: data, forcePasswordChange: data.forcePasswordChange }); },
    has: (permission) => session?.user.role === 'ADMIN' || rolePermissions[session?.user.role ?? 'RECEPTIONIST']?.includes(permission) === true,
  }), [session]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

const rolePermissions: Record<string, string[]> = {
  ADMIN: ['USER_READ', 'USER_MANAGE', 'ROLE_READ', 'AUDIT_READ', 'PATIENT_READ', 'PATIENT_WRITE', 'APPOINTMENT_READ', 'APPOINTMENT_WRITE', 'EMR_READ', 'EMR_WRITE', 'VITALS_WRITE', 'LAB_READ', 'LAB_WRITE', 'PHARMACY_READ', 'PHARMACY_WRITE', 'INVOICE_READ', 'INVOICE_WRITE', 'INVOICE_VOID', 'REPORT_READ', 'REPORT_EXPORT', 'SETTINGS_MANAGE'],
  DOCTOR: ['PATIENT_READ', 'PATIENT_WRITE', 'APPOINTMENT_READ', 'APPOINTMENT_WRITE', 'EMR_READ', 'EMR_WRITE', 'VITALS_WRITE', 'LAB_READ', 'PHARMACY_READ', 'INVOICE_READ', 'REPORT_READ', 'REPORT_EXPORT'],
  NURSE: ['PATIENT_READ', 'APPOINTMENT_READ', 'EMR_READ', 'VITALS_WRITE', 'LAB_READ', 'LAB_WRITE', 'PHARMACY_READ'],
  RECEPTIONIST: ['PATIENT_READ', 'PATIENT_WRITE', 'APPOINTMENT_READ', 'APPOINTMENT_WRITE', 'INVOICE_READ'],
  LAB_STAFF: ['PATIENT_READ', 'LAB_READ', 'LAB_WRITE'], PHARMACIST: ['PATIENT_READ', 'PHARMACY_READ', 'PHARMACY_WRITE', 'INVOICE_READ'], ACCOUNTANT: ['PATIENT_READ', 'INVOICE_READ', 'INVOICE_WRITE', 'INVOICE_VOID', 'REPORT_READ', 'REPORT_EXPORT'],
};
export const useAuth = () => { const value = useContext(AuthContext); if (!value) throw new Error('useAuth must be used within AuthProvider'); return value; };
