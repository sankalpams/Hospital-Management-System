import { Route, Routes } from 'react-router-dom';
import { AppShell } from './components/AppShell';
import { RequireAuth, RequirePasswordChange, RequirePermission } from './components/guards';
import { LoginPage, ForgotPasswordPage, ResetPasswordPage, ChangePasswordPage } from './pages/AuthPages';
import { HomePage } from './pages/HomePage';
import { ProfilePage } from './pages/ProfilePage';
import { UsersPage } from './pages/UsersPage';
import { AuditLogPage } from './pages/AuditLogPage';
import { RolesPage } from './pages/RolesPage';
import { PatientsPage } from './pages/PatientsPage';
import { PatientProfilePage } from './pages/PatientProfilePage';
import { DepartmentsPage } from './pages/DepartmentsPage';
import { DoctorsPage } from './pages/DoctorsPage';
import { EmployeesPage } from './pages/EmployeesPage';
import { ForbiddenPage, NotFoundPage } from './pages/NotFoundPage';

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />

      <Route element={<RequireAuth />}>
        <Route path="/change-password" element={<ChangePasswordPage />} />

        <Route element={<RequirePasswordChange><AppShell /></RequirePasswordChange>}>
          <Route path="/" element={<HomePage />} />
          <Route path="/profile" element={<ProfilePage />} />
          <Route path="/forbidden" element={<ForbiddenPage />} />

          {/* Phase 2: Patients & Clinical Demographics */}
          <Route
            path="/patients"
            element={
              <RequirePermission permission="PATIENT_READ">
                <PatientsPage />
              </RequirePermission>
            }
          />
          <Route
            path="/patients/:id"
            element={
              <RequirePermission permission="PATIENT_READ">
                <PatientProfilePage />
              </RequirePermission>
            }
          />

          {/* Phase 2: Departments, Doctors & Staff */}
          <Route
            path="/departments"
            element={
              <RequirePermission permission="USER_READ">
                <DepartmentsPage />
              </RequirePermission>
            }
          />
          <Route
            path="/doctors"
            element={
              <RequirePermission permission="USER_READ">
                <DoctorsPage />
              </RequirePermission>
            }
          />
          <Route
            path="/employees"
            element={
              <RequirePermission permission="USER_READ">
                <EmployeesPage />
              </RequirePermission>
            }
          />

          {/* Phase 1: Administration */}
          <Route
            path="/users"
            element={
              <RequirePermission permission="USER_READ">
                <UsersPage />
              </RequirePermission>
            }
          />
          <Route
            path="/audit-logs"
            element={
              <RequirePermission permission="AUDIT_READ">
                <AuditLogPage />
              </RequirePermission>
            }
          />
          <Route
            path="/roles"
            element={
              <RequirePermission permission="ROLE_READ">
                <RolesPage />
              </RequirePermission>
            }
          />
        </Route>
      </Route>

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
