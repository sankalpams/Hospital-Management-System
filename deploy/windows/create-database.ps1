<#
.SYNOPSIS
  Creates the HMS database, the application login and a read-only backup login (docs/08 section 4).

.DESCRIPTION
  Asks for the MySQL root password and two new passwords. Credentials reach the MySQL client only
  through temporary option files readable by the current user, never on a command line.
  Writes C:\hms\backups\backup.cnf (Administrators and SYSTEM only) for backup-hms.ps1.
  Run once, elevated. Safe to re-run: existing objects are kept and passwords are reset.
  Unattended: set HMS_MYSQL_ROOT_PASSWORD, HMS_DB_PASSWORD and HMS_BACKUP_DB_PASSWORD instead of answering prompts.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File create-database.ps1
#>
[CmdletBinding()]
param(
    [string]$MySqlBin = 'C:\Program Files\MySQL\MySQL Server 8.0\bin',
    [string]$Database = 'hms',
    [string]$AppUser = 'hms_app',
    [string]$BackupUser = 'hms_backup',
    [string]$AccountHost = 'localhost',   # where the logins may connect from; keep localhost on a single server
    [string]$BackupDir = 'C:\hms\backups',
    [string]$MySqlHost = '127.0.0.1',
    [int]$Port = 3306
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'hms-common.ps1')

if ($Database -notmatch '^[A-Za-z0-9_]+$') { throw 'Database name: letters, digits and underscore only.' }
$root = Read-Secret 'MySQL root password' 'HMS_MYSQL_ROOT_PASSWORD'
$app = Read-NewSecret "New password for '$AppUser' (the application)" 'HMS_DB_PASSWORD'
$backup = Read-NewSecret "New password for '$BackupUser' (backups, read-only)" 'HMS_BACKUP_DB_PASSWORD'

$sql = @"
CREATE DATABASE IF NOT EXISTS ``$Database`` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS '$AppUser'@'$AccountHost' IDENTIFIED BY '$(Escape-SqlString $app)';
ALTER USER '$AppUser'@'$AccountHost' IDENTIFIED BY '$(Escape-SqlString $app)';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES, DROP ON ``$Database``.* TO '$AppUser'@'$AccountHost';
CREATE USER IF NOT EXISTS '$BackupUser'@'$AccountHost' IDENTIFIED BY '$(Escape-SqlString $backup)';
ALTER USER '$BackupUser'@'$AccountHost' IDENTIFIED BY '$(Escape-SqlString $backup)';
GRANT SELECT, SHOW VIEW, TRIGGER, EVENT, LOCK TABLES ON ``$Database``.* TO '$BackupUser'@'$AccountHost';
FLUSH PRIVILEGES;
"@

Invoke-MySql -MySqlBin $MySqlBin -User 'root' -Password $root -MySqlHost $MySqlHost -Port $Port -Sql $sql
Write-Host "Database '$Database' and logins '$AppUser', '$BackupUser' are ready." -ForegroundColor Green

New-Item -ItemType Directory -Force -Path $BackupDir | Out-Null
$cnf = Join-Path $BackupDir 'backup.cnf'
Write-OptionFile -Path $cnf -User $BackupUser -Password $backup -MySqlHost $MySqlHost -Port $Port
Protect-AdminOnly $cnf
Write-Host "Backup credentials saved to $cnf (Administrators and SYSTEM only)."
Write-Host "Use the '$AppUser' password for HMS_DB_PASSWORD when you run install-hms.ps1."
