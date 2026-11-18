<#
.SYNOPSIS
  Break-glass: issues a temporary password for a Super Admin who cannot sign in (docs/09).

.DESCRIPTION
  For when no administrator can sign in (forgotten password, account locked or deactivated). Runs the
  installed backend once in console mode with the service's own settings (from hms-backend.xml), on a
  random loopback port next to the running service. The temporary password is shown on this console only; it must be changed at the
  next sign-in. The account is unlocked and reactivated, its sessions end, and the action is written to the
  audit log (ADMIN_RECOVERY). The running service does not need to be stopped.
  Only Super Admin accounts can be recovered this way; everyone else is reset by an administrator in the app.

.EXAMPLE
  C:\hms\windows\recover-admin.ps1 -Username superadmin       # elevated
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string]$Username,
    [string]$InstallRoot = 'C:\hms'
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'hms-common.ps1')
Assert-Administrator

$xmlPath = Join-Path $InstallRoot 'backend\hms-backend.xml'
if (-not (Test-Path $xmlPath)) { throw "No installation found at $InstallRoot." }
[xml]$x = Get-Content -Raw $xmlPath
foreach ($e in $x.service.env) { [Environment]::SetEnvironmentVariable($e.name, $e.value) }   # this process only
$jar = Join-Path $InstallRoot 'backend\hms-backend.jar'
& $x.service.executable -jar $jar "--hms.recover-admin=$Username" '--server.port=0' '--logging.level.root=WARN' '--spring.main.banner-mode=off'
if ($LASTEXITCODE -ne 0) { throw 'Recovery did not complete; see the message above.' }
