# Hospital Management System (HMS)

![Java](https://img.shields.io/badge/Java-21-ED8B00?style=flat&logo=openjdk&logoColor=white&labelColor=555)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-6DB33F?style=flat&logo=springboot&logoColor=white&labelColor=555)
![React](https://img.shields.io/badge/React-18-61DAFB?style=flat&logo=react&logoColor=white&labelColor=555)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?style=flat&logo=postgresql&logoColor=white&labelColor=555)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat&logo=docker&logoColor=white&labelColor=555)
![License](https://img.shields.io/badge/License-MIT-yellow?style=flat&labelColor=555)

A full-stack web application that manages hospital operations in one place:
patients, appointments, medical records, laboratory, pharmacy, inpatient care,
billing, staff, and reports, with role-based access and a complete audit trail.

## What it does

- **Patients:** registration, search, duplicate detection, documents, consents
- **Appointments:** doctor schedules, booking, daily queue, reminders
- **Medical records:** consultations, vitals, ICD-10 diagnoses, prescriptions, PDFs
- **Laboratory:** test orders, sample tracking, results, critical-value alerts
- **Pharmacy:** inventory, batches, FEFO dispensing, expiry and low-stock alerts
- **Inpatient:** wards and beds, admission and discharge, nursing notes, triage
- **Billing:** invoices, split payments, refunds, receipts
- **Reports:** dashboards for each role, with CSV and PDF export
- **Security:** JWT auth, role-based permissions, audit logs

## Tech stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3, Spring Security, JPA, Flyway |
| Frontend | React 18, TypeScript, Vite, Tailwind CSS, shadcn/ui |
| Database | PostgreSQL 16 |
| DevOps | Docker Compose, GitHub Actions |

## Getting started

```bash
git clone <your-repo-url> hms
cd hms
cp .env.example .env
docker compose up --build
```

| Service | URL |
|---|---|
| Web app | http://localhost:3000 |
| API docs (Swagger) | http://localhost:8080/swagger-ui.html |
| Mail inbox (dev) | http://localhost:8025 |

Demo login (dev only): `admin@hms.local` / `ChangeMe@123`

## Project structure

```
hms/
├── backend/    # Spring Boot API
├── frontend/   # React app
├── docker/     # Dockerfiles and configs
├── docs/       # ERD, permissions, API notes
└── docker-compose.yml
```

## License

Distributed under the MIT License. See [LICENSE](LICENSE) for details.

## Author

**Malith Shehan**: [GitHub](https://github.com/SankalpaMS)