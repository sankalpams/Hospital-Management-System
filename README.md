# HMS — Hospital Management System

## Run the Phase 1 application

1. Copy `.env.example` to `.env` and replace its secrets for any non-local use.
2. Run `docker compose up --build`.
3. Open [http://localhost:8080](http://localhost:8080); API documentation is at [http://localhost:8081/swagger-ui.html](http://localhost:8081/swagger-ui.html), MailHog at [http://localhost:8025](http://localhost:8025).

The development profile seeds deterministic Phase 1 accounts. Every account uses `Welcome@12345`:

| Role | Username |
| --- | --- |
| Admin | `admin` |
| Doctor | `doctor` |
| Nurse | `nurse` |
| Receptionist | `reception` |
| Lab staff | `lab` |
| Pharmacist | `pharmacy` |
| Accountant | `accounts` |

Those credentials are development-only. New accounts receive a temporary password and are required to change it at first sign-in. Database timestamps are UTC; the UI displays Asia/Colombo time and LKR formatting.

## Project Documentation

Detailed architecture specifications, engineering logs, and design guides are maintained under `/docs`:
- [Project Implementation Log](file:///d:/Projects/HMS/docs/work-log.md): Complete chronological record of architecture decisions, schemas, security engines, and tests.
- [Permission Matrix](file:///d:/Projects/HMS/docs/permissions.md): Granular role-to-permission mapping for all 7 hospital roles.
- [Design System Guide](file:///d:/Projects/HMS/docs/design-system.md): Tokens, typography, microcopy, and UI standards.

## Local checks

Run `mvn verify` from `backend`, then `npm ci && npm run test -- --run && npm run build` from `frontend`. Docker images use Java 21 and Node 22 regardless of host installations.
