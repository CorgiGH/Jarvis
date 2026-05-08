# tools/install-block-enforcer.ps1 - register block-enforcer.ps1 as a
# Windows scheduled task that auto-starts on user logon.
#
# Mirror of install-pc-relay.ps1 pattern. Idempotent: existing task
# replaced; running powershell.exe instances on the existing task name
# are killed first so the user gets a clean slate.

$ErrorActionPreference = 'Stop'

$TaskName  = "JarvisBlockEnforcer"
$ScriptDir = "C:\Users\User\jarvis-kotlin\tools"
$ScriptPath = Join-Path $ScriptDir "block-enforcer.ps1"
$VbsPath   = Join-Path $ScriptDir "start_block_enforcer_hidden.vbs"

if (-not (Test-Path $ScriptPath)) {
    throw "block-enforcer.ps1 not found at $ScriptPath - clone or copy first."
}

# 1. Stop any existing scheduled-task run + kill any lingering instances.
Write-Host "Stopping existing $TaskName task (if any)..."
schtasks /End /TN $TaskName 2>$null | Out-Null
Start-Sleep -Seconds 1
Get-Process powershell -ErrorAction SilentlyContinue |
    Where-Object { $_.MainWindowTitle -like "*block-enforcer*" -or
                   $_.CommandLine -like "*block-enforcer*" } |
    ForEach-Object { Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue }

# 2. Write the VBS launcher (lets the powershell loop run without a
#    visible console window - same hiding pattern as start_relay_hidden.vbs).
$vbsContent = @"
Set objShell = CreateObject("WScript.Shell")
objShell.Run "powershell -NoProfile -ExecutionPolicy Bypass -File """ & _
    "$ScriptPath" & """ -Loop", 0, False
"@
Set-Content -Path $VbsPath -Value $vbsContent -Encoding ASCII
Write-Host "Wrote VBS launcher: $VbsPath"

# 3. Register / replace the scheduled task. Trigger ONLOGON.
schtasks /Delete /TN $TaskName /F 2>$null | Out-Null
$action = "wscript.exe"
$args = """$VbsPath"""
schtasks /Create /SC ONLOGON /TN $TaskName `
    /TR "$action $args" /RL LIMITED /F | Out-Null
Write-Host "Registered scheduled task: $TaskName (ONLOGON)"

# 4. Start it once so the loop is running now.
schtasks /Run /TN $TaskName | Out-Null
Write-Host "Started $TaskName."

Write-Host ""
Write-Host "Installed. Verify with:"
Write-Host "  schtasks /Query /TN $TaskName /V /FO LIST | Select-String 'Status|Last Run'"
Write-Host ""
Write-Host "Smoke-test the alert UI without waiting for a real drift signal:"
Write-Host "  powershell -NoProfile -ExecutionPolicy Bypass -File $ScriptPath -Test"
Write-Host ""
Write-Host "Logs land at $env:USERPROFILE\.jarvis-block-enforcer.log"
Write-Host "Last-seen ts at $env:USERPROFILE\.jarvis-drift-lastseen"
Write-Host ""
Write-Host "Stop / uninstall:"
Write-Host "  schtasks /End /TN $TaskName"
Write-Host "  schtasks /Delete /TN $TaskName /F"
