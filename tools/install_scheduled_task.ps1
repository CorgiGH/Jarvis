# Registers JarvisActivityLogger as a Windows scheduled task that runs on logon
# and survives the active session, capturing the foreground window every 5 min.
# Run from any PowerShell session: .\install_scheduled_task.ps1

param(
    [switch]$Uninstall
)

$TaskName = "JarvisActivityLogger"
$VbsPath  = "C:\Users\User\jarvis-kotlin\tools\start_logger_hidden.vbs"

if ($Uninstall) {
    schtasks /Delete /TN $TaskName /F
    exit $LASTEXITCODE
}

if (-not (Test-Path $VbsPath)) {
    Write-Error "VBS launcher missing: $VbsPath. Run 'gradle installDist' first."
    exit 1
}

# Replace any existing task with the same name; trigger at user logon; run as
# the current user with limited privileges.
schtasks /Create `
    /TN $TaskName `
    /TR "wscript.exe `"$VbsPath`"" `
    /SC ONLOGON `
    /RL LIMITED `
    /F

if ($LASTEXITCODE -ne 0) {
    Write-Error "Failed to register scheduled task."
    exit $LASTEXITCODE
}

Write-Host "Task '$TaskName' registered. Starting now..."
schtasks /Run /TN $TaskName
