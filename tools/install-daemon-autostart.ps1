<#
.SYNOPSIS
    Registers "Jarvis Daemon" + "Jarvis Reverse SSH Tunnel" scheduled tasks.

.DESCRIPTION
    Both run as the current interactive user (NOT SYSTEM) so ~/.ssh and the
    OS keychain are accessible. Restart-on-failure: 3x with 1-minute delay.

    Pre-requisites checked:
      1. daemon\target\release\jarvis-daemon.exe must exist.
      2. ~/.ssh/id_ed25519 must exist (key is NOT auto-generated).

    Dry-run: $env:JARVIS_DAEMON_INSTALL_DRYRUN=1 or -DryRun skips schtasks calls
    (admin check is also bypassed in dry-run mode).

.PARAMETER DaemonExe
    Path to the daemon binary (default: $RepoRoot\daemon\target\release\jarvis-daemon.exe).

.PARAMETER SshKey
    Path to the SSH private key (default: $env:USERPROFILE\.ssh\id_ed25519).

.PARAMETER DryRun
    Skip actual schtasks calls.
#>

param(
    [string]$DaemonExe = "",
    [string]$SshKey    = "",
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$isDryRun = $DryRun.IsPresent -or ($env:JARVIS_DAEMON_INSTALL_DRYRUN -eq "1")

# Require admin for real installs; dry-run skips the elevation check.
if (-not $isDryRun) {
    $currentPrincipal = [Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
    if (-not $currentPrincipal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
        Write-Error "This script must run as Administrator (right-click PowerShell > Run as Administrator).`nFor a no-op test, set `$env:JARVIS_DAEMON_INSTALL_DRYRUN=1 or pass -DryRun."
        exit 1
    }
}

$RepoRoot = Split-Path -Parent $PSScriptRoot

if ($DaemonExe -eq "") { $DaemonExe = Join-Path $RepoRoot "daemon\target\release\jarvis-daemon.exe" }
if ($SshKey -eq "")    { $SshKey    = Join-Path $env:USERPROFILE ".ssh\id_ed25519" }

$XmlTemplate = Join-Path $PSScriptRoot "jarvis-daemon-task.xml"
$LogDir      = Join-Path $env:USERPROFILE ".jarvis"
$LogFile     = Join-Path $LogDir "daemon-autostart.log"

Write-Host "=== Jarvis Daemon Autostart Installer ===" -ForegroundColor Cyan

# ── Pre-flight checks ──
if (-not (Test-Path $DaemonExe)) {
    Write-Error "Daemon binary not found: $DaemonExe`nRun: cd daemon && cargo build --release"
    exit 1
}
if (-not (Test-Path $SshKey)) {
    Write-Error "SSH private key not found: $SshKey`nGenerate with: ssh-keygen -t ed25519 -f `"$SshKey`""
    exit 1
}
if (-not (Test-Path $XmlTemplate)) {
    Write-Error "Task XML template not found: $XmlTemplate"
    exit 1
}

if (-not (Test-Path $LogDir)) { New-Item -ItemType Directory -Force -Path $LogDir | Out-Null }

$currentUser = "$env:USERDOMAIN\$env:USERNAME"

function Register-JarvisTask {
    param([string]$TaskName, [string]$XmlContent, [string]$DescriptionForLog)
    $tmpXml = [System.IO.Path]::GetTempFileName() + ".xml"
    [System.IO.File]::WriteAllText($tmpXml, $XmlContent, [System.Text.Encoding]::Unicode)

    if ($isDryRun) {
        Write-Host "DRY-RUN: schtasks /Create /TN `"$TaskName`" /XML `"$tmpXml`" /F"
        Write-Host "         ($DescriptionForLog)"
        Remove-Item $tmpXml -ErrorAction SilentlyContinue
        return
    }

    Write-Host "Registering: $TaskName ..."
    schtasks.exe /Create /TN $TaskName /XML $tmpXml /F
    if ($LASTEXITCODE -ne 0) {
        Remove-Item $tmpXml -ErrorAction SilentlyContinue
        Write-Error "schtasks /Create failed for '$TaskName' (exit $LASTEXITCODE)"
        exit $LASTEXITCODE
    }
    Remove-Item $tmpXml -ErrorAction SilentlyContinue
    Write-Host "[OK] Task '$TaskName' registered."
}

# ── Daemon task ──
$daemonXml = (Get-Content $XmlTemplate -Raw -Encoding UTF8)
$daemonXml = $daemonXml.Replace("__DAEMON_EXE__", $DaemonExe)
$daemonXml = $daemonXml.Replace("__CURRENT_USER__", $currentUser)
Register-JarvisTask -TaskName "Jarvis Daemon" -XmlContent $daemonXml -DescriptionForLog "jarvis-daemon.exe on 127.0.0.1:7331"

# ── Tunnel task ──
$sshArgs = "-N -R 7331:127.0.0.1:7331 root@46.247.109.91 -i `"$SshKey`" -o ServerAliveInterval=30 -o ServerAliveCountMax=3 -o ExitOnForwardFailure=yes"

$tunnelXml = @"
<?xml version="1.0" encoding="UTF-16"?>
<Task version="1.4" xmlns="http://schemas.microsoft.com/windows/2004/02/mit/task">
  <RegistrationInfo>
    <Description>Reverse SSH tunnel: local 127.0.0.1:7331 -&gt; VPS 46.247.109.91:7331.</Description>
    <Author>$currentUser</Author>
  </RegistrationInfo>
  <Triggers>
    <LogonTrigger>
      <Enabled>true</Enabled>
      <UserId>$currentUser</UserId>
      <Delay>PT15S</Delay>
    </LogonTrigger>
    <BootTrigger>
      <Enabled>true</Enabled>
      <Delay>PT35S</Delay>
    </BootTrigger>
  </Triggers>
  <Principals>
    <Principal id="Author">
      <UserId>$currentUser</UserId>
      <LogonType>InteractiveToken</LogonType>
      <RunLevel>LeastPrivilege</RunLevel>
    </Principal>
  </Principals>
  <Settings>
    <MultipleInstancesPolicy>IgnoreNew</MultipleInstancesPolicy>
    <DisallowStartIfOnBatteries>false</DisallowStartIfOnBatteries>
    <StopIfGoingOnBatteries>false</StopIfGoingOnBatteries>
    <AllowHardTerminate>true</AllowHardTerminate>
    <StartWhenAvailable>true</StartWhenAvailable>
    <RunOnlyIfNetworkAvailable>true</RunOnlyIfNetworkAvailable>
    <IdleSettings>
      <StopOnIdleEnd>false</StopOnIdleEnd>
      <RestartOnIdle>false</RestartOnIdle>
    </IdleSettings>
    <AllowStartOnDemand>true</AllowStartOnDemand>
    <Enabled>true</Enabled>
    <Hidden>false</Hidden>
    <RunOnlyIfIdle>false</RunOnlyIfIdle>
    <WakeToRun>false</WakeToRun>
    <ExecutionTimeLimit>PT0S</ExecutionTimeLimit>
    <RestartOnFailure>
      <Interval>PT1M</Interval>
      <Count>3</Count>
    </RestartOnFailure>
  </Settings>
  <Actions Context="Author">
    <Exec>
      <Command>ssh</Command>
      <Arguments>$sshArgs</Arguments>
    </Exec>
  </Actions>
</Task>
"@

Register-JarvisTask -TaskName "Jarvis Reverse SSH Tunnel" -XmlContent $tunnelXml -DescriptionForLog "reverse SSH -R 7331 to VPS"

# ── Health log ──
$stamp = Get-Date -Format "yyyy-MM-ddTHH:mm:ssZ"
$logEntry = "$stamp  install OK  daemon=$DaemonExe  sshKey=$SshKey  user=$currentUser"

if ($isDryRun) {
    Write-Host "DRY-RUN: would append to log: $LogFile"
    Write-Host "         $logEntry"
} else {
    Add-Content -Path $LogFile -Value $logEntry -Encoding UTF8
    Write-Host "[OK] Health log updated: $LogFile"
}

Write-Host ""
Write-Host "=== Install complete ===" -ForegroundColor Green
Write-Host "Next steps:"
Write-Host "  1. Open Task Scheduler (taskschd.msc); both tasks should show 'Ready'"
Write-Host "  2. Reboot to verify auto-fire, or run now:"
Write-Host "       schtasks /Run /TN `"Jarvis Daemon`""
Write-Host "       schtasks /Run /TN `"Jarvis Reverse SSH Tunnel`""
Write-Host "  3. Tutor header pill should go green within ~30s of login"
Write-Host "  4. Logs: $LogFile"
