# 05 — Roles and Permissions

Access is **permission-based**. Roles are only bundles of permissions:

- **Backend**: every endpoint carries `@PreAuthorize("hasAuthority('<permission>')")`. Record-level rules are enforced in services (§3).
- **Frontend**: routes and menu items declare the permission they need. Hiding a button is for usability only; it is never the security control.
- **Source of truth**: `V9__roles_permissions.sql` seeds the matrix below. Super Admin can adjust role→permission grants at runtime; system roles cannot be deleted.

## 1. Permissions

| Module | Permission | Meaning |
|---|---|---|
| Users | `user:read` | List and view user accounts |
| | `user:create` | Create accounts |
| | `user:update` | Edit account details and role assignment |
| | `user:deactivate` | Activate or deactivate accounts (revokes their sessions) |
| | `user:reset-password` | Issue a temporary password |
| | `role:manage` | Edit role → permission grants |
| System | `settings:manage` | Hospital settings (tax rate, timezone, expiry window …) |
| | `audit:read` | View audit logs and login activity |
| Departments | `department:read` | View departments |
| | `department:manage` | Create and edit departments |
| Patients | `patient:read` | Search and view demographics |
| | `patient:create` | Register patients |
| | `patient:update` | Edit demographics, contacts, status |
| | `patient:document:read` | Download patient documents |
| | `patient:document:upload` | Upload documents |
| Doctors | `doctor:read` | View doctors and availability |
| | `doctor:manage` | Create and edit doctor profiles |
| | `schedule:manage` | Edit doctor schedules and exceptions |
| Appointments | `appointment:read` | View appointments and the calendar |
| | `appointment:create` | Book appointments (incl. walk-ins) |
| | `appointment:update` | Confirm, reschedule, cancel, mark no-show |
| | `appointment:checkin` | Check a patient in |
| | `appointment:consult` | Start and complete a consultation |
| EMR | `emr:read` | View diagnoses, notes, vitals, allergies, history |
| | `emr:write` | Record diagnoses and doctor notes; sign notes |
| | `vitals:record` | Record vital signs |
| | `nursing-note:write` | Write nursing notes |
| | `allergy:write` | Record and resolve allergies |
| | `prescription:create` | Write and cancel prescriptions |
| Inpatient | `admission:read` | View admissions, bed board |
| | `admission:request` | Raise an admission request |
| | `admission:manage` | Admit (allocate a bed), transfer, discharge |
| | `ward:manage` | Manage wards, rooms, beds, bed status |
| Laboratory | `lab:request:create` | Order lab tests |
| | `lab:request:read` | View lab requests and results |
| | `lab:sample:collect` | Record sample collection and processing |
| | `lab:result:enter` | Enter results |
| | `lab:result:review` | Review (sign off) results |
| | `lab:catalogue:manage` | Manage the test catalogue |
| Pharmacy | `prescription:read` | View prescriptions |
| | `pharmacy:dispense` | Dispense medicines |
| | `medicine:read` | View medicines and stock levels |
| | `medicine:manage` | Manage medicines and categories |
| | `stock:manage` | Receive stock, adjust, write off expired |
| | `supplier:manage` | Manage suppliers |
| Billing | `billing:read` | View charges, invoices, payments |
| | `charge:create` | Add manual service charges |
| | `invoice:create` | Build and issue invoices, apply discounts |
| | `invoice:void` | Void an unpaid invoice |
| | `payment:record` | Record payments, print receipts |
| | `payment:refund` | Record refunds |
| Staff | `employee:read` | View employees |
| | `employee:manage` | Register and edit employees |
| | `attendance:manage` | Record and edit attendance |
| | `leave:approve` | Approve or reject leave |
| Reports | `report:operational` | Patients, appointments, occupancy, lab and pharmacy analytics |
| | `report:financial` | Revenue, outstanding balances, payment analytics |
| | `report:staff` | Staff, attendance, leave reports |

**Granted to every user**: view and update their own profile, change their own password, read their own notifications, and request their own leave (if they are linked to an employee).

## 2. Role → permission matrix

Legend: ● granted · — not granted

| Permission | Super Admin | Hospital Admin | Doctor | Nurse | Receptionist | Lab Staff | Pharmacist | Accountant | HR Manager |
|---|---|---|---|---|---|---|---|---|---|
| user:read | ● | ● | — | — | — | — | — | — | ● |
| user:create / update / deactivate / reset-password | ● | ● | — | — | — | — | — | — | — |
| role:manage, settings:manage | ● | — | — | — | — | — | — | — | — |
| audit:read | ● | ● | — | — | — | — | — | — | — |
| department:read | ● | ● | ● | ● | ● | ● | ● | ● | ● |
| department:manage | ● | ● | — | — | — | — | — | — | — |
| patient:read | ● | ● | ● | ● | ● | ● | ● | ● | — |
| patient:create / update | ● | ● | — | — | ● | — | — | — | — |
| patient:document:read | ● | ● | ● | ● | ● | — | — | — | — |
| patient:document:upload | ● | ● | ● | — | ● | ● | — | — | — |
| doctor:read | ● | ● | ● | ● | ● | ● | ● | ● | ● |
| doctor:manage, schedule:manage | ● | ● | — | — | — | — | — | — | — |
| appointment:read | ● | ● | ● | ● | ● | — | — | ● | — |
| appointment:create / update / checkin | ● | ● | — | — | ● | — | — | — | — |
| appointment:consult | — | — | ● | — | — | — | — | — | — |
| emr:read | — | — | ● | ● | — | — | — | — | — |
| emr:write, prescription:create | — | — | ● | — | — | — | — | — | — |
| vitals:record | — | — | ● | ● | — | — | — | — | — |
| nursing-note:write | — | — | — | ● | — | — | — | — | — |
| allergy:write | — | — | ● | ● | — | — | — | — | — |
| admission:read | ● | ● | ● | ● | ● | — | — | ● | — |
| admission:request | — | — | ● | — | ● | — | — | — | — |
| admission:manage | ● | ● | — | — | ● | — | — | — | — |
| ward:manage | ● | ● | — | — | — | — | — | — | — |
| lab:request:create | — | — | ● | — | — | — | — | — | — |
| lab:request:read | — | — | ● | ● | — | ● | — | — | — |
| lab:sample:collect, lab:result:enter | — | — | — | — | — | ● | — | — | — |
| lab:result:review | — | — | ● | — | — | — | — | — | — |
| lab:catalogue:manage | ● | ● | — | — | — | ● | — | — | — |
| prescription:read | — | — | ● | ● | — | — | ● | — | — |
| pharmacy:dispense, stock:manage | — | — | — | — | — | — | ● | — | — |
| medicine:read | ● | ● | ● | ● | — | — | ● | — | — |
| medicine:manage, supplier:manage | ● | ● | — | — | — | — | ● | — | — |
| billing:read | ● | ● | — | — | ● | — | — | ● | — |
| charge:create | — | — | — | — | ● | — | — | ● | — |
| invoice:create, payment:record | — | — | — | — | ● | — | — | ● | — |
| invoice:void, payment:refund | — | — | — | — | — | — | — | ● | — |
| employee:read | ● | ● | — | — | — | — | — | — | ● |
| employee:manage, attendance:manage, leave:approve | ● | — | — | — | — | — | — | — | ● |
| report:operational | ● | ● | — | — | — | — | — | — | — |
| report:financial | ● | ● | — | — | — | — | — | ● | — |
| report:staff | ● | ● | — | — | — | — | — | — | ● |

### Deliberate exclusions (least privilege)

- **Admins do not get clinical-record access** (`emr:*`). Medical data is restricted to treating staff, a key privacy requirement. Admin oversight comes from operational and audit reports instead.
- **Lab staff see only demographics and lab orders**, not the full EMR.
- **Accountants see charges and appointments**, but not diagnoses.
- **Receptionist** can take payments at the front desk but cannot void invoices or issue refunds. Separation of duties: only the Accountant voids and refunds.

## 3. Record-level rules (in services, on top of permissions)

| Rule | Enforcement |
|---|---|
| A doctor's EMR writes attach only to encounters where they are the treating doctor | `EncounterService` checks `encounter.doctor.employee.user == currentUser` |
| Doctor EMR **reads** are allowed for any patient (continuity of care), but outside their own patients they need a reason ("break-the-glass"), which is recorded as `VIEW_SENSITIVE` in `audit_logs` | `EmrAccessService` |
| A nurse's EMR access covers patients admitted to their assigned wards, plus OPD patients checked in today in their department; anything else uses break-the-glass | `EmrAccessService` |
| Only the requesting doctor, or a doctor in the same department, can review a lab result | `LabService.review` |
| Clinical notes cannot be edited after signing; corrections are addenda | `ClinicalNoteService` |
| A user cannot deactivate themselves or remove the last active Super Admin | `UserService` |
| Leave approval cannot be done by the requester | `LeaveService` |
