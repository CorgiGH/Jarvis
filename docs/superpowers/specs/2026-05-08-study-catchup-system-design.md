# Study catch-up system — master design (2026-05-08)

> User-driven scoping (4 questions answered):
>   - Deadline: 2026-05-21 (13 days, until finals start)
>   - Subjects: all of study-guide
>   - Schedule: manual `schedule.json`
>   - Side goals: light tracking now, intensive after finals

## North star (this batch)

System that knows what the user should be doing right now to catch up across all subjects, surfaces drift from that plan, and adapts as the user's activity reveals where they actually are.

## Components (priority order)

### S1 — Schedule (manual JSON, ~80 LOC)
- New `schedule.json` at `$JARVIS_DATA_DIR/schedule.json`.
- Schema: `{blocks: [{date: "YYYY-MM-DD", start: "HH:MM", end: "HH:MM", kind: "lecture|lab|exam|study|review", subject: str, topic: str?}]}`.
- `Schedule.kt` reads + queries: `currentBlock(now)`, `todaysBlocks()`, `nextExam()`.

### S2 — KnowledgeState (~150 LOC)
- New `knowledge.jsonl` append-only ledger. Rows: `{concept: str, subject: str, lastSeenTs, source: "lecture|chat|activity|review", confidence: 0..1}`.
- Confidence = exponentially-decayed exposure count. `KnowledgeState.staleness(concept)` = days since last touch.
- Concept enumeration: walk archival study-guide markdown; extract `## ` headings as concept catalog. One-shot at startup, cached.

### S3 — StudyPlanner (~120 LOC)
- Pure function: given (Schedule, KnowledgeState, current Activity), produces today's plan ranked by:
  - schedule blocks happening today (exact times)
  - SR queue (stale concepts past their decay threshold)
  - upcoming exam-window bias (closer exam → boost subject)
- Output: ordered list of "Block: subject — topic — rationale".
- Surfaces via `[[plan: today]]` chat tool + system-prompt block.

### S4 — Active-doc detector (~80 LOC)
- Parse activity titles for known concept names (from S2 catalog). Map active window → concept.
- Boost the concept's confidence on detection (passive review counts).
- ProactiveLoop reads "current concept"; if mismatch with `Schedule.currentBlock`, importance signal triggers nudge.

### S5 — Distraction nudge (~50 LOC)
- During a `kind=study|review` schedule block, if stress proxy scrolling-share > 0.3 in last 5 min OR active-concept = unknown (not in study-guide), emit a high-importance ProactiveSignal: "you're on social/X. Block says: revise probabilities."
- Cooldown 30 min — same as ProactiveLoop default.

## What this DOESN'T do (deferred)
- Calendar OAuth (R8 stub remains; user skipped this)
- Fitness/weight goals → light Goals.jsonl tracking only (already shipped)
- AI-job optimizer → post-finals
- Multi-modal (audio/video capture) → never (anti-pattern list)

## Acceptance criteria
- Run `[[plan: today]]` via chat → returns structured plan.
- Edit schedule.json → next chat turn sees updated plan.
- Active study-guide doc detected → KnowledgeState update visible in `knowledge.jsonl`.
- Drift from schedule block triggers a single proactive notification.

## LOC + tests budget
- Total: ~480 LOC main + ~200 LOC tests across 5 files.
- All-or-nothing approach rejected: each component ships separately, deployable mid-batch.

## Tomorrow / day-after items (NOT this session)
- Phase 5 retraining harness (post-feedback-data accumulation)
- Spaced-repetition algorithm tuning (start with simple decay; can switch to SM-2 later)
- Pedagogical adapter wiring TeachingSubsystem → KnowledgeState (next step after S2 ships)
- Schedule editor UI (today user edits schedule.json directly)

## Honest tradeoff statement

13 days is short. This batch hits the highest-leverage 80% of the user's wishlist. The "intensively above me, optimizing daily life" framing risks attention-tax during finals (notification spam, false-positive nudges). Conservative defaults: nudge cooldown 30 min, single active-doc concept boost per turn, no auto-emails until S1+S2 verify on real data. User retains kill switches (Settings screen + env flags).
