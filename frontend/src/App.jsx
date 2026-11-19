import { Suspense, lazy } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { AnonymousOnly, RequireAuth, RequirePermission } from './auth/guards';
import { AppShell } from './components/layout/AppShell';
import { useAuth } from './auth/AuthProvider';
import { permissionFor } from './config/navigation';
import LoginPage from './features/auth/LoginPage';
import SignUpPage from './features/auth/SignUpPage';
import ChangePasswordPage from './features/auth/ChangePasswordPage';
import DashboardPage from './features/dashboard/DashboardPage';
import UsersPage from './features/users/UsersPage';
import AuditLogPage from './features/audit/AuditLogPage';
import DemoStaffPage from './features/users/DemoStaffPage';
import DepartmentsPage from './features/departments/DepartmentsPage';
import PatientsPage from './features/patients/PatientsPage';
import PatientFormPage from './features/patients/PatientFormPage';
import PatientDetailPage from './features/patients/PatientDetailPage';
import DoctorsPage from './features/doctors/DoctorsPage';
import DoctorFormPage from './features/doctors/DoctorFormPage';
import DoctorDetailPage from './features/doctors/DoctorDetailPage';
import AppointmentsPage from './features/appointments/AppointmentsPage';
import BookAppointmentPage from './features/appointments/BookAppointmentPage';
import ConsultationPage from './features/emr/ConsultationPage';
import { LabReportPrintPage, LabWorklistPage } from './features/lab/LabPages';
import PharmacyPage from './features/pharmacy/PharmacyPage';
import InpatientPage from './features/inpatient/InpatientPage';
import { BillingPage, InvoicePage, ReceiptPrintPage } from './features/billing/BillingPages';
import { MyLeavePage, StaffPage } from './features/staff/StaffPages';
import { FullPageSpinner } from './components/ui/Spinner';

// charts are heavy; load the reports screen on first visit
const ReportsPage = lazy(() => import('./features/analytics/ReportsPage'));

import NotFoundPage from './features/errors/NotFoundPage';

/** Page wrapped in a permission check (defaults to the sidebar item's permission for that path). */
function guarded(element, permission) {
  return <RequirePermission permission={permission}>{element}</RequirePermission>;
}

export default function App() {
  const { user } = useAuth();
  return (
    <Routes>
      <Route element={<AnonymousOnly />}>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/sign-up" element={<SignUpPage />} />
      </Route>
      <Route element={<RequireAuth />}>
        {user?.mustChangePassword ? (
          // Temporary password: standalone page, no navigation; every other path leads here.
          <>
            <Route path="/change-password" element={<ChangePasswordPage />} />
            <Route path="*" element={<Navigate to="/change-password" replace />} />
          </>
        ) : (
          <Route element={<AppShell />}>
            <Route index element={<DashboardPage />} />
            <Route path="/change-password" element={<ChangePasswordPage />} />

            <Route path="/patients" element={guarded(<PatientsPage />, permissionFor('/patients'))} />
            <Route path="/patients/new" element={guarded(<PatientFormPage />, 'patient:create')} />
            <Route path="/patients/:id" element={guarded(<PatientDetailPage />, 'patient:read')} />
            <Route path="/patients/:id/edit" element={guarded(<PatientFormPage />, 'patient:update')} />

            <Route path="/appointments" element={guarded(<AppointmentsPage />, permissionFor('/appointments'))} />
            <Route path="/appointments/new" element={guarded(<BookAppointmentPage />, 'appointment:create')} />
            <Route path="/appointments/:id/reschedule" element={guarded(<BookAppointmentPage />, 'appointment:update')} />
            <Route path="/appointments/:id/consult" element={guarded(<ConsultationPage />, 'appointment:consult')} />

            <Route path="/lab" element={guarded(<LabWorklistPage />, permissionFor('/lab'))} />
            <Route path="/lab/requests/:id/print" element={guarded(<LabReportPrintPage />, 'lab:request:read')} />

            <Route path="/inpatients" element={guarded(<InpatientPage />, permissionFor('/inpatients'))} />
            <Route path="/billing" element={guarded(<BillingPage />, permissionFor('/billing'))} />
            <Route path="/billing/invoices/:id" element={guarded(<InvoicePage />, 'billing:read')} />
            <Route path="/billing/invoices/:id/receipts/:paymentId" element={guarded(<ReceiptPrintPage />, 'billing:read')} />
            <Route path="/staff" element={guarded(<StaffPage />, permissionFor('/staff'))} />
            <Route path="/reports" element={guarded(<Suspense fallback={<FullPageSpinner label="Loading reports" />}><ReportsPage /></Suspense>, permissionFor('/reports'))} />
            <Route path="/my-leave" element={<MyLeavePage />} />
            <Route path="/pharmacy" element={guarded(<PharmacyPage />, permissionFor('/pharmacy'))} />

            <Route path="/doctors" element={guarded(<DoctorsPage />, permissionFor('/doctors'))} />
            <Route path="/doctors/new" element={guarded(<DoctorFormPage />, 'doctor:manage')} />
            <Route path="/doctors/:id" element={guarded(<DoctorDetailPage />, 'doctor:read')} />
            <Route path="/doctors/:id/edit" element={guarded(<DoctorFormPage />, 'doctor:manage')} />

            <Route path="/departments" element={guarded(<DepartmentsPage />, permissionFor('/departments'))} />
            <Route path="/users" element={guarded(<UsersPage />, permissionFor('/users'))} />
            <Route path="/audit" element={guarded(<AuditLogPage />, permissionFor('/audit'))} />
            <Route path="/staff-logins" element={guarded(<DemoStaffPage />, permissionFor('/staff-logins'))} />
            <Route path="*" element={<NotFoundPage />} />
          </Route>
        )}
      </Route>
    </Routes>
  );
}
