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

# Runner block — skipped in test mode so the test harness can dot-source us.
if ($env:MEMORY_VERIFY_TEST_MODE -ne '1') {
    Write-Host "memory-verify.ps1: runner not yet implemented (Task 3)"
}
