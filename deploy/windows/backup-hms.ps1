<#
.SYNOPSIS
  Nightly HMS backup: a consistent database dump (zipped) plus a mirror of the uploaded documents.

.DESCRIPTION
  - mysqldump --single-transaction (no locks on InnoDB; the hospital keeps working) with credentials
    from backup.cnf (written by create-database.ps1, Administrators/SYSTEM only)
  - the dump must end with "-- Dump completed", otherwise the run fails and the partial file is deleted
  - zipped to <BackupDir>\db\hms-<timestamp>.zip, with its SHA-256 next to it
  - documents mirrored to <BackupDir>\uploads (robocopy /MIR, only changed files are copied)
  - zips older than -KeepDays are deleted, but the newest -KeepAtLeast are always kept
  - a log line per run in <BackupDir>\backup.log; exit code 0 = success (Task Scheduler shows failures)

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File C:\hms\windows\backup-hms.ps1
#>
[CmdletBinding()]
param(
    [string]$MySqlBin = 'C:\Program Files\MySQL\MySQL Server 8.0\bin',
    [string]$Database = 'hms',
    [string]$BackupDir = 'C:\hms\backups',
    [string]$UploadsDir = 'C:\hms\data\uploads',
    [string]$OptionFile,
    [int]$KeepDays = 30,
    [int]$KeepAtLeast = 7
)
$ErrorActionPreference = 'Stop'
if (-not $OptionFile) { $OptionFile = Join-Path $BackupDir 'backup.cnf' }
$log = Join-Path $BackupDir 'backup.log'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$dbDir = Join-Path $BackupDir 'db'
$sql = Join-Path $dbDir "hms-$stamp.sql"
$zip = Join-Path $dbDir "hms-$stamp.zip"

function Log([string]$level, [string]$text) {
    $line = "{0} {1} {2}" -f (Get-Date -Format 's'), $level, $text
    Add-Content -Path $log -Value $line -Encoding UTF8
    Write-Host $line
}

try {
    if (-not (Test-Path $OptionFile)) { throw "Missing $OptionFile (run create-database.ps1 first)." }
    New-Item -ItemType Directory -Force -Path $dbDir | Out-Null
    $started = Get-Date

    # mysqldump writes UTF-8; going through cmd keeps the bytes exact (PowerShell 5.1 pipes would re-encode)
    $dump = Join-Path $MySqlBin 'mysqldump'
    $cmd = "`"$dump`" --defaults-extra-file=`"$OptionFile`" --single-transaction --routines --triggers --events " +
           "--no-tablespaces --set-gtid-purged=OFF --default-character-set=utf8mb4 --databases $Database > `"$sql`""
    & cmd.exe /d /s /c "`"$cmd`""
    if ($LASTEXITCODE -ne 0) { throw "mysqldump failed with exit code $LASTEXITCODE" }
    $tail = Get-Content $sql -Tail 1
    if ($tail -notmatch '^-- Dump completed') { throw 'The dump is incomplete (no "-- Dump completed" line).' }

    Compress-Archive -Path $sql -DestinationPath $zip -CompressionLevel Optimal
    Remove-Item $sql
    $hash = (Get-FileHash $zip -Algorithm SHA256).Hash.ToLower()
    "$hash  $(Split-Path $zip -Leaf)" | Set-Content -Encoding ASCII "$zip.sha256"

    if (Test-Path $UploadsDir) {
        & robocopy.exe $UploadsDir (Join-Path $BackupDir 'uploads') /MIR /R:2 /W:5 /NP /NFL /NDL /NJH /NJS | Out-Null
        if ($LASTEXITCODE -ge 8) { throw "robocopy failed with exit code $LASTEXITCODE" }
    }

    $old = Get-ChildItem $dbDir -Filter 'hms-*.zip' | Sort-Object LastWriteTime -Descending | Select-Object -Skip $KeepAtLeast |
        Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-$KeepDays) }
    foreach ($f in $old) { Remove-Item $f.FullName, "$($f.FullName).sha256" -ErrorAction SilentlyContinue }

    Log 'OK' ("{0} {1:N1} MB in {2:N0}s; removed {3} old backup(s)" -f (Split-Path $zip -Leaf), ((Get-Item $zip).Length / 1MB), ((Get-Date) - $started).TotalSeconds, @($old).Count)
    exit 0
} catch {
    Remove-Item $sql, $zip -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force -Path $BackupDir | Out-Null
    Log 'FAILED' $_.Exception.Message
    exit 1
}
