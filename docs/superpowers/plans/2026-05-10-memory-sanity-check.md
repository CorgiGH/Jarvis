# Memory Sanity-Check System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a 5-component sanity-check system (verifiable memory frontmatter + cross-platform pre-flight runner + `BRIDGE.md` handoff doc + `/sanity` deep-audit slash command + CLAUDE.md trust-but-verify rule) that catches hallucinations / drift / wrong-identity at session boundaries.

**Architecture:** PowerShell + bash runners parse YAML frontmatter from memory files; SessionStart hook runs the runner before any user message; report file at `<memory>/.verify-report.md` carries `[OK]` / `[STALE]` / `[ERROR]` per file; Claude reads the report + flags stale entries in the first reply per CLAUDE.md trust-but-verify rule. `/sanity` slash command dispatches a fresh sub-agent for deep audit when the runner flags ≥2 stale entries OR user smells drift.

**Tech Stack:** PowerShell 7+ (Windows primary), bash (POSIX mirror for VPS / WSL); no external YAML library — regex over the bounded `^---$` block; Claude Code SessionStart hook + slash command files.

**Spec:** `docs/superpowers/specs/2026-05-10-memory-sanity-check-design.md` (commit `161f058`).

---

## File Structure

**New tools:**
- `tools/memory-verify.ps1` — PowerShell pre-flight runner (Windows)
- `tools/memory-verify.sh` — bash mirror (VPS / WSL)
- `tools/memory-verify-test.ps1` — fixture-based test harness (PowerShell)
- `tools/memory-verify-test.sh` — fixture-based test harness (bash)
- `tools/memory-verify-fixtures/ok.md` — fixture: passes verify
- `tools/memory-verify-fixtures/stale.md` — fixture: verify cmd output mismatches
- `tools/memory-verify-fixtures/error.md` — fixture: verify cmd not-found / errors
- `tools/memory-verify-fixtures/no-verify.md` — fixture: no `verify:` block

**New Claude Code surfaces:**
- `.claude/commands/sanity.md` — slash command for deep audit
- `.claude/commands/wrap.md` — slash command for session-end handoff

**Modified Claude Code config:**
- `.claude/settings.json` — add SessionStart hook (create if absent)
- `C:\Users\User\.claude\CLAUDE.md` — append trust-but-verify rule

**Memory backfill:**
- `C:\Users\User\.claude\projects\C--Users-User-jarvis-kotlin\memory\project_jarvis_overhaul_active.md` — add `verify:` block
- `C:\Users\User\.claude\projects\C--Users-User-jarvis-kotlin\memory\project_jarvis_2026-05-09_session_wrap.md` — add `verify:` block

**Memory bootstrap:**
- `C:\Users\User\.claude\projects\C--Users-User-jarvis-kotlin\memory\BRIDGE.md` — bootstrapped with current state entry

---

## Conventions

- Tests-first per task. Every implementation has a fixture + assertion.
- Commit per task with `feat:` / `test:` / `chore:` prefix.
- Caveman mode in chat / commits / comments stays normal English.
- All paths absolute. PowerShell tests assume `pwsh` on PATH; bash tests assume `bash` + `grep` + `curl`.
- Repo root: `C:\Users\User\jarvis-kotlin`. All `git add` / `git commit` runs from repo root.

---

## Task 1: Bootstrap fixture memory files

**Files:**
- Create: `tools/memory-verify-fixtures/ok.md`
- Create: `tools/memory-verify-fixtures/stale.md`
- Create: `tools/memory-verify-fixtures/error.md`
- Create: `tools/memory-verify-fixtures/no-verify.md`

- [ ] **Step 1: Create `ok.md` fixture (verify passes)**

`tools/memory-verify-fixtures/ok.md`:

```markdown
---
name: OK fixture
description: verify passes; cmd echoes magic and matches the expected regex
type: project
verify:
  - cmd: 'echo MAGIC_OK'
    expect_match: '^MAGIC_OK$'
    on_fail: 'this should never fire'
last_verified_at: '2026-05-10T00:00:00Z'
---

OK fixture body.
```

- [ ] **Step 2: Create `stale.md` fixture (verify mismatches)**

`tools/memory-verify-fixtures/stale.md`:

```markdown
---
name: Stale fixture
description: verify cmd succeeds but output does not match expected regex
type: project
verify:
  - cmd: 'echo CURRENT_HASH'
    expect_match: '^OLD_HASH$'
    on_fail: 'hash drifted; rewrite the bundle line'
---

Stale fixture body.
```

- [ ] **Step 3: Create `error.md` fixture (cmd errors out)**

`tools/memory-verify-fixtures/error.md`:

```markdown
---
name: Error fixture
description: verify cmd does not exist; runner must mark as ERROR not STALE
type: project
verify:
  - cmd: 'this-command-never-exists-anywhere'
    expect_exit: 0
    on_fail: 'binary should be installed'
---

Error fixture body.
```

- [ ] **Step 4: Create `no-verify.md` fixture (durable preference)**

`tools/memory-verify-fixtures/no-verify.md`:

```markdown
---
name: No-verify fixture
description: durable preference; runner should mark OK without running anything
type: feedback
---

No-verify fixture body.
```

- [ ] **Step 5: Commit**

```bash
cd C:\Users\User\jarvis-kotlin
git add tools/memory-verify-fixtures/
git commit -m "test(memory-verify): bootstrap 4 fixture memory files (ok/stale/error/no-verify)"
```

---

## Task 2: PowerShell runner — frontmatter parser

**Files:**
- Create: `tools/memory-verify.ps1` (parser-only first)
- Test: `tools/memory-verify-test.ps1` (parser tests)

- [ ] **Step 1: Write failing parser test**

`tools/memory-verify-test.ps1`:

```powershell
#!/usr/bin/env pwsh
# Test harness for tools/memory-verify.ps1.
# Sources the script as a module and exercises the parser + verify path.

$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo = Split-Path -Parent $here
$script = Join-Path $repo 'tools/memory-verify.ps1'

# Dot-source the script with a sentinel that prevents the runner block from firing.
$env:MEMORY_VERIFY_TEST_MODE = '1'
. $script

$failures = 0

function Assert-Equal($expected, $actual, $label) {
    if ($expected -ne $actual) {
        Write-Host "[FAIL] $label  expected=<$expected>  actual=<$actual>" -ForegroundColor Red
        $script:failures++
    } else {
        Write-Host "[OK]   $label" -ForegroundColor Green
    }
}

# Test 1: Parse frontmatter from ok.md
$okPath = Join-Path $repo 'tools/memory-verify-fixtures/ok.md'
$parsed = Parse-MemoryFrontmatter -Path $okPath
Assert-Equal 'OK fixture' $parsed.name 'parse name from ok.md'
Assert-Equal 1 $parsed.verify.Count 'parse 1 verify entry from ok.md'
Assert-Equal 'echo MAGIC_OK' $parsed.verify[0].cmd 'parse cmd from ok.md'
Assert-Equal '^MAGIC_OK$' $parsed.verify[0].expect_match 'parse expect_match from ok.md'

# Test 2: Parse no-verify.md returns empty verify array
$nvPath = Join-Path $repo 'tools/memory-verify-fixtures/no-verify.md'
$nvParsed = Parse-MemoryFrontmatter -Path $nvPath
Assert-Equal 'No-verify fixture' $nvParsed.name 'parse name from no-verify.md'
Assert-Equal 0 $nvParsed.verify.Count 'no-verify.md has empty verify array'

if ($failures -gt 0) {
    Write-Host "$failures test(s) failed" -ForegroundColor Red
    exit 1
}
Write-Host "All tests passed" -ForegroundColor Green
exit 0
```

- [ ] **Step 2: Run test to confirm fail**

```
pwsh -ExecutionPolicy Bypass -File tools/memory-verify-test.ps1
```
Expected: FAIL — `tools/memory-verify.ps1` does not exist.

- [ ] **Step 3: Implement parser in `tools/memory-verify.ps1`**

`tools/memory-verify.ps1`:

```powershell
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
```

- [ ] **Step 4: Run test to confirm pass**

```
pwsh -ExecutionPolicy Bypass -File tools/memory-verify-test.ps1
```
Expected: 4 `[OK]` lines, "All tests passed", exit 0.

- [ ] **Step 5: Commit**

```bash
cd C:\Users\User\jarvis-kotlin
git add tools/memory-verify.ps1 tools/memory-verify-test.ps1
git commit -m "feat(memory-verify): PowerShell frontmatter parser + parser tests"
```

---

## Task 3: PowerShell runner — verify cmd executor

**Files:**
- Modify: `tools/memory-verify.ps1`
- Modify: `tools/memory-verify-test.ps1`

- [ ] **Step 1: Append failing test cases**

Append to `tools/memory-verify-test.ps1` (before the final `if ($failures -gt 0)` block):

```powershell
# Test 3: Run-Verify on ok.md returns OK
$okResult = Invoke-MemoryVerify -Path $okPath
Assert-Equal 'OK' $okResult.status 'ok.md verify status is OK'

# Test 4: Run-Verify on stale.md returns STALE
$staleResult = Invoke-MemoryVerify -Path (Join-Path $repo 'tools/memory-verify-fixtures/stale.md')
Assert-Equal 'STALE' $staleResult.status 'stale.md verify status is STALE'

# Test 5: Run-Verify on error.md returns ERROR
$errorResult = Invoke-MemoryVerify -Path (Join-Path $repo 'tools/memory-verify-fixtures/error.md')
Assert-Equal 'ERROR' $errorResult.status 'error.md verify status is ERROR'

# Test 6: Run-Verify on no-verify.md returns OK trivially
$nvResult = Invoke-MemoryVerify -Path $nvPath
Assert-Equal 'OK' $nvResult.status 'no-verify.md verify status is OK (trusted)'
```

- [ ] **Step 2: Run test to confirm fail**

```
pwsh -ExecutionPolicy Bypass -File tools/memory-verify-test.ps1
```
Expected: FAIL — `Invoke-MemoryVerify` undefined.

- [ ] **Step 3: Implement `Invoke-MemoryVerify` in `memory-verify.ps1`**

Insert before the runner block:

```powershell
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
            # Treat any non-zero unexpected exit as ERROR (binary not on PATH, etc).
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

    # Freshness window check (informational; only sets STALE if no other verdict and window exceeded).
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
```

- [ ] **Step 4: Run test to confirm pass**

```
pwsh -ExecutionPolicy Bypass -File tools/memory-verify-test.ps1
```
Expected: 8 `[OK]` lines, "All tests passed", exit 0.

- [ ] **Step 5: Commit**

```bash
cd C:\Users\User\jarvis-kotlin
git add tools/memory-verify.ps1 tools/memory-verify-test.ps1
git commit -m "feat(memory-verify): PowerShell verify cmd executor with OK/STALE/ERROR statuses"
```

---

## Task 4: PowerShell runner — directory walker + report writer

**Files:**
- Modify: `tools/memory-verify.ps1`
- Modify: `tools/memory-verify-test.ps1`

- [ ] **Step 1: Append failing test for full directory walk**

Append to `tools/memory-verify-test.ps1`:

```powershell
# Test 7: Walk fixture dir + produce report
$fixturesDir = Join-Path $repo 'tools/memory-verify-fixtures'
$reportPath = Join-Path $env:TEMP 'memory-verify-test-report.md'
if (Test-Path $reportPath) { Remove-Item $reportPath -Force }
Invoke-MemoryVerifyDirectory -MemoryDir $fixturesDir -ReportPath $reportPath
Assert-Equal $true (Test-Path $reportPath) 'report file written'

$reportContent = Get-Content $reportPath -Raw
if ($reportContent -notmatch '\[OK\]\s+ok\.md') {
    Write-Host "[FAIL] report contains [OK] ok.md  body=<$reportContent>" -ForegroundColor Red; $script:failures++
} else { Write-Host "[OK]   report contains [OK] ok.md" -ForegroundColor Green }

if ($reportContent -notmatch '\[STALE\]\s+stale\.md') {
    Write-Host "[FAIL] report contains [STALE] stale.md" -ForegroundColor Red; $script:failures++
} else { Write-Host "[OK]   report contains [STALE] stale.md" -ForegroundColor Green }

if ($reportContent -notmatch '\[ERROR\]\s+error\.md') {
    Write-Host "[FAIL] report contains [ERROR] error.md" -ForegroundColor Red; $script:failures++
} else { Write-Host "[OK]   report contains [ERROR] error.md" -ForegroundColor Green }

if ($reportContent -notmatch '\[OK\]\s+no-verify\.md') {
    Write-Host "[FAIL] report contains [OK] no-verify.md" -ForegroundColor Red; $script:failures++
} else { Write-Host "[OK]   report contains [OK] no-verify.md" -ForegroundColor Green }
```

- [ ] **Step 2: Run test to confirm fail**

```
pwsh -ExecutionPolicy Bypass -File tools/memory-verify-test.ps1
```
Expected: FAIL — `Invoke-MemoryVerifyDirectory` undefined.

- [ ] **Step 3: Implement `Invoke-MemoryVerifyDirectory` + replace runner block**

Insert before the runner block in `tools/memory-verify.ps1`:

```powershell
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
```

Replace the runner block at the bottom of the file:

```powershell
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
```

- [ ] **Step 4: Run test to confirm pass**

```
pwsh -ExecutionPolicy Bypass -File tools/memory-verify-test.ps1
```
Expected: 12 `[OK]` lines, "All tests passed", exit 0.

- [ ] **Step 5: Manual smoke against real memory**

```
pwsh -ExecutionPolicy Bypass -File tools/memory-verify.ps1
type "$env:USERPROFILE\.claude\projects\C--Users-User-jarvis-kotlin\memory\.verify-report.md"
```
Expected: report file generated, lists every `*.md` in the memory dir with `[OK]` / `[STALE]` / `[ERROR]`.

- [ ] **Step 6: Commit**

```bash
cd C:\Users\User\jarvis-kotlin
git add tools/memory-verify.ps1 tools/memory-verify-test.ps1
git commit -m "feat(memory-verify): directory walker + report writer + smoke against real memory"
```

---

## Task 5: Bash mirror — `tools/memory-verify.sh`

**Files:**
- Create: `tools/memory-verify.sh`
- Create: `tools/memory-verify-test.sh`

- [ ] **Step 1: Write failing bash test**

`tools/memory-verify-test.sh`:

```bash
#!/usr/bin/env bash
# Test harness for tools/memory-verify.sh.
set -u  # pipefail intentionally off so we can detect script failures.

REPO="$(cd "$(dirname "$0")/.." && pwd)"
SCRIPT="$REPO/tools/memory-verify.sh"
FIXTURES="$REPO/tools/memory-verify-fixtures"
REPORT_TMP="$(mktemp)"
failures=0

assert_eq() {
    local expected="$1"; local actual="$2"; local label="$3"
    if [ "$expected" = "$actual" ]; then
        echo "[OK]   $label"
    else
        echo "[FAIL] $label  expected=<$expected>  actual=<$actual>"
        failures=$((failures + 1))
    fi
}

assert_contains() {
    local haystack="$1"; local needle="$2"; local label="$3"
    if echo "$haystack" | grep -q -- "$needle"; then
        echo "[OK]   $label"
    else
        echo "[FAIL] $label  needle=<$needle>"
        failures=$((failures + 1))
    fi
}

# Test 1: Run script against fixture dir, capture report
CLAUDE_MEMORY_DIR="$FIXTURES" MEMORY_VERIFY_REPORT_PATH="$REPORT_TMP" bash "$SCRIPT"
assert_eq "0" "$?" "script exit 0"

REPORT="$(cat "$REPORT_TMP")"
assert_contains "$REPORT" "[OK] ok.md"          "report contains [OK] ok.md"
assert_contains "$REPORT" "[STALE] stale.md"    "report contains [STALE] stale.md"
assert_contains "$REPORT" "[ERROR] error.md"    "report contains [ERROR] error.md"
assert_contains "$REPORT" "[OK] no-verify.md"   "report contains [OK] no-verify.md (trusted)"

rm -f "$REPORT_TMP"
if [ "$failures" -gt 0 ]; then
    echo "$failures test(s) failed"
    exit 1
fi
echo "All tests passed"
exit 0
```

Make it executable:

```
chmod +x tools/memory-verify-test.sh
```

- [ ] **Step 2: Run test to confirm fail**

```
bash tools/memory-verify-test.sh
```
Expected: FAIL — `tools/memory-verify.sh` does not exist.

- [ ] **Step 3: Implement `tools/memory-verify.sh`**

```bash
#!/usr/bin/env bash
# Memory sanity-check runner (bash mirror of memory-verify.ps1).
# Walks $CLAUDE_MEMORY_DIR (or default), parses YAML frontmatter via grep,
# runs `verify:` cmds, writes report to <memory>/.verify-report.md.
set -u

# --- Resolve memory dir ---
MEMORY_DIR="${CLAUDE_MEMORY_DIR:-}"
if [ -z "$MEMORY_DIR" ]; then
    SANITIZED="$(pwd | sed 's|[/\\:]|-|g')"
    MEMORY_DIR="$HOME/.claude/projects/$SANITIZED/memory"
fi

if [ ! -d "$MEMORY_DIR" ]; then
    echo "[memory-verify] memory dir not found: $MEMORY_DIR (skipping)"
    exit 0
fi

REPORT_PATH="${MEMORY_VERIFY_REPORT_PATH:-$MEMORY_DIR/.verify-report.md}"
NOW="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"

# Strip surrounding single/double quotes from a YAML-ish value.
unquote() { sed "s/^['\"]//; s/['\"]$//" <<< "$1"; }

# Parse a single memory file's frontmatter into a sequence of "key=value"
# lines on stdout. verify entries get prefixed numbered keys verify_0_cmd,
# verify_0_expect_match, etc.
parse_frontmatter() {
    local file="$1"
    awk '
        BEGIN { inblock = 0; inverify = 0; vidx = -1 }
        NR == 1 && $0 == "---" { inblock = 1; next }
        inblock && $0 == "---" { exit }
        !inblock { next }
        /^name:/        { sub(/^name:[[:space:]]*/, "");        print "name=" $0; inverify = 0; next }
        /^description:/ { sub(/^description:[[:space:]]*/, ""); print "description=" $0; inverify = 0; next }
        /^type:/        { sub(/^type:[[:space:]]*/, "");        print "type=" $0; inverify = 0; next }
        /^last_verified_at:/    { sub(/^last_verified_at:[[:space:]]*/, "");    print "last_verified_at=" $0; inverify = 0; next }
        /^freshness_window_hours:/ { sub(/^freshness_window_hours:[[:space:]]*/, ""); print "freshness_window_hours=" $0; inverify = 0; next }
        /^verify:[[:space:]]*$/  { inverify = 1; next }
        inverify && /^[[:space:]]*-[[:space:]]*cmd:/  { sub(/^[[:space:]]*-[[:space:]]*cmd:[[:space:]]*/, ""); vidx++; print "verify_" vidx "_cmd=" $0; next }
        inverify && /^[[:space:]]+expect_match:/      { sub(/^[[:space:]]+expect_match:[[:space:]]*/, "");      print "verify_" vidx "_expect_match=" $0; next }
        inverify && /^[[:space:]]+expect_no_match:/   { sub(/^[[:space:]]+expect_no_match:[[:space:]]*/, "");   print "verify_" vidx "_expect_no_match=" $0; next }
        inverify && /^[[:space:]]+expect_exit:/       { sub(/^[[:space:]]+expect_exit:[[:space:]]*/, "");       print "verify_" vidx "_expect_exit=" $0; next }
        inverify && /^[[:space:]]+on_fail:/           { sub(/^[[:space:]]+on_fail:[[:space:]]*/, "");           print "verify_" vidx "_on_fail=" $0; next }
    ' "$file"
}

# Run a single file's verify entries; print "STATUS|reasons-pipe-joined" on stdout.
run_one_file() {
    local file="$1"
    local fm
    fm="$(parse_frontmatter "$file")"

    local status="OK"
    local reasons=""
    local ran=0

    # Discover number of verify entries.
    local max_idx=-1
    while IFS= read -r line; do
        if [[ "$line" =~ ^verify_([0-9]+)_cmd= ]]; then
            local idx="${BASH_REMATCH[1]}"
            if [ "$idx" -gt "$max_idx" ]; then max_idx="$idx"; fi
        fi
    done <<< "$fm"

    if [ "$max_idx" -lt 0 ]; then
        echo "OK|0|"
        return
    fi

    for idx in $(seq 0 "$max_idx"); do
        ran=$((ran + 1))
        local cmd="" exp_match="" exp_no_match="" exp_exit="0" on_fail=""
        while IFS= read -r line; do
            case "$line" in
                "verify_${idx}_cmd="*)            cmd="$(unquote "${line#verify_${idx}_cmd=}")" ;;
                "verify_${idx}_expect_match="*)   exp_match="$(unquote "${line#verify_${idx}_expect_match=}")" ;;
                "verify_${idx}_expect_no_match="*) exp_no_match="$(unquote "${line#verify_${idx}_expect_no_match=}")" ;;
                "verify_${idx}_expect_exit="*)    exp_exit="$(unquote "${line#verify_${idx}_expect_exit=}")" ;;
                "verify_${idx}_on_fail="*)        on_fail="$(unquote "${line#verify_${idx}_on_fail=}")" ;;
            esac
        done <<< "$fm"

        # Run the cmd, capture stdout + exit.
        local stdout exit_code
        stdout="$(bash -c "$cmd" 2>&1)"
        exit_code=$?

        if [ "$exit_code" -ne "$exp_exit" ] && [ "$exit_code" -ne 0 ]; then
            status="ERROR"
            reasons="${reasons}cmd '$cmd' exit=$exit_code (expected $exp_exit). on_fail: $on_fail|"
            continue
        fi

        if [ -n "$exp_match" ]; then
            if ! echo "$stdout" | grep -Eq -- "$exp_match"; then
                if [ "$status" = "OK" ]; then status="STALE"; fi
                reasons="${reasons}cmd '$cmd' output '$stdout' does NOT match '$exp_match'. on_fail: $on_fail|"
            fi
        fi
        if [ -n "$exp_no_match" ]; then
            if echo "$stdout" | grep -Eq -- "$exp_no_match"; then
                if [ "$status" = "OK" ]; then status="STALE"; fi
                reasons="${reasons}cmd '$cmd' output matches forbidden '$exp_no_match'. on_fail: $on_fail|"
            fi
        fi
    done

    echo "$status|$ran|$reasons"
}

# --- Walk the directory ---
{
    echo "# Verify report — $NOW"
    echo ""
    for f in "$MEMORY_DIR"/*.md; do
        [ -e "$f" ] || continue
        base="$(basename "$f")"
        case "$base" in
            MEMORY.md|BRIDGE.md|.*.md) continue ;;
        esac
        result="$(run_one_file "$f")"
        IFS='|' read -r status ran reasons <<< "$result"
        if [ "$ran" = "0" ]; then
            echo "[$status] $base (no verify; trusted)"
        else
            echo "[$status] $base ($ran verify)"
        fi
        if [ -n "$reasons" ]; then
            IFS='|' read -ra rs <<< "$reasons"
            for r in "${rs[@]}"; do
                [ -n "$r" ] && echo "    reason: $r"
            done
        fi
    done
} > "$REPORT_PATH"

echo "[memory-verify] report written to $REPORT_PATH"
exit 0
```

Make executable:

```
chmod +x tools/memory-verify.sh
```

- [ ] **Step 4: Run bash tests to pass**

```
bash tools/memory-verify-test.sh
```
Expected: 5 `[OK]` lines, "All tests passed", exit 0.

- [ ] **Step 5: Commit**

```bash
cd C:\Users\User\jarvis-kotlin
git add tools/memory-verify.sh tools/memory-verify-test.sh
git commit -m "feat(memory-verify): bash mirror with same OK/STALE/ERROR semantics"
```

---

## Task 6: Backfill `verify:` block on `project_jarvis_overhaul_active.md`

**Files:**
- Modify: `C:\Users\User\.claude\projects\C--Users-User-jarvis-kotlin\memory\project_jarvis_overhaul_active.md`

- [ ] **Step 1: Read current frontmatter**

```
type "C:\Users\User\.claude\projects\C--Users-User-jarvis-kotlin\memory\project_jarvis_overhaul_active.md"
```
Expected: file starts with `---\nname: ...\ndescription: ...\ntype: project\n---`.

- [ ] **Step 2: Verify the live bundle hash before writing the verify cmd**

```
curl -sk https://corgflix.duckdns.org/tutor/ | grep -oE "index-[A-Za-z0-9_-]+\.js" | head -1
```
Expected: prints `index-CFXAulB7.js` (or whatever is current — capture it for the regex below).

- [ ] **Step 3: Append `verify:` block to the frontmatter**

Edit the file so the frontmatter becomes (replace `index-CFXAulB7\.js` with whatever step 2 returned):

```yaml
---
name: Active overhaul roadmap — 8-phase tutor + life-OS plan
description: ... (keep existing description)
type: project
verify:
  - cmd: 'curl -sk https://corgflix.duckdns.org/tutor/ | grep -oE "index-[A-Za-z0-9_-]+\.js" | head -1'
    expect_match: '^index-CFXAulB7\.js$'
    on_fail: 'live bundle hash drifted; rewrite the bundle line in the body'
  - cmd: 'git -C C:/Users/User/jarvis-kotlin rev-parse --short HEAD'
    expect_match: '^[0-9a-f]{7,}$'
    on_fail: 'git rev-parse failed (repo missing? wrong cwd?)'
last_verified_at: '2026-05-10T18:00:00Z'
freshness_window_hours: 168
---
```

(Body of the file stays untouched.)

- [ ] **Step 4: Run runner against real memory dir to confirm it goes [OK]**

```
pwsh -ExecutionPolicy Bypass -File tools/memory-verify.ps1
type "$env:USERPROFILE\.claude\projects\C--Users-User-jarvis-kotlin\memory\.verify-report.md"
```
Expected: report shows `[OK] project_jarvis_overhaul_active.md (2 verify)`.

- [ ] **Step 5: Commit (memory dir is outside repo — record edit notes only)**

Memory files live outside the git repo. Record what changed in repo-tracked notes:

```
echo "2026-05-10: backfilled verify: block on project_jarvis_overhaul_active.md (bundle hash + git rev-parse)" >> docs/notes/2026-05-10-memory-verify-backfill.md
git add docs/notes/2026-05-10-memory-verify-backfill.md
git commit -m "chore(memory): backfill verify block on project_jarvis_overhaul_active.md"
```

---

## Task 7: Backfill `verify:` block on `project_jarvis_2026-05-09_session_wrap.md`

**Files:**
- Modify: `C:\Users\User\.claude\projects\C--Users-User-jarvis-kotlin\memory\project_jarvis_2026-05-09_session_wrap.md`

- [ ] **Step 1: Read current frontmatter**

```
type "C:\Users\User\.claude\projects\C--Users-User-jarvis-kotlin\memory\project_jarvis_2026-05-09_session_wrap.md"
```

- [ ] **Step 2: Append `verify:` block — softer (this file is a wrap snapshot, not live state)**

Edit the frontmatter to:

```yaml
---
name: Session wrap 2026-05-09 — Tutor Layer B + task-context V0/V1 + 6-stage build-everything
description: ... (keep existing)
type: project
verify:
  - cmd: 'git -C C:/Users/User/jarvis-kotlin log --oneline 612bf1e -1'
    expect_match: '612bf1e'
    on_fail: 'commit 612bf1e cited in this wrap is missing from the repo (rewrite or rebase?)'
last_verified_at: '2026-05-10T18:00:00Z'
freshness_window_hours: 720
---
```

(720 hours = 30 days. Wrap snapshots can be reasonably stale; we just want to catch repo-history rewrites.)

- [ ] **Step 3: Run runner to confirm [OK]**

```
pwsh -ExecutionPolicy Bypass -File tools/memory-verify.ps1
type "$env:USERPROFILE\.claude\projects\C--Users-User-jarvis-kotlin\memory\.verify-report.md"
```
Expected: report shows `[OK] project_jarvis_2026-05-09_session_wrap.md (1 verify)`.

- [ ] **Step 4: Commit notes**

```
echo "2026-05-10: backfilled verify: block on project_jarvis_2026-05-09_session_wrap.md (commit 612bf1e exists)" >> docs/notes/2026-05-10-memory-verify-backfill.md
git add docs/notes/2026-05-10-memory-verify-backfill.md
git commit -m "chore(memory): backfill verify block on project_jarvis_2026-05-09_session_wrap.md"
```

---

## Task 8: Bootstrap `BRIDGE.md`

**Files:**
- Create: `C:\Users\User\.claude\projects\C--Users-User-jarvis-kotlin\memory\BRIDGE.md`

- [ ] **Step 1: Write the file**

`BRIDGE.md`:

```markdown
# Bridge — jarvis-kotlin

## Index (newest first)
- 2026-05-10T18:00 → memory sanity-check system shipped; Slice 1 spec + plan ready

---

## 2026-05-10T18:00 → next session

**identity:** Alex (amoalexandru5@gmail.com). Romanian uni. Finals Jun 1-21 2026. Subjects: PA / PS / POO / ALO / SO+RC.

**hot work (in priority):**
1. Slice 1 plan Phase D-J expansion — `docs/superpowers/plans/2026-05-10-tutor-drill-workspace-slice1.md`
2. Memory sanity-check rollout — this very plan; finish remaining tasks

**bundle:** `index-CFXAulB7.js`
verify-cmd: `curl -sk https://corgflix.duckdns.org/tutor/ | grep -oE 'index-[A-Za-z0-9_-]+\.js' | head -1`

**tests:** 599 backend + 140 frontend + 16 daemon + 7 node = 762

**dormant integrations (1):** Telegram bot producer (token-blocked)

**blockers:**
- Stream 1: Phase D-J expansion before subagent-driven-development dispatch
- Stream 2: trust-but-verify rule needs CLAUDE.md append + SessionStart hook wiring

**user-said (verbatim, last 5):**
- "ok looks good" (Slice 1 spec approval)
- "do the daemon autostart" (added to Slice 1)
- "no, just continue" (after handoff prompt)
- "good" (sanity-check spec approval)
- "yes" (sanity-check Sections 5-6 approval)

**don't relitigate:** brutalist-mono yellow-on-black; no paid APIs; no deadline framing; build-everything mode; mobile first-class; single-user

**hallucination triggers:** `gws` doesn't exist as a binary or npm package; `gam` not installed on VPS; sympy IS installed (1.9 via apt 2026-05-10). Re-verify any specific filepath / route / SHA / bundle hash before citing.

**Slice 1 spec:** `docs/superpowers/specs/2026-05-10-tutor-drill-workspace-slice1-design.md` (commit `0e14a04`)
**Slice 1 plan:** `docs/superpowers/plans/2026-05-10-tutor-drill-workspace-slice1.md` (commit `f2a7d45`); Phases A-C expanded, D-J summary
**Sanity-check spec:** `docs/superpowers/specs/2026-05-10-memory-sanity-check-design.md` (commit `161f058`)
**Sanity-check plan:** `docs/superpowers/plans/2026-05-10-memory-sanity-check.md` (this file)
```

- [ ] **Step 2: Verify the runner skips it (BRIDGE.md should not be parsed)**

```
pwsh -ExecutionPolicy Bypass -File tools/memory-verify.ps1
type "$env:USERPROFILE\.claude\projects\C--Users-User-jarvis-kotlin\memory\.verify-report.md"
```
Expected: report does NOT mention `BRIDGE.md`. Runner skips it per the directory walker filter.

- [ ] **Step 3: Commit notes**

```
echo "2026-05-10: bootstrapped BRIDGE.md with current state entry" >> docs/notes/2026-05-10-memory-verify-backfill.md
git add docs/notes/2026-05-10-memory-verify-backfill.md
git commit -m "chore(memory): bootstrap BRIDGE.md handoff doc"
```

---

## Task 9: SessionStart hook wiring

**Files:**
- Create or modify: `.claude/settings.json`

- [ ] **Step 1: Check whether `.claude/settings.json` exists**

```
type C:\Users\User\jarvis-kotlin\.claude\settings.json
```
Expected: either file content or "file not found".

- [ ] **Step 2: Write or merge SessionStart hook**

If the file does not exist, create with:

```json
{
  "hooks": {
    "SessionStart": [
      {
        "command": "powershell -ExecutionPolicy Bypass -File C:\\Users\\User\\jarvis-kotlin\\tools\\memory-verify.ps1"
      }
    ]
  }
}
```

If the file exists and already has a `hooks` block, merge the SessionStart entry without overwriting other hooks. Manual edit; preserve existing JSON structure.

- [ ] **Step 3: Smoke-test the hook by re-launching a Claude Code session**

Manual: close and reopen Claude Code in this repo. The first message of the new session should include the verify-report content as a system reminder. If not, check the hook is firing via:

```
pwsh -ExecutionPolicy Bypass -File C:\Users\User\jarvis-kotlin\tools\memory-verify.ps1
type "$env:USERPROFILE\.claude\projects\C--Users-User-jarvis-kotlin\memory\.verify-report.md"
```

- [ ] **Step 4: Commit**

```bash
cd C:\Users\User\jarvis-kotlin
git add .claude/settings.json
git commit -m "feat(claude): wire SessionStart hook to memory-verify.ps1"
```

---

## Task 10: `/sanity` slash command

**Files:**
- Create: `.claude/commands/sanity.md`

- [ ] **Step 1: Verify the commands directory exists**

```
mkdir -p C:\Users\User\jarvis-kotlin\.claude\commands
```

- [ ] **Step 2: Write the slash command file**

`.claude/commands/sanity.md`:

```markdown
---
description: Deep memory audit — cross-checks every concrete claim against current state via fresh sub-agent. Token cost ~5-10k. Use when /verify-report.md flagged ≥2 [STALE] entries or you smell drift.
---

Dispatch a fresh general-purpose sub-agent with the prompt below. Wait for the report. Present it to me. Do NOT modify memory inline — let me choose the fixes.

```
You are a memory auditor for the jarvis-kotlin project.

Read every file under the project's memory directory. Resolve the directory from environment variable CLAUDE_MEMORY_DIR if set, else default to:
~/.claude/projects/C--Users-User-jarvis-kotlin/memory/

Skip MEMORY.md (it's an index, no frontmatter) and BRIDGE.md (handoff doc).

For each remaining .md file, identify every factual claim that names a specific:
- filepath
- external binary name (gws, gam, gcloud, npm CLIs, etc)
- npm/pip/cargo package name
- HTTP route path
- commit SHA
- bundle hash
- test count
- live URL

For each such claim, verify it against current state. Use:
- `grep -r` against C:/Users/User/jarvis-kotlin
- `git -C C:/Users/User/jarvis-kotlin log --oneline <sha>` for SHA refs
- `curl -sk https://corgflix.duckdns.org/<path>` for live state
- `which <binary>` or `ssh root@46.247.109.91 'which <binary>'` for binaries
- `ls C:/Users/User/jarvis-kotlin/<path>` for files

Build a delta report grouped by memory file. Each entry tagged:
- [OK]            — claim verified
- [STALE: ...]    — claim was true but drifted (e.g. SHA exists but isn't HEAD anymore)
- [HALLUCINATED: ...] — claim was never true (e.g. gws binary)

Include the verify command + its actual output for every entry.

End with a section "Recommended rewrites" listing each memory file that needs updating + the corrected text for each stale/hallucinated claim.

Stay under 1500 words.
```

When the agent returns, summarize the top 3 most actionable fixes for me. Wait for my approval before any rewrites.
```

- [ ] **Step 3: Commit**

```bash
cd C:\Users\User\jarvis-kotlin
git add .claude/commands/sanity.md
git commit -m "feat(claude): /sanity slash command for deep memory audit"
```

---

## Task 11: `/wrap` slash command

**Files:**
- Create: `.claude/commands/wrap.md`

- [ ] **Step 1: Write the slash command file**

`.claude/commands/wrap.md`:

```markdown
---
description: Append session handoff entry to BRIDGE.md. Run before context exhaustion or at session end.
---

Append a new dated block to ~/.claude/projects/C--Users-User-jarvis-kotlin/memory/BRIDGE.md with the structure below. Update the index line at the top of the file too. Do NOT delete any prior entries — BRIDGE.md is append-only.

Required fields (gather facts via tool calls before writing — do not hallucinate):

- **identity** — Alex (amoalexandru5@gmail.com). Confirm from memory/user_identity.md.
- **hot work (in priority)** — top 1-3 active streams with file paths.
- **bundle** — current live bundle hash. Verify via:
  `curl -sk https://corgflix.duckdns.org/tutor/ | grep -oE 'index-[A-Za-z0-9_-]+\.js' | head -1`
  Include the verify-cmd literally so the next session can re-run it.
- **tests** — backend / frontend / daemon / node count totals. Best-effort; note if you didn't actually run them this session.
- **dormant integrations** — list anything user-blocked.
- **blockers** — for Claude (next session) and for user.
- **user-said (verbatim, last 3-5)** — quote the user verbatim. Do not paraphrase.
- **don't relitigate** — locked-in rules from feedback memory files.
- **hallucination triggers** — known wrong facts that have crept into earlier sessions; warn the next session off them.
- **active spec / plan paths** — paths to the in-flight spec and plan docs with commit SHAs.

Format mirror Section C of docs/superpowers/specs/2026-05-10-memory-sanity-check-design.md. Append after the last `---` separator at the bottom of BRIDGE.md, with an updated `## YYYY-MM-DDTHH:MM → next session` heading.

After writing, also prepend a 1-line entry to the `## Index (newest first)` section at the top of BRIDGE.md.
```

- [ ] **Step 2: Commit**

```bash
cd C:\Users\User\jarvis-kotlin
git add .claude/commands/wrap.md
git commit -m "feat(claude): /wrap slash command for session-end handoff"
```

---

## Task 12: CLAUDE.md trust-but-verify rule

**Files:**
- Modify: `C:\Users\User\.claude\CLAUDE.md`

- [ ] **Step 1: Read current `CLAUDE.md` to find the right insertion point**

```
type C:\Users\User\.claude\CLAUDE.md
```
Expected: file content; look for an existing "## " heading near the bottom to append after.

- [ ] **Step 2: Append the rule**

Append to `C:\Users\User\.claude\CLAUDE.md`:

```markdown

## Memory verification rule (load-bearing)

Before acting on a memory claim that names ANY of:
- specific filepath
- external binary name (e.g. `gws`, `gam`, `gcloud`)
- npm/pip/cargo package name
- HTTP route path
- commit SHA
- bundle hash
- test count
- live URL or bundle hash

Re-verify against current state first. Use grep / curl / `which` / `ls` / `git ls-files` / `git rev-parse HEAD` etc. If the claim doesn't hold, update the memory file BEFORE acting. Especially: external CLI tool names (the `gws` lesson — `@googleworkspace/cli` was hallucinated for 5+ commits in earlier sessions because no one verified the npm package existed).

Memory captures intent + history. Reality lives in the repo + on the VPS. Trust reality.

When `~/.claude/projects/<cwd>/memory/.verify-report.md` exists and contains `[STALE]` or `[ERROR]` markers: surface the stale entries in your first response and offer to refresh them before doing other work.
```

- [ ] **Step 3: Verify the file is still valid markdown**

```
type C:\Users\User\.claude\CLAUDE.md | findstr /C:"Memory verification rule"
```
Expected: prints the heading.

- [ ] **Step 4: Note the change**

`C:\Users\User\.claude\CLAUDE.md` is user-global, not in the jarvis-kotlin repo. Record the edit in the repo notes:

```
echo "2026-05-10: appended Memory verification rule to user-global C:\\Users\\User\\.claude\\CLAUDE.md" >> docs/notes/2026-05-10-memory-verify-backfill.md
git add docs/notes/2026-05-10-memory-verify-backfill.md
git commit -m "chore(memory): append trust-but-verify rule to user-global CLAUDE.md"
```

---

## Task 13: End-to-end smoke test

**Files:**
- None modified; this is a manual verification.

- [ ] **Step 1: Run the full PowerShell test suite**

```
pwsh -ExecutionPolicy Bypass -File tools/memory-verify-test.ps1
```
Expected: 12 `[OK]` lines, "All tests passed", exit 0.

- [ ] **Step 2: Run the full bash test suite (WSL or Git Bash)**

```
bash tools/memory-verify-test.sh
```
Expected: 5 `[OK]` lines, "All tests passed", exit 0.

- [ ] **Step 3: Run the runner against real memory dir**

```
pwsh -ExecutionPolicy Bypass -File tools/memory-verify.ps1
type "$env:USERPROFILE\.claude\projects\C--Users-User-jarvis-kotlin\memory\.verify-report.md"
```
Expected: report shows `[OK]` for `project_jarvis_overhaul_active.md` and `project_jarvis_2026-05-09_session_wrap.md` and the feedback / user_identity files. No `[STALE]` or `[ERROR]` markers.

- [ ] **Step 4: Restart Claude Code session and confirm hook fires**

Manual: close and reopen Claude Code. The first system reminder should include verify-report contents. If yes, the SessionStart hook is wired correctly.

- [ ] **Step 5: Manually invoke `/sanity` to confirm the slash command resolves**

In the Claude Code chat, type `/sanity` and press enter. Expected: Claude dispatches the sub-agent per the slash-command file. (Sub-agent execution costs tokens — only run this once for confirmation; not part of routine flow.)

- [ ] **Step 6: Append e2e notes**

```
echo "2026-05-10: e2e smoke pass — PS tests, bash tests, real-memory runner, hook fires, /sanity resolves" >> docs/notes/2026-05-10-memory-verify-backfill.md
git add docs/notes/2026-05-10-memory-verify-backfill.md
git commit -m "test(memory): e2e smoke pass — runner + hook + slash commands all green"
```

---

## Self-review (per skill)

**1. Spec coverage:**

- §A frontmatter `verify:` block → Tasks 1, 6, 7 ✓
- §B Pre-flight runner (PS + bash) → Tasks 2, 3, 4, 5 ✓
- §C BRIDGE.md handoff doc → Task 8 ✓ (template in body, runner skips it via Task 4 directory walker filter)
- §D `/sanity` slash command → Task 10 ✓
- §E CLAUDE.md trust-but-verify rule → Task 12 ✓
- Plus: SessionStart hook wiring → Task 9 ✓
- Plus: `/wrap` slash command (mentioned in spec §C closing ritual) → Task 11 ✓
- Plus: e2e smoke → Task 13 ✓

No spec section unmapped.

**2. Placeholder scan:**

- No "TBD" / "TODO" / "implement later" anywhere in the plan.
- Every task has complete code blocks where code is required.
- Every command has expected output.
- No "similar to Task N" — each task self-contained.
- Bundle hash `index-CFXAulB7.js` is a literal value captured at plan-write time. Task 6 Step 2 explicitly tells the implementer to re-run the curl and use the hash they observe instead of trusting the plan's literal — that's the right move because the hash will drift.

**3. Type consistency:**

- `Parse-MemoryFrontmatter` (PowerShell) returns hashtable with consistent keys: `name`, `description`, `type`, `verify`, `last_verified_at`, `freshness_window_hours`. Used identically in Tasks 2-4.
- `Invoke-MemoryVerify` returns hashtable with `path`, `name`, `status`, `reasons`, `ranCount`. Used in Tasks 3 + 4.
- Bash `parse_frontmatter` outputs `key=value` lines including `verify_<idx>_cmd` etc. Consumed identically by `run_one_file` in Task 5.
- Status taxonomy (`OK` / `STALE` / `ERROR`) consistent across PS + bash + report file format.
- Report file format (`[STATUS] filename (N verify)` + indented `reason:` lines) consistent between PS Task 4 and bash Task 5.

No type drift.

---

## Execution handoff

Plan complete and saved to `docs/superpowers/plans/2026-05-10-memory-sanity-check.md`. Two execution options:

**1. Subagent-Driven (recommended)** — fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — execute tasks in this session via `executing-plans`, batch with checkpoints.

Which approach?
