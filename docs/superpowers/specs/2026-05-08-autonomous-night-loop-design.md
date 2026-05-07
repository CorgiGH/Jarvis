# Autonomous overnight loop — design (2026-05-08 ~02:50 Bucharest)

> User authorized "set up a system for you to review your work, come up with new ideas from fresh agents and research properly" + going to sleep. HARD-GATE skipped per autonomous mandate; user reviews artifacts in the morning.

## Scope

5-6 hours of unsupervised work. Bias: critical-fix > new-feature. Apply council retro findings the user already endorsed. Avoid speculative breadth (council said REJECT on that pattern).

## Loop shape

Each cycle ≈30-45 min:

1. **Pick one item** from priority queue (see below)
2. **Implement** with TDD where it fits
3. **Test → commit → deploy → smoke**
4. **Self-review** via 1 council-advisor agent on the diff
5. **Apply CRITICAL fixes** inline if any
6. **Idea generation** every 3rd cycle: spawn fresh-eyes agent for next-thing
7. **Stop conditions:** deploy fails 2× consecutive · agent reports CRITICAL with no obvious fix · context budget exhausted · 6 cycles complete

## Priority queue (initial, retro-derived)

### CRITICAL (Risk Auditor): apply first
1. **JSONL rotation** — 7 ledgers grow unbounded (activity, conversations, signals, knowledge, goals, assignments, last_access, feedback). Add 10MB rotate + .1 archive.
2. **Kill-switch env** — `JARVIS_LOOPS_DISABLE=true` halts all 4 always-on coroutines (ProactiveLoop, ReflectionLoop, StateCache, BlockReminder) one flag.
3. **JSONL read-amplification** — Conversations.recentByImportance reads whole file; cap to last-N-bytes tail-read for hot path.

### HIGH (Domain Expert): swap reinventions for named precedents
4. **FSRS-5 spaced repetition** — replace exp-decay with FSRS-5 algorithm. Custom 7-day half-life is Ebbinghaus 1985; FSRS-5 is Anki default since 2024.
5. **Pomodoro 25/5 protocol** — BlockReminder fires 25-on/5-off cycles inside study blocks instead of just block-start.

### MEDIUM (User Advocate + Devil): missing actually-helpful features
6. **Concept-based study session tracker** — when study_now picks a concept, log a "session-start", then session-end on next user input. Confidence bumps on completion.
7. **Onboarding flow** — replace pre-filled schedule with `[[onboard]]` chat tool that asks user 4 questions + writes schedule.json.

### LOW (NEW IDEAS via agent)
8. Whatever the fresh-eyes agent surfaces.

## Self-review template (per cycle)

Spawn 1 `claude-council:council-advisor` agent (Devil's Advocate stance) with:
- The commit diff
- One sentence: what user really cares about (finals catch-up in 13 days)
- Question: is this the right thing right now?

If verdict REJECT + 1-line fix is obvious → fix inline. Else → revert via `git reset --hard HEAD~1` and skip to next queue item.

## Idea generation (every 3rd cycle)

Spawn `general-purpose` agent with:
- Project state summary (memory file + recent commits)
- User's stated goals (finals catch-up, AI job, weight/workout, "smarter than me")
- Question: what's the highest-leverage thing to build next that nobody else has thought of?
- Limit response to 1 idea + 50-LOC implementation sketch

## Output contract

End-of-night summary at `docs/notes/2026-05-08-night-shift-wrap.md`:
- Cycles completed
- Commits + brief description each
- Council verdicts received
- Ideas generated (whether implemented or queued)
- Stop reason
- Suggestions for what user should review/test first on waking

## Constraints honored

- No destructive ops without rollback path
- Default-OFF for any new always-on behavior (per CLAUDE.md no-modify-env without user)
- All deploys go through `bash tools/deploy.sh` (existing rollback)
- Per-cycle commit so user can `git log` + cherry-pick or revert
- No PII in council prompts (snippets summarized only)
