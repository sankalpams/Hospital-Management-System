# 08 — Windows Deployment Guide (Apache + Spring Boot + MySQL)

> **Status (2026-11-19).** What's been rehearsed:
> - The release build, `create-database.ps1`, `install-hms.ps1` (first install, upgrade, and the automatic rollback of a broken upgrade), `backup-hms.ps1` and `restore-hms.ps1` were rehearsed end to end on the development machine (docs/07 §7).
> - In that rehearsal, a fresh install was reached through the browser under the production security headers, the first administrator signed in, and the forced password change worked.
>
> What still needs a real server:
> - Apache, WinSW and native MySQL are not installed on the development machine. In the rehearsal the backend ran as a plain process (`-NoService`) and the MySQL client ran in a container.
> - Sections 3 (MySQL), 6 (the WinSW service) and 7 (Apache) must be walked through once on the target server; tick off the checks in section 8 there.

Target layout on the server:

```
C:\hms\
├── frontend\          React production build (Apache DocumentRoot)
├── frontend-previous\ the version before the last upgrade (for rollback)
├── backend\           hms-backend.jar, hms-backend.exe (WinSW), hms-backend.xml, logs\, previous\
├── data\uploads\      patient documents (NOT under the web root)
├── backups\           db\*.zip (+ .sha256), uploads\ (mirror), backup.cnf, backup.log
└── windows\           the deployment scripts from the release
```

Run every command in an **elevated PowerShell** unless noted otherwise. The scripts are plain ASCII and work in Windows PowerShell 5.1, which ships with Windows.

## 1. Install Java 17 (Temurin)

1. Download the **Eclipse Temurin JDK 17 (x64 MSI)** from adoptium.net and install it, ticking *Set JAVA_HOME* and *Add to PATH*.
2. Verify: `java -version` should print `17.x`.

## 2. Build a release (on a build machine with the repository)

```powershell
powershell -ExecutionPolicy Bypass -File deploy\windows\build-release.ps1
```

This runs the backend tests and the frontend gate (contrast + tests + build). It then writes `release\hms-<version>-<timestamp>\`, plus a `.zip` of it, containing:
- the jar
- the static frontend
- the Apache config
- the scripts
- this guide
- `VERSION.txt` (commit, build time, whether tests ran, and whether the working copy had uncommitted changes)
- `SHA256SUMS.txt`

Nothing secret goes into the release. Stop any local backend or `npm run dev` first; the script says so if one is holding files it has to replace.

Copy the zip to the server and unpack it, for example to `C:\hms\release\hms-0.2.0-…`. Then copy its `windows\` folder to `C:\hms\windows\`.

## 3. Install MySQL 8.0

1. Download **MySQL Installer for Windows** (8.0.x) from dev.mysql.com and choose *Server only*.
2. Configuration:
   - Server Computer
   - port **3306**
   - *Use Strong Password Encryption*
   - set a root password
   - run as Windows service **MySQL80**, starting at boot
3. Add to `C:\ProgramData\MySQL\MySQL Server 8.0\my.ini` under `[mysqld]`, then run `Restart-Service MySQL80`:
   ```ini
   character-set-server=utf8mb4
   collation-server=utf8mb4_0900_ai_ci
   default-time-zone=+00:00
   sql-mode=STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION,ONLY_FULL_GROUP_BY
   log-bin=hms-bin
   binlog_expire_logs_seconds=604800
   bind-address=127.0.0.1
   ```
   - `log-bin` keeps the binary log, which makes point-in-time recovery possible (docs/09).
   - `bind-address` keeps MySQL off the network.

## 4. Create the database and logins

```powershell
C:\hms\windows\create-database.ps1
```

It asks for the root password and two new passwords (12+ characters, with a letter and a digit), then creates:
- database `hms` (utf8mb4)
- login **`hms_app`**, which the application uses: data and schema rights on `hms` only, never root
- login **`hms_backup`**: read-only rights needed by mysqldump
- `C:\hms\backups\backup.cnf` with the backup credentials, readable only by Administrators and SYSTEM

Credentials reach MySQL only through temporary option files that only the current user can read, so they never appear on a command line. For unattended installs, set `HMS_MYSQL_ROOT_PASSWORD`, `HMS_DB_PASSWORD` and `HMS_BACKUP_DB_PASSWORD` instead of answering prompts.

## 5. (nothing to do) Tables

Flyway creates and upgrades the tables when the backend starts. `flyway clean` is disabled, so no configuration can wipe the database.

## 6. Install the backend as a Windows service

1. Download WinSW **v2.12.0** (`WinSW-x64.exe`) from github.com/winsw/winsw/releases.
2. Install from the unpacked release:
   ```powershell
   C:\hms\release\hms-0.2.0-…\windows\install-hms.ps1 -WinSwExe C:\Downloads\WinSW-x64.exe
   ```
   It asks for the `hms_app` password and a **one-time password for the first administrator** (`superadmin`). Then it:
   1. creates the folders and copies the jar and frontend
   2. writes `C:\hms\backend\hms-backend.xml`, readable only by Administrators and SYSTEM
   3. installs the **hms-backend** service and starts it
   4. waits for `/actuator/health` to report UP
   5. **removes the one-time password from the file** and restarts the service
3. Useful parameters:
   - `-CookieSecure $true`: once HTTPS is set up (section 9)
   - `-DbUrl`: if MySQL is not local
   - `-Port`: if 8080 is taken
   - `-JavaExe`: if Java isn't found automatically

The backend listens on **127.0.0.1 only**, so it can only be reached through Apache. It stops gracefully, finishing in-flight requests such as payments and dispensing before it exits.

### Upgrading

```powershell
C:\hms\release\hms-0.3.0-…\windows\install-hms.ps1 -Upgrade
```

The upgrade:
1. stops the service
2. keeps the current jar in `backend\previous\` and the current frontend in `frontend-previous\`
3. installs the new version and starts it
4. **rolls back automatically** if the new version doesn't report UP within 3 minutes

Take a backup first (section 10). A new version may migrate the database, and a rollback of the jar does not undo that; see docs/09.

## 7. Install Apache HTTP Server 2.4

1. Download the **Apache 2.4 Win64** build from apachelounge.com, along with the matching *Visual C++ Redistributable*. Extract it to `C:\Apache24`.
2. In `C:\Apache24\conf\httpd.conf`, uncomment:
   ```
   LoadModule deflate_module modules/mod_deflate.so
   LoadModule expires_module modules/mod_expires.so
   LoadModule headers_module modules/mod_headers.so
   LoadModule proxy_module modules/mod_proxy.so
   LoadModule proxy_http_module modules/mod_proxy_http.so
   ```
   Confirm that `mod_dir` and `mod_reqtimeout` are loaded (both are by default).
3. Also in `httpd.conf`:
   ```
   ServerName localhost:80
   Include conf/extra/httpd-hms.conf
   ```
4. Copy the release's `apache\httpd-hms.conf` to `C:\Apache24\conf\extra\`. If you didn't use `C:/hms/frontend`, adjust `HMS_WEB_ROOT`.
5. Validate the config, then install and start the service:
   ```powershell
   C:\Apache24\bin\httpd.exe -t
   C:\Apache24\bin\httpd.exe -k install -n Apache2.4
   Start-Service Apache2.4
   ```

What the config does:

- serves the SPA, with `FallbackResource /index.html` so deep links work
- proxies `/api/` to `127.0.0.1:8080` and blocks every actuator endpoint except health
- sets the security headers: a strict CSP with no inline scripts or styles, X-Frame-Options DENY, nosniff, Referrer-Policy and Permissions-Policy
- hides the server version, disables TRACE and times out slow requests (slowloris)
- caches hashed assets for a year, never caches `index.html`, and compresses text

## 8. Test the complete application

| Check | Command or action | Expected |
|---|---|---|
| Health through Apache | `Invoke-RestMethod http://localhost/api/v1/actuator/health` | `status: UP` |
| Actuator locked down | `Invoke-WebRequest http://localhost/api/v1/actuator/env` | 403 |
| SPA deep link | Open `http://localhost/change-password` directly | The app loads (not a 404) |
| Login | Open `http://localhost/` and sign in as `superadmin` with the one-time password | Forced password change, then the dashboard |
| Headers | `(Invoke-WebRequest http://localhost/).Headers` | CSP, X-Frame-Options and X-Content-Type-Options present; `Server: Apache` with no version |
| Session cookie | Browser dev tools → Application → Cookies | `HMS_SESSION`: HttpOnly, SameSite=Strict (and Secure once on HTTPS) |
| Per-IP throttle | 30 wrong passwords from one workstation | The 31st attempt says "Too many failed sign-ins from this computer" (429) for 15 minutes; other workstations are unaffected |
| Backup | section 10, `register-backup-task.ps1` | "First backup succeeded" |

## 9. HTTPS and network access

`http://localhost` is fine on the server itself. For access from other workstations on the hospital LAN:

1. Follow the commented **HTTPS block at the end of `httpd-hms.conf`**:
   - port 443 with the hospital's certificate, TLS 1.2 and 1.3 only
   - `X-Forwarded-Proto https`
   - HSTS
   - port 80 redirecting to 443
2. Set `HMS_COOKIE_SECURE=true` in `C:\hms\backend\hms-backend.xml` and restart the `hms-backend` service.
3. Open port 443 in Windows Defender Firewall, for the LAN (Domain/Private) profile only.

Patient data must not travel over plain HTTP on a shared network.

The per-IP sign-in limit sees each workstation's own address through Apache. If every workstation reaches Apache through one shared proxy address, raise `security.ip_max_failed_logins` in `system_settings` accordingly.

## 10. Backups and recovery

Schedule the nightly backup, once:

```powershell
C:\hms\windows\register-backup-task.ps1            # daily at 02:00 as SYSTEM; runs once immediately to prove it works
```

Each night `backup-hms.ps1`:
- dumps the database with `--single-transaction` (no locks; the hospital keeps working)
- rejects an incomplete dump
- zips it to `C:\hms\backups\db\hms-<timestamp>.zip` with a `.sha256` next to it
- mirrors `C:\hms\data\uploads` to `C:\hms\backups\uploads`
- keeps 30 days (never fewer than 7 backups)
- writes one line to `C:\hms\backups\backup.log`

A failed run exits non-zero, so the Task Scheduler history shows it.

**Copy `C:\hms\backups` off the server** (another machine, a NAS, or offline media) on a schedule the hospital controls. A backup on the same disk does not survive the disk.

**Restore** never touches the live database. It loads a backup into a new database and checks it:

```powershell
C:\hms\windows\restore-hms.ps1 -Backup C:\hms\backups\db\hms-20261119-020000.zip
```

It verifies the checksum, then loads the dump into `hms_restore_<timestamp>`. It checks:
- the migration history is complete and successful
- the key tables are readable
- the stock ledger still balances

It prints row counts. Add `-RestoreUploads` to copy the document mirror back.

**Switch over** (when the live database is lost or damaged), as a deliberate step:
1. `Stop-Service hms-backend`
2. In `C:\hms\backend\hms-backend.xml`, change the database name at the end of `HMS_DB_URL` (`/hms?` → `/hms_restore_…?`).
3. Grant the app login on the restored database:
   ```sql
   GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES, DROP ON `hms_restore_…`.* TO 'hms_app'@'localhost';
   ```
4. `Start-Service hms-backend` and check health.

The full recovery procedures (lost server, bad upgrade, point-in-time recovery, lost admin password) are in **docs/09-operations-runbook.md**.
