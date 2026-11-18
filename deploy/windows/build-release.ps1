<#
.SYNOPSIS
  Builds a release folder and zip with everything a hospital server needs (docs/08).

.DESCRIPTION
  Runs the backend tests and the frontend gate (contrast + tests + build), then assembles:
    release\hms-<version>-<timestamp>\
      backend\hms-backend.jar
      frontend\            (static SPA for Apache)
      apache\httpd-hms.conf
      windows\*.ps1, hms-backend.xml (template)
      docs\08-windows-deployment.md
      VERSION.txt, SHA256SUMS.txt
  and zips it. Nothing secret goes into the release.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File deploy\windows\build-release.ps1
  powershell -ExecutionPolicy Bypass -File deploy\windows\build-release.ps1 -SkipTests   # emergency rebuild only
#>
[CmdletBinding()]
param(
    [switch]$SkipTests
)
$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..\..')

function Step($text) { Write-Host "==> $text" -ForegroundColor Cyan }
function Run($exe, [string[]]$arguments, $dir) {
    Push-Location $dir
    try {
        & $exe @arguments
        if ($LASTEXITCODE -ne 0) { throw "$exe $($arguments -join ' ') failed with exit code $LASTEXITCODE" }
    } finally { Pop-Location }
}

$version = (Select-String -Path (Join-Path $root 'backend\pom.xml') -Pattern '<version>([^<]+)</version>' | Select-Object -Skip 1 -First 1).Matches[0].Groups[1].Value
$commit = (& git -C $root rev-parse --short HEAD).Trim()
$dirty = [bool](& git -C $root status --porcelain --untracked-files=no -- backend frontend deploy)
$stamp = Get-Date -Format 'yyyyMMdd-HHmm'
$name = "hms-$version-$stamp"
$out = Join-Path $root "release\$name"

$jar = Join-Path $root 'backend\target\hms-backend.jar'
if (Test-Path $jar) {
    try { [IO.File]::Open($jar, 'Open', 'ReadWrite', 'None').Close() }
    catch { throw "backend\target\hms-backend.jar is in use (is a local backend running from it?). Stop it and run this again." }
}
# npm ci replaces node_modules; a running Vite dev server keeps esbuild.exe open
$esbuild = Join-Path $root 'frontend\node_modules\@esbuild\win32-x64\esbuild.exe'
if (Test-Path $esbuild) {
    try { [IO.File]::Open($esbuild, 'Open', 'ReadWrite', 'None').Close() }
    catch { throw "frontend\node_modules is in use (is 'npm run dev' or 'npm run preview' running?). Stop it and run this again." }
}

Step "Backend ($(if ($SkipTests) { 'tests skipped' } else { 'with tests' }))"
$mvnArgs = @('-B', 'clean', 'package')
if ($SkipTests) { $mvnArgs += '-DskipTests' }
Run (Join-Path $root 'backend\mvnw.cmd') $mvnArgs (Join-Path $root 'backend')

Step 'Frontend'
Run 'npm.cmd' @('ci', '--no-audit', '--no-fund') (Join-Path $root 'frontend')
Run 'npm.cmd' @('run', $(if ($SkipTests) { 'build' } else { 'check' })) (Join-Path $root 'frontend')

Step "Assembling $name"
New-Item -ItemType Directory -Force -Path "$out\backend", "$out\frontend", "$out\apache", "$out\windows", "$out\docs" | Out-Null
Copy-Item (Join-Path $root 'backend\target\hms-backend.jar') "$out\backend\"
Copy-Item (Join-Path $root 'frontend\dist\*') "$out\frontend\" -Recurse
Copy-Item (Join-Path $root 'deploy\apache\httpd-hms.conf') "$out\apache\"
Get-ChildItem (Join-Path $root 'deploy\windows') -File | Where-Object { $_.Name -ne 'build-release.ps1' } | Copy-Item -Destination "$out\windows\"
Copy-Item (Join-Path $root 'docs\08-windows-deployment.md') "$out\docs\"
@(
    "HMS $version"
    "Commit:  $commit$(if ($dirty) { ' (with uncommitted changes)' })"
    "Built:   $(Get-Date -Format 's') on $env:COMPUTERNAME"
    "Tests:   $(if ($SkipTests) { 'SKIPPED' } else { 'passed (backend + frontend gate)' })"
) | Set-Content -Encoding UTF8 "$out\VERSION.txt"

Get-ChildItem $out -Recurse -File | Where-Object { $_.Name -ne 'SHA256SUMS.txt' } | ForEach-Object {
    "{0}  {1}" -f (Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLower(), $_.FullName.Substring($out.Length + 1).Replace('\', '/')
} | Set-Content -Encoding ASCII "$out\SHA256SUMS.txt"

Compress-Archive -Path "$out\*" -DestinationPath "$out.zip" -Force
Step "Done: $out.zip"
if ($dirty) { Write-Warning 'Built from a working copy with uncommitted changes; VERSION.txt says so.' }
