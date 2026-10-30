# Permission matrix

Phase 1 seeds permissions from the following matrix. `ADMIN` has every permission and is the only role that can manage users, inspect audit logs, or view this matrix.

| Permission | ADMIN | DOCTOR | NURSE | RECEPTIONIST | LAB_STAFF | PHARMACIST | ACCOUNTANT |
| --- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| USER_READ / USER_MANAGE / ROLE_READ / AUDIT_READ / SETTINGS_MANAGE | ✓ | | | | | | |
| PATIENT_READ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| PATIENT_WRITE | ✓ | ✓ | | ✓ | | | |
| APPOINTMENT_READ / APPOINTMENT_WRITE | ✓ | ✓ | ✓ / | ✓ | | | |
| EMR_READ | ✓ | ✓ | ✓ | | | | |
| EMR_WRITE | ✓ | ✓ | | | | | |
| VITALS_WRITE | ✓ | ✓ | ✓ | | | | |
| LAB_READ / LAB_WRITE | ✓ | ✓ / | ✓ | ✓ | ✓ | | |
| PHARMACY_READ / PHARMACY_WRITE | ✓ | ✓ / | ✓ / | | | ✓ | |
| INVOICE_READ | ✓ | ✓ | | ✓ | | ✓ | ✓ |
| INVOICE_WRITE / INVOICE_VOID | ✓ | | | | | | ✓ |
| REPORT_READ / REPORT_EXPORT | ✓ | ✓ | | | | | ✓ |

Receptionists intentionally have no `EMR_*` permission, so later clinical API endpoints and UI routes must never return diagnoses or clinical notes to them.
