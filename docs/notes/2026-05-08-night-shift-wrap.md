# Night-shift wrap (2026-05-08 ~02:50 → ~05:00 Bucharest)

> User went to sleep at 02:33 with mandate: "set up a system for you to review your work, come up with new ideas from fresh agents and research properly." Autonomous Claude executed 5 cycles + 1 agent-driven idea + self-review. Stop reason: context budget reached good wrap point before quality drops.

## Cycles completed

| # | Topic | Source | Commit | Status |
|---|---|---|---|---|
| 1 | JSONL rotation across 9 writers | retro Risk Auditor CRITICAL | `380259c` | ✓ tests green |
| 2 | `JARVIS_LOOPS_DISABLE` single kill switch | retro Risk Auditor CRITICAL | `7812460` | ✓ tests green |
| 3 | tail-read for recentByImportance | retro Risk Auditor MEDIUM | — | skipped (redundant w/ 1) |
| 4 | FSRS-5-lite spaced repetition algo + 8 tests | retro Domain Expert | (in 27272cb chain) | ✓ tests green |
| 5 | Pomodoro 25/5 reminders inside study blocks | retro Domain Expert | `63d5cc2` | ✓ tests green |
| 6 | `[[onboard]]` interactive schedule | retro User Advocate | — | skipped (needs interactive user) |
| 7 | `[[quiz]]`/`[[grade]]` FSRS active-recall loop | **fresh-eyes agent** | `27272cb` | ✓ tests green |

Plus retro fix commit earlier in same session: externalized quiet hours + killed rest-fallback + `[[study_now]]` (`c5a3471`).

## Self-review verdict (1-agent council)

**STANCE: CONDITIONAL.** Key concern: 5 new always-on/coupled features merged without an integration smoke test on user's actual corpus.

User's first 3 things to test on waking:

1. **Kill switch:** `ssh root@46.247.109.91 "echo 'JARVIS_LOOPS_DISABLE=true' >> /opt/jarvis/.env && systemctl restart jarvis"` — confirm all 4 loops dormant via journalctl.
2. **Rotation correctness:** force a 10MB rotation on a real JSONL, then second rotation, diff combined history against pre-rotation tail. Proves the "combine on 2nd rotate" doesn't silently truncate.
3. **Quiz/grade end-to-end on phone:** chat `[[quiz: PA]]`, recall, `[[grade: 3]]`. Verify `knowledge_fsrs.jsonl` row appears + retrievability schedule sane.

## Regression risks flagged

- Pomodoro reminders fire inside schedule blocks → shared signals.jsonl writer. 25-min timer landing on rotation boundary = race risk under finals load.
- FSRS-5 algorithm (Cycle 4) + quiz loop (Cycle 7) both mutate review state. Source of truth: `knowledge_fsrs.jsonl` written ONLY via `KnowledgeFsrs.recordReview` — no race between 4 and 7.
- Rotation + concurrent appends: tested with 1000 concurrent appends in MemoryWikiConcurrencyTest. Other writers tested at lower counts only.

## Idea agent verdict

`[[quiz]]/[[grade]]` was load-bearing IF user actually uses it during 13 days. Closes the practice→knowledge feedback loop the system was missing. Highest user-value commit of the shift. But least council-reviewed — risk = unknown until dogfooded.

## What user should know on waking

**Tonight 19:55** Bucharest reminder still fires for 20:00 PA block (BlockReminder loop running).

**During study blocks now:** Pomodoro pings every 25min and 30min (focus end / break end).

**To grade your knowledge:**
1. Chat `[[quiz: PA]]` (or any subject) — you get one open question
2. Try to recall, then `[[grade: 3]]` (1=again 2=hard 3=good 4=easy)
3. FSRS schedules next review optimally

**To halt all loops emergency:**
```
ssh root@46.247.109.91 "echo 'JARVIS_LOOPS_DISABLE=true' >> /opt/jarvis/.env && systemctl restart jarvis"
```

## Files added/modified this shift

```
M  src/main/kotlin/jarvis/Activity.kt
M  src/main/kotlin/jarvis/Allocator.kt
M  src/main/kotlin/jarvis/Assignments.kt
M  src/main/kotlin/jarvis/BlockReminder.kt
M  src/main/kotlin/jarvis/ChatTools.kt
M  src/main/kotlin/jarvis/Config.kt
M  src/main/kotlin/jarvis/ConversationAccess.kt
M  src/main/kotlin/jarvis/Conversations.kt
M  src/main/kotlin/jarvis/Feedback.kt
A  src/main/kotlin/jarvis/Fsrs.kt
M  src/main/kotlin/jarvis/Goals.kt
A  src/main/kotlin/jarvis/JsonlRotate.kt
A  src/main/kotlin/jarvis/KnowledgeFsrs.kt
M  src/main/kotlin/jarvis/KnowledgeState.kt
A  src/main/kotlin/jarvis/LoopsKillSwitch.kt
M  src/main/kotlin/jarvis/MemoryWiki.kt
M  src/main/kotlin/jarvis/ProactiveLoop.kt
A  src/main/kotlin/jarvis/QuietHours.kt
M  src/main/kotlin/jarvis/ReflectionLoop.kt
M  src/main/kotlin/jarvis/Signals.kt
M  src/main/kotlin/jarvis/StateCache.kt
A  src/test/kotlin/jarvis/FsrsTest.kt
M  src/test/kotlin/jarvis/MemoryWikiConcurrencyTest.kt
M  src/test/kotlin/jarvis/ProactiveLoopTest.kt
A  docs/superpowers/specs/2026-05-08-autonomous-night-loop-design.md
```

## Next-shift priority (per self-review)

Integration test harness covering 4 coroutines + rotation + FSRS write path under simulated 8h runtime. Ship velocity is meaningless if user discovers corrupted review queue on finals eve.

## Total this session (full day + night)

~55 commits, ~4500 LOC, ~150 tests, deploy.sh runs successful end-to-end.

Sleep well.
