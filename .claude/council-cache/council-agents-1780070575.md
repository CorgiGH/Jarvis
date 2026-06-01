# Council — E3 scope boundary (generate + route explanation)

_2026-05-29 · deep-execution (agent-enhanced) · question: define the precise IN/DEFERRED/TRAP scope of E3 against the whole plan, so we don't prematurely build later-gate (E4+) work._

## Provider status
- 🟦 **Gemini — FAILED.** Hard quota error `limit: 0` for `generate_content_free_tier_requests` on `gemini-3.1-pro-preview` (two attempts, immediate + 8s backoff). NOT transient — free tier disabled/exhausted. The "gemini" member below is the spawning agent's **fallback** analysis (confidence low). **Load-bearing side-effect: the spec's planned cross-family critic = "Gemini Flash free" may itself be unavailable. Verify a free non-DeepSeek-family critic before building generation.**
- Internal lenses (scope-minimalist, spec-archaeologist, build-realist): all **high confidence**, strong convergence.

## SYNTHESIS — E3 scope contract

### IN scope
**Generation half**
- `DrillGenerator` (Kotlin, new — only `DrillGrader` exists today): given a KC (one of 6 PA KCs) + grounded source span + target shape → fresh `Problem{statement, kcIds=[targetKc], shape, canonicalAnswer and/or rubricItems, referenceSolution}`. Free LLM (OpenRouter `:free`).
- Safeguard pipeline (the "prove quality before real exams" bar): (1) **cross-family critic** (free, different family from generator) → confidence, gates groundedness/leakage; (2) **self-solve-to-verify** — generator solves its own problem; `canonicalAnswer` must reconcile via reused E1 `GradeScoring.answerMatches` (computational), or critic confirms rubric answerable (proof/rubric shapes); (3) **reject-don't-ship** (generation-time analog of E1 defer-don't-record); + regex answer-leak check on the stem.
- Persist generated `Problem` via the **existing** `POST /api/v1/task/{id}/prep-authored` → `problemsJson` → gradable by existing `/api/v1/drill/grade` → mastery records on server-canonical `Problem.kcIds`. **No schema change for gradability.**

**Routing half (4-part viz fix + a 5th MOUNT piece)**
1. add `viz_id` + `requires_visual` to `KnowledgeConcept` (ContentSchema.kt, additive Kotlin default — **the one schema change**, lands on the KC not the drill);
2. `vizId→component` registry mapping to the ~21 existing components (scope to PA-relevant subset);
3. `ContentValidator` **ERROR** when a `requires_visual` KC has an unresolvable `viz_id` (anti-ghost gate, `gradle check` red);
4. Playwright first-paint check on the **real** drill surface (not /viz-demo);
5. **MOUNT** the routed component on the production drill surface (DrillStack/DrillCard children in TutorWorkspace) — the often-omitted piece; per CLAUDE.md ghost-component rule, a build-it task MUST pair with a named mount-it task.
- **Mode-by-shape at ingest**: deterministic `shape → mode` map (real-visual / worked-example / symbolic-text), persisted + enforced, never per-response AI whim. Text/worked-example fallback when no registry-resolvable viz ("never fake fancy boxes").

**Acceptance (both halves proven TOGETHER on ≥1 PA KC)**
- E2E: generate → safeguards pass → persist via prep-authored → grade → **mastery moves on the right KC** (server-side, no client kcIds).
- Playwright: `requires_visual` KC surface **paints the registry-resolved component** (data-testid visible), zero 4xx/5xx first paint, click-through no 404/error text (interaction-smoke gate).
- Unit: unresolvable `viz_id` → validator ERROR; mode-by-shape table-tested; generator reject path drops bad problems.

### DEFERRED (E4+ / stale)
- 5-phase drill traversal (study loop) · FSRS-on-drill-grade (separate route) · voice (parked) · N=1 Thompson / GEPA judge (optimization; GEPA-on-Opus also breaks free-LLM lock) · learner profile + ADHD-mode + exam-awareness (E4 explicit) · Postgres pedagogy/templates tables (STALE — trust the code, JSON-blob) · prereq-gating in mastery (E4 sequencing) · real-content authoring other subjects (owner-locked) · full 21-viz × 5-subject coverage · EWMA→BKT/PFA replacement · DSPy agent zoo (MisconceptionDetector/IdeaAgent/DeepSolve/etc.).

### TRAPS
- **Look-E3-are-E4**: 5-phase traversal, FSRS-on-grade, Thompson/GEPA, **the Postgres `templates` table** (looks like THE generation mechanism; it's a stale persistence migration — freeform+critic+self-solve over JSON blobs is the E3 path).
- **Look-later-are-E3 (under-scope killers)**: self-solve-verify (without it, drills ungradable → silent mastery corruption), `viz_id`+validator-ERROR (without it, components stay ghosts), the **production MOUNT + Playwright paint-check** (without it = ghost a third time), and the **cross-family critic must actually be cross-family** (same-family = false confidence; same lesson that killed E2 "two machine reads").
- **Borderline**: video prerequisite-aware nodes — E2 §2 defers them to E3 *by name*, but they need prereq-gating (doesn't exist) and add no fixture-proof value → **E3-optional tail; push to E4 if the pass runs long.**

### BIGGEST RISK (unanimous, bidirectional)
Too-wide on generation (swallow the E4 study loop under "generate the right drill") burns the pass on unprovable loop work; too-narrow on routing (declare done because components paint in /viz-demo) ships the **ghost-component trap a third time** (already burned: Slice 1, Slice 1.5). Hold the line: generation = generator+cross-family-critic+self-solve onto the existing prep-authored path; routing = 4-part-fix + mount + mode-by-shape — proven by an end-to-end mastery-moves round-trip AND a production-surface Playwright paint+smoke gate.

### Cross-cutting open risks to carry into the plan
1. **Gemini free critic DEAD** — pick + verify a free non-DeepSeek-family critic via OpenRouter (Qwen/Llama/Mistral `:free`) before building. (gws-lesson: verify the model endpoint is actually reachable.)
2. **PA is proof/symbolic-heavy** (formalize→implement→prove→analyze) — the 6 PA KCs may NOT exercise the real-visual paint path; the viz-paint gate might only be provable against a borrowed/synthetic visual KC. The `shape→mode→component` mapping is itself an undefined E3 design decision.
3. **E2 PA re-grounding (Task 3b) NOT done** — KCs span-less; generating "grounded" drills inherits ungrounded inputs, and a `requires_visual` ERROR gate could turn the corpus red. Sequencing dependency on the E2 grounding baseline.
4. self-solve with the **same** model = correlated-error risk; cross-family critic mitigates, doesn't eliminate (accepted frontier).
5. proof shapes have no `canonicalAnswer` → self-solve degrades to self-produce-rubric; weaker bar than computational.
6. `DrillCard` children prop-shape vs the components' generator-prop API = integration risk (component-reuse-contract rule).
7. verify OpenRouter `:free` model actually wired for generation in current code.

---

## Member outputs (structured)

### 🟦 Gemini (FALLBACK — external failed, confidence LOW)
See provider status. Converged with the internal lenses on every verdict; flagged the Gemini-free-tier death as its top blind spot.

### Scope-minimalist (confidence HIGH)
Smallest coherent E3 provable on 6 PA KCs / fixtures. generation = generator + ONE cross-family critic + self-solve, emitting Problems on prep-authored. Routing = 4-part fix + mode-by-shape; scope registry to PA-relevant components only. Video nodes E4. Cross-family pairing is load-bearing (same-family voids the safeguard). drillsJson stays vestigial.

### Spec-archaeologist (confidence HIGH)
Mapped every candidate to spec/roadmap. Added the **MOUNT-IT task** as a mandatory 5th routing piece (CLAUDE.md rule). Flagged templates/mastery_pfa/Postgres §5 as STALE double-builds. Video nodes "in-scope-by-decree" (E2 §2 names them) but lowest priority → recommend E4. Added regex answer-leak check (spec §7.3.2).

### Build-realist (confidence HIGH)
Guarded under-scoping: both halves must be proven TOGETHER end-to-end on the production surface, not separately-green in units. Named concrete mount site (DrillStack/DrillCard children in TutorWorkspace). reject-don't-ship = generation-time analog of E1 defer-don't-record. Hard acceptance = E2E mastery-moves + Playwright paint + interaction-smoke.
