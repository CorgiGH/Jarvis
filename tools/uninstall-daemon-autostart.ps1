<#
.SYNOPSIS
    Removes "Jarvis Daemon" and "Jarvis Reverse SSH Tunnel" scheduled tasks.

.DESCRIPTION
    Idempotent. Already-removed tasks are skipped silently.
    Does NOT remove the daemon binary or SSH key.

    Dry-run: $env:JARVIS_DAEMON_INSTALL_DRYRUN=1 or -DryRun skips schtasks calls
    (admin check is also bypassed in dry-run mode).

.PARAMETER DryRun
    Skip actual schtasks calls.
#>

param(
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Continue"

$isDryRun = $DryRun.IsPresent -or ($env:JARVIS_DAEMON_INSTALL_DRYRUN -eq "1")

# Require admin for real uninstalls; dry-run skips the elevation check.
if (-not $isDryRun) {
    $currentPrincipal = [Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
    if (-not $currentPrincipal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
        Write-Error "This script must run as Administrator (right-click PowerShell > Run as Administrator).`nFor a no-op test, set `$env:JARVIS_DAEMON_INSTALL_DRYRUN=1 or pass -DryRun."
        exit 1
    }
}

$TaskNames = @("Jarvis Daemon", "Jarvis Reverse SSH Tunnel")

Write-Host "=== Jarvis Daemon Autostart Uninstaller ===" -ForegroundColor Cyan
if ($isDryRun) {
    Write-Host "[DRY-RUN] No schtasks calls will be made." -ForegroundColor Yellow
}

foreach ($name in $TaskNames) {
    Write-Host "Removing task: $name ..."
    if ($isDryRun) {
        Write-Host "[DRY-RUN] Would run: schtasks.exe /Delete /TN `"$name`" /F"
        continue
    }

    $output = schtasks.exe /Delete /TN $name /F 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "[OK] Removed: $name"
    } elseif ($output -match "cannot find the file|The specified task name") {
        Write-Host "[SKIP] Not found (already removed): $name"
    } else {
        Write-Warning "schtasks /Delete failed for '$name' (exit $LASTEXITCODE): $output"
    }
}

Write-Host ""
Write-Host "=== Uninstall complete ===" -ForegroundColor Green
Write-Host "Daemon binary and SSH key were NOT removed."
