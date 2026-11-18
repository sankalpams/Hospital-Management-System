# 09 — Operations and Recovery Runbook

For whoever runs HMS on the hospital server. Installation is covered in [docs/08](08-windows-deployment.md). Every command runs in an **elevated PowerShell** on the server. The scripts are in `C:\hms\windows\`.

## 1. Routine checks

| How often | Check | Where | Healthy |
|---|---|---|---|
| Daily | Last night's backup | `C:\hms\backups\backup.log` (last line) | `OK hms-<date>.zip …` dated today |
| Daily | Application up | `Invoke-RestMethod http://localhost/api/v1/actuator/health` | `status: UP` |
| Weekly | Backups copied off the server | the NAS, other machine or offline media | this week's zips are there |
| Weekly | Disk space | `Get-PSDrive C` | over 20% free (dumps, logs, binlogs and documents grow) |
| Weekly | Errors in the backend log | `C:\hms\backend\logs\hms-backend.log` (search for `ERROR`) | nothing new and unexplained |
| Monthly | **Restore test** | `restore-hms.ps1 -Backup <latest>` (section 4) | it completes and the counts look right; then drop the test database |
| Monthly | Audit of sign-in security | the app: Audit log → Sign-in activity (filter: Failed) | no unexplained bursts; `ADMIN_RECOVERY` only when you ran it |

A backup that has never been restored is only a hope. The monthly restore test is the one check that proves you can recover.

## 2. The application is down

1. `Get-Service hms-backend, Apache2.4, MySQL80`. Start anything that is stopped, in that order: MySQL, then backend, then Apache.
2. If `hms-backend` won't stay up, read the end of `C:\hms\backend\logs\hms-backend.log` (and `hms-backend.err.log` next to it):

| Message contains | Cause | Fix |
|---|---|---|
| `Communications link failure` | MySQL is not running or not reachable | `Start-Service MySQL80`; check `my.ini` `bind-address=127.0.0.1` |
| `Access denied for user 'hms_app'` | the database password changed | section 7, "Change the database password" |
| `Port 8080 was already in use` | another program took the port | find it with `Get-NetTCPConnection -LocalPort 8080`, or reinstall with `-Port` |
| `Validate failed: Migrations have failed` / `Detected applied migration not resolved locally` | the database is from a newer HMS than this jar (for example after a rollback) | section 5 |
| `does not meet the password policy` (bootstrap) | the one-time admin password in the XML is too weak | first install only: choose a stronger one in `hms-backend.xml`, restart |

3. If Apache is up but the page shows **503**, the backend is down (step 2). If it shows **404 for every page**, `HMS_WEB_ROOT` in `httpd-hms.conf` doesn't point at `C:\hms\frontend`.

## 3. Nobody can sign in

- **One user is locked out:** the lock lifts by itself after 15 minutes. An administrator can also reset their password in Users; that clears the lock too.
- **A whole workstation gets "Too many failed sign-ins from this computer":** that computer made 30 failed attempts in 15 minutes. Wait 15 minutes, and find out why: a shared, misremembered password, or someone guessing. Every attempt is listed in Audit log → Sign-in activity.
- **No administrator can sign in** (forgotten password, left the hospital, or locked):
  ```powershell
  C:\hms\windows\recover-admin.ps1 -Username superadmin
  ```
  It prints a temporary password on this console only, unlocks and reactivates the account, ends its sessions and records `ADMIN_RECOVERY` in the audit log. Sign in and set a new password immediately. It works only for Super Admin accounts and needs no restart.

## 4. Restore data

### Restore last night's backup (database damaged or data deleted by mistake)

1. Load it into a new database and check it. The live database is not touched:
   ```powershell
   C:\hms\windows\restore-hms.ps1 -Backup (Get-ChildItem C:\hms\backups\db\*.zip | Sort-Object Name | Select-Object -Last 1).FullName
   ```
   Note the database name it prints, for example `hms_restore_20261119_0830`.
2. Decide.
   - **Recover a few records:** copy them across with SQL from the restored database, then drop it.
   - **Replace the whole database:** follow docs/08 §10 "Switch over": stop the backend, point `HMS_DB_URL` at the restored database, grant `hms_app` on it, and start.
3. Anything entered **after the backup** is not in it. To recover it, see point-in-time recovery below, or re-enter it from paper records.

### Point-in-time recovery (up to just before the incident)

This needs the binary log (`log-bin` in `my.ini`, docs/08 §3), kept for 7 days.

1. Restore the last backup before the incident into a new database (above).
2. Find the time of the incident (for example from the audit log) and replay the changes made after the backup up to one second before it:
   ```powershell
   $bin = 'C:\Program Files\MySQL\MySQL Server 8.0\bin'
   & "$bin\mysqlbinlog.exe" --database=hms --rewrite-db="hms->hms_restore_20261119_0830" `
       --start-datetime="2026-11-19 02:00:00" --stop-datetime="2026-11-19 10:41:59" `
       C:\ProgramData\MySQL\MySQL Server 8.0\Data\hms-bin.0000* > C:\hms\backups\replay.sql
   cmd /c "`"$bin\mysql.exe`" -u root -p hms_restore_20261119_0830 < C:\hms\backups\replay.sql"
   ```
   The binlog times are in the server's time zone (UTC with the docs/08 `my.ini`); the audit log shows UTC as well.
3. Check, then switch over as above. Delete `replay.sql` afterwards, because it contains patient data.

### A single patient document is missing

The nightly mirror has every document as of last night. Find the file name in the database (`patient_documents.stored_name`) and copy it back:
```powershell
Copy-Item C:\hms\backups\uploads\<stored_name> C:\hms\data\uploads\
```
`restore-hms.ps1 -RestoreUploads` copies back everything that is missing.

## 5. An upgrade went wrong

- **`install-hms.ps1 -Upgrade` said "Rolled back to the previous version, which is running":** nothing more to do now. Send `C:\hms\backend\logs\hms-backend.log` to the developers.
- **The rollback also failed, with a migration error in the log:** the new version had already changed the database schema, and the old jar refuses the newer schema. Two ways out:
  1. **Forward (preferred):** fix the problem with the new version (configuration, disk or memory, as the log says) and run the upgrade again.
  2. **Back:** restore the backup taken **before** the upgrade (section 4), switch to it, and run the previous release with `install-hms.ps1 -Upgrade` from its folder.

  This is why every upgrade starts with a backup:
  ```powershell
  powershell -File C:\hms\windows\backup-hms.ps1      # then: install-hms.ps1 -Upgrade
  ```

## 6. The server is lost

You need:
- the newest backup zip and the `uploads` mirror (from your off-server copy)
- the release zip
- docs/08

1. On the new server, follow docs/08 sections 1, 3, 4, 6 and 7, with the same release version as before if possible.
2. Restore the backup (section 4) and switch the service to it, or restore into a database called `hms` before the first start. Then copy the `uploads` mirror to `C:\hms\data\uploads\`.
3. Register the backup task again (docs/08 §10).
4. Users keep their passwords (they're in the database). Sessions from before are gone, so everyone signs in again.

## 7. Routine maintenance

| Task | How |
|---|---|
| Change the database password | `ALTER USER 'hms_app'@'localhost' IDENTIFIED BY '…';` as root. Update `HMS_DB_PASSWORD` in `C:\hms\backend\hms-backend.xml`, then `Restart-Service hms-backend`. The backup login `hms_backup` lives in `C:\hms\backups\backup.cnf`. |
| Change a security limit | Edit `system_settings` as root (`security.max_failed_logins`, `security.lock_minutes`, `security.ip_max_failed_logins`, `security.ip_window_minutes`). Takes effect immediately. |
| Turn on HTTPS | docs/08 §9 |
| Renew the certificate | Replace the files in `C:\Apache24\conf\ssl\`, run `httpd.exe -t`, then `Restart-Service Apache2.4` |
| Keep more backups | `register-backup-task.ps1 -KeepDays 60` (re-registers the task) |
| Restart everything | `Restart-Service MySQL80; Restart-Service hms-backend; Restart-Service Apache2.4`. The backend finishes in-flight requests before it stops. |

## 8. Who to tell

Record every recovery action (date, what happened, what was restored, who did it) in the hospital's IT incident log. If patient data may have been exposed or lost, follow the hospital's data-protection procedure. HMS provides the audit trail, but it does not replace that procedure.
