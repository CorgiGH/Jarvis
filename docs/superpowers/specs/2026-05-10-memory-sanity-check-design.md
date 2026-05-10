# Memory Sanity-Check System · Design (2026-05-10)

> **Status:** spec under user review.
> **Authoring path:** brainstorm-driven; user requested a session-transition system after this session surfaced 3 memory-failure modes (hallucinated `gws` binary, wrong identity "Victor" instead of Alex, drifted bundle hash + test count).
> **Sibling spec:** `2026-05-10-tutor-drill-workspace-slice1-design.md` (separate work stream; this spec is independent).

## Goal (one sentence)

Catch hallucinations, drift, and identity bugs at session boundaries via a 4-component system: verifiable memory frontmatter, pre-flight runner, append-only `BRIDGE.md` handoff doc, and an opt-in `/sanity` deep auditor — backed by a `CLAUDE.md` trust-but-verify rule.

## Failure modes addressed (all three at once)

1. **Hallucinations** — memory cites things that never existed (e.g. `@googleworkspace/cli` referenced for 5+ commits without anyone verifying the npm package was real).
2. **Drift** — memory cites things that existed at write time but moved (live bundle hash, test count, commit SHA at HEAD).
3. **Wrong identity / preferences** — memory carries wrong personal facts that propagate across sessions ("Victor" vs Alex).

User answered "all three roughly equal" when asked which hurts most. Solution must address all three simultaneously.

## Architecture

Four components + one rule:

### A. Memory frontmatter `verify:` block

Optional extension to the existing memory frontmatter. Files describing volatile state (project state, recent commits, live infrastructure) get verify blocks. Files describing durable preferences (feedback, identity) skip verify and stay trusted.

Shape:

```yaml
---
name: Active overhaul roadmap
description: Pointer to canonical spec; volatile state.
type: project
verify:
  - cmd: 'curl -sk https://corgflix.duckdns.org/tutor/ | grep -oE "index-[A-Za-z0-9_-]+\.js" | head -1'
    expect_match: '^index-CFXAulB7\.js$'
    on_fail: 'live bundle hash drifted; rewrite the bundle line'
  - cmd: 'git rev-parse HEAD'
    expect_match: '^[a-f0-9]{40}$'
    on_fail: 'commit SHA referenced no longer at HEAD'
last_verified_at: '2026-05-10T17:30:00+03:00'
freshness_window_hours: 168
---
```

Verb semantics:

- `expect_exit: 0` (default) — cmd must exit zero.
- `expect_match: <regex>` — cmd stdout must match.
- `expect_no_match: <regex>` — stdout must NOT match.
- `on_fail: <string>` — human-readable reason surfaced in report.
- `last_verified_at` + `freshness_window_hours` — past the window without re-verify, mark stale even if no `verify:` block.

### B. Pre-flight runner

Two scripts, same behavior:
- `tools/memory-verify.ps1` — Windows primary (Alex's machine).
- `tools/memory-verify.sh` — bash mirror for VPS / WSL parity.

Logic:

1. Resolve memory dir from `$env:CLAUDE_PROJECT_DIR` or fall back to `~/.claude/projects/<sanitized cwd>/memory/`.
2. Walk `*.md`. Skip `MEMORY.md` (no frontmatter).
3. Parse YAML frontmatter (between leading `---` lines). No PyYAML dep — use regex over the bounded block.
4. For each `verify:` entry: spawn cmd with 5s timeout; capture exit + stdout; compare against `expect_*`.
5. Build report at `<memory>/.verify-report.md` with `[OK]` / `[STALE: <reason>]` / `[ERROR: <cmd-failed>]` per file.
6. Exit 0 always (don't block session start). Stale signal lives in the report file.

Wired via SessionStart hook in `.claude/settings.json` (project-local, not user-global):

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

Claude Code auto-includes hook output as system-reminder context, so the report reaches Claude before any user message.

### C. `BRIDGE.md` append-only handoff doc

File: `~/.claude/projects/<cwd>/memory/BRIDGE.md`. Append-only. Top has 1-line index of past handoffs. Bottom has the most recent. Old blocks never deleted.

Template:

```markdown
# Bridge — jarvis-kotlin

## Index (newest first)
- 2026-05-10T17:45 → Slice 1 spec + plan ready, sanity-check brainstorm in flight
- 2026-05-10T13:20 → 76-commit overhaul shipped, 9/10 deferrals closed

---

## 2026-05-10T17:45 → next session

**identity:** Alex (amoalexandru5@gmail.com). Romanian uni. Finals Jun 1-21.

**hot work (in priority):**
1. Slice 1 plan Phase D-J expansion
2. Memory sanity-check brainstorm (Stream 2)

**bundle:** `index-CFXAulB7.js`
verify-cmd: `curl -sk https://corgflix.duckdns.org/tutor/ | grep -oE 'index-[A-Za-z0-9_-]+\.js' | head -1`

**tests:** 599 backend / 140 frontend / 16 daemon / 7 node = 762

**dormant integrations (1):** Telegram bot producer (token-blocked)

**blockers:** none for Claude on Stream 2; Stream 1 needs Phase D-J expansion before dispatch

**user-said (verbatim, last 3):**
- "ok looks good"
- "do the daemon autostart"
- "no, just continue"

**don't relitigate:** brutalist-mono yellow-on-black; no paid APIs; no deadline framing; build-everything mode; mobile first-class; single-user

**hallucination triggers:** `gws` doesn't exist; `gam` not installed; sympy IS installed (1.9 via apt). Re-verify any specific filepath / route / SHA / bundle hash before citing.
```

Closing ritual: new `/wrap` slash command (or session-end recognition) appends a fresh block. File grows ~1KB/session.

### D. `/sanity` slash command

Manual deep audit. Dispatches fresh sub-agent that reads memory + cross-checks every concrete factual claim against current state. Returns delta report. Optional `--fix` mode rewrites drifted entries (asks user approval per entry).

Slash command file: `.claude/commands/sanity.md`:

```markdown
---
description: Deep memory audit. Cross-checks every concrete claim against current state. Token cost ~5-10k.
---
Dispatch a fresh general-purpose sub-agent. Wait for report. Present to user. Do NOT modify memory inline — let user choose fixes.

Prompt:
"You are a memory auditor. Read every file under the project's memory directory (resolve from $CLAUDE_PROJECT_DIR or ~/.claude/projects/<cwd>/memory/). For each factual claim that names a specific filepath, binary, package (npm/pip/cargo), HTTP route, commit SHA, bundle hash, or test count: verify against current state via grep / curl / git / ls / which / etc. Build a delta report grouped by memory file. Each entry: [OK] | [STALE: <reason>] | [HALLUCINATED: <evidence>]. Include the verify command + its output. End with 'recommended rewrites' listing memory entries + corrected text. Under 1500 words."
```

User invokes when they smell drift OR pre-flight runner flagged ≥2 stale entries.

### E. CLAUDE.md trust-but-verify rule

Append to `C:\Users\User\.claude\CLAUDE.md` (user-global):

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

When `~/.claude/projects/<cwd>/memory/.verify-report.md` exists and contains `[STALE]` markers: surface the stale entries in your first response and offer to refresh them before doing other work.
```

## Component composition

- **Boot:** SessionStart hook runs runner → writes report → next user message reads MEMORY + BRIDGE + report; if any `[STALE]` Claude flags it in first reply.
- **Working:** trust-but-verify rule enforced; memory updates happen inline as new facts surface during work.
- **Wrap:** `/wrap` (or session-end pattern recognition) appends to `BRIDGE.md`.
- **Suspicion:** user runs `/sanity` for the deep audit.

## Files

**New:**
- `tools/memory-verify.ps1` — PowerShell pre-flight runner.
- `tools/memory-verify.sh` — bash mirror.
- `tools/memory-verify-test.ps1` + `.sh` — fixture-based tests.
- `.claude/commands/sanity.md` — slash command definition.
- `.claude/commands/wrap.md` — slash command for closing ritual.

**Modified:**
- `.claude/settings.json` — add SessionStart hook.
- `C:\Users\User\.claude\CLAUDE.md` — append trust-but-verify rule.
- `~/.claude/projects/C--Users-User-jarvis-kotlin/memory/project_jarvis_overhaul_active.md` — backfill `verify:` block.
- `~/.claude/projects/C--Users-User-jarvis-kotlin/memory/project_jarvis_2026-05-09_session_wrap.md` — backfill `verify:` block.

**Created at install:**
- `~/.claude/projects/C--Users-User-jarvis-kotlin/memory/BRIDGE.md` — bootstrapped with current state.

## Tests

- `tools/memory-verify-test.ps1` — fixture memory dir with 3 files (OK / stale / hallucinated). Run verifier, assert report content matches expected.
- `tools/bridge-template-test.sh` — unit-test YAML frontmatter parser across all `verify:` shapes.
- Manual: run `/sanity` against current jarvis-kotlin memory, capture report, fix anything flagged.

## Rollout

1. Ship `tools/memory-verify.ps1` + `.sh`. Test against this repo's memory dir manually. No hook wiring yet.
2. Add SessionStart hook to `.claude/settings.json` (project-local).
3. Backfill `verify:` blocks on the 2 project state files. Don't touch feedback files or user_identity.
4. Bootstrap `BRIDGE.md` with 1 entry covering current state.
5. Ship `/sanity` slash command + auditor prompt.
6. Ship `/wrap` slash command.
7. Append CLAUDE.md trust-but-verify rule.
8. Manual end-to-end: pretend new session, run hook, verify report renders, run `/sanity`, confirm output is useful.

## Rollback

Every component is independent. Verifier failing = report doesn't generate; session continues normally. BRIDGE missing = falls back to MEMORY.md. `/sanity` is opt-in. CLAUDE.md rule is just instruction. Zero blast radius from any single component breaking.

## Cross-platform

PowerShell primary (Alex's box). Bash mirror for VPS / WSL parity. Both scripts share the YAML parser logic via simple regex over the bounded `^---$` block (no external YAML lib dependency).

## Out of scope

- Auto-rewrite of stale memory (auditor proposes; user approves).
- Distributed memory (this is per-cwd; cross-project sync is a separate problem).
- Memory diffing across sessions (BRIDGE.md handles informally).
- Encryption / privacy (memory already local-only on Alex's machine).
- Migration of older memory files to add `verify:` (only the 2 project state files get backfilled in Slice 1; rest stays trusted).

## Self-review

**1. Placeholder scan:** none. Every section concrete; commands shown literally; file paths absolute.

**2. Internal consistency:** SessionStart hook ↔ verifier output ↔ Claude reads report ↔ trust-but-verify rule cites the same `.verify-report.md` path consistently. `/sanity` and runner do not overlap (runner is auto + cheap; sanity is manual + deep).

**3. Scope check:** 8-step rollout, ~3-5h of work. Single coherent push. Doesn't depend on or touch the Slice 1 drill workspace work — independent stream.

**4. Ambiguity check:** verify cmd timeout specified (5s). Report file path specified. Hook config example shown. Frontmatter shape backed by literal example. Rollback path defined per component.
