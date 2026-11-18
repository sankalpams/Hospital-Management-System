<#
.SYNOPSIS
  Restores an HMS backup into a NEW database, checks it, and tells you how to switch over.

.DESCRIPTION
  Never overwrites the live database. Steps:
    1. verifies the zip against its .sha256 (when present)
    2. creates the target database (default hms_restore_<timestamp>); refuses if it already has tables
    3. loads the dump into it (the dump's own CREATE DATABASE/USE lines are rewritten to the target)
    4. checks it: Flyway history present and successful, key tables readable, and prints row counts
  With -RestoreUploads the document mirror is copied back to the uploads folder (existing files kept).
  Switching the application over is a deliberate, separate step: see docs/08 section 10.

.EXAMPLE
  .\restore-hms.ps1 -Backup C:\hms\backups\db\hms-20261119-020000.zip
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string]$Backup,
    [string]$TargetDatabase = "hms_restore_$(Get-Date -Format 'yyyyMMdd_HHmm')",
    [string]$MySqlBin = 'C:\Program Files\MySQL\MySQL Server 8.0\bin',
    [string]$MySqlHost = '127.0.0.1',
    [int]$Port = 3306,
    [switch]$RestoreUploads,
    [string]$BackupDir = 'C:\hms\backups',
    [string]$UploadsDir = 'C:\hms\data\uploads'
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'hms-common.ps1')
if ($TargetDatabase -notmatch '^[A-Za-z0-9_]+$') { throw 'Target database name: letters, digits and underscore only.' }
if ($TargetDatabase -eq 'hms') { throw 'Restore into a new database, never over the live one.' }

if (Test-Path "$Backup.sha256") {
    $expected = (Get-Content "$Backup.sha256").Split(' ')[0]
    if ((Get-FileHash $Backup -Algorithm SHA256).Hash.ToLower() -ne $expected) { throw 'Checksum mismatch: the backup file is damaged.' }
    Write-Host 'Checksum OK.'
} else {
    Write-Warning 'No .sha256 next to the backup; skipping the checksum.'
}

$work = Join-Path ([IO.Path]::GetTempPath()) "hms-restore-$([guid]::NewGuid().ToString('N'))"
Expand-Archive -Path $Backup -DestinationPath $work
try {
    $dump = Get-ChildItem $work -Filter '*.sql' | Select-Object -First 1
    if (-not $dump) { throw 'No .sql file inside the backup.' }
    # point the dump at the target database (it was taken with --databases <name>)
    $rewritten = Join-Path $work 'restore.sql'
    $reader = New-Object IO.StreamReader($dump.FullName, (New-Object Text.UTF8Encoding($false)))
    $writer = New-Object IO.StreamWriter($rewritten, $false, (New-Object Text.UTF8Encoding($false)))
    try {
        while ($null -ne ($line = $reader.ReadLine())) {
            if ($line -match '^CREATE DATABASE ') { continue }
            if ($line -match '^USE `[^`]+`;') { $line = "USE ``$TargetDatabase``;" }
            $writer.WriteLine($line)
        }
    } finally { $reader.Close(); $writer.Close() }

    $root = Read-Secret 'MySQL root password' 'HMS_MYSQL_ROOT_PASSWORD'
    $existing = Invoke-MySql -MySqlBin $MySqlBin -User root -Password $root -MySqlHost $MySqlHost -Port $Port `
        -Sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$TargetDatabase';"
    if ([int]$existing -gt 0) { throw "Database $TargetDatabase already has tables. Choose another -TargetDatabase." }
    Invoke-MySql -MySqlBin $MySqlBin -User root -Password $root -MySqlHost $MySqlHost -Port $Port `
        -Sql "CREATE DATABASE IF NOT EXISTS ``$TargetDatabase`` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
    Write-Host "Loading into $TargetDatabase ..."
    Invoke-MySql -MySqlBin $MySqlBin -User root -Password $root -MySqlHost $MySqlHost -Port $Port -InputFile $rewritten

    $check = Invoke-MySql -MySqlBin $MySqlBin -User root -Password $root -MySqlHost $MySqlHost -Port $Port -Database $TargetDatabase -Sql @"
SELECT 'migrations', CONCAT(COUNT(*), ' applied, latest V', MAX(CAST(version AS UNSIGNED)), IF(SUM(success = 0) > 0, ', SOME FAILED', '')) FROM flyway_schema_history;
SELECT 'users', COUNT(*) FROM users;
SELECT 'patients', COUNT(*) FROM patients;
SELECT 'appointments', COUNT(*) FROM appointments;
SELECT 'invoices', COUNT(*) FROM invoices;
SELECT 'stock ledger mismatches', COUNT(*) FROM medicine_batches b WHERE b.quantity_on_hand <> (SELECT COALESCE(SUM(t.quantity), 0) FROM stock_transactions t WHERE t.batch_id = b.id);
"@
    Write-Host "`nRestored $TargetDatabase :" -ForegroundColor Green
    $check | ForEach-Object { '  ' + ($_ -replace "`t", ': ') }
    if ($check -match 'SOME FAILED' -or ($check | Where-Object { $_ -match '^stock ledger mismatches\t[1-9]' })) { throw 'The restored data failed a check; do not switch to it.' }
} finally {
    Remove-Item $work -Recurse -Force -ErrorAction SilentlyContinue
}

if ($RestoreUploads) {
    & robocopy.exe (Join-Path $BackupDir 'uploads') $UploadsDir /E /XO /R:2 /W:5 /NP /NFL /NDL /NJH /NJS | Out-Null
    if ($LASTEXITCODE -ge 8) { throw "robocopy failed with exit code $LASTEXITCODE" }
    Write-Host 'Documents copied back (newer files on disk were kept).'
}
Write-Host "`nTo switch HMS to it: docs/08 section 10 'Switch over' (stop the service, point HMS_DB_URL at $TargetDatabase, start)." -ForegroundColor Cyan
