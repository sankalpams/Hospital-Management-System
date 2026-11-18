<#
.SYNOPSIS
  Schedules backup-hms.ps1 every night (default 02:00) as SYSTEM, and runs it once now to prove it works.

.EXAMPLE
  .\register-backup-task.ps1                    # elevated
  .\register-backup-task.ps1 -At 23:30 -KeepDays 60
#>
[CmdletBinding()]
param(
    [string]$At = '02:00',
    [string]$ScriptPath = (Join-Path $PSScriptRoot 'backup-hms.ps1'),
    [int]$KeepDays = 30,
    [string]$MySqlBin = 'C:\Program Files\MySQL\MySQL Server 8.0\bin'
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'hms-common.ps1')
Assert-Administrator

$arguments = "-NoProfile -ExecutionPolicy Bypass -File `"$ScriptPath`" -KeepDays $KeepDays -MySqlBin `"$MySqlBin`""
$action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument $arguments
$trigger = New-ScheduledTaskTrigger -Daily -At $At
$principal = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -ExecutionTimeLimit (New-TimeSpan -Hours 2) -RestartCount 2 -RestartInterval (New-TimeSpan -Minutes 15)
Register-ScheduledTask -TaskName 'HMS nightly backup' -Action $action -Trigger $trigger -Principal $principal -Settings $settings `
    -Description 'Database dump + document mirror for the Hospital Management System (deploy/windows/backup-hms.ps1)' -Force | Out-Null

Write-Host "Scheduled 'HMS nightly backup' daily at $At. Running it once now..."
Start-ScheduledTask -TaskName 'HMS nightly backup'
do { Start-Sleep 3; $info = Get-ScheduledTaskInfo -TaskName 'HMS nightly backup' } while ((Get-ScheduledTask -TaskName 'HMS nightly backup').State -eq 'Running')
if ($info.LastTaskResult -eq 0) { Write-Host 'First backup succeeded. See C:\hms\backups\backup.log.' -ForegroundColor Green }
else { throw "The first backup failed (result $($info.LastTaskResult)). See C:\hms\backups\backup.log." }
