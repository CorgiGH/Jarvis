# tools/block-enforcer.ps1 — PC-side drift interrupter.
#
# Polls https://corgflix.duckdns.org/api/signals every 60s. On any new
# signal whose rationale starts with "drift:" OR kind in {"drift_alert"},
# pops a blocking always-on-top red dialog + plays alert beeps. The user
# must dismiss it explicitly — the dialog will not auto-close.
#
# This is the user's "I asked for something invasive" surface. Less brutal
# than killing windows; more brutal than the silent Telegram daily push.
#
# Install:
#   schtasks /Create /SC ONLOGON /TN JarvisBlockEnforcer ^
#     /TR "powershell -NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass ^
#          -File C:\Users\User\jarvis-kotlin\tools\block-enforcer.ps1 -Loop"
#
# Manual one-shot:
#   powershell -File tools\block-enforcer.ps1
#
# Test (force a fake drift):
#   powershell -File tools\block-enforcer.ps1 -Test
#
# Stop the loop: kill the powershell process or `schtasks /End /TN JarvisBlockEnforcer`.

param(
    [switch]$Loop,
    [switch]$Test
)

$ErrorActionPreference = 'Continue'

$ApiBase   = $env:JARVIS_API_BASE
if (-not $ApiBase) { $ApiBase = "https://corgflix.duckdns.org" }
$TokenPath = "C:\Users\User\jarvis-kotlin\tools\AUTH_TOKEN.txt"
$LastSeenPath = "$env:USERPROFILE\.jarvis-drift-lastseen"
$LogPath = "$env:USERPROFILE\.jarvis-block-enforcer.log"

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

function Write-Log($msg) {
    $line = "{0} {1}" -f (Get-Date -Format "o"), $msg
    Add-Content -Path $LogPath -Value $line -ErrorAction SilentlyContinue
}

function Get-Token {
    if (-not (Test-Path $TokenPath)) {
        Write-Log "ERROR: AUTH_TOKEN.txt missing at $TokenPath"
        return $null
    }
    return (Get-Content $TokenPath -Raw).Trim()
}

function Get-LastSeen {
    if (Test-Path $LastSeenPath) {
        return (Get-Content $LastSeenPath -Raw).Trim()
    }
    return (Get-Date).ToUniversalTime().AddMinutes(-5).ToString("o")
}

function Set-LastSeen($ts) {
    Set-Content -Path $LastSeenPath -Value $ts -Encoding utf8
}

function Show-DriftModal($snippet, $rationale) {
    # Three rapid beeps to wake the user from whatever flow they're in.
    for ($i = 0; $i -lt 3; $i++) {
        [System.Console]::Beep(1500, 250)
        Start-Sleep -Milliseconds 100
    }
    $form = New-Object System.Windows.Forms.Form
    $form.Text = "JARVIS DRIFT ALERT"
    $form.BackColor = [System.Drawing.Color]::FromArgb(192, 0, 0)
    $form.ForeColor = [System.Drawing.Color]::White
    $form.TopMost = $true
    $form.StartPosition = [System.Windows.Forms.FormStartPosition]::CenterScreen
    $form.Width = 900
    $form.Height = 500
    $form.FormBorderStyle = [System.Windows.Forms.FormBorderStyle]::FixedDialog
    $form.MinimizeBox = $false
    $form.MaximizeBox = $false
    $form.ShowInTaskbar = $true

    $title = New-Object System.Windows.Forms.Label
    $title.Text = "OFF TRACK"
    $title.Font = New-Object System.Drawing.Font("Segoe UI", 36, [System.Drawing.FontStyle]::Bold)
    $title.Dock = [System.Windows.Forms.DockStyle]::Top
    $title.TextAlign = [System.Drawing.ContentAlignment]::MiddleCenter
    $title.Height = 90
    $form.Controls.Add($title)

    $body = New-Object System.Windows.Forms.Label
    $body.Text = ("$snippet`n`nRATIONALE: $rationale`n`n" +
                  "(close this window to acknowledge, or hit Alt+Tab back to study)")
    $body.Font = New-Object System.Drawing.Font("Segoe UI", 14)
    $body.Dock = [System.Windows.Forms.DockStyle]::Fill
    $body.TextAlign = [System.Drawing.ContentAlignment]::MiddleCenter
    $body.Padding = New-Object System.Windows.Forms.Padding(20)
    $form.Controls.Add($body)

    $button = New-Object System.Windows.Forms.Button
    $button.Text = "I'm getting back to it"
    $button.Dock = [System.Windows.Forms.DockStyle]::Bottom
    $button.Height = 60
    $button.Font = New-Object System.Drawing.Font("Segoe UI", 14, [System.Drawing.FontStyle]::Bold)
    $button.BackColor = [System.Drawing.Color]::White
    $button.ForeColor = [System.Drawing.Color]::Black
    $button.Add_Click({ $form.Close() })
    $form.Controls.Add($button)

    $form.Add_Shown({ $form.Activate() })
    [void]$form.ShowDialog()
    Write-Log "modal dismissed: snippet=$($snippet.Substring(0, [Math]::Min(80, $snippet.Length)))"
}

function Test-Drift($sig) {
    if (-not $sig) { return $false }
    if ($sig.kind -eq "drift_alert") { return $true }
    if ($sig.rationale -and $sig.rationale.StartsWith("drift:")) { return $true }
    return $false
}

function Poll-And-Fire {
    $token = Get-Token
    if (-not $token) { return }
    $since = Get-LastSeen
    try {
        $url = "$ApiBase/api/signals?since=$since&limit=20"
        $resp = Invoke-RestMethod -Uri $url -Headers @{ Authorization = "Bearer $token" } `
                                  -TimeoutSec 15 -ErrorAction Stop
    } catch {
        Write-Log "poll failed: $_"
        return
    }
    if (-not $resp) { return }
    $signals = if ($resp -is [array]) { $resp } else { @($resp) }
    if ($signals.Count -eq 0) { return }
    $maxTs = $since
    foreach ($sig in $signals) {
        if ($sig.ts -gt $maxTs) { $maxTs = $sig.ts }
        if (Test-Drift $sig) {
            Write-Log "drift fired: id=$($sig.id) kind=$($sig.kind) rat=$($sig.rationale)"
            Show-DriftModal $sig.snippet $sig.rationale
        }
    }
    Set-LastSeen $maxTs
}

if ($Test) {
    Write-Log "TEST mode: forcing drift modal"
    Show-DriftModal `
        "Off-track example: scheduled PA but you're in Magic Garden / Discord." `
        "drift: scheduled=PA, actual=browser-game, cooldown=elapsed"
    exit 0
}

if ($Loop) {
    Write-Log "loop start (interval 60s, base $ApiBase)"
    while ($true) {
        Poll-And-Fire
        Start-Sleep -Seconds 60
    }
} else {
    Poll-And-Fire
}
