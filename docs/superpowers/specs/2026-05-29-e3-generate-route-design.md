# E3 — Generate + Route Explanation (design, rev 2)

_2026-05-29 · roadmap position: E1 grader+mastery (done) → E2 ingest+classify (code spine done) → **E3 generate+route** → E4 study loop. Direction: `memory/project_reorientation_teaching_engine.md`._
_Rev 1 scope vetted by council `.claude/council-cache/council-agents-1780070575.md`. **Rev 2 = post spec-review revision** (`.claude/council-cache/council-agents-1780072900-specreview.md`): the review found rev 1 walked INTO the ghost-component trap (generated drills graded server-side but never painted) + several "rides the existing path" claims that were false against the code. Rev 2 fixes all of it. External Gemini failed both rounds (free tier dead — `limit:0`); verdicts are 4 grounded high-confidence reviewers._

## 0. What + why (corrected)

E3 gives the tutor two abilities: (1) **generate** fresh practice drills for a knowledge concept (KC) instead of only copying problems from exam PDFs; (2) **route** each concept to the right explanation surface (real interactive visualization / worked-example / symbolic text) **and actually mount it on the student-facing drill screen**. Free LLMs only.

The two halves are **proven on two independent tracks** (they share no causal link — see §9): **Track A** = a generated drill grades server-side and moves mastery (Kotlin E2E); **Track B** = a drill + its routed visual actually paint on the real surface (Playwright). Rev 1 falsely bound them into one "proven together" claim. **Honesty note:** the 6 PA KCs are all `bloom: understand/apply`, definitional/proof — they carry **no visuals, no `span` anchors, and no numeric answers**. So Track B's visual path is proven via a **required** borrowed visual fixture KC (§7), the strong numeric self-solve leg is exercised by a computational fixture (not a real PA KC), and grounding rides the existing `quote` text (spans stay deferred to E2 Task 3b).

## 1. Scope contract

### IN
**Generation half**
- A new `DrillGenerator` (Kotlin) that, given a KC + its source `quote`(s) + a target `shape`, produces a **drill bundle**: a gradable `Problem` (for `problemsJson`) AND its renderable `DrillContent` (for `drillsJson`) — see §2. The generator emits the render fields (`drill`/`worked`/`definition`/`check`/`expectedAnswerHint`) too, not just grading fields.
- A **safeguard pipeline** gating every drill before persist: (1) **cross-family critic** (independent model, different family — §4) returns `{confidence, grounded, leak, solvable}`; (2) **self-solve-to-verify** — generator solves its own problem; computational shapes reconcile the produced `canonicalAnswer` via the reused `GradeScoring.answerMatches` (GradeScoring.kt:33); non-computational shapes fall to the critic confirming the rubric is answerable; (3) **reject-don't-ship** (generation-time analog of E1's defer-don't-record); (4) **answer-leak regex** on the stem.
- A new `POST /api/v1/task/{id}/generate-drills` route that **read-merge-writes** (appends, never clobbers — §2) the bundle into both stores, then the **existing** `/api/v1/drill/grade` (TutorRoutes.kt:1796) grades it on server-canonical `Problem.kcIds`.

**Routing half (4-part viz fix + concrete MOUNT)**
1. `viz_id: String?` + `requires_visual: Boolean = false` on `KnowledgeConcept` (ContentSchema.kt) — additive, Kotlin-defaulted. **The one Kotlin schema change.**
2. A **shared, language-neutral viz-id source of truth** (`content/viz-ids.yaml`, checked in): the Kotlin validator reads it; a TS test asserts the `vizRegistry.ts` covers exactly it (§5). Closes the "JVM can't see TS" gap.
3. `ContentValidator` raises an **ERROR** (rule `viz_reference`, turns `gradle check` red via the existing `validateContent` over real `content/`, build.gradle.kts:~170-181) when a `requires_visual` KC's `viz_id` is missing or absent from `viz-ids.yaml`.
4. A **Playwright harness stood up from scratch** (none exists in the repo today — its own task) + a first-paint/interaction-smoke check on the **real** drill surface.
5. **MOUNT:** add `vizId?: String` to `DrillContent` (DrillStack.tsx:9-24); in `DrillStack`, when `content.vizId` is set, render the `vizRegistry`-resolved component inside a panel stamped `data-testid="routed-viz-<vizId>"`. The generate route sets `DrillContent.vizId` from the KC's `viz_id` when mode resolves to `real-visual` (§6).

**Mode-by-shape (computed, not stored, not AI-whim):** §6.

### DEFERRED — E4+ / stale
5-phase traversal · FSRS-on-drill-grade · learner profile + ADHD-mode + exam-awareness · N=1 Thompson / GEPA · voice · Postgres `templates`/`attempts`/`fsrs_cards`/`mastery_pfa` tables (spec §5 STALE — JSON-blob persistence is reality) · prereq-gating in mastery · EWMA→BKT/PFA · DSPy agent zoo · full 21-viz × 5-subject registry · real-content authoring for other subjects (one borrowed visual fixture KC excepted, §7) · E2 PA span re-grounding (Task 3b).

### TRAP
- **Look-E3, are-E4:** 5-phase traversal, FSRS-on-grade, Thompson/GEPA, the Postgres `templates` table (stale persistence — freeform+critic over JSON blobs is the E3 path).
- **Look-later, are-E3 (under-scope killers):** self-solve-verify; `viz_id`+validator-ERROR; **the production MOUNT + Playwright paint-check**; the critic actually being **cross-family** (env-var collision below would silently make it same-family Llama — §4).
- **Resolved-DEFER (DEC-3):** video prerequisite-aware nodes — E2 §2 defers them to E3 by name, but they need prereq-gating (absent, E4) and add no fixture-proof value → **pushed to E4** (not in E3).

## 2. Data model + persistence — the two-store reality

A fully-working drill needs an entry in **BOTH** JSON stores, keyed by `problemId`:
- `TaskPrep.problemsJson` = **grade store**: `List<Problem>` (PdfProblemExtractor.kt:17-30 — `problemId, page, statement, equationRefs, dataGivens, kcIds, rubricItems, referenceSolution, canonicalAnswer, shape`). The grade route resolves the server-side `Problem` by `(taskId, problemId)` and records mastery on `Problem.kcIds`.
- `TaskPrep.drillsJson` = **render store**: `Record<problemId, DrillContent>`. `DrillContent` (DrillStack.tsx:9-24) = `{drill, worked, definition, check, expectedAnswerHint, language?, referenceSolution?, rubricItems?}` — rendered by `DrillStack` (4 cards DRILL→WORKED→DEFINITION→CHECK), and `DrillStack` mounts **only if** `drillsByProblem[problemId]` exists (TutorWorkspace.tsx:114-116, 205). **This is why rev 1 was a ghost: it left `drillsJson="{}"`, so generated drills never rendered.** Rev 2: `drillsJson` is the render store, populated by generation. To mount a viz, `DrillContent` gains `vizId?: String` (§1.5).

**`Problem` → `DrillContent` mapping the generator must satisfy:**
| DrillContent field | source |
|---|---|
| `drill` | the generated problem statement |
| `worked` | a generated worked solution/explanation |
| `definition` | the KC's definitional sentence (from the KC `name`/`quote`) |
| `check` | a generated self-check question |
| `expectedAnswerHint` | a generated hint (passed to grader) |
| `language?`/`referenceSolution?`/`rubricItems?` | from the generated `Problem` (code/grading) |
| `vizId?` (new) | the KC's `viz_id` when mode == `real-visual` (§6) |

**Persistence semantics (CORRECTED — rev 1 was false here):** `prep-authored` does a full `prepRepo.upsert(...)` that **overwrites** the whole `problemsJson` (TutorRoutes.kt:~1395-1400; `ApiPrepAuthoredRequest(problems: List<Problem>, version: Int = 1)`, TutorRoutes.kt:~2374). So the generate route **MUST read-merge-write**: load existing `problemsJson` + `drillsJson`, append/replace by `problemId`, re-encode both, upsert. A naive reuse of `prep-authored` would **wipe** E2 authored drills (commit 88a7134). Same clobber applies to `/reprep` (TutorRoutes.kt:~1341-1358 writes a fresh `problemsJson` + `drillsJson="{}"`).

**`/reprep` guard (committed decision — no longer "minimally a log"):** `/reprep` becomes **kcId-preserving merge** — it must not overwrite any existing `Problem` that carries non-empty `kcIds` (authored or generated). New PDF-extracted problems append; kcId-bearing problems survive. Testable contract: after `prep-authored` then `/reprep`, the kcId-bearing problems are still present with their `kcIds`/`rubricItems`/`canonicalAnswer` intact.

**Schema additions, total:** Kotlin — `KnowledgeConcept.{viz_id, requires_visual}`. TS — `DrillContent.vizId`, and the frontend `Problem` type (TutorWorkspace.tsx:~18-24) + the prep payload gain `shape` + `viz_id` so the router can read them client-side. `Problem` (Kotlin) is unchanged (`shape` already exists). No new persistence type, no Postgres tables.

## 3. Generation architecture

```
DrillGenerator.generate(kc, sources, shape, count) → List<DrillBundle>   // DrillBundle = (Problem, DrillContent)
  for each requested drill:
    1. generatorLlm.complete(SHAPE_PROMPT[shape], kc, sources)  → candidate bundle (NEW parser, §8 — NOT parseLlmJson)
    2. answer-leak regex on candidate.statement                 → fail ⇒ reject
    3. self-solve:
         computational (canonicalAnswer != null):
            generatorLlm solves; GradeScoring.answerMatches(canonicalAnswer!!, solved) → mismatch ⇒ reject
         non-computational: skip numeric reconcile
    4. criticLlm.review(candidate, kc, sources) → {confidence: Double, grounded, leak, solvable}
         confidence < THRESHOLD || !grounded || leak || !solvable ⇒ reject
    5. accept ⇒ DrillBundle(Problem{kcIds=[kc.id], shape, rubricItems, referenceSolution, canonicalAnswer}, DrillContent{...§2})
  return accepted (may be < count; LOG accepted/reject counts + per-reject reason — no silent cap)
```

- **NEW output parser (CRITICAL — rev 2):** `PdfProblemExtractor.parseLlmJson` (PdfProblemExtractor.kt:33-51) **drops** `kcIds`/`shape`/`canonicalAnswer`/`rubricItems`. Reusing it = silently ungradable drills. The generator needs its own parser capturing all grading + render fields.
- **LLM seam (CRITICAL — rev 2):** add an injectable `drillGeneratorLlmFactory` + `drillCriticLlmFactory` (mirroring the existing `drillGraderLlmFactory`, TutorRoutes.kt:106) so Track-A's E2E uses fake LLMs and is deterministic (the repo's pattern; E2LoopSmokeTest.kt uses `FakeGraderLlm`). No live-network test in CI.
- **Why freeform-with-safeguards, not the templates table:** the master spec `templates` table (lines 174-177) is the stale Postgres §5 design; persistence is JSON blobs. The cross-family critic + self-solve IS the locked "prove quality before real exams" bar. Not both in E3.

## 4. LLM wiring (verified + corrected)

- **Generator:** `OpenRouterChatLlm`, default `meta-llama/llama-3.3-70b-instruct:free` (OpenRouterChatLlm.kt:73; family Meta). _Note: the class docstring (lines 39-42) still says `google/gemini-2.0-flash-exp:free` — STALE; trust line 73._
- **Critic (owner-chosen, DEC-1 RESOLVED = relay-only): Claude via the relay, NO fallback.** `critic = RelayLlm()` directly (Llm.kt's `FallbackLlm` is NOT used for the critic — the relay→copilot composite there is unrelated). The critic runs **only at drill-generation time** (a deliberate, occasional prep action — never during study/grading), and the owner generates with the home PC on, so a fallback is unnecessary. If the relay is unreachable at generation time, the generate route returns a clear error and the caller retries — generation is not blocked silently, just deferred to when the PC is on.
- **Cross-family holds without any OpenRouter critic:** generator = `OpenRouterChatLlm` Llama (Meta family); critic = `RelayLlm` Claude (Anthropic family). Different families → the safeguard is real. (The rev-1 env-var-collision risk is **moot** now — there is no OpenRouter critic sharing `JARVIS_OPENROUTER_MODEL`. Keep a cheap runtime check that the critic provider tag is the relay/Claude, logging loudly if it ever isn't.)
- **Verify-and-pin first (gws-lesson, hard gate before any generator code):** ping live and pin in the plan — generator `llama-3.3-70b:free` answers via OpenRouter; relay (`JARVIS_RELAY_URL`/`TOKEN`, home PC awake) answers with a Claude reply. (No fallback id to pin — DEC-1 is relay-only.)
  - **Task 0 result (2026-05-30):** OpenRouter key authenticates (✓); `meta-llama/llama-3.3-70b-instruct:free` is a real, currently-listed model (✓). BUT every free model pinged (llama-3.3, qwen3-next-80b, gemma-4-31b) returned HTTP 429 "rate-limited upstream" (`retry_after ~12s`, `is_byok:false`) — a free-TIER account-wide throttle, not a model outage. `OpenRouterChatLlm` already retries 429. Free-tier generation reliability is the accepted frontier (§11); mitigate with credits/BYOK or the relay. Critic-relay (`RelayLlm` Claude) ping NOT run — needs the owner's home PC + `JARVIS_RELAY_URL`/`TOKEN` (absent locally); owner runs plan Task 0 Step 2.
  - **Task 0 Step 2 + real-model proof (2026-05-30, this session):** ✓ **DONE.** The dev box itself runs `claude` CLI, so the relay (`tools/pc-relay-server.py`) was started **locally** (`127.0.0.1:9999`, wrapping `claude --print`) — no separate home PC needed. Relay `/healthz` + `/complete` → 200. **Generation pipeline PROVEN on a real model (claude-max via relay, used for BOTH generator and critic since Llama is 429'd):** new env-gated test `src/test/kotlin/jarvis/tutor/E3RealRelayProofTest.kt` (skips in CI without `JARVIS_RELAY_URL`). (a) CORE — `DrillGenerator.generate` authored a correct, grounded, safeguard-passing computational drill (height 5 → 63); (b) E2E — through the real `POST /generate-drills` route → ≥1 accepted + persisted server-canonical `Problem` → `/drill/grade` → `KcMastery.observations=1`. **Reliability findings → `docs/superpowers/findings/2026-05-30-real-relay-generation-proof.md`** (self-solve match brittleness on chatty model output = false-rejects; occasional critic JSON parse-failure + leak false-positive; real grader emits correct-vs-rubric inconsistency that the trustworthy layer correctly defers). Relay operational fix: spawn `claude` from a clean cwd + `--strict-mcp-config` (else MCP/hook cold-start hangs 40–60s+); added additive `JARVIS_CLAUDE_ARGS`/`JARVIS_CLAUDE_CWD` env to `pc-relay-server.py`.

## 5. Routing architecture

- **Schema:** `KnowledgeConcept.{viz_id: String?, requires_visual: Boolean = false}` (ContentSchema.kt, additive).
- **Shared viz-id source of truth:** `content/viz-ids.yaml` (checked in) lists every valid viz id. (a) `ContentValidator` reads it for the `viz_reference` rule; (b) a Vitest test asserts `vizRegistry.ts`'s keys == this list (keeps Kotlin + TS in sync). This is the bridge rev 1 lacked.
- **Validator rule `viz_reference`:** `requires_visual` KC with `viz_id` null or ∉ `viz-ids.yaml` ⇒ ERROR. Enforced by a test that loads the **live** `content/` through `validate(...)` (not just fixtures).
- **Registry:** `vizRegistry.ts` maps id → component for the PA-relevant + fixture components (not all 21). Most existing viz components are **zero-prop static illustrations** (e.g. `RecursionTree(): ReactNode`, a fixed fib(5) trace, RecursionTree.tsx:445) — a routed visual is a **concept-level animation, not a render of the specific generated problem**. That is acceptable for proving routing; stated plainly so no one expects per-problem viz.
- **Mount:** `DrillContent.vizId?` → `DrillStack` resolves `vizRegistry[vizId]` and renders it in a panel with `data-testid="routed-viz-<vizId>"`. `data-testid="drill-card"` already exists (DrillCard.tsx:52).
- **Playwright (build from zero):** install + config + harness wiring is a task. Then assert against the **real** drill surface (not `/viz-demo`): selectors visible, zero 4xx/5xx first paint, click-through no `/404|HTTP \d{3}|not found|error/i`.

## 6. Mode-by-shape mapping

Mode is **computed** (pure function of `Problem.shape` + the KC's `viz_id`/`requires_visual`) — never an LLM per-response choice, and **not a new persisted field** (derivable; surfaced to the client via the payload additions in §2).

| `shape` | mode | uses `viz_id` | fallback (no viz) |
|---|---|---|---|
| `computational` | real-visual | yes | worked-example |
| `analysis-trace` | real-visual | yes | worked-example |
| `design-implement` | worked-example | no | worked-example (code) |
| `proof-derivation` | worked-example | no | symbolic-text |
| `fact-conceptual` | symbolic-text | no | symbolic-text |

`requires_visual=true` asserts "must resolve to a real visual" (validator errors if not). Default `false` keeps the 6 span-less PA KCs green (they route to worked-example/symbolic-text, no visual forced).

## 7. The DEC-2 required visual fixture (promoted from "open" to REQUIRED)

Track B's visual paint-path is **unreachable on the real PA corpus** (all 6 KCs definitional, no visuals). So routing acceptance REQUIRES one borrowed visual fixture KC. Full spec, satisfying every existing `ContentValidator` rule:
- `id: pa-kc-fixture-recursion` (or a clearly-marked fixture id), `subject: PA`, bilingual `name_ro`/`name_en` (bilingual gate).
- `exam_weight: 0.0` — the 6 PA KCs already sum to 1.00; the sum must stay 1.0±0.02 (`checkExamWeights`), so the fixture carries 0 weight.
- A **prereq edge** to a tier-1 PA root in `edges.yaml` — else `detectOrphans` errors (MAX_HOPS=8 from roots).
- At least one **source ref with a real `quote`** present in `_sources/` — empty source list is an ERROR (`checkVerbatimSources`, ~line 179). Span optional (standard tier ⇒ warning, not error).
- `requires_visual: true`, `viz_id: recursion-tree` (listed in `viz-ids.yaml`, mapped in `vizRegistry.ts` → `RecursionTree`).
- A generated (or hand-seeded) `shape: analysis-trace` drill for it so mode → `real-visual` → mounts `RecursionTree`.

This fixture is the ONLY thing that exercises the `real-visual` row end-to-end; without it the paint-gate never fires. **A computational fixture KC** (numeric `canonicalAnswer`) is likewise needed so the strong self-solve reconcile leg (§3 step 3) is exercised — the existing E2 smoke uses a synthetic "compute 6×7→42" (E2LoopSmokeTest.kt:124), not a real PA KC.

## 8. Contracts / DTOs (so the plan can TDD)

- **`POST /api/v1/task/{id}/generate-drills`**
  - Request: `{ kcId: String, shape: String? (default per KC→shape table), count: Int }`.
  - Response: `{ taskId, accepted: List<{problemId, shape}>, rejectedCount: Int, rejectReasons: List<String>, criticUsed: String, generatedAt }`.
- **KC → shape assignment:** the route resolves a target `shape` per KC. Source: the `shape?` request param if given, else a per-KC default table (KCs carry no `shape`; it lives on `Problem`). The plan defines the default (e.g. PA's foundational KCs → `proof-derivation`/`fact-conceptual`; the fixture → `analysis-trace`/`computational`).
- **Critic return:** `{ confidence: Double (0.0–1.0), grounded: Boolean, leak: Boolean, solvable: Boolean }`; reject if `confidence < THRESHOLD` (pin a constant, e.g. 0.7) OR any flag fails. JSON parser with brace-balance resilience (mirror DrillGrader's parse).
- **Shape-keyed prompt skeletons:** one template per shape (computational | proof-derivation | design-implement | analysis-trace | fact-conceptual). Each states: KC name/definition + source `quote`, bloom level, difficulty, output JSON schema (the full bundle incl. `kcIds`, `canonicalAnswer` for computational, `referenceSolution` for code, plus render fields `worked`/`definition`/`check`/`expectedAnswerHint`), and a "do not leak the answer in the statement" instruction. The plan writes the actual template text.
- **Frontend plumbing:** extend the TS `Problem` type + the prep payload with `shape` + `viz_id`; extend `DrillContent` with `vizId?`; a routing container that stamps `routed-viz-<vizId>`.

## 9. Acceptance — TWO independent tracks + sequencing

**Prerequisite gates (block the relevant track until green):**
- P1 — §4 verify-and-pin (generator `llama-3.3-70b:free` + relay Claude critic; relay-only per DEC-1) ⇒ blocks all generation code.
- P2 — Playwright harness stood up + first run green ⇒ blocks Track B.
- (E2 PA span re-grounding is NOT a prerequisite — generation grounds on `quote` text; spans stay deferred.)

**Track A — generation grades + moves mastery (Kotlin, server-side, deterministic via the LLM seam):** generate a drill for a computational fixture KC → safeguards pass (with `FakeGenerator`/`FakeCritic`) → read-merge-write persists into both stores → POST `/api/v1/drill/grade` with the client sending **no** kcIds/canonicalAnswer → assert `KcMasteryRepo` observation moves on the correct KC. Plus unit tests: wrong-answer candidate rejected by self-solve; leaking stem rejected by regex; low-confidence critic drops the drill; reject count logged; the new parser keeps kcIds/shape/answer; `/reprep` kcId-preserving-merge contract.

**Track B — drill + routed visual paint on the real surface (Playwright):** seed the borrowed visual fixture KC + its drill bundle (both stores) → open the **real** drill surface → assert `[data-testid="drill-card"]` AND `[data-testid="routed-viz-recursion-tree"]` visible on first paint, zero 4xx/5xx, click-through no error text. Plus: `requires_visual` KC with unresolvable `viz_id` ⇒ `ContentValidator` ERROR (corpus-loading test); `viz-ids.yaml` ↔ `vizRegistry.ts` parity test; the mounted component renders without runtime error (component-reuse contract — show `RecursionTree`'s zero-prop signature + the mount-site JSX; `tsc --noEmit` on the diff).

**Build order:** schema (`viz_id`/`requires_visual`, `DrillContent.vizId`, payload) → shared `viz-ids.yaml` + validator rule + registry → `DrillGenerator` + new parser + critic construction + LLM seams + generate route (read-merge-write) → `/reprep` guard → fixture KCs → Track A E2E → Playwright stand-up → Track B paint+smoke.

## 10. Decisions (all resolved)
- **DEC-1 RESOLVED = relay-only.** Critic = `RelayLlm()` (Claude), no fallback. The critic runs only at generation time and the owner generates with the home PC on; relay unreachable ⇒ generate route errors + retry (§4).
- **DEC-2 RESOLVED = required.** The borrowed visual fixture KC (+ a computational fixture) is in scope (§7), not an open choice.
- **DEC-3 RESOLVED = push to E4.** Video prerequisite-aware nodes are NOT in E3 — they depend on prereq-gating (E4) and add no fixture-proof value.

## 11. Accepted frontier (named, not solved)
- Free-model generation on math-dense proof shapes: the critic may pass subtly-wrong derivations (E2 §9 resolvable-but-wrong). Cross-family critic + self-solve is **necessary, not provably sufficient** — which is why real content stays deferred until these gates are observed working on fixtures.
- Self-solve uses the **same** model as generation (correlated-error risk — the lesson that killed E2's "two machine reads"). The **cross-family critic** is the independent mitigation; self-solve alone is not.
- Proof shapes have no `canonicalAnswer` → self-solve degrades to self-produce-rubric; gradability rests on rubric quality (weaker than numeric).
- Routed visuals are **concept-level static animations**, not renders of the specific generated problem.

## 12. Out of scope (guardrails)
No study-loop sequencing, no FSRS-on-grade, no learner profile/ADHD-mode, no Thompson/GEPA, no voice, no Postgres pedagogy/templates tables, no prereq-gating, no EWMA replacement, no E2 span re-grounding, no video prerequisite-aware nodes (DEC-3 → E4), no critic fallback (DEC-1 → relay-only), no real-content authoring beyond the PA fixtures + the two required fixture KCs (§7).

## 13. Changelog
- **rev 2.1 (2026-05-29):** decisions resolved — DEC-1 relay-only (critic = `RelayLlm()` Claude, no fallback; runs at generation time only); DEC-2 required visual+computational fixtures; DEC-3 video nodes pushed to E4. §4/§10/§12 updated.
- **rev 2 (2026-05-29):** post spec-review. Fixed the ghost-trap (generation now writes the render store `drillsJson`, with a concrete `DrillContent.vizId` mount in `DrillStack`); corrected the false "rides the existing path" claims (prep-authored OVERWRITES → read-merge-write; `FallbackLlm` relay→OpenRouter is NOT pre-wired → explicit construction + env-var-collision fix + cross-family runtime assert); added the shared `viz-ids.yaml` Kotlin↔TS bridge; promoted DEC-2 to a required visual fixture KC (+ a computational fixture) with full validator-constraint spec; dropped the "grounded spans" overclaim (ground on `quote`); split acceptance into Track A (server) + Track B (paint) with prerequisite gates; added all missing contracts (generate DTO, critic return, prompt skeletons, KC→shape, new parser, LLM seams, frontend plumbing); committed `/reprep` to a kcId-preserving merge; flagged Playwright as unbuilt (own task) and the stale OpenRouter docstring.
