# Helpers shared by the HMS deployment scripts. Dot-source it: . (Join-Path $PSScriptRoot 'hms-common.ps1')
# Windows PowerShell 5.1 compatible.

# For unattended runs a secret can come from the named environment variable instead of a prompt.
function Read-Secret([string]$Prompt, [string]$EnvName = '') {
    if ($EnvName -and [Environment]::GetEnvironmentVariable($EnvName)) { return [Environment]::GetEnvironmentVariable($EnvName) }
    $secure = Read-Host -Prompt $Prompt -AsSecureString
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) } finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
}

function Test-StrongPassword([string]$Value) {
    $Value.Length -ge 12 -and $Value -match '[A-Za-z]' -and $Value -match '\d'
}

# A new password, typed twice: 12+ characters with a letter and a digit.
function Read-NewSecret([string]$Prompt, [string]$EnvName = '') {
    if ($EnvName -and [Environment]::GetEnvironmentVariable($EnvName)) {
        $v = [Environment]::GetEnvironmentVariable($EnvName)
        if (-not (Test-StrongPassword $v)) { throw "$EnvName must have at least 12 characters, including a letter and a digit." }
        return $v
    }
    while ($true) {
        $a = Read-Secret $Prompt
        if (-not (Test-StrongPassword $a)) { Write-Warning 'Use at least 12 characters, including a letter and a digit.'; continue }
        if ($a -ne (Read-Secret 'Type it again')) { Write-Warning 'The two entries differ.'; continue }
        return $a
    }
}

function Escape-SqlString([string]$Value) {
    $Value.Replace('\', '\\').Replace("'", "''")
}

# MySQL option file; the value is quoted so #, ; and spaces survive.
function Write-OptionFile([string]$Path, [string]$User, [string]$Password, [string]$MySqlHost = '127.0.0.1', [int]$Port = 3306) {
    $quoted = '"' + $Password.Replace('\', '\\').Replace('"', '\"') + '"'
    $text = "[client]`r`nuser=$User`r`npassword=$quoted`r`nhost=$MySqlHost`r`nport=$Port`r`n"
    [IO.File]::WriteAllText($Path, $text, (New-Object Text.UTF8Encoding($false)))
}

# Only Administrators and SYSTEM may read the file (it holds a secret).
function Protect-AdminOnly([string]$Path) {
    & icacls.exe $Path /inheritance:r /grant:r '*S-1-5-32-544:F' '*S-1-5-18:F' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Could not restrict access to $Path" }
}

# Only the current user may read the file.
function Protect-CurrentUserOnly([string]$Path) {
    & icacls.exe $Path /inheritance:r /grant:r "$($env:USERDOMAIN)\$($env:USERNAME):F" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Could not restrict access to $Path" }
}

# Runs SQL through mysql with credentials in a temporary option file that is deleted afterwards.
function Invoke-MySql([string]$MySqlBin, [string]$User, [string]$Password, [string]$Sql, [string]$Database = '',
                      [string]$MySqlHost = '127.0.0.1', [int]$Port = 3306, [string]$InputFile = '') {
    $cnf = [IO.Path]::Combine([IO.Path]::GetTempPath(), "hms-$([guid]::NewGuid().ToString('N')).cnf")
    New-Item -ItemType File -Path $cnf | Out-Null
    Protect-CurrentUserOnly $cnf
    try {
        Write-OptionFile -Path $cnf -User $User -Password $Password -MySqlHost $MySqlHost -Port $Port
        $mysqlArgs = @("--defaults-extra-file=$cnf", '--batch', '--skip-column-names', '--default-character-set=utf8mb4')
        if ($Database) { $mysqlArgs += $Database }
        $mysql = Join-Path $MySqlBin 'mysql'
        # SQL always goes in as a BOM-less UTF-8 file through cmd redirection: PowerShell 5.1 pipes to
        # native programs re-encode text and can add a byte-order mark
        $sqlFile = $InputFile
        if (-not $sqlFile) {
            $sqlFile = "$cnf.sql"
            New-Item -ItemType File -Path $sqlFile | Out-Null
            Protect-CurrentUserOnly $sqlFile   # may contain passwords (CREATE USER)
            [IO.File]::WriteAllText($sqlFile, $Sql, (New-Object Text.UTF8Encoding($false)))
        }
        & cmd.exe /d /s /c "`"`"$mysql`" $($mysqlArgs -join ' ') < `"$sqlFile`"`""
        if ($LASTEXITCODE -ne 0) { throw "mysql failed with exit code $LASTEXITCODE" }
    } finally {
        Remove-Item -Force $cnf, "$cnf.sql" -ErrorAction SilentlyContinue
    }
}

function Wait-Healthy([string]$Url = 'http://127.0.0.1:8080/api/v1/actuator/health', [int]$Seconds = 120) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    while ((Get-Date) -lt $deadline) {
        try { if ((Invoke-RestMethod -Uri $Url -TimeoutSec 5).status -eq 'UP') { return $true } } catch { }
        Start-Sleep -Seconds 3
    }
    return $false
}

function Assert-Administrator {
    $id = [Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
    if (-not $id.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) { throw 'Run this from an elevated PowerShell (Run as administrator).' }
}
