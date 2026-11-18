# 04 — REST API

**Base URL**: `/api/v1`. In production this is served through Apache at `http://localhost/api/v1`. Interactive documentation is at `/api/v1/docs` (springdoc OpenAPI); it is only enabled in the `dev` profile.

**Status**: ✅ implemented · 🔜 planned for the phase shown. This table is updated as each module ships.

## 1. Conventions

| Topic | Rule |
|---|---|
| Format | JSON, camelCase. Dates are ISO-8601: `2026-11-18`, and timestamps in UTC as `2026-11-18T04:30:00Z`. |
| Auth | Session cookie `HMS_SESSION`. Every state-changing request must send `X-XSRF-TOKEN` matching the `XSRF-TOKEN` cookie. |
| Paging | `?page=0&size=20&sort=lastName,asc`. `size` is capped at 100. Response: `{ "content": [...], "page": 0, "size": 20, "totalElements": 134, "totalPages": 7 }` |
| Filtering | Explicit query parameters per endpoint (`status`, `from`, `to`, `departmentId`, `q`) |
| Status changes | Explicit action endpoints (`POST /appointments/{id}/check-in`), not generic PATCHes. Each action is one transaction with one audit entry. |
| Concurrency | Mutable resources return `version`. Updates send it back; a stale version gets **409** `CONCURRENT_MODIFICATION`. |
| Errors | `{ "status", "code", "message", "fieldErrors": [{ "field", "message" }], "requestId", "timestamp" }` |
| Status codes | 200 OK · 201 Created (+ Location) · 204 No Content · 400 validation · 401 not authenticated / session expired · 403 missing permission or must change password · 404 not found · 409 conflict or business rule · 422 is not used (400 covers validation) · 500 with a generic message only |

## 2. Endpoints

### Auth — ✅ Phase 2

| Method | Path | Permission | Notes |
|---|---|---|---|
| GET | `/auth/csrf` | public | Issues the `XSRF-TOKEN` cookie |
| POST | `/auth/login` | public | `{ username, password }` → the current user. 401 `BAD_CREDENTIALS`, 423 `ACCOUNT_LOCKED`, 403 `ACCOUNT_INACTIVE` |
| POST | `/auth/logout` | authenticated | Invalidates the session |
| GET | `/auth/me` | authenticated | `{ id, username, fullName, roles[], permissions[], mustChangePassword }` |
| POST | `/auth/change-password` | authenticated | `{ currentPassword, newPassword }`. Clears `mustChangePassword` and rotates the session. |

### Users and access — ✅ Phase 3

| Method | Path | Permission | Notes |
|---|---|---|---|
| GET | `/users?q=&status=&role=` | user:read | Paged; q matches username, name or email |
| POST | `/users` | user:create | Returns `{ user, temporaryPassword }`. The password is shown once and must be changed at first sign-in. |
| GET / PUT | `/users/{id}` | user:read / user:update | PUT carries `version`. A role change signs the user out everywhere. |
| POST | `/users/{id}/activate` · `/deactivate` | user:deactivate | Deactivation ends sessions. You cannot deactivate yourself or the last active Super Admin. |
| POST | `/users/{id}/reset-password` | user:reset-password | New temporary password; sessions ended |
| GET | `/roles` | user:read | Roles with their permission codes |
| GET | `/audit-logs?q=&entityType=&action=&from=&to=` | audit:read | Newest first |
| GET | `/login-activity?q=&success=&from=&to=` | audit:read | Every sign-in attempt |

Only a Super Admin can assign the `SUPER_ADMIN` role or modify a Super Admin account (`SUPER_ADMIN_REQUIRED`).

🔜 Later: `PUT /roles/{id}/permissions` (role:manage) and `GET/PUT /settings` (settings:manage), Phase 7 administration.

### Departments — ✅ Phase 3

| Method | Path | Permission |
|---|---|---|
| GET | `/departments?q=&type=&active=` | department:read |
| GET | `/departments/options` (active, for dropdowns) | department:read |
| GET / POST / PUT | `/departments[/{id}]` | department:read / department:manage |
| POST | `/departments/{id}/activate` · `/deactivate` | department:manage |

Codes are unique and cannot be changed. A department cannot be deactivated while active staff are assigned to it (`DEPARTMENT_IN_USE`).

### Patients — ✅ Phase 3

| Method | Path | Permission | Notes |
|---|---|---|---|
| GET | `/patients?q=&status=` | patient:read | q matches MRN, NIC or phone exactly, or name partially |
| POST | `/patients/duplicate-check` | patient:create or patient:update | Returns candidates: same NIC; same name + date of birth; same name + phone |
| POST | `/patients` | patient:create | `NIC_EXISTS` / `PASSPORT_EXISTS` (hard stop). `POSSIBLE_DUPLICATE` unless `confirmNotDuplicate: true`. |
| GET / PUT | `/patients/{id}` | patient:read / patient:update | PUT carries `version`, and replaces the emergency contacts list (max 3, one primary) |
| POST | `/patients/{id}/status` | patient:update | `{ status, deceasedAt?, reason }`. A deceased patient's status is final. |
| GET | `/patients/{id}/documents` | patient:document:read | |
| POST | `/patients/{id}/documents` (multipart `file`, `category`, `title`) | patient:document:upload | PDF, JPEG or PNG by content (magic bytes), ≤ 10 MB |
| GET | `/documents/{docId}/download` | patient:document:read | Served as an attachment with `nosniff`. Each download is written to the audit log (`VIEW_SENSITIVE`). |

A patient's appointment history is `GET /appointments?patientId=`. The clinical timeline comes with EMR in Phase 4.

### Doctors and schedules — ✅ Phase 3

| Method | Path | Permission |
|---|---|---|
| GET | `/doctors?q=&departmentId=&status=` · `/doctors/{id}` | doctor:read |
| GET | `/doctors/options?departmentId=` (bookable doctors only) | doctor:read |
| POST / PUT | `/doctors[/{id}]` (employee + doctor profile in one request; optional `userId` link) | doctor:manage |
| GET | `/doctors/{id}/schedules` · `/doctors/{id}/exceptions` | doctor:read |
| POST / PUT | `/doctors/{id}/schedules[/{scheduleId}]` | schedule:manage |
| POST / DELETE | `/doctors/{id}/exceptions[/{exceptionId}]` | schedule:manage |

Rules:
- Overlapping active sessions on the same weekday are rejected (`SCHEDULE_OVERLAP`).
- A linked login account must hold the Doctor role and be linked to only one doctor.

### Appointments — ✅ Phase 3

Times in requests are hospital-local (`date`, `time`). Responses carry UTC instants (`slotStart`) together with `localDate` / `localTime`, plus `allowedActions` (what the state machine and time rules permit now).

| Method | Path | Permission |
|---|---|---|
| GET | `/doctors/{id}/availability?date=` | appointment:read |
| GET | `/appointments?from=&to=&doctorId=&departmentId=&patientId=&status=A,B&q=&mine=` | appointment:read |
| GET | `/appointments/calendar?from=&to=&doctorId=&departmentId=` (per-day counts, ≤ 62 days) | appointment:read |
| GET | `/appointments/{id}` | appointment:read |
| POST | `/appointments` `{ patientId, doctorId, date, time, type, reason, confirmNow }` | appointment:create |
| POST | `/appointments/walk-in` `{ patientId, doctorId, reason }` | appointment:create + appointment:checkin |
| POST | `/appointments/{id}/confirm` · `/reschedule` · `/cancel {reason}` · `/no-show` | appointment:update |
| POST | `/appointments/{id}/check-in` | appointment:checkin |
| POST | `/appointments/{id}/start` · `/complete` | appointment:consult (own appointments only) |

Error codes:
- **Booking:** `SLOT_TAKEN`, `SLOT_NOT_IN_SCHEDULE`, `SLOT_IN_PAST`, `DATE_IN_PAST`, `DOCTOR_UNAVAILABLE`, `DOCTOR_NOT_AVAILABLE`, `PATIENT_DOUBLE_BOOKED`, `PATIENT_NOT_ACTIVE`, `NO_SLOT_AVAILABLE` (walk-in).
- **Status changes:** `INVALID_STATUS_TRANSITION`, `CHECK_IN_NOT_TODAY`, `TOO_EARLY_FOR_NO_SHOW`, `NOT_YOUR_APPOINTMENT`.

**Start** opens the encounter; **complete** needs a diagnosis and raises the consultation charge (Phase 4).

### Notifications — ✅ Phase 4
`GET /notifications?unread=` · `GET /notifications/unread-count` · `POST /notifications/{id}/read` · `POST /notifications/read-all` (self only). Raised by: STAT/urgent lab orders (lab staff), results entered (requesting doctor), low stock after dispensing (stock managers), leave requests (leave approvers) and leave decisions (the employee).

### EMR — ✅ Phase 4
Starting an appointment opens an outpatient encounter; admitting opens an inpatient one. Completing an appointment requires a diagnosis and raises the consultation charge; discharging closes the inpatient encounter.

| Method | Path | Permission |
|---|---|---|
| GET | `/patients/{id}/emr?reason=` (break-the-glass reason, ≥ 10 chars, when not treating) | emr:read + care relationship |
| GET | `/encounters/{id}?reason=` | emr:read + care relationship |
| POST | `/encounters/{id}/diagnoses` · `/notes`; PUT `/notes/{id}`; POST `/notes/{id}/sign` · `/addenda` | emr:write (treating doctor) |
| POST | `/patients/{id}/vitals` | vitals:record |
| POST | `/encounters/{id}/nursing-notes` | nursing-note:write |
| POST/PUT | `/patients/{id}/allergies` | allergy:write |
| POST | `/encounters/{id}/prescriptions` · `/prescriptions/{id}/cancel` | prescription:create |

Error codes: `DIAGNOSIS_REQUIRED`, `NOTE_SIGNED`, `NOTE_NOT_SIGNED`, `ALLERGY_CONFLICT` (send `allergyOverrideReason` ≥ 10 chars to override), `INVALID_BP`, `EMPTY_VITALS`, `BREAK_GLASS_REQUIRED`, `NOT_TREATING_DOCTOR`.

### Inpatient — ✅ Phase 4

| Method | Path | Permission |
|---|---|---|
| GET | `/wards` (bed board: rooms, beds, occupant, occupancy counts) | admission:read or ward:manage |
| POST | `/wards` · `/wards/{id}/rooms {roomNo, roomType, dailyRate, beds}` | ward:manage |
| POST | `/beds/{id}/status {AVAILABLE\|CLEANING\|MAINTENANCE}` | ward:manage or admission:manage |
| GET | `/admissions?status=A,B&patientId=&q=` · `/admissions/{id}` | admission:read |
| POST | `/admissions {patientId, attendingDoctorId, admissionType, reason}` | admission:request |
| POST | `/admissions/{id}/admit {bedId}` · `/transfer {bedId}` · `/discharge {dischargeType, summary}` · `/cancel {reason}` | admission:manage |

Bed rows are locked on allocation and unique keys on the live bed assignment forbid double occupancy. A freed bed goes to CLEANING. Discharge raises one ADMISSION charge per stay: started days (minimum 1) × that room's daily rate. Error codes: `ALREADY_ADMITTED`, `BED_NOT_AVAILABLE`, `BED_OCCUPIED`, `SAME_BED`, `INVALID_STATUS_TRANSITION`.

### Laboratory — ✅ Phase 4

| Method | Path | Permission |
|---|---|---|
| GET | `/lab-tests` | lab:request:read |
| POST | `/encounters/{id}/lab-requests {testIds, priority, clinicalNotes}` | lab:request:create (treating doctor) |
| GET | `/lab-requests?status=&priority=&patientId=&q=` · `/lab-requests/{id}` | lab:request:read |
| POST | `/lab-items/{id}/collect-sample` · `/start-processing` | lab:sample:collect |
| POST | `/lab-items/{id}/result {numericValue \| textValue, comments}` | lab:result:enter |
| POST | `/lab-items/{id}/review` | lab:result:review (requester or same department) |
| POST | `/lab-items/{id}/cancel {reason}` | lab:request:create (only before collection) |

Each ordered test raises a LABORATORY charge; cancelling removes it. Numeric results are flagged H/L/N against the reference range. The printable report is the `/lab/requests/{id}/print` page.

### Pharmacy — ✅ Phase 4

| Method | Path | Permission |
|---|---|---|
| GET | `/pharmacy/queue?status=&patientId=` | prescription:read |
| POST | `/prescriptions/{id}/dispense {items:[{prescriptionItemId, quantity}]}` | pharmacy:dispense |
| GET | `/medicines?q=&categoryId=&lowStock=` · `/medicines/{id}/batches` · `/pharmacy/alerts` | medicine:read |
| POST/PUT | `/medicines` · `/medicine-categories` | medicine:manage |
| POST | `/stock/receipts` · `/stock/adjustments` · `/stock/write-off-expired` | stock:manage |
| GET | `/stock/transactions?medicineId=&type=&from=&to=` | medicine:read |
| GET/POST/PUT | `/suppliers` | supplier:manage |

Dispensing locks batches first-expiry-first, never uses expired stock and writes every movement to the stock ledger (the only way stock changes). Each dispensed line raises a PHARMACY charge.

### Billing — ✅ Phase 4

| Method | Path | Permission |
|---|---|---|
| GET | `/patients/{id}/unbilled-charges` · `/invoices?status=&patientId=&q=&from=&to=` · `/invoices/{id}` | billing:read |
| GET | `/services` · POST `/charges {patientId, serviceId, quantity}` | charge:create |
| POST | `/invoices {patientId, chargeIds, discountAmount, discountReason}` · `/invoices/{id}/issue` | invoice:create |
| POST | `/invoices/{id}/void {reason}` (only with no net payments; charges return to unbilled) | invoice:void |
| POST | `/invoices/{id}/payments {amount, method, referenceNo, notes}` | payment:record |
| POST | `/payments/{id}/refund {amount, reason}` | payment:refund |

Money is rounded HALF_UP to 2 places; tax applies after discount. Non-cash payments need a reference. The invoice row is locked for payment. Receipts print from `/billing/invoices/{id}/receipts/{paymentId}`. Error codes: `CHARGE_ALREADY_BILLED`, `INVOICE_NOT_PAYABLE`, `REFERENCE_REQUIRED`, `OVERPAYMENT`, `INVOICE_HAS_PAYMENTS`.

### Staff — ✅ Phase 4

| Method | Path | Permission |
|---|---|---|
| GET | `/employees?q=&category=&departmentId=&status=` · `/employees/{id}` | employee:read |
| POST/PUT | `/employees` (every category except DOCTOR, which the Doctors page manages) | employee:manage |
| GET | `/employees/{id}/attendance?from=&to=` · `/employees/{id}/leave-balances?year=` | employee:read |
| GET/PUT | `/attendance?date=` (register; PUT upserts `{entries:[{employeeId, status, clockIn, clockOut, remarks}]}`) | attendance:manage |
| GET | `/leave-types` · `/me/leave` | signed in |
| POST | `/leave-requests {employeeId?, leaveTypeId, startDate, endDate, halfDay, reason}` | own record, or employee:manage |
| GET | `/leave-requests?status=&employeeId=` | leave:approve or employee:manage |
| POST | `/leave-requests/{id}/approve {note}` · `/reject {note}` | leave:approve (never your own) |
| POST | `/leave-requests/{id}/cancel` | applicant or employee:manage, before the leave starts |

Leave counts calendar days. Error codes: `USE_DOCTORS_PAGE`, `NIC_EXISTS`, `FUTURE_DATE`, `INVALID_TIME`, `TIMES_NOT_ALLOWED`, `LEAVE_OVERLAP`, `LEAVE_BALANCE`, `SPANS_YEARS`, `SELF_APPROVAL`, `NO_EMPLOYEE_RECORD`.

### Analytics — ✅ Phase 5
Every figure is a SQL aggregate over the live tables (no stored or hard-coded numbers). Reports take `?from=&to=&departmentId=&granularity=day|week|month`:
- dates are hospital-local and inclusive; the default range is the last 30 days;
- granularity defaults from the span (up to 31 days by day, up to 6 months by week, then by month); weeks start on Monday;
- every period in the range is returned, with zero where there was no activity;
- limits: at most two years, and day granularity for at most 92 days (`INVALID_RANGE`, `INVALID_FILTER`).

Every report returns the same shape: `{ report, from, to, granularity, departmentId, kpis[], trends[], breakdowns[], tables[], notes[] }`.
- `kpis`: `{ key, label, value, unit, link }`, where unit is count, percent, LKR, minutes, hours or days.
- `trends`: `{ key, label, unit, group, points:[{ bucket, value }] }`; series sharing a `group` belong on one chart.
- `breakdowns`: `{ key, label, unit, rows:[{ label, value }] }`.
- `tables`: `{ key, label, columns[], rows[][] }`.
- `notes`: says what the department filter does not cover and how ratios are defined.

The dashboard (`/analytics/dashboard`, any signed-in user) uses the same shape. It returns today's cards for each permission the caller holds (for example appointment:read, admission:read, medicine:read, billing:read, leave:approve), a doctor's own appointments, and 14-day trends.

CSV export gives the same report in long format: UTF-8 with a BOM, and cells starting with `= + - @` prefixed with an apostrophe so spreadsheets do not run them as formulas.

| Path | Permission | Content |
|---|---|---|
| `/analytics/dashboard` | role-aware | KPI cards for the caller's role |
| `/analytics/patients` | report:operational | Registrations trend, demographics |
| `/analytics/appointments` | report:operational | Volume trend, status mix, cancellation by weekday, hour and department, no-show rate, average wait (check-in → start) |
| `/analytics/doctors/workload` | report:operational | Consultations per doctor, average consult time |
| `/analytics/departments` | report:operational | Patients, appointments and revenue per department |
| `/analytics/inpatient` | report:operational | Occupancy %, average length of stay, admissions and discharges trend |
| `/analytics/laboratory` | report:operational | Requests per test, turnaround vs target, pending backlog |
| `/analytics/pharmacy` | report:operational | Top medicines by quantity, consumption trend, low stock, expiring |
| `/analytics/revenue` | report:financial | Revenue by category and month, collections by method, outstanding ageing |
| `/analytics/staff` | report:staff | Headcount, attendance rate, leave usage |
| `/analytics/{report}/export.csv` | same as the report | CSV export |
