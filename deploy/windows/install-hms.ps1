<#
.SYNOPSIS
  Installs or upgrades HMS from an unpacked release folder (docs/08 section 5-6).

.DESCRIPTION
  First install (elevated):
    - creates C:\hms\{backend,frontend,data\uploads,backups,logs}
    - copies the jar and the static frontend
    - writes backend\hms-backend.xml (WinSW) with the database password and a one-time bootstrap admin
      password typed at prompts, readable by Administrators and SYSTEM only
    - installs and starts the service, waits for /actuator/health = UP, then removes the bootstrap admin
      password from the file and restarts (the admin must change it at first sign-in)
  Upgrade (-Upgrade): stops the service, keeps the previous jar and frontend in backend\previous and
  frontend-previous, installs the new ones, starts, and rolls back automatically if health is not UP.

  Unattended: set HMS_DB_PASSWORD and HMS_BOOTSTRAP_ADMIN_PASSWORD instead of answering prompts.
  -NoService runs the backend as a plain background process instead of a service (rehearsal on a
  machine without WinSW); everything else is identical. Run it from a console window: a caller that
  captures this script's output through a pipe keeps waiting for as long as that background JVM runs.

.EXAMPLE
  .\install-hms.ps1 -WinSwExe C:\Downloads\WinSW-x64.exe
  .\install-hms.ps1 -Upgrade
#>
[CmdletBinding()]
param(
    [string]$Release = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path,
    [string]$InstallRoot = 'C:\hms',
    [string]$WinSwExe,
    [string]$JavaExe,
    [string]$DbUrl = 'jdbc:mysql://127.0.0.1:3306/hms?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true',
    [string]$DbUser = 'hms_app',
    [string]$AdminUsername = 'superadmin',
    [bool]$CookieSecure = $false,
    [int]$Port = 8080,
    [switch]$Upgrade,
    [switch]$NoService
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'hms-common.ps1')
if (-not $NoService) { Assert-Administrator }

$backendDir = Join-Path $InstallRoot 'backend'
$frontendDir = Join-Path $InstallRoot 'frontend'
$xmlPath = Join-Path $backendDir 'hms-backend.xml'
$health = "http://127.0.0.1:$Port/api/v1/actuator/health"
foreach ($f in 'backend\hms-backend.jar', 'frontend\index.html', 'windows\hms-backend.xml') {
    if (-not (Test-Path (Join-Path $Release $f))) { throw "$f is missing from the release folder $Release" }
}

function Get-Java {
    if ($JavaExe) { return $JavaExe }
    if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")) { return "$env:JAVA_HOME\bin\java.exe" }
    $c = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($c) { return $c.Source }
    throw 'Java 17 not found: install Temurin 17 or pass -JavaExe.'
}

# --- process control: the WinSW service, or a plain process for -NoService rehearsals ------------
$pidFile = Join-Path $backendDir 'rehearsal.pid'
function Start-Backend {
    if (-not $NoService) { Start-Service hms-backend; return }
    [xml]$x = Get-Content -Raw $xmlPath
    # the environment is set only for the moment of the launch; output goes to files so the caller's
    # console handles are not inherited (a captured console would otherwise wait for the JVM to exit)
    $saved = @{}
    foreach ($e in $x.service.env) { $saved[$e.name] = [Environment]::GetEnvironmentVariable($e.name); [Environment]::SetEnvironmentVariable($e.name, $e.value) }
    try {
        $p = Start-Process -FilePath $x.service.executable -ArgumentList $x.service.arguments -WorkingDirectory $x.service.workingdirectory `
            -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $backendDir 'rehearsal.out.log') -RedirectStandardError (Join-Path $backendDir 'rehearsal.err.log')
    } finally {
        foreach ($k in $saved.Keys) { [Environment]::SetEnvironmentVariable($k, $saved[$k]) }
    }
    $p.Id | Set-Content $pidFile
}
function Stop-Backend {
    if (-not $NoService) {
        if (Get-Service hms-backend -ErrorAction SilentlyContinue) { Stop-Service hms-backend -ErrorAction SilentlyContinue }
        return
    }
    if (Test-Path $pidFile) { Stop-Process -Id (Get-Content $pidFile) -Force -ErrorAction SilentlyContinue; Remove-Item $pidFile }
}

function Copy-Frontend {
    if (Test-Path $frontendDir) { Get-ChildItem $frontendDir | Remove-Item -Recurse -Force }
    Copy-Item (Join-Path $Release 'frontend\*') $frontendDir -Recurse
}

# ===================================================================================== upgrade
if ($Upgrade) {
    if (-not (Test-Path $xmlPath)) { throw "No existing installation at $InstallRoot (missing $xmlPath)." }
    Write-Host '==> Stopping the backend'
    Stop-Backend
    $prev = Join-Path $backendDir 'previous'
    New-Item -ItemType Directory -Force -Path $prev | Out-Null
    Copy-Item (Join-Path $backendDir 'hms-backend.jar') $prev -Force
    $prevFront = Join-Path $InstallRoot 'frontend-previous'
    if (Test-Path $prevFront) { Remove-Item $prevFront -Recurse -Force }
    Copy-Item $frontendDir $prevFront -Recurse
    Write-Host '==> Installing the new version'
    Copy-Item (Join-Path $Release 'backend\hms-backend.jar') $backendDir -Force
    Copy-Frontend
    Start-Backend
    if (Wait-Healthy $health 180) {
        Write-Host "Upgrade complete: $(Get-Content (Join-Path $Release 'VERSION.txt') -TotalCount 2 -ErrorAction SilentlyContinue)" -ForegroundColor Green
        return
    }
    Write-Warning 'The new version did not become healthy. Rolling back.'
    Stop-Backend
    Copy-Item (Join-Path $prev 'hms-backend.jar') $backendDir -Force
    Get-ChildItem $frontendDir | Remove-Item -Recurse -Force
    Copy-Item (Join-Path $prevFront '*') $frontendDir -Recurse
    Start-Backend
    if (Wait-Healthy $health 180) { throw 'Rolled back to the previous version, which is running. Check backend\logs for why the new one failed.' }
    throw 'Rollback also failed to start. Check backend\logs and the database (a migration may have run).'
}

# ===================================================================================== first install
if (Test-Path $xmlPath) { throw "HMS is already installed at $InstallRoot. Use -Upgrade." }
if (-not $NoService) {
    if (-not $WinSwExe -or -not (Test-Path $WinSwExe)) { throw 'Pass -WinSwExe with the path of WinSW-x64.exe v2.12.0 (docs/08 section 6).' }
    $winswVersion = (& $WinSwExe version 2>$null | Out-String).Trim()
    if ($winswVersion -notmatch '^2\.') { throw "That file does not look like WinSW 2.x (it reported '$winswVersion')." }
}
$java = Get-Java

Write-Host '==> Creating folders'
foreach ($d in $backendDir, $frontendDir, "$InstallRoot\data\uploads", "$InstallRoot\backups", "$backendDir\logs") {
    New-Item -ItemType Directory -Force -Path $d | Out-Null
}
Copy-Item (Join-Path $Release 'backend\hms-backend.jar') $backendDir -Force
Copy-Frontend
if (-not $NoService) { Copy-Item $WinSwExe (Join-Path $backendDir 'hms-backend.exe') -Force }

Write-Host '==> Service settings'
$dbPassword = Read-Secret "Password of the MySQL login '$DbUser'" 'HMS_DB_PASSWORD'
$adminPassword = Read-NewSecret "One-time password for the first administrator '$AdminUsername' (changed at first sign-in)" 'HMS_BOOTSTRAP_ADMIN_PASSWORD'

[xml]$x = Get-Content -Raw (Join-Path $Release 'windows\hms-backend.xml')
$s = $x.service
$s.executable = [string]$java
$s.arguments = [string]"-Xms256m -Xmx1024m -Dfile.encoding=UTF-8 -jar `"$backendDir\hms-backend.jar`""
$s.workingdirectory = [string]$backendDir
$values = @{
    SPRING_PROFILES_ACTIVE = 'prod'; HMS_DB_URL = $DbUrl; HMS_DB_USERNAME = $DbUser; HMS_DB_PASSWORD = $dbPassword
    HMS_BOOTSTRAP_ADMIN_USERNAME = $AdminUsername; HMS_BOOTSTRAP_ADMIN_PASSWORD = $adminPassword
    HMS_UPLOAD_DIR = "$InstallRoot\data\uploads"; HMS_LOG_DIR = "$backendDir\logs"
    HMS_COOKIE_SECURE = $CookieSecure.ToString().ToLower(); HMS_SERVER_PORT = "$Port"
}
foreach ($e in @($s.env)) { [void]$s.RemoveChild($e) }
foreach ($k in $values.Keys | Sort-Object) {
    $n = $x.CreateElement('env'); $n.SetAttribute('name', $k); $n.SetAttribute('value', [string]$values[$k]); [void]$s.AppendChild($n)
}
New-Item -ItemType File -Force -Path $xmlPath | Out-Null
if ($NoService) { Protect-CurrentUserOnly $xmlPath } else { Protect-AdminOnly $xmlPath }   # it holds secrets
$x.Save($xmlPath)

Write-Host '==> Starting'
if (-not $NoService) {
    & (Join-Path $backendDir 'hms-backend.exe') install
    if ($LASTEXITCODE -ne 0) { throw 'WinSW could not install the service.' }
}
Start-Backend
if (-not (Wait-Healthy $health 180)) { throw "The backend did not report UP within 3 minutes. See $backendDir\logs." }

Write-Host '==> Removing the one-time administrator password from the service file'
$x.service.env | Where-Object { $_.name -eq 'HMS_BOOTSTRAP_ADMIN_PASSWORD' } | ForEach-Object { [void]$x.service.RemoveChild($_) }
$x.Save($xmlPath)
Stop-Backend
Start-Backend
if (-not (Wait-Healthy $health 180)) { throw 'The backend did not come back after removing the bootstrap password.' }

Write-Host "HMS backend is running (health UP). Next: Apache (docs/08 section 7), then sign in as '$AdminUsername' and set a new password." -ForegroundColor Green
