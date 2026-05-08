# tools/tutor-window.ps1 - desktop chat window that talks to /api/chat.
#
# Opens a Forms window with conversation history + input box. Sends user
# turns to https://corgflix.duckdns.org/api/chat with the saved auth
# token; renders bot replies inline. Designed to launch from
# block-enforcer.ps1's modal "Tutor me" button so a drift event flows
# directly into an active teaching session for the scheduled subject.
#
# Pre-fills the first turn with [[lesson: SUBJECT]] so the bot opens
# in lesson mode immediately. User can then type follow-up questions,
# attempts, [[lesson_check: ...]], etc.
#
# Usage:
#   powershell -File tutor-window.ps1 -Subject PS
#   powershell -File tutor-window.ps1 -Subject PA -Concept "greedy algorithms"
#   powershell -File tutor-window.ps1   (no prefill; user types from scratch)

param(
    [string]$Subject = "",
    [string]$Concept = "",
    [string]$ApiBase = ""
)

if (-not $ApiBase) { $ApiBase = "https://corgflix.duckdns.org" }
$TokenPath = "C:\Users\User\jarvis-kotlin\tools\AUTH_TOKEN.txt"
$LogPath = "$env:USERPROFILE\.jarvis-tutor.log"

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

function Write-Log($msg) {
    $line = "{0} {1}" -f (Get-Date -Format "o"), $msg
    Add-Content -Path $LogPath -Value $line -ErrorAction SilentlyContinue
}

function Get-Token {
    if (-not (Test-Path $TokenPath)) { return $null }
    return (Get-Content $TokenPath -Raw).Trim()
}

$Token = Get-Token
if (-not $Token) {
    [System.Windows.Forms.MessageBox]::Show(
        "AUTH_TOKEN.txt missing at $TokenPath. Cannot reach jarvis.",
        "Tutor",
        [System.Windows.Forms.MessageBoxButtons]::OK,
        [System.Windows.Forms.MessageBoxIcon]::Error
    ) | Out-Null
    exit 1
}

# Form
$form = New-Object System.Windows.Forms.Form
$form.Text = if ($Subject) { "Jarvis Tutor - $Subject$(if ($Concept) { " / $Concept" })" } else { "Jarvis Tutor" }
$form.Width = 1100
$form.Height = 750
$form.StartPosition = [System.Windows.Forms.FormStartPosition]::CenterScreen
$form.BackColor = [System.Drawing.Color]::FromArgb(30, 30, 35)
$form.ForeColor = [System.Drawing.Color]::WhiteSmoke

# History (RichTextBox - read-only, scrollable, supports formatting)
$history = New-Object System.Windows.Forms.RichTextBox
$history.Dock = [System.Windows.Forms.DockStyle]::Fill
$history.ReadOnly = $true
$history.BackColor = [System.Drawing.Color]::FromArgb(20, 20, 24)
$history.ForeColor = [System.Drawing.Color]::WhiteSmoke
$history.Font = New-Object System.Drawing.Font("Consolas", 11)
$history.WordWrap = $true
$history.DetectUrls = $true
$history.BorderStyle = [System.Windows.Forms.BorderStyle]::None

# Bottom panel: input + send button
$bottomPanel = New-Object System.Windows.Forms.TableLayoutPanel
$bottomPanel.Dock = [System.Windows.Forms.DockStyle]::Bottom
$bottomPanel.Height = 90
$bottomPanel.ColumnCount = 2
$bottomPanel.RowCount = 1
[void]$bottomPanel.ColumnStyles.Add((New-Object System.Windows.Forms.ColumnStyle([System.Windows.Forms.SizeType]::Percent, 85)))
[void]$bottomPanel.ColumnStyles.Add((New-Object System.Windows.Forms.ColumnStyle([System.Windows.Forms.SizeType]::Percent, 15)))

$input = New-Object System.Windows.Forms.TextBox
$input.Dock = [System.Windows.Forms.DockStyle]::Fill
$input.Multiline = $true
$input.ScrollBars = [System.Windows.Forms.ScrollBars]::Vertical
$input.AcceptsReturn = $false
$input.BackColor = [System.Drawing.Color]::FromArgb(40, 40, 48)
$input.ForeColor = [System.Drawing.Color]::WhiteSmoke
$input.Font = New-Object System.Drawing.Font("Consolas", 11)
$input.BorderStyle = [System.Windows.Forms.BorderStyle]::FixedSingle

$sendBtn = New-Object System.Windows.Forms.Button
$sendBtn.Text = "Send (Ctrl+Enter)"
$sendBtn.Dock = [System.Windows.Forms.DockStyle]::Fill
$sendBtn.Font = New-Object System.Drawing.Font("Segoe UI", 11, [System.Drawing.FontStyle]::Bold)
$sendBtn.BackColor = [System.Drawing.Color]::FromArgb(60, 130, 200)
$sendBtn.ForeColor = [System.Drawing.Color]::White
$sendBtn.FlatStyle = [System.Windows.Forms.FlatStyle]::Flat

$bottomPanel.Controls.Add($input, 0, 0)
$bottomPanel.Controls.Add($sendBtn, 1, 0)

$form.Controls.Add($history)
$form.Controls.Add($bottomPanel)

# Status strip (top): show "thinking..." while waiting for bot.
$status = New-Object System.Windows.Forms.Label
$status.Dock = [System.Windows.Forms.DockStyle]::Top
$status.Height = 24
$status.Text = "Connected: $ApiBase"
$status.Font = New-Object System.Drawing.Font("Segoe UI", 9)
$status.ForeColor = [System.Drawing.Color]::Gray
$status.TextAlign = [System.Drawing.ContentAlignment]::MiddleLeft
$status.Padding = New-Object System.Windows.Forms.Padding(8, 0, 0, 0)
$form.Controls.Add($status)

# Append helpers - bold labels for "you" / "jarvis", wrapped body.
function Append-Turn($who, $text) {
    $color = if ($who -eq "you") {
        [System.Drawing.Color]::FromArgb(255, 200, 100)
    } else {
        [System.Drawing.Color]::FromArgb(120, 220, 160)
    }
    $history.SelectionStart = $history.TextLength
    $history.SelectionFont = New-Object System.Drawing.Font("Consolas", 11, [System.Drawing.FontStyle]::Bold)
    $history.SelectionColor = $color
    $history.AppendText("[$who]`n")
    $history.SelectionFont = New-Object System.Drawing.Font("Consolas", 11)
    $history.SelectionColor = [System.Drawing.Color]::WhiteSmoke
    $history.AppendText("$text`n`n")
    $history.SelectionStart = $history.TextLength
    $history.ScrollToCaret()
}

function Send-Turn($msg) {
    if ([string]::IsNullOrWhiteSpace($msg)) { return }
    Append-Turn "you" $msg
    $status.Text = "thinking..."
    $sendBtn.Enabled = $false
    $input.Enabled = $false
    $form.Refresh()
    try {
        $body = @{ msg = $msg } | ConvertTo-Json -Compress
        $resp = Invoke-RestMethod -Uri "$ApiBase/api/chat" `
            -Method Post `
            -Headers @{
                Authorization = "Bearer $Token"
                "Content-Type" = "application/json"
            } `
            -Body $body `
            -TimeoutSec 180 `
            -ErrorAction Stop
        $reply = $resp.reply
        if ([string]::IsNullOrWhiteSpace($reply)) { $reply = "(empty reply)" }
        Append-Turn "jarvis" $reply
        Write-Log "turn ok: msg=$($msg.Substring(0,[Math]::Min(80,$msg.Length))) reply=$($reply.Substring(0,[Math]::Min(80,$reply.Length)))"
    } catch {
        $err = "ERROR: $_"
        Append-Turn "jarvis" $err
        Write-Log "turn err: $_"
    } finally {
        $status.Text = "Connected: $ApiBase"
        $sendBtn.Enabled = $true
        $input.Enabled = $true
        $input.Clear()
        $input.Focus()
    }
}

$sendBtn.Add_Click({ Send-Turn $input.Text })

# Ctrl+Enter sends; plain Enter inserts newline (multiline).
$input.Add_KeyDown({
    param($s, $e)
    if ($e.Control -and $e.KeyCode -eq [System.Windows.Forms.Keys]::Enter) {
        Send-Turn $input.Text
        $e.SuppressKeyPress = $true
    }
})

# Open with prefill turn if subject given.
$form.Add_Shown({
    $form.Activate()
    $input.Focus()
    if ($Subject) {
        $prefill = if ($Concept) {
            "[[lesson: $Subject/$Concept]]"
        } else {
            "[[lesson: $Subject]]"
        }
        Append-Turn "you" $prefill
        # Fire send synchronously after first paint.
        $form.BeginInvoke([Action]{ Send-Turn $prefill }) | Out-Null
    } else {
        Append-Turn "jarvis" "Hi. I am your jarvis tutor. Type a question, paste a problem, or run [[lesson: SUBJECT]] to start a structured walkthrough."
    }
})

[void]$form.ShowDialog()
