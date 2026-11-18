import type { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import type { Permission } from '@hms/shared';
import { useAuth } from './useAuth';

export function RequirePermission({ perm, children }: { perm: Permission; children: ReactNode }) {
  const { can } = useAuth();
  if (!can(perm)) return <Navigate to="/403" replace />;
  return <>{children}</>;
}
