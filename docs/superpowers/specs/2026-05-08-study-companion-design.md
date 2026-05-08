# Study companion v1 — design

**Status:** draft v2 (2026-05-08, post council 1778241788)
**Trigger:** user shifted scope from "AGI harness" north star to "make jarvis usable as study companion" given UAIC finals window June 1-21, PS HW deadline May 21.
**Branch:** `main` (autonomous-authorized)
**Council history:** v1 ruled FLAWED (2 REJECT, 3 CONDITIONAL). v2 applies all CRITICAL/HIGH fixes inline (RAG quiz grounding, `archival/_extras` catalog exclusion, review-cap rethink).

## Goal

Make jarvis-kotlin a working catch-up + active progress tracker for the user's 5 active UAIC subjects (POO, PA, ALO, PS, SO+RC). Concretely: when user opens phone APK and asks "what should I study now" or "what am I behind on this week", bot returns a useful, material-aware answer backed by FSRS scheduling and the user's actual schedule.

## Current state (from audit 2026-05-08)

90% of the framework is already built:

- `ConceptCatalog.kt` walks `archival/` for `.md` files, extracts `## ` headings as concept names. Subjects = first path component.
- `KnowledgeState.kt` implements append-only `knowledge.jsonl` ledger with exponential-decay confidence (7-day half-life). Stale-detector at confidence<0.3 + ≥3d untouched.
- `KnowledgeFsrs.kt` (FSRS-5-lite) maintains `knowledge_fsrs.jsonl` for graded review state; computes due-soon queues.
- `StudyPlanner.kt` `today()` already composes today's schedule + stale review queue + 1-3 untouched concepts biased toward next exam.
- `Schedule.kt` reads `schedule.json` with `kind ∈ {lecture, lab, exam, study, review, break}`. `nextExam()` looks for `kind="exam"`.
- `ChatTools.kt` exposes `[[plan]]`, `[[next_block]]`, `[[study_now]]`, `[[wake]]`, `[[quiz]]`, `[[grade]]`, `[[assignment_set]]`, `[[calendar]]` already.
- `tools/seed_concepts.sh` hardcodes 187 concepts across POO/PA/ALO/PS/SO&RC.
- `tools/schedule_seed.json` populated for May 8-21 (lectures, labs, study + review blocks).

What's empty:
- `archival/` empty on VPS (concepts not seeded).
- `schedule.json` on VPS may be empty or out of date.
- No `kind="exam"` entries in seed → `Schedule.nextExam()` returns null → `today()` doesn't apply exam-window bias.
- No multi-day catch-up surface; only single-day `today()`.
- No course-material ingest from user's PDFs / study guides.

## Workstreams

### W1 — Concept catalog seeding (data, no code)

Run existing `tools/seed_concepts.sh` on VPS. Produces `/opt/jarvis/data/archival/{POO,PA,ALO,PS,SO&RC}/concepts.md`. ConceptCatalog auto-discovers them.

Verification: `[[stats]]` returns ~187 concepts spread across 5 subjects.

### W2 — Schedule with exam dates

Extend `tools/schedule_seed.json` with 6 `kind="exam"` rows:

- 2026-05-21 — PS (PS HW deadline; tagged as `topic: "PS HW deadline"`)
- 2026-06-05, 2026-06-08, 2026-06-12, 2026-06-15, 2026-06-19 — placeholders for ALO/POO/PA/PS/T.RC respectively, with `topic` containing `"placeholder — UPDATE WHEN CONFIRMED"`.

User edits dates when concrete schedule firms up by SSHing to VPS + editing `/opt/jarvis/data/schedule.json` directly. Schedule is manual-only — system never writes it.

Deploy: `scp tools/schedule_seed.json root@VPS:/opt/jarvis/data/schedule.json`.

Verification: `[[plan: today]]` shows today's blocks + "Next exam: PS in 13d (2026-05-21)" header from `Schedule.nextExam`.

### W3 — `[[catchup: N]]` chat tool

New method on `StudyPlanner`:

```kotlin
fun multiDay(
    days: Int,
    schedule: ScheduleFile,
    knowledgeStats: List<ConceptStat>,
    fsrsRows: List<FsrsRow>,
    catalog: List<Concept>,
    now: Instant,
    zone: ZoneId,
): List<DayPlan>

data class DayPlan(
    val date: LocalDate,
    val blocks: List<PlanItem>,
    val reviews: List<PlanItem>,
    val newCatchup: List<PlanItem>,
    val reviewDebt: Int,        // count of FSRS-due rows ABOVE the rendered cap
    val examNote: String?,      // e.g. "PS exam in 13d" if any exam in window
)
```

Algorithm per day `d` in `[today, today+days)`:

1. Pull blocks from `schedule.todaysBlocks(d)`.
2. Compute "stale" review queue at `d` (FSRS-aware: include rows whose `nextReview ≤ d`).
   - **Council v1 fix (DE):** review queue is **uncapped logically** — FSRS produces what it produces, capping below FSRS-due count creates Anki-style "review hell" backlog within ~14 days of seeding 187 concepts.
   - For renderability, render cap = 8 rows/day; surplus surfaces in `reviewDebt: Int` and renderer prints "review debt: N rows due — bulk-clear when free" line so user can decide to drain.
3. Compute "new catchup" untouched concepts: bias toward subject of next exam IF exam is within 14 days of `d`, else round-robin across subjects.
   - Renamed `MAX_CATCHUP_ITEMS` → `MAX_NEW_CATCHUP_PER_DAY = 3`. This cap stays — controls cold-start velocity (Anki/Duolingo precedent: 3-20 new/day depending on system).
4. Track which concepts have been added on prior days; skip duplicates so the 7-day plan covers 7×3 = 21 distinct catchup concepts (if catalog has that many).
5. Attach `examNote` if `Schedule.nextExam(d)` returns a block within 14 days.

Render as markdown:
```
# Catch-up plan: 2026-05-08 → 2026-05-14

Next exam: PS HW deadline in 13d (2026-05-21).

## Mon 2026-05-08
- [20:00-22:00] PA — weekend kickoff: catch-up (scheduled study)
- Review: PA / Dynamic Programming (stale 5d, conf=0.21)
- Catch-up: PS / Bayes' theorem (never touched, exam soon)

## Tue 2026-05-09
- [10:00-13:00] SO&RC — weekend catch-up (scheduled study)
...
```

New ChatTools dispatcher entry `[[catchup: N]]` (N optional, default 7, capped at 30). New `executeCatchup` private function. Pattern matches existing `executeStudyNow` / `executePlan`.

Tests:

- `StudyPlannerMultiDayTest.kt`: 5 cases.
  - Empty schedule + empty knowledge → returns N empty days but with examNote if exam exists.
  - Synthetic schedule with blocks today + tomorrow → blocks appear on correct days.
  - Stale concepts → review queue populated, capped at 5/day.
  - Untouched catalog → catch-up populated, no duplicates across days.
  - Exam in 13 days → catchup biased toward exam subject.
- `ChatToolsCatchupTest.kt`: dispatcher accepts `[[catchup]]`, `[[catchup: 14]]`, `[[catchup: 0]]` (returns "needs ≥1 day"), `[[catchup: 50]]` (clamps to 30).

### W4 — Course-material ingest

Python script `tools/ingest-pdfs.py` (~120 LOC):

- Walks user-configured paths (default: `Desktop/SO/`, `Desktop/PS laborator/`).
- For each `.pdf`:
  - Extract text via `pypdf` (pip install pypdf).
  - Detect section boundaries via regex on `^Chapter \d+|^Section \d|^[A-Z][A-Z\s]+$|^\d+\.\s+[A-Z]`.
  - Chunk by detected section. Skip chunks <50 chars (likely artifacts).
  - Write `archival/_extras/{subject}/{pdf-slug}.md` with `# Source: {filename}` + `## {section heading}` per detected section + extracted text.
- Subject inference from path: `Desktop/SO/` → `SO&RC`. `Desktop/PS laborator/` → `PS`. Configurable via JSON map.
- Idempotent: skip if output mtime > pdf mtime.
- Output total bytes / chunks per subject for visibility.

**Council v1 fix (RA CRITICAL):** output goes under `archival/_extras/{subject}/...` with **leading underscore**. `ConceptCatalog.scan()` adds a path-component filter that skips any directory whose name starts with `_`. Therefore extras are:
- ✓ Searchable via `[[search]]` (Files.walk) and `[[recall]]` (semantic) and `[[read]] archival/_extras/...`.
- ✓ Loadable as RAG context by W4b quiz-grounding (see below).
- ✗ NOT counted in `ConceptCatalog.concepts()` — preserves catchup-pool balance across subjects (otherwise SO&RC's 11 PDFs balloon to 500+ "concepts" and starve POO/ALO/PA/PS in catchup biaser, recreating council 1778078829).
- ✗ NOT counted in `[[stats]]` concept totals.

Why ingest is safe vs council 1778078829: archival is search-only after Letta-split (commit 1d7e613). [[search]]/[[recall]] are explicit tool calls; bulk ingest doesn't auto-pollute chat context. The `_extras/` filter on the catalog walk additionally keeps catchup-pool composition controlled by the curated 187-concept seed.

Risk mitigation:
- Extract path-tagged provenance (`# Source:` line) so `[[read] archival/_extras/SO&RC/X.md]` shows origin.
- Cap extraction depth (skip chunks >32KB; further chunk if needed).
- Manual review before deploy: user `cat archival/_extras/SO\&RC/*.md | head -200` to spot-check.

Defer to follow-up if first run produces bad output: degrade to W4-lite (headings-only, no body text).

### W4b — Quiz prompt RAG grounding (council v1 fix, DA+DE+FP CRITICAL)

Modify `executeQuiz` in `ChatTools.kt` (~30min, 5-15 lines):

Algorithm:
1. Existing logic picks a concept (FSRS-due first, then weakest stale, then unseen).
2. **NEW:** When concept selected, walk `archival/_extras/{subject}/*.md` looking for files containing `## {ConceptName}` heading (case-insensitive exact-string). If found, read up to 8KB of body following that heading until next `## ` or EOF.
3. **NEW:** If body found, inject into LLM quiz prompt as RAG context:
   ```
   Generate a recall question grounded in the following user's lecture material:
   ---
   [Source: {sourcePath}]
   ## {ConceptName}
   {body, max 8KB}
   ---
   Ask one open-ended recall question that tests understanding of THIS material specifically. Use UAIC-style framing if visible. Do not invent details outside the provided text.
   ```
4. **NEW:** If NO body found, fall back to current name-only prompt with prefix `[STUB - no material grounding for this concept]` rendered VISIBLY in user-facing question. Lets user immediately see which quizzes are grounded vs hallucinated.

Tests (`ChatToolsQuizGroundingTest.kt`):
- Matching `_extras/` chunk exists → quiz prompt contains body excerpt; "STUB" marker absent in render.
- No chunk → quiz prompt is name-only; `[STUB - no material grounding]` appears in user-visible question.
- Body truncation enforced at 8KB.
- Heading match is case-insensitive but exact-string (don't fuzzy-match "Markov chains" to "Markov chain").
- Multiple matching chunks across files → first by file path order (deterministic).

Why this is the keystone fix: per council DA+DE+FP, quizzing UAIC-finals user against name-only prompts produces fluent hallucination of UAIC-specific notation/framing. With grounding from user's own ingested PDFs, LLM has concrete substrate. Without grounding, the visible STUB warning makes the quality gap obvious to user (vs silently hallucinating).

### W5 — Tests, deploy, smoke

Order:

1. Implement W3 + tests locally. `gradle :test` green.
2. Implement W4b (quiz grounding) + ChatCoolsQuizGroundingTest. `gradle :test` green.
3. Commit "[[catchup]] multi-day planner + RAG-grounded quiz".
4. Implement W4 (Python ingest script). **Trial-run on ONE PDF first** (per council CONFIDENCE: data needed on output size). Verify output looks reasonable. Then run on all configured paths.
5. Update `ConceptCatalog.scan()` to skip `_*` directory components. Test that `_extras/` files don't appear in `concepts()` results.
6. Commit "PDF ingest + ConceptCatalog _extras filter".
7. Deploy: 
   - `bash tools/deploy.sh` (W3 + W4b + W4 code).
   - `ssh VPS "bash -s" < tools/seed_concepts.sh` (W1 data).
   - `scp tools/schedule_seed.json root@VPS:/opt/jarvis/data/schedule.json` (W2 data).
   - `scp -r archival/_extras/ root@VPS:/opt/jarvis/data/archival/` (W4 ingest output).
8. Smoke through phone APK (acceptance gate):
   - `[[stats]]` → expect concepts ~187 (seed only — `_extras/` excluded by catalog filter, council fix verification).
   - `[[plan: today]]` → today's blocks + next exam header + review/catchup.
   - `[[catchup: 7]]` → 7-day plan with review debt line if applicable.
   - `[[quiz: SO&RC]]` → LLM question with **visible grounding marker** (no `[STUB...]` prefix means RAG hit). Body excerpt should reflect user's actual PDF content, not generic textbook.
   - `[[quiz: ALO]]` → expect `[STUB - no material grounding]` prefix (no PDFs ingested for ALO yet — visible degradation, council acceptance signal).
   - Reply with `[[grade: 3]]` → FSRS row appended to `knowledge_fsrs.jsonl`.
   - `[[recall: process scheduling]]` → returns chunks from `archival/_extras/SO&RC/...`.

## Out of scope (defer)

- Active activity-based knowledge inference (Phase 1.1 from autonomous-resume.md). Bot still passive; user must explicitly `[[study_now]]` / `[[grade]]`.
- Proactive reminder push for "you're behind on X". R5/R6 partial scaffolding exists but unwired to study-companion data.
- Material-grounded quiz generation (LLM uses concept name only, may hallucinate). After W4, LLM at least has TOC awareness via `## headings` discovery, but quiz prompt itself doesn't yet inject ingest text.
- Multi-week revision strategy. `[[catchup: N]]` is daily. No "spaced revisitation across 4 weeks" view.

## Effort (revised post-council)

Full path with all 3 council fixes:
- W3 code + tests (with reviewDebt + uncapped review): 1.5h
- W4b quiz-grounding + tests: 30min
- W4 ingest script (incl. `_extras/` path) + 1-PDF trial run + spot-check: 2h
- ConceptCatalog `_*` filter + test: 15min
- W2 schedule edit: 15min
- W1 seed deploy + W5 deploy + smoke + 2 commits + iterate: 1-1.5h
- Buffer for PDF parsing surprises: 1h

Total: ~6-7h. Up from original 5-6h estimate; council fixes add ~45min.

If W4 PDF ingest produces bad output, fall back to W4-lite (headings-only, ~30min). W4b grounding still works on heading-only files (just less context per quiz).

**Pragmatist fallback (council preferred 90/50 cut):** ship W1+W2+W3+W5 only — disable `[[quiz]]` in `CHAT_SYSTEM_PROMPT` exposure (keep dispatcher entry, omit from prompt's tool list so LLM doesn't suggest it). User uses `[[catchup]]+[[plan]]+[[remember]]+manual study`. W4+W4b deferred to follow-up. ~3h. Avoids quiz-quality risk this session at cost of one fewer surface.

## Risks (revised post-council)

| Risk | Severity | Mitigation |
|------|----------|------------|
| **Quiz hallucination of UAIC-specific framing** (DA + DE + FP CRITICAL in v1) | **was CRITICAL → now LOW with W4b** | W4b RAG-injects body chunks when grounding exists; STUB warning when not (visible degradation, no silent hallucination) |
| **Catalog imbalance from W4 SO&RC PDF deluge** (RA CRITICAL in v1) | **was CRITICAL → now LOW** | `archival/_extras/` leading-underscore + `ConceptCatalog.scan()` skip-`_*` filter. Concepts pool stays seed-controlled. |
| **Review-cap "review hell" backlog** (DE in v1) | **was MEDIUM → now LOW** | Reviews uncapped logically; renderer caps to 8/day display + reviewDebt line surfaces overflow |
| PDF parsing varies wildly across UAIC course PDFs | Medium | Trial-run on 1 PDF first per W5 step 4; degrade to headings-only if body extract is junk |
| Archival re-pollution of chat retrieval | Low (per current Letta-split) | Confirm `buildChatContext` does NOT walk archival; smoke-test verification that chat turn doesn't include random PDF chunks |
| `[[catchup: 7]]` output too noisy with 7×8 = 56+ items | Medium | Renderer compact + reviewDebt summary; user can `[[catchup: 3]]` for shorter horizon |
| Placeholder exam dates mislead `nextExam` priority | Low | Topic field literally contains `"placeholder — UPDATE WHEN CONFIRMED"`; render in plan output so user sees the warning. `Schedule.nextExam` already filters past dates via `isAfter(today.minusDays(1))`. |
| `tools/seed_concepts.sh` runs on VPS but archival path doesn't exist | Low | Script does `mkdir -p` per subject |
| W4b prompt-injection bug → quiz never finds matches | Medium | Test cases on heading-match logic (case-insensitive exact-string); STUB fallback ensures user always gets *something*, just visibly degraded |

## Acceptance

Ship-ready when, on phone APK:
1. `[[stats]]` shows ~187 concepts (seed only — `_extras/` properly excluded; council v1 RA fix verification).
2. `[[plan: today]]` shows today's blocks AND "Next exam: ... in Nd".
3. `[[catchup: 7]]` returns a structured 7-day plan with at least 3 study-able items per day; `reviewDebt` line appears when applicable.
4. `[[quiz: SO&RC]]` (or other ingested subject) picks a concept and produces a question WITHOUT the `[STUB - no material grounding]` prefix; body excerpt visibly reflects user's PDF content (council v1 W4b verification).
5. `[[quiz: ALO]]` (no PDFs ingested) produces a question WITH `[STUB - no material grounding]` prefix — visible degradation signal proves grounding logic is wired.
6. `[[grade: 3]]` is accepted, FSRS row appended to `knowledge_fsrs.jsonl`.
7. `[[recall: process scheduling]]` returns chunks from `archival/_extras/SO&RC/...`.
8. Phone replies in <60s P95 latency for the above.

## Migration / rollback

No schema migration (reuses existing types). Rollback: `bash tools/deploy.sh rollback` reverts code; data files (archival/, schedule.json, knowledge.jsonl, knowledge_fsrs.jsonl) persist across deploys (in `/opt/jarvis/data/`, not under the rotating `jarvis-kotlin/` install dir).
