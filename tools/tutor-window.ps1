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

# Color palette (GitHub-dark-ish for low-eye-strain reading)
$colBg       = [System.Drawing.Color]::FromArgb(13, 17, 23)    # body bg
$colSurface  = [System.Drawing.Color]::FromArgb(22, 27, 34)    # header/footer surfaces
$colInputBg  = [System.Drawing.Color]::FromArgb(33, 38, 45)    # input box bg
$colBorder   = [System.Drawing.Color]::FromArgb(48, 54, 61)
$colText     = [System.Drawing.Color]::FromArgb(201, 209, 217) # body text
$colMuted    = [System.Drawing.Color]::FromArgb(139, 148, 158) # secondary
$colAccentYou = [System.Drawing.Color]::FromArgb(88, 166, 255)  # blue, you
$colAccentBot = [System.Drawing.Color]::FromArgb(126, 231, 135) # green, bot
$colSendBg   = [System.Drawing.Color]::FromArgb(35, 134, 54)
$colSendHv   = [System.Drawing.Color]::FromArgb(46, 160, 67)

# Form
$form = New-Object System.Windows.Forms.Form
$form.Text = if ($Subject) { "Jarvis Tutor - $Subject$(if ($Concept) { " / $Concept" })" } else { "Jarvis Tutor" }
$form.Width = 1100
$form.Height = 780
$form.MinimumSize = New-Object System.Drawing.Size(720, 480)
$form.StartPosition = [System.Windows.Forms.FormStartPosition]::CenterScreen
$form.BackColor = $colBg
$form.ForeColor = $colText
$form.Padding = New-Object System.Windows.Forms.Padding(0)

# Header bar
$header = New-Object System.Windows.Forms.Panel
$header.Dock = [System.Windows.Forms.DockStyle]::Top
$header.Height = 56
$header.BackColor = $colSurface
$header.Padding = New-Object System.Windows.Forms.Padding(20, 8, 20, 8)

$headerTitle = New-Object System.Windows.Forms.Label
$headerTitle.Dock = [System.Windows.Forms.DockStyle]::Fill
$headerTitle.Text = if ($Subject) {
    "Jarvis Tutor  -  $Subject$(if ($Concept) { ' / ' + $Concept })"
} else {
    "Jarvis Tutor"
}
$headerTitle.Font = New-Object System.Drawing.Font("Segoe UI Semibold", 14)
$headerTitle.ForeColor = $colText
$headerTitle.TextAlign = [System.Drawing.ContentAlignment]::MiddleLeft
$headerTitle.AutoSize = $false

$header.Controls.Add($headerTitle)
$form.Controls.Add($header)

# Body wrapper with side padding
$bodyWrap = New-Object System.Windows.Forms.Panel
$bodyWrap.Dock = [System.Windows.Forms.DockStyle]::Fill
$bodyWrap.BackColor = $colBg
$bodyWrap.Padding = New-Object System.Windows.Forms.Padding(20, 12, 20, 0)

# History (RichTextBox - read-only, scrollable, supports formatting)
$history = New-Object System.Windows.Forms.RichTextBox
$history.Dock = [System.Windows.Forms.DockStyle]::Fill
$history.ReadOnly = $true
$history.BackColor = $colBg
$history.ForeColor = $colText
$history.Font = New-Object System.Drawing.Font("Segoe UI", 12)
$history.WordWrap = $true
$history.DetectUrls = $true
$history.BorderStyle = [System.Windows.Forms.BorderStyle]::None

$bodyWrap.Controls.Add($history)

# Footer with input + send button
$footer = New-Object System.Windows.Forms.Panel
$footer.Dock = [System.Windows.Forms.DockStyle]::Bottom
$footer.Height = 130
$footer.BackColor = $colSurface
$footer.Padding = New-Object System.Windows.Forms.Padding(20, 14, 20, 14)

# Inner table for input (85%) + send (15%)
$bottomPanel = New-Object System.Windows.Forms.TableLayoutPanel
$bottomPanel.Dock = [System.Windows.Forms.DockStyle]::Fill
$bottomPanel.ColumnCount = 2
$bottomPanel.RowCount = 1
$bottomPanel.BackColor = $colSurface
[void]$bottomPanel.ColumnStyles.Add((New-Object System.Windows.Forms.ColumnStyle([System.Windows.Forms.SizeType]::Percent, 80)))
[void]$bottomPanel.ColumnStyles.Add((New-Object System.Windows.Forms.ColumnStyle([System.Windows.Forms.SizeType]::Percent, 20)))

$txtInputWrap = New-Object System.Windows.Forms.Panel
$txtInputWrap.Dock = [System.Windows.Forms.DockStyle]::Fill
$txtInputWrap.BackColor = $colBorder
$txtInputWrap.Padding = New-Object System.Windows.Forms.Padding(1)
$txtInputWrap.Margin = New-Object System.Windows.Forms.Padding(0, 0, 12, 0)

$txtInput = New-Object System.Windows.Forms.TextBox
$txtInput.Dock = [System.Windows.Forms.DockStyle]::Fill
$txtInput.Multiline = $true
$txtInput.ScrollBars = [System.Windows.Forms.ScrollBars]::Vertical
$txtInput.AcceptsReturn = $false
$txtInput.BackColor = $colInputBg
$txtInput.ForeColor = $colText
$txtInput.Font = New-Object System.Drawing.Font("Segoe UI", 12)
$txtInput.BorderStyle = [System.Windows.Forms.BorderStyle]::None
$txtInputWrap.Controls.Add($txtInput)

$sendBtn = New-Object System.Windows.Forms.Button
$sendBtn.Text = "Send"
$sendBtn.Dock = [System.Windows.Forms.DockStyle]::Fill
$sendBtn.Font = New-Object System.Drawing.Font("Segoe UI Semibold", 12)
$sendBtn.BackColor = $colSendBg
$sendBtn.ForeColor = [System.Drawing.Color]::White
$sendBtn.FlatStyle = [System.Windows.Forms.FlatStyle]::Flat
$sendBtn.FlatAppearance.BorderSize = 0
$sendBtn.FlatAppearance.MouseOverBackColor = $colSendHv
$sendBtn.Cursor = [System.Windows.Forms.Cursors]::Hand
$sendBtn.Margin = New-Object System.Windows.Forms.Padding(0)

$bottomPanel.Controls.Add($txtInputWrap, 0, 0)
$bottomPanel.Controls.Add($sendBtn, 1, 0)
$footer.Controls.Add($bottomPanel)

# Status strip below footer (one-liner, italic muted)
$status = New-Object System.Windows.Forms.Label
$status.Dock = [System.Windows.Forms.DockStyle]::Bottom
$status.Height = 22
$status.Text = "Ctrl+Enter to send  -  $ApiBase"
$status.Font = New-Object System.Drawing.Font("Segoe UI", 9, [System.Drawing.FontStyle]::Italic)
$status.ForeColor = $colMuted
$status.BackColor = $colSurface
$status.TextAlign = [System.Drawing.ContentAlignment]::MiddleLeft
$status.Padding = New-Object System.Windows.Forms.Padding(20, 0, 20, 4)

# Order matters for docking: bottom-most first
$form.Controls.Add($status)
$form.Controls.Add($footer)
$form.Controls.Add($bodyWrap)

# Append helpers - bold labels for "you" / "jarvis", wrapped body.
function script:Append-Turn($who, $text) {
    $headerColor = if ($who -eq "you") { $script:colAccentYou } else { $script:colAccentBot }
    $label = if ($who -eq "you") { "you" } else { "jarvis" }
    $script:history.SelectionStart = $script:history.TextLength
    $script:history.SelectionFont = New-Object System.Drawing.Font("Segoe UI Semibold", 11)
    $script:history.SelectionColor = $headerColor
    $script:history.AppendText("$label`n")
    $script:history.SelectionFont = New-Object System.Drawing.Font("Segoe UI", 12)
    $script:history.SelectionColor = $script:colText
    $script:history.AppendText("$text`n`n")
    $script:history.SelectionStart = $script:history.TextLength
    $script:history.ScrollToCaret()
}

function script:Reset-UI() {
    $script:status.Text = "Ctrl+Enter to send  -  $script:ApiBase"
    $script:sendBtn.Enabled = $true
    $script:txtInput.Enabled = $true
    try { $script:txtInput.Focus() } catch {}
}

function script:Send-Turn($msg) {
    if ([string]::IsNullOrWhiteSpace($msg)) { return }
    Append-Turn "you" $msg
    $script:status.Text = "thinking..."
    $script:sendBtn.Enabled = $false
    $script:txtInput.Enabled = $false
    $script:txtInput.Clear()

    $rs = [runspacefactory]::CreateRunspace()
    $rs.Open()
    $rs.SessionStateProxy.SetVariable("ApiBase", $script:ApiBase)
    $rs.SessionStateProxy.SetVariable("Token", $script:Token)
    $rs.SessionStateProxy.SetVariable("msg", $msg)
    $ps = [powershell]::Create()
    $ps.Runspace = $rs
    [void]$ps.AddScript({
        try {
            $body = @{ msg = $msg } | ConvertTo-Json -Compress
            # Use Invoke-WebRequest + manual UTF-8 decode. Invoke-RestMethod
            # defaults the response decoder to cp1252 when the Content-Type
            # header lacks an explicit charset, mojibaking Romanian (Î¼, Â·,
            # â) + math symbols (greek letters, em-dash). Forcing UTF-8 on
            # the raw byte stream keeps text faithful end-to-end.
            $resp = Invoke-WebRequest -Uri "$ApiBase/api/chat" `
                -Method Post `
                -Headers @{
                    Authorization = "Bearer $Token"
                    "Content-Type" = "application/json; charset=utf-8"
                } `
                -Body ([System.Text.Encoding]::UTF8.GetBytes($body)) `
                -TimeoutSec 180 `
                -UseBasicParsing `
                -ErrorAction Stop
            $bytes = if ($resp.RawContentStream) {
                $resp.RawContentStream.ToArray()
            } else {
                [System.Text.Encoding]::UTF8.GetBytes($resp.Content)
            }
            $jsonText = [System.Text.Encoding]::UTF8.GetString($bytes)
            $obj = $jsonText | ConvertFrom-Json
            return @{ ok = $true; reply = $obj.reply }
        } catch {
            return @{ ok = $false; err = "$_" }
        }
    })
    $async = $ps.BeginInvoke()

    # Persist timer + runspace handles in script scope so they survive
    # past Send-Turn return + so we can defensively kill them if something
    # goes wrong.
    if (-not $script:activeTurns) { $script:activeTurns = @() }
    $turn = @{ ps = $ps; rs = $rs; async = $async; timer = $null }
    $timer = New-Object System.Windows.Forms.Timer
    $timer.Interval = 200
    $turn.timer = $timer
    $script:activeTurns += $turn

    $timer.Add_Tick({
        # Find the turn matching this timer (closure-free for scope safety).
        $myTimer = $this
        $turn = $null
        foreach ($t in $script:activeTurns) {
            if ($t.timer -eq $myTimer) { $turn = $t; break }
        }
        if (-not $turn) { $myTimer.Stop(); $myTimer.Dispose(); return }
        if (-not $turn.async.IsCompleted) { return }
        $myTimer.Stop()
        $reply = $null
        $err = $null
        try {
            $result = $turn.ps.EndInvoke($turn.async) | Select-Object -First 1
            if ($result.ok) { $reply = $result.reply } else { $err = $result.err }
        } catch {
            $err = "$_"
        }
        try { $turn.ps.Dispose() } catch {}
        try { $turn.rs.Close() } catch {}
        $script:activeTurns = @($script:activeTurns | Where-Object { $_.timer -ne $myTimer })
        try { $myTimer.Dispose() } catch {}

        if ($null -ne $reply) {
            $r = if ([string]::IsNullOrWhiteSpace($reply)) { "(empty reply)" } else { $reply }
            Append-Turn "jarvis" $r
            Write-Log "turn ok: reply len=$($r.Length)"
        } else {
            Append-Turn "jarvis" "ERROR: $err"
            Write-Log "turn err: $err"
        }
        Reset-UI
    })
    $timer.Start()
}

$sendBtn.Add_Click({
    $msg = $txtInput.Text
    Write-Log "send button clicked, msg len=$($msg.Length)"
    Send-Turn $msg
}.GetNewClosure())

# Ctrl+Enter sends; plain Enter inserts newline (multiline).
$txtInput.Add_KeyDown({
    param($s, $e)
    if ($e.Control -and $e.KeyCode -eq [System.Windows.Forms.Keys]::Enter) {
        Send-Turn $txtInput.Text
        $e.SuppressKeyPress = $true
    }
}.GetNewClosure())

# Open with prefill turn if subject given.
$form.Add_Shown({
    $form.Activate()
    $txtInput.Focus()
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
