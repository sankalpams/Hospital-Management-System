import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from './AuthProvider';
import { FullPageSpinner } from '../components/ui/Spinner';
import ForbiddenPage from '../features/errors/ForbiddenPage';

/** Only signed-in users; users with a temporary password are held on the change-password page. */
export function RequireAuth() {
  const { status, user } = useAuth();
  const location = useLocation();
  if (status === 'loading') return <FullPageSpinner label="Checking your session" />;
  if (status !== 'authenticated') {
    // A visitor arriving at the home address sees the landing page; deep links go straight to sign-in.
    if (location.pathname === '/') return <Navigate to="/welcome" replace />;
    return <Navigate to="/login" replace state={{ from: location }} />;
  }
  if (user?.mustChangePassword && location.pathname !== '/change-password') {
    return <Navigate to="/change-password" replace />;
  }
  return <Outlet />;
}

/** Route-level permission check; mirrors the backend @PreAuthorize for UX only. */
export function RequirePermission({ permission, children }) {
  const { hasPermission } = useAuth();
  return hasPermission(permission) ? children : <ForbiddenPage />;
}

/** Sign-in page is only for anonymous visitors. */
export function AnonymousOnly() {
  const { status } = useAuth();
  if (status === 'loading') return <FullPageSpinner label="Checking your session" />;
  if (status === 'authenticated') return <Navigate to="/" replace />;
  return <Outlet />;
}
