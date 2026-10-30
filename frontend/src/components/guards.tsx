import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../hooks/use-auth';
import { EmptyState } from './EmptyState';
export function RequireAuth() { const { session } = useAuth(); const location = useLocation(); return session ? <Outlet /> : <Navigate to="/login" replace state={{ from: location.pathname }} />; }
export function RequirePermission({ permission, children }: { permission: string; children: React.ReactNode }) { const { has } = useAuth(); return has(permission) ? <>{children}</> : <Navigate to="/forbidden" replace />; }
export function RequirePasswordChange({ children }: { children: React.ReactNode }) { const { session } = useAuth(); return session?.forcePasswordChange ? <Navigate to="/change-password" replace /> : <>{children}</>; }
