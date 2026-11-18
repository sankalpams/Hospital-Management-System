import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { Spinner } from '../components/ui/Spinner';
import { useAuth } from './useAuth';

export function RequireAuth({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <Spinner label="Loading" />
      </div>
    );
  }
  // TODO(Task 9): when user.mustChangePassword, route to the change-password screen.
  if (!user) return <Navigate to="/login" replace state={{ from: `${location.pathname}${location.search}${location.hash}` }} />;
  return <>{children}</>;
}
