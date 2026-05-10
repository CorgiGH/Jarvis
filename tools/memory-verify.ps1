#!/usr/bin/env pwsh
# Memory sanity-check runner.
# Walks ~/.claude/projects/<sanitized-cwd>/memory/*.md, parses frontmatter,
# runs `verify:` cmds, writes report to <memory>/.verify-report.md.
# Spec: docs/superpowers/specs/2026-05-10-memory-sanity-check-design.md

$ErrorActionPreference = 'Stop'

function Parse-MemoryFrontmatter {
    param([string]$Path)

    if (-not (Test-Path $Path)) {
        return @{ name = ''; description = ''; type = ''; verify = @(); last_verified_at = $null; freshness_window_hours = $null }
    }

    $lines = Get-Content -LiteralPath $Path -Encoding UTF8

    # Find the bounded ^---$ block at top of file.
    if ($lines.Count -lt 2 -or $lines[0] -ne '---') {
        return @{ name = ''; description = ''; type = ''; verify = @(); last_verified_at = $null; freshness_window_hours = $null }
    }

    $endIdx = -1
    for ($i = 1; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -eq '---') { $endIdx = $i; break }
    }
    if ($endIdx -lt 0) {
        return @{ name = ''; description = ''; type = ''; verify = @(); last_verified_at = $null; freshness_window_hours = $null }
    }

    $body = $lines[1..($endIdx - 1)]

    $name = ''
    $description = ''
    $type = ''
    $verify = @()
    $lastVerified = $null
    $freshnessHours = $null

    $inVerify = $false
    $current = $null

    foreach ($raw in $body) {
        $line = $raw.TrimEnd()
        if ($line -match '^name:\s*(.+)$') { $name = $Matches[1].Trim().Trim("'`""); $inVerify = $false; continue }
        if ($line -match '^description:\s*(.+)$') { $description = $Matches[1].Trim().Trim("'`""); $inVerify = $false; continue }
        if ($line -match '^type:\s*(.+)$') { $type = $Matches[1].Trim().Trim("'`""); $inVerify = $false; continue }
        if ($line -match '^last_verified_at:\s*(.+)$') { $lastVerified = $Matches[1].Trim().Trim("'`""); $inVerify = $false; continue }
        if ($line -match '^freshness_window_hours:\s*(\d+)$') { $freshnessHours = [int]$Matches[1]; $inVerify = $false; continue }
        if ($line -match '^verify:\s*$') { $inVerify = $true; continue }
        if (-not $inVerify) { continue }

        # In verify: block. New entry starts with "  - cmd: ..."
        if ($line -match '^\s*-\s*cmd:\s*(.+)$') {
            if ($current -ne $null) { $verify += $current }
            $current = @{ cmd = $Matches[1].Trim().Trim("'`""); expect_match = $null; expect_no_match = $null; expect_exit = 0; on_fail = '' }
            continue
        }
        if ($line -match '^\s+expect_match:\s*(.+)$' -and $current -ne $null) {
            $current.expect_match = $Matches[1].Trim().Trim("'`""); continue
        }
        if ($line -match '^\s+expect_no_match:\s*(.+)$' -and $current -ne $null) {
            $current.expect_no_match = $Matches[1].Trim().Trim("'`""); continue
        }
        if ($line -match '^\s+expect_exit:\s*(\d+)$' -and $current -ne $null) {
            $current.expect_exit = [int]$Matches[1]; continue
        }
        if ($line -match '^\s+on_fail:\s*(.+)$' -and $current -ne $null) {
            $current.on_fail = $Matches[1].Trim().Trim("'`""); continue
        }
    }
    if ($current -ne $null) { $verify += $current }

    return @{
        name = $name
        description = $description
        type = $type
        verify = $verify
        last_verified_at = $lastVerified
        freshness_window_hours = $freshnessHours
    }
}

function Invoke-MemoryVerify {
    param([string]$Path)

    $parsed = Parse-MemoryFrontmatter -Path $Path
    $result = @{
        path = $Path
        name = $parsed.name
        status = 'OK'
        reasons = @()
        ranCount = 0
    }

    # No verify block + no freshness gate => trusted.
    if ($parsed.verify.Count -eq 0 -and $null -eq $parsed.freshness_window_hours) {
        return $result
    }

    foreach ($entry in $parsed.verify) {
        $result.ranCount++
        $stdout = ''
        $exitCode = 0

        try {
            $stdout = pwsh -NoProfile -Command $entry.cmd 2>&1 | Out-String
            $exitCode = $LASTEXITCODE
            if ($null -eq $exitCode) { $exitCode = 0 }
        } catch {
            $result.status = 'ERROR'
            $result.reasons += "cmd '$($entry.cmd)' threw: $($_.Exception.Message). on_fail: $($entry.on_fail)"
            continue
        }

        # Differentiate ERROR (cmd-not-found / exit != expected) from STALE (cmd ok, output mismatch).
        if ($exitCode -ne $entry.expect_exit -and $exitCode -ne 0) {
            $result.status = 'ERROR'
            $result.reasons += "cmd '$($entry.cmd)' exit=$exitCode (expected $($entry.expect_exit)). on_fail: $($entry.on_fail)"
            continue
        }

        $stdoutTrimmed = $stdout.Trim()

        if ($null -ne $entry.expect_match) {
            if ($stdoutTrimmed -notmatch $entry.expect_match) {
                if ($result.status -eq 'OK') { $result.status = 'STALE' }
                $result.reasons += "cmd '$($entry.cmd)' output '$stdoutTrimmed' does NOT match '$($entry.expect_match)'. on_fail: $($entry.on_fail)"
            }
        }
        if ($null -ne $entry.expect_no_match) {
            if ($stdoutTrimmed -match $entry.expect_no_match) {
                if ($result.status -eq 'OK') { $result.status = 'STALE' }
                $result.reasons += "cmd '$($entry.cmd)' output '$stdoutTrimmed' matches forbidden pattern '$($entry.expect_no_match)'. on_fail: $($entry.on_fail)"
            }
        }
    }

    # Freshness window check.
    if ($null -ne $parsed.freshness_window_hours -and $parsed.last_verified_at) {
        try {
            $lastTs = [DateTime]::Parse($parsed.last_verified_at)
            $hoursSince = ([DateTime]::UtcNow - $lastTs.ToUniversalTime()).TotalHours
            if ($hoursSince -gt $parsed.freshness_window_hours -and $result.status -eq 'OK') {
                $result.status = 'STALE'
                $result.reasons += "last_verified_at $($parsed.last_verified_at) exceeds freshness_window_hours $($parsed.freshness_window_hours)"
            }
        } catch { }
    }

    return $result
}

function Invoke-MemoryVerifyDirectory {
    param(
        [string]$MemoryDir,
        [string]$ReportPath
    )

    if (-not (Test-Path $MemoryDir)) {
        Set-Content -LiteralPath $ReportPath -Value "# Verify report — memory dir not found: $MemoryDir`n" -Encoding UTF8
        return
    }

    $files = Get-ChildItem -LiteralPath $MemoryDir -Filter '*.md' -File |
        Where-Object { $_.Name -ne 'MEMORY.md' -and $_.Name -ne 'BRIDGE.md' -and -not $_.Name.StartsWith('.') } |
        Sort-Object Name

    $now = [DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ')
    $sb = New-Object System.Text.StringBuilder
    [void]$sb.AppendLine("# Verify report — $now")
    [void]$sb.AppendLine('')

    foreach ($f in $files) {
        $r = Invoke-MemoryVerify -Path $f.FullName
        $tag = "[$($r.status)]"
        if ($r.ranCount -eq 0) {
            [void]$sb.AppendLine("$tag $($f.Name) (no verify; trusted)")
        } else {
            [void]$sb.AppendLine("$tag $($f.Name) ($($r.ranCount) verify)")
        }
        foreach ($reason in $r.reasons) {
            [void]$sb.AppendLine("    reason: $reason")
        }
    }

    Set-Content -LiteralPath $ReportPath -Value $sb.ToString() -Encoding UTF8
}

# Runner block — skipped in test mode so the test harness can dot-source us.
if ($env:MEMORY_VERIFY_TEST_MODE -ne '1') {
    # Resolve memory dir.
    $memoryDir = $env:CLAUDE_MEMORY_DIR
    if (-not $memoryDir) {
        $cwd = (Get-Location).Path
        $sanitized = $cwd -replace '[\\:]', '-'
        $memoryDir = Join-Path $env:USERPROFILE ".claude/projects/$sanitized/memory"
    }
    if (-not (Test-Path $memoryDir)) {
        Write-Host "[memory-verify] memory dir not found: $memoryDir (skipping)"
        exit 0
    }
    $reportPath = Join-Path $memoryDir '.verify-report.md'
    Invoke-MemoryVerifyDirectory -MemoryDir $memoryDir -ReportPath $reportPath
    Write-Host "[memory-verify] report written to $reportPath"
    exit 0
}
