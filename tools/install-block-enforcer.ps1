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

# Helper: run a native command, swallow nonzero exit + stderr noise. PS's
# Stop preference treats schtasks "task not found" stderr as terminating;
# wrap so first-time install doesn't blow up.
function Invoke-Quiet([string]$exe, [string[]]$args) {
    & $exe @args 2>&1 | Out-Null
    $global:LASTEXITCODE = 0
}

# 1. Stop any existing scheduled-task run + kill lingering loop processes.
# Loop runs as either pythonw, wscript, or powershell hosting block-enforcer.
Write-Host "Stopping existing $TaskName task (if any)..."
Invoke-Quiet "schtasks" @("/End", "/TN", $TaskName)
Start-Sleep -Seconds 1
$loopMatch = '*block-enforcer*'
Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
    Where-Object {
        ($_.Name -in @("wscript.exe", "cscript.exe", "powershell.exe", "pwsh.exe")) -and
        ($_.CommandLine -like $loopMatch)
    } |
    ForEach-Object {
        Write-Host "Killing existing loop pid $($_.ProcessId): $($_.Name)"
        Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
    }

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
Invoke-Quiet "schtasks" @("/Delete", "/TN", $TaskName, "/F")
$action = "wscript.exe"
$argStr = """$VbsPath"""
# Use Register-ScheduledTask cmdlet (creates the task in the current user
# context without admin rights, unlike schtasks /Create which sometimes
# refuses on standard accounts).
$registered = $false
try {
    Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false -ErrorAction SilentlyContinue
    $taskAction  = New-ScheduledTaskAction -Execute "wscript.exe" -Argument "`"$VbsPath`""
    $taskTrigger = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME
    $taskSettings = New-ScheduledTaskSettingsSet `
        -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
        -StartWhenAvailable -ExecutionTimeLimit (New-TimeSpan -Days 0)
    $task = New-ScheduledTask -Action $taskAction -Trigger $taskTrigger -Settings $taskSettings
    Register-ScheduledTask -TaskName $TaskName -InputObject $task -Force | Out-Null
    Write-Host "Registered scheduled task: $TaskName (auto-start at logon)"
    $registered = $true
} catch {
    Write-Host "Register-ScheduledTask failed: $_"
    Write-Host "Falling back: launching loop in this session only. Will not auto-start next logon."
    Start-Process wscript.exe -ArgumentList "`"$VbsPath`""
    Write-Host "Loop running now. Logs at $env:USERPROFILE\.jarvis-block-enforcer.log"
    exit 0
}

# 4. Start it once so the loop is running now.
try {
    Start-ScheduledTask -TaskName $TaskName -ErrorAction Stop
    Write-Host "Started $TaskName."
} catch {
    Write-Host "Start-ScheduledTask failed: $_"
    Write-Host "Launching loop directly so it's running now."
    Start-Process wscript.exe -ArgumentList "`"$VbsPath`""
}

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
