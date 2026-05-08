# tools/block-enforcer.ps1 - PC-side drift interrupter.
#
# Polls https://corgflix.duckdns.org/api/signals every 60s. On any new
# signal whose rationale starts with "drift:" OR kind in {"drift_alert"},
# pops a blocking always-on-top red dialog + plays alert beeps. The user
# must dismiss it explicitly - the dialog will not auto-close.
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

function Get-OpenPathFromSnippet($snippet) {
    # DriftDirective format: "... OPEN: <path>. Chat: ..."
    # Parse the path between "OPEN: " and the next ". " or end-of-string.
    if ($snippet -match 'OPEN:\s*([^\.\n]+(?:\.[a-zA-Z0-9]+)?)') {
        $rel = $matches[1].Trim().TrimEnd('.')
        # Resolve to a real file: try archival (where Kotlin emits paths
        # relative to /opt/jarvis/data/archival), then absolute Desktop
        # mirror, then the relative path itself.
        $candidates = @(
            "C:\Users\User\Desktop\Second brain\$rel",
            "C:\Users\User\Desktop\SO\$rel",
            $rel
        )
        foreach ($c in $candidates) {
            if (Test-Path $c) { return (Resolve-Path $c).Path }
        }
        return $rel  # return raw - let Process.Start try
    }
    return $null
}

function Get-LessonHintFromSnippet($snippet) {
    if ($snippet -match '\[\[lesson:\s*([^\]]+)\]\]') {
        return "[[lesson: $($matches[1].Trim())]]"
    }
    return $null
}

function Show-DriftModal($snippet, $rationale) {
    # Three rapid beeps to wake the user from whatever flow they're in.
    for ($i = 0; $i -lt 3; $i++) {
        [System.Console]::Beep(1500, 250)
        Start-Sleep -Milliseconds 100
    }
    $openPath = Get-OpenPathFromSnippet $snippet
    $lessonHint = Get-LessonHintFromSnippet $snippet

    $form = New-Object System.Windows.Forms.Form
    $form.Text = "JARVIS DRIFT ALERT"
    $form.BackColor = [System.Drawing.Color]::FromArgb(192, 0, 0)
    $form.ForeColor = [System.Drawing.Color]::White
    $form.TopMost = $true
    $form.StartPosition = [System.Windows.Forms.FormStartPosition]::CenterScreen
    $form.Width = 900
    $form.Height = 540
    $form.FormBorderStyle = [System.Windows.Forms.FormBorderStyle]::FixedDialog
    $form.MinimizeBox = $false
    $form.MaximizeBox = $false
    $form.ShowInTaskbar = $true

    $title = New-Object System.Windows.Forms.Label
    $title.Text = "OFF TRACK"
    $title.Font = New-Object System.Drawing.Font("Segoe UI", 36, [System.Drawing.FontStyle]::Bold)
    $title.Dock = [System.Windows.Forms.DockStyle]::Top
    $title.TextAlign = [System.Drawing.ContentAlignment]::MiddleCenter
    $title.Height = 80
    $form.Controls.Add($title)

    $body = New-Object System.Windows.Forms.Label
    $body.Text = "$snippet`n`nRATIONALE: $rationale"
    $body.Font = New-Object System.Drawing.Font("Segoe UI", 14)
    $body.Dock = [System.Windows.Forms.DockStyle]::Fill
    $body.TextAlign = [System.Drawing.ContentAlignment]::MiddleCenter
    $body.Padding = New-Object System.Windows.Forms.Padding(20)
    $form.Controls.Add($body)

    # Bottom button row - actionable surfaces. Layout panel for 3 buttons.
    $buttonPanel = New-Object System.Windows.Forms.TableLayoutPanel
    $buttonPanel.Dock = [System.Windows.Forms.DockStyle]::Bottom
    $buttonPanel.Height = 70
    $buttonPanel.ColumnCount = 3
    $buttonPanel.RowCount = 1
    [void]$buttonPanel.ColumnStyles.Add((New-Object System.Windows.Forms.ColumnStyle([System.Windows.Forms.SizeType]::Percent, 33.34)))
    [void]$buttonPanel.ColumnStyles.Add((New-Object System.Windows.Forms.ColumnStyle([System.Windows.Forms.SizeType]::Percent, 33.33)))
    [void]$buttonPanel.ColumnStyles.Add((New-Object System.Windows.Forms.ColumnStyle([System.Windows.Forms.SizeType]::Percent, 33.33)))

    $openBtn = New-Object System.Windows.Forms.Button
    $openBtn.Text = if ($openPath) { "Open: $(Split-Path -Leaf $openPath)" } else { "(no file ref)" }
    $openBtn.Enabled = ($openPath -ne $null)
    $openBtn.Dock = [System.Windows.Forms.DockStyle]::Fill
    $openBtn.Font = New-Object System.Drawing.Font("Segoe UI", 11, [System.Drawing.FontStyle]::Bold)
    $openBtn.BackColor = [System.Drawing.Color]::White
    $openBtn.ForeColor = [System.Drawing.Color]::Black
    $openBtn.Add_Click({
        if ($openPath) {
            try { Start-Process -FilePath $openPath -ErrorAction Stop }
            catch { Write-Log "open failed for $openPath : $_" }
            $form.Close()
        }
    })

    $copyBtn = New-Object System.Windows.Forms.Button
    $copyBtn.Text = if ($lessonHint) { "Copy: $lessonHint" } else { "(no chat hint)" }
    $copyBtn.Enabled = ($lessonHint -ne $null)
    $copyBtn.Dock = [System.Windows.Forms.DockStyle]::Fill
    $copyBtn.Font = New-Object System.Drawing.Font("Segoe UI", 11, [System.Drawing.FontStyle]::Bold)
    $copyBtn.BackColor = [System.Drawing.Color]::White
    $copyBtn.ForeColor = [System.Drawing.Color]::Black
    $copyBtn.Add_Click({
        if ($lessonHint) {
            Set-Clipboard -Value $lessonHint
            Write-Log "copied lesson hint: $lessonHint"
            $form.Close()
        }
    })

    $closeBtn = New-Object System.Windows.Forms.Button
    $closeBtn.Text = "I'm getting back"
    $closeBtn.Dock = [System.Windows.Forms.DockStyle]::Fill
    $closeBtn.Font = New-Object System.Drawing.Font("Segoe UI", 11, [System.Drawing.FontStyle]::Bold)
    $closeBtn.BackColor = [System.Drawing.Color]::White
    $closeBtn.ForeColor = [System.Drawing.Color]::Black
    $closeBtn.Add_Click({ $form.Close() })

    $buttonPanel.Controls.Add($openBtn, 0, 0)
    $buttonPanel.Controls.Add($copyBtn, 1, 0)
    $buttonPanel.Controls.Add($closeBtn, 2, 0)
    $form.Controls.Add($buttonPanel)

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
    # Realistic snippet - same shape DriftDirective.build emits.
    Show-DriftModal `
        ("GET BACK TO PA. NEXT: Tema 5 dynamic programming (2026-05-15 6d). " +
         "OPEN: Courses/PA/lecture11_ro.pdf. Chat: [[lesson: PA]]") `
        "drift: scheduled=PA, actual=non-study app (chrome.exe), cooldown=elapsed"
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
