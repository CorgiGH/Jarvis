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
# Aesthetic: "Midnight Editorial". Deep midnight blue body, cream text,
# coral->amber gradient stripe down the left edge as a brand signature,
# serif Constantia display for the title, italic Cascadia Mono for the
# status caption. Role labels rendered as small-caps marginalia with
# vertical-bar stripes in role-tinted accent.
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

# ============================================================================
# "Midnight Editorial" palette
# ============================================================================
$colBg       = [System.Drawing.Color]::FromArgb(15, 15, 26)     # #0F0F1A midnight
$colSurface  = [System.Drawing.Color]::FromArgb(22, 22, 38)     # #161626 footer/status
$colInputBg  = [System.Drawing.Color]::FromArgb(28, 28, 46)     # #1C1C2E input
$colBorder   = [System.Drawing.Color]::FromArgb(48, 48, 75)     # #30304B
$colText     = [System.Drawing.Color]::FromArgb(245, 240, 232)  # #F5F0E8 cream
$colTextDim  = [System.Drawing.Color]::FromArgb(195, 190, 182)  # body-dim
$colMuted    = [System.Drawing.Color]::FromArgb(140, 140, 165)  # caption gray
$colCoral    = [System.Drawing.Color]::FromArgb(255, 107, 107)  # #FF6B6B you / send
$colCoralHv  = [System.Drawing.Color]::FromArgb(255, 138, 138)
$colCoralAct = [System.Drawing.Color]::FromArgb(220, 80, 80)
$colCoralDim = [System.Drawing.Color]::FromArgb(60, 255, 107, 107)  # alpha for glow
$colGold     = [System.Drawing.Color]::FromArgb(212, 165, 116)  # #D4A574 jarvis
$colAmber    = [System.Drawing.Color]::FromArgb(255, 217, 61)   # #FFD93D stripe-bottom

# ============================================================================
# Form
# ============================================================================
$form = New-Object System.Windows.Forms.Form
$form.Text = if ($Subject) { "Jarvis Tutor - $Subject$(if ($Concept) { ' / ' + $Concept })" } else { "Jarvis Tutor" }
$form.Width = 1100
$form.Height = 780
$form.MinimumSize = New-Object System.Drawing.Size(720, 480)
$form.StartPosition = [System.Windows.Forms.FormStartPosition]::CenterScreen
$form.BackColor = $colBg
$form.ForeColor = $colText
$form.Padding = New-Object System.Windows.Forms.Padding(0)
$form.DoubleBuffered = $true

# ----------------------------------------------------------------------------
# Left vertical accent stripe - coral->amber gradient. Brand signature.
# ----------------------------------------------------------------------------
$accentStripe = New-Object System.Windows.Forms.Panel
$accentStripe.Dock = [System.Windows.Forms.DockStyle]::Left
$accentStripe.Width = 6
$accentStripe.Add_Paint({
    param($s, $e)
    $rect = New-Object System.Drawing.Rectangle 0, 0, $s.Width, $s.Height
    $brush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        $rect, $script:colCoral, $script:colAmber,
        [System.Drawing.Drawing2D.LinearGradientMode]::Vertical
    )
    $e.Graphics.FillRectangle($brush, $rect)
    $brush.Dispose()
})

# ----------------------------------------------------------------------------
# Header - serif display title + italic subtitle + connected pulse dot
# ----------------------------------------------------------------------------
$header = New-Object System.Windows.Forms.Panel
$header.Dock = [System.Windows.Forms.DockStyle]::Top
$header.Height = 92
$header.BackColor = $colBg
$header.Add_Paint({
    param($s, $e)
    # Bottom hairline divider
    $pen = New-Object System.Drawing.Pen($script:colBorder, 1)
    $e.Graphics.DrawLine($pen, 36, ($s.Height - 1), ($s.Width - 36), ($s.Height - 1))
    $pen.Dispose()
    # Connected pulse dot, right-aligned
    $e.Graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $dotR = 5
    $dotX = $s.Width - 44
    $dotY = 38
    # Outer glow halo
    $glowBrush = New-Object System.Drawing.SolidBrush($script:colCoralDim)
    $e.Graphics.FillEllipse($glowBrush, $dotX - 4, $dotY - 4, ($dotR * 2) + 8, ($dotR * 2) + 8)
    $glowBrush.Dispose()
    # Solid dot
    $dotBrush = New-Object System.Drawing.SolidBrush($script:colCoral)
    $e.Graphics.FillEllipse($dotBrush, $dotX, $dotY, $dotR * 2, $dotR * 2)
    $dotBrush.Dispose()
})

$headerTitle = New-Object System.Windows.Forms.Label
$headerTitle.Text = "JARVIS"
$headerTitle.Font = New-Object System.Drawing.Font("Constantia", 26, [System.Drawing.FontStyle]::Bold)
$headerTitle.ForeColor = $colText
$headerTitle.BackColor = [System.Drawing.Color]::Transparent
$headerTitle.Location = New-Object System.Drawing.Point(36, 18)
$headerTitle.AutoSize = $true

$headerBullet = New-Object System.Windows.Forms.Label
$headerBullet.Text = "/"
$headerBullet.Font = New-Object System.Drawing.Font("Constantia", 18, [System.Drawing.FontStyle]::Italic)
$headerBullet.ForeColor = $colCoral
$headerBullet.BackColor = [System.Drawing.Color]::Transparent
$headerBullet.AutoSize = $true

$headerSub = New-Object System.Windows.Forms.Label
$headerSub.Text = if ($Subject) {
    "tutor . $Subject$(if ($Concept) { '  -  ' + $Concept })"
} else {
    "tutor"
}
$headerSub.Font = New-Object System.Drawing.Font("Segoe UI", 11, [System.Drawing.FontStyle]::Italic)
$headerSub.ForeColor = $colMuted
$headerSub.BackColor = [System.Drawing.Color]::Transparent
$headerSub.AutoSize = $true

# Right-side "online" caption (monospace, small)
$headerOnline = New-Object System.Windows.Forms.Label
$headerOnline.Text = "online"
$headerOnline.Font = New-Object System.Drawing.Font("Cascadia Mono", 8, [System.Drawing.FontStyle]::Italic)
$headerOnline.ForeColor = $colMuted
$headerOnline.BackColor = [System.Drawing.Color]::Transparent
$headerOnline.AutoSize = $true

$header.Controls.Add($headerTitle)
$header.Controls.Add($headerBullet)
$header.Controls.Add($headerSub)
$header.Controls.Add($headerOnline)

# Position bullet + subtitle right after the title baseline; online caption
# right-aligned. Recompute on header resize.
$header.Add_Layout({
    $script:headerTitle.Refresh()
    $titleRight = $script:headerTitle.Left + $script:headerTitle.PreferredWidth
    $script:headerBullet.Left = $titleRight + 14
    $script:headerBullet.Top  = 38
    $script:headerSub.Left    = $script:headerBullet.Left + $script:headerBullet.PreferredWidth + 10
    $script:headerSub.Top     = 44
    $script:headerOnline.Left = $header.Width - 60 - $script:headerOnline.PreferredWidth
    $script:headerOnline.Top  = 42
})

# ----------------------------------------------------------------------------
# Body wrapper with generous side padding (editorial margins)
# ----------------------------------------------------------------------------
$bodyWrap = New-Object System.Windows.Forms.Panel
$bodyWrap.Dock = [System.Windows.Forms.DockStyle]::Fill
$bodyWrap.BackColor = $colBg
$bodyWrap.Padding = New-Object System.Windows.Forms.Padding(36, 24, 36, 0)

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

# ----------------------------------------------------------------------------
# Footer - input panel + custom-painted send button
# ----------------------------------------------------------------------------
$footer = New-Object System.Windows.Forms.Panel
$footer.Dock = [System.Windows.Forms.DockStyle]::Bottom
$footer.Height = 154
$footer.BackColor = $colBg
$footer.Padding = New-Object System.Windows.Forms.Padding(36, 16, 36, 14)
$footer.Add_Paint({
    param($s, $e)
    # Top hairline divider matching header
    $pen = New-Object System.Drawing.Pen($script:colBorder, 1)
    $e.Graphics.DrawLine($pen, 36, 0, ($s.Width - 36), 0)
    $pen.Dispose()
})

$inputRow = New-Object System.Windows.Forms.TableLayoutPanel
$inputRow.Dock = [System.Windows.Forms.DockStyle]::Fill
$inputRow.ColumnCount = 2
$inputRow.RowCount = 1
$inputRow.BackColor = $colBg
[void]$inputRow.ColumnStyles.Add((New-Object System.Windows.Forms.ColumnStyle([System.Windows.Forms.SizeType]::Percent, 100)))
[void]$inputRow.ColumnStyles.Add((New-Object System.Windows.Forms.ColumnStyle([System.Windows.Forms.SizeType]::Absolute, 156)))

# Input panel with custom border paint
$txtInputWrap = New-Object System.Windows.Forms.Panel
$txtInputWrap.Dock = [System.Windows.Forms.DockStyle]::Fill
$txtInputWrap.BackColor = $colInputBg
$txtInputWrap.Margin = New-Object System.Windows.Forms.Padding(0, 0, 16, 0)
$txtInputWrap.Padding = New-Object System.Windows.Forms.Padding(16, 12, 16, 12)
$txtInputWrap.Add_Paint({
    param($s, $e)
    $rect = New-Object System.Drawing.Rectangle 0, 0, ($s.Width - 1), ($s.Height - 1)
    $pen = New-Object System.Drawing.Pen($script:colBorder, 1)
    $e.Graphics.DrawRectangle($pen, $rect)
    $pen.Dispose()
})

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

# Send button - solid coral, white arrow, hover to lighter coral
$sendBtn = New-Object System.Windows.Forms.Button
$sendBtn.Text = "SEND  ->"
$sendBtn.Dock = [System.Windows.Forms.DockStyle]::Fill
$sendBtn.Margin = New-Object System.Windows.Forms.Padding(0)
$sendBtn.Font = New-Object System.Drawing.Font("Segoe UI", 11, [System.Drawing.FontStyle]::Bold)
$sendBtn.BackColor = $colCoral
$sendBtn.ForeColor = $colBg
$sendBtn.FlatStyle = [System.Windows.Forms.FlatStyle]::Flat
$sendBtn.FlatAppearance.BorderSize = 0
$sendBtn.FlatAppearance.MouseOverBackColor = $colCoralHv
$sendBtn.FlatAppearance.MouseDownBackColor = $colCoralAct
$sendBtn.Cursor = [System.Windows.Forms.Cursors]::Hand
$sendBtn.TextAlign = [System.Drawing.ContentAlignment]::MiddleCenter

$inputRow.Controls.Add($txtInputWrap, 0, 0)
$inputRow.Controls.Add($sendBtn, 1, 0)
$footer.Controls.Add($inputRow)

# ----------------------------------------------------------------------------
# Status strip - monospace italic, very dim, sits at bottom edge
# ----------------------------------------------------------------------------
$status = New-Object System.Windows.Forms.Label
$status.Dock = [System.Windows.Forms.DockStyle]::Bottom
$status.Height = 28
$status.Text = "  ready  .  ctrl+enter to send  .  $ApiBase"
$status.Font = New-Object System.Drawing.Font("Cascadia Mono", 8, [System.Drawing.FontStyle]::Italic)
$status.ForeColor = $colMuted
$status.BackColor = $colSurface
$status.TextAlign = [System.Drawing.ContentAlignment]::MiddleLeft
$status.Padding = New-Object System.Windows.Forms.Padding(36, 0, 36, 4)

# ----------------------------------------------------------------------------
# Dock order matters - innermost (last added) wins. Add bottom-most first.
# ----------------------------------------------------------------------------
$form.Controls.Add($accentStripe)   # Dock=Left, takes leftmost 6px
$form.Controls.Add($status)         # Dock=Bottom (outermost bottom)
$form.Controls.Add($footer)         # Dock=Bottom (above status)
$form.Controls.Add($bodyWrap)       # Dock=Fill (middle)
$form.Controls.Add($header)         # Dock=Top

# ============================================================================
# Append helpers - role-tinted small-caps labels with marginalia stripe
# ============================================================================
function script:Append-Turn($who, $text) {
    $headerColor = if ($who -eq "you") { $script:colCoral } else { $script:colGold }
    $label = if ($who -eq "you") { "YOU" } else { "JARVIS" }
    $stripe = [char]0x258E  # ▎ left one-quarter block, looks like marginalia bar
    $script:history.SelectionStart = $script:history.TextLength
    # Stripe + label in role color, bold serif
    $script:history.SelectionFont = New-Object System.Drawing.Font("Constantia", 11, [System.Drawing.FontStyle]::Bold)
    $script:history.SelectionColor = $headerColor
    $script:history.AppendText("$stripe  $label`n")
    # Body in cream sans
    $script:history.SelectionFont = New-Object System.Drawing.Font("Segoe UI", 12)
    $script:history.SelectionColor = $script:colText
    $script:history.AppendText("$text`n`n")
    $script:history.SelectionStart = $script:history.TextLength
    $script:history.ScrollToCaret()
}

function script:Reset-UI() {
    $script:status.Text = "  ready  .  ctrl+enter to send  .  $script:ApiBase"
    $script:sendBtn.Enabled = $true
    $script:txtInput.Enabled = $true
    try { $script:txtInput.Focus() } catch {}
}

function script:Send-Turn($msg) {
    if ([string]::IsNullOrWhiteSpace($msg)) { return }
    Append-Turn "you" $msg
    $script:status.Text = "  thinking  .  $script:ApiBase"
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
            # header lacks an explicit charset, mojibaking Romanian chars
            # + math symbols. Forcing UTF-8 on the raw byte stream keeps
            # text faithful end-to-end.
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

    if (-not $script:activeTurns) { $script:activeTurns = @() }
    $turn = @{ ps = $ps; rs = $rs; async = $async; timer = $null }
    $timer = New-Object System.Windows.Forms.Timer
    $timer.Interval = 200
    $turn.timer = $timer
    $script:activeTurns += $turn

    $timer.Add_Tick({
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
        $form.BeginInvoke([Action]{ Send-Turn $prefill }) | Out-Null
    } else {
        Append-Turn "jarvis" "Hi. I'm your jarvis tutor. Type a question, paste a problem, or run [[lesson: SUBJECT]] for a structured walkthrough."
    }
})

[void]$form.ShowDialog()
