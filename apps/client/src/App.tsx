import { Route, Routes } from 'react-router-dom';
import { PERMISSIONS } from '@hms/shared';
import { RequireAuth } from './auth/RequireAuth';
import { RequirePermission } from './auth/RequirePermission';
import { AppShell } from './layout/AppShell';
import { ForbiddenPage } from './pages/ForbiddenPage';
import { HomePage } from './pages/HomePage';
import { LoginPage } from './pages/LoginPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { ChangePasswordPage } from './pages/ChangePasswordPage';
import { AuditLogPage } from './pages/admin/AuditLogPage';
import { SettingsPage } from './pages/admin/SettingsPage';
import { UsersPage } from './pages/admin/UsersPage';

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      {/* Full-page (no nav) so a forced password change cannot wander off. */}
      <Route
        path="/change-password"
        element={
          <RequireAuth>
            <ChangePasswordPage />
          </RequireAuth>
        }
      />
      <Route
        element={
          <RequireAuth>
            <AppShell />
          </RequireAuth>
        }
      >
        <Route path="/" element={<HomePage />} />
        <Route path="/403" element={<ForbiddenPage />} />
        <Route
          path="/admin/users"
          element={
            <RequirePermission perm={PERMISSIONS.USER_MANAGE}>
              <UsersPage />
            </RequirePermission>
          }
        />
        <Route
          path="/admin/settings"
          element={
            <RequirePermission perm={PERMISSIONS.SETTINGS_MANAGE}>
              <SettingsPage />
            </RequirePermission>
          }
        />
        <Route
          path="/admin/audit"
          element={
            <RequirePermission perm={PERMISSIONS.AUDIT_VIEW}>
              <AuditLogPage />
            </RequirePermission>
          }
        />
        {/* Later slices add routes here, wrapped in <RequirePermission>. */}
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
