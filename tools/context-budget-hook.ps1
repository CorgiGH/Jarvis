#!/usr/bin/env pwsh
# context-budget-hook.ps1
# UserPromptSubmit hook: estimates session context usage and nudges a /wrap handoff
# when usage crosses a configurable band.
#
# NEVER auto-runs anything — recommend only.
# FAIL-SILENT: any error exits 0 with no output so it can never break a turn.
#
# TOKEN ESTIMATION METHOD:
#   Reads the session JSONL transcript, extracts text content from user/assistant
#   message entries (type=text blocks, tool_use inputs, tool_result content, and
#   hook attachment content). Sums character lengths and divides by 4 (standard
#   chars-per-token approximation for English/code). This is a LOWER BOUND — it
#   ignores the system prompt, context injections, and CLAUDE.md overhead, which
#   can add 20-40k tokens. The thresholds below are set conservatively to account
#   for this.
#
# THRESHOLD BANDS (calibrated against jarvis-kotlin sessions, 2026-06-15):
#   Band 1 (WARN)  = 50% of EDITABLE_WINDOW  → gentle heads-up
#   Band 2 (HIGH)  = 70% of EDITABLE_WINDOW  → strong nudge
#   Band 3 (CRIT)  = 85% of EDITABLE_WINDOW  → urgent nudge
#
# HYSTERESIS: last-fired band is persisted to .claude/temp/context-budget-state.json
# so the same nudge does not repeat on every turn. A band only re-fires once the
# measured usage rises to the NEXT band.
#
# STATE FILE: .claude/temp/context-budget-state.json
# Stores { "lastBand": <int 0-3>, "sessionId": "<uuid>", "ts": "<iso>" }
# sessionId lets the hook detect a fresh session and reset hysteresis.

#--------------------------------------------------------------------------
# CALIBRATABLE CONSTANTS
#--------------------------------------------------------------------------
# Conservative editable context window (tokens). Adjust downward if you want
# earlier nudges. The actual Claude context window is much larger, but the
# "editable" portion excludes the system prompt, CLAUDE.md injections, and
# the active-constraints.md re-injected every turn (≈20-40k tokens fixed).
$EDITABLE_WINDOW_TOKENS = 200000

# Band thresholds as fractions of EDITABLE_WINDOW_TOKENS
$BAND_THRESHOLDS = @(0.0, 0.50, 0.70, 0.85)  # index 0 = "silent", 1..3 = nudge bands

# State file (relative to project root, inside .claude/temp — gitignored-safe location)
$STATE_FILE_REL = '.claude\temp\context-budget-state.json'
#--------------------------------------------------------------------------

try {
    # ---- 1. Read stdin JSON -----------------------------------------------
    $stdinRaw = [Console]::In.ReadToEnd()
    $stdinObj = $null
    try { $stdinObj = $stdinRaw | ConvertFrom-Json -ErrorAction Stop } catch {}

    # ---- 2. Derive transcript path (3-layer fallback) ----------------------
    # Layer A: transcript_path field in stdin (if CC provides it)
    $transcriptPath = $null
    if ($stdinObj -and $stdinObj.PSObject.Properties['transcript_path']) {
        $candidate = $stdinObj.transcript_path
        if ($candidate -and (Test-Path $candidate)) {
            $transcriptPath = $candidate
        }
    }

    # Layer B: session_id from stdin + known project sessions dir
    if (-not $transcriptPath -and $stdinObj -and $stdinObj.PSObject.Properties['session_id']) {
        $sessionId = $stdinObj.session_id
        if ($sessionId) {
            $cwdRaw = $null
            if ($stdinObj.PSObject.Properties['cwd']) { $cwdRaw = $stdinObj.cwd }
            if (-not $cwdRaw) { $cwdRaw = (Get-Location).Path }
            $sanitizedCwd = $cwdRaw -replace '[\\:]', '-'
            $candidate = Join-Path $env:USERPROFILE ".claude\projects\$sanitizedCwd\$sessionId.jsonl"
            if (Test-Path $candidate) {
                $transcriptPath = $candidate
            }
        }
    }

    # Layer C: newest .jsonl in the project sessions dir (using $PSScriptRoot to derive project root)
    if (-not $transcriptPath) {
        # $PSScriptRoot is reliable even when invoked via pwsh -File; MyInvocation.MyCommand.Path can be empty in pipe context
        $scriptDirC = if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path $MyInvocation.MyCommand.Path -Parent }
        if ($scriptDirC) {
            $projectRootC = Split-Path $scriptDirC -Parent
            $sanitizedCwdC = $projectRootC -replace '[\\:]', '-'
            $sessionsDirC = Join-Path $env:USERPROFILE ".claude\projects\$sanitizedCwdC"
            if (Test-Path $sessionsDirC) {
                $newest = Get-ChildItem $sessionsDirC -Filter '*.jsonl' -File -ErrorAction SilentlyContinue |
                          Sort-Object LastWriteTime -Descending |
                          Select-Object -First 1
                if ($newest) { $transcriptPath = $newest.FullName }
            }
        }
    }

    # Bail if we can't locate the transcript
    if (-not $transcriptPath) { exit 0 }

    # Extract session ID from the path for hysteresis
    $sessionIdFromPath = [System.IO.Path]::GetFileNameWithoutExtension($transcriptPath)

    # ---- 3. Read and estimate tokens ---------------------------------------
    $totalChars = 0
    $lines = [System.IO.File]::ReadAllLines($transcriptPath, [System.Text.Encoding]::UTF8)
    foreach ($line in $lines) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        try {
            $obj = $line | ConvertFrom-Json -ErrorAction Stop
            $t = $obj.type

            if ($t -eq 'user' -or $t -eq 'assistant') {
                $content = $obj.message.content
                if ($content -is [string]) {
                    $totalChars += $content.Length
                } elseif ($content -is [array]) {
                    foreach ($c in $content) {
                        if ($c.type -eq 'text' -and $c.PSObject.Properties['text']) {
                            $totalChars += $c.text.Length
                        } elseif ($c.type -eq 'tool_use' -and $c.PSObject.Properties['input']) {
                            try {
                                $inputStr = $c.input | ConvertTo-Json -Compress -Depth 4 -ErrorAction Stop
                                $totalChars += $inputStr.Length
                            } catch {}
                        } elseif ($c.type -eq 'tool_result') {
                            if ($c.PSObject.Properties['content']) {
                                $rc = $c.content
                                if ($rc -is [string]) {
                                    $totalChars += $rc.Length
                                } elseif ($rc -is [array]) {
                                    foreach ($r in $rc) {
                                        if ($r.PSObject.Properties['text']) { $totalChars += $r.text.Length }
                                    }
                                }
                            }
                        }
                    }
                }
            } elseif ($t -eq 'attachment' -and $obj.PSObject.Properties['attachment']) {
                $att = $obj.attachment
                if ($att.PSObject.Properties['content'] -and $att.content -is [string]) {
                    $totalChars += $att.content.Length
                }
            }
        } catch {}
    }

    $estimatedTokens = [int]($totalChars / 4)
    $usageFraction = $estimatedTokens / $EDITABLE_WINDOW_TOKENS

    # ---- 4. Determine which band we're in ----------------------------------
    $currentBand = 0
    for ($i = $BAND_THRESHOLDS.Count - 1; $i -ge 1; $i--) {
        if ($usageFraction -ge $BAND_THRESHOLDS[$i]) {
            $currentBand = $i
            break
        }
    }

    # ---- 5. Hysteresis: load state, check if we should fire ----------------
    $scriptDir2 = if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path $MyInvocation.MyCommand.Path -Parent }
    $projectRoot2 = if ($scriptDir2) { Split-Path $scriptDir2 -Parent } else { $null }
    $stateFile = if ($projectRoot2) { Join-Path $projectRoot2 $STATE_FILE_REL } else { $null }

    $lastBand = 0
    $lastSession = ''
    if ($stateFile -and (Test-Path $stateFile)) {
        try {
            $state = Get-Content $stateFile -Raw | ConvertFrom-Json -ErrorAction Stop
            if ($state.PSObject.Properties['lastBand']) { $lastBand = [int]$state.lastBand }
            if ($state.PSObject.Properties['sessionId']) { $lastSession = $state.sessionId }
        } catch {}
    }

    # Reset hysteresis on new session
    if ($lastSession -ne $sessionIdFromPath) {
        $lastBand = 0
    }

    # Only fire if we've entered a NEW (higher) band
    $shouldNudge = ($currentBand -gt 0) -and ($currentBand -gt $lastBand)

    if ($shouldNudge) {
        # Save state
        $newState = @{
            lastBand  = $currentBand
            sessionId = $sessionIdFromPath
            ts        = [DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ')
        }
        try {
            if ($stateFile) {
                $stateDir = Split-Path $stateFile -Parent
                if (-not (Test-Path $stateDir)) { New-Item -ItemType Directory $stateDir -Force | Out-Null }
                $newState | ConvertTo-Json -Compress | Set-Content $stateFile -Encoding UTF8 -NoNewline
            }
        } catch {}

        # Build nudge text
        $pct = [int]($usageFraction * 100)
        $bandLabel = @('', 'WARN', 'HIGH', 'CRIT')[$currentBand]
        $winK = [int]($EDITABLE_WINDOW_TOKENS / 1000)
        $nudge = "[CONTEXT-BUDGET $bandLabel ~${pct}% of ${winK}k-token window] " +
                 "Session context is filling. At the next clean boundary (green commit / phase edge), " +
                 "run /wrap and continue in a fresh session to stay in the high-quality zone. " +
                 "Do NOT push past the hard limit — hand off now."

        # Output via additionalContext so it rides into the model context
        @{
            hookSpecificOutput = @{
                hookEventName     = 'UserPromptSubmit'
                additionalContext = $nudge
            }
        } | ConvertTo-Json -Compress
    }

    exit 0

} catch {
    # FAIL-SILENT: never break a turn
    exit 0
}
