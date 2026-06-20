# Jarvis Tutor — Full Project Handoff Prompt

> A complete, self-contained briefing on the `jarvis-kotlin` project. Paste this into another model/agent to bring it fully up to speed on what we're building, why, how, with what, and where it stands. Everything below is grounded in the repo's binding spec, locked master plan, frozen interface lock, verified subject sweep, and a live codebase map (all cited at the end).

---

## 0. TL;DR

We are building a **personal, AI-powered university tutor** for one learner (a Romanian AI-bachelor student at UAIC). It ingests *any* course material (PDF, URL, scan, notes), automatically turns it into **correctness-verified, interactive lessons, drills, and mock exams in Romanian**, and tracks mastery with spaced repetition — for all of his subjects.

The defining design principle: **machines verify TRUTH; the human only approves EXPERIENCE.** The student can never be asked to judge whether content is correct (that's the whole reason the tutor exists). Correctness is proven by non-LLM oracles, code execution, and deterministic gates — never by an LLM judging itself.

Tech: **Kotlin/Ktor backend + React 19/TypeScript frontend (Vite) + SQLite, single self-hosted JVM.** Local/free LLMs only (no paid APIs). Currently ~1050 commits; the verification/lesson/practice substrate is built and CI-green on `main`; the bulk ingestion→generation half and full-content rollout are the remaining work; **nothing is deployed yet (deploy is deliberately last).**

---

## 1. North-star context

**Jarvis is conceived as a personal "life-OS."** The tutor (`jarvis-kotlin`) is its **first and only active vertical.** Planned-but-deferred sibling verticals (do NOT build unless explicitly opened): nutrition/cook, finance, career, life-aware scheduler. Design the tutor so it can later wire cleanly into a bigger Jarvis — but do **not** build integration scaffolding for that now, and do not hard-couple.

**The learner (the single user):**
- Romanian, AI-bachelor student at UAIC (Universitatea Alexandru Ioan Cuza, Iași), program **BScIA** (Inteligență Artificială).
- Unmedicated ADHD; self-describes low programming confidence and a bad "ask-AI, read backwards, never learn" loop he wants out of.
- **Cannot vet subject content himself** — he can't tell if a generated lesson is correct, and can't name a subject's pedagogy. He only ever supplies *raw files*. This is load-bearing for the whole design (see §3).
- Teaching style he responds to: intuition-first, "no walls," predict→reveal, I-do/we-do/you-do, real visuals (3Blue1Brown energy), one concept at a time.

**Language law:** All *teaching* content is in **Romanian**. All *project/meta* discussion (architecture, code, planning) is in **English**. Quoted source spans stay in their original language, embedded inside Romanian framing. Practice code uses the **exam's** language (Alk for PA, R for PS, C++ for POO, bash/POSIX for SO).

---

## 2. The one-pass digestion pipeline (the whole system, one loop)

Everything flows through exactly **one** path from raw material to learner. No side doors, no hand-authored lesson bypass, no "just this once" direct DB insert.

1. **UPLOAD** — user drops a file or pastes a URL. Nothing else is asked.
2. **CLASSIFY** — by *content* (never filename) into one of **9 artifact kinds** (§4). Every artifact gets tagged with subject + exam-component; SORC items also get an SO-vs-RC domain tag.
3. **DIGEST** — decompose into: knowledge concepts (KCs), per-beat teaching content, problems with parameter slots, rubrics, grade-model facts, dates — all generated in the learner's language.
4. **VERIFY** — machine-only gates run (admission trust-net + semantic figure gates + rendered Playwright gates). **No human verifies truth.**
5. **SERVE** — 5-beat lessons, drills, code practice, mock exams, via a priority queue.
6. **TRACK** — every learner interaction writes unified mastery + spaced-repetition (FSRS) state; exam dates shape priority.
7. **REVISE** — forgetting triggers a compressed re-lesson with *fresh* parameter values; a fired misconception queues a targeted remediation drill.

**Invariant:** every row of servable content is `VERIFIED`; flipping a row unverified must make the serve API refuse it.

---

## 3. The verification law (the most important rule)

> **Machines verify TRUTH** (faithfulness to source, trace correctness, render correctness). **The user approves EXPERIENCE** (does this teach well, does it feel right). Neither party does the other's job. **The user never iterates on correctness.**

- A hard failure becomes a **gap-record**: an in-app card that asks the user for **FILES, never judgments.** "Upload the seminar sheet for week 6" is legal. "Is this proof correct?" is **forbidden** (the no-oracle-inversion rule).
- **Why LLM-as-judge is distrusted for correctness:** truth has computable answers (numerics, code execution, proof structure). An LLM judges *prose* fluency only and is *never* the sole scorer. When an LLM must judge, it is always paired with a structural grader so it never alone decides a score. (This was learned the hard way: a "default-to-refute" adversarial verifier once refuted a *true* correctness finding; a free-tier vision model missed 2 of 3 planted figure defects a deterministic geometric gate caught. Vision/LLM checks are advisory/shadow, never hard gates.)

**Grader stack, ordered by trust (LLM LAST):**
1. **Numeric oracle** — SymPy / tolerance comparison. Local, free, first choice for anything computable.
2. **Execution grader** — run the learner's code locally: R, Python, C++ (MinGW), Alk (via the `alk-language/java-semantics` interpreter). bash/POSIX → rubric (no safe local exec).
3. **Rubric grader** — structural item-by-item scoring (G1..Gn).
4. **LLM-judge** — prose only, always paired with a structural grader, carries per-subject misconception codes.

**Two-layer architecture** (the key conceptual split):
- **Layer 1 — TRUTH** (course facts: definitions, theorems, methods, derivations, complexity, code): **PROOF-DEMANDED**, hard decidable gates. Fallback when unprovable = a visible `verifică sursa` ("check the source") warning, excluded from mastery, counted on the board.
- **Layer 2 — TEACHING** (scaffolding: framing, visuals, analogies, predict→reveal): served by an **honest ESTIMATOR**, never dressed as proof. Fallback = a `neverificat` ("unverified") flag, named as a residual.

The only human step is a **per-artifact experience checkpoint**: the user clicks through the rendered real lesson and approves or rejects with a one-line note. Reject #1 → re-generate once with the note. Reject #2 on the same artifact → pipeline *design review*, never a third blind retry. The user may later relax checkpointing to sampled spot-checks **by his own choice only** — the system never auto-graduates.

---

## 4. The 9 artifact kinds (classified by content, never filename)

1. **lecture** — expository material; primary KC source.
2. **lab** — practical sheets (worked examples + exercises; graded code deliverables for POO).
3. **seminar** — exercise sheets (PA/ALO/PS only — seminar tag with any other subject is a classifier error).
4. **homework** — assigned problems with deadlines.
5. **past-exam** — real exam papers (drive mock-exam mode + structure/permitted-materials metadata).
6. **grading-doc** — grade-model statements (formulas, weights, minimum gates) → feeds a grade-model registry.
7. **cheat-sheet** — permitted-materials / formula sheets → proof templates + mock-exam phases.
8. **student-notes** — non-professor material, provenance-tiered *below* professor sources (stricter faithful-check).
9. **grade-ledger** — score rosters + the user's standing (analytics only; seeds FSRS difficulty + per-question rubrics).

---

## 5. Knowledge model

- **KC (knowledge concept)** = the atom of mastery, scheduling, and verification.
- **`concept_type`** (enum, assigned at digestion, verifiable): `procedure` · `proof` · `definition-taxonomy` · `code-trace` · `timing` · `probabilistic` · `comparison` · `formula-application`. Drives beat-variant and viz-family selection. Type/content mismatch fails admission.
- **Per-beat teaching fields** (language-keyed, one set per KC): PREDICT (prompt + 3–4 options + per-option callback text), ATTEMPT (sub-task + both-path feedback; numerical variant stores skeleton rows + trace steps), REVEAL (ordered steps + per-step callouts fused to the step + figure binding), NAME (formal definition + invariant + why/where-it-breaks), CHECK (a different-instance graded item).
- **Problem bank** (separate from KCs): archetype, parameter slots (re-roll for fresh instances), `solution_present` bool, rubric rows (G1..Gn with all-or-nothing/penalty flags), exam-language constraint, data-file deps, many-to-many KC links.
- **Course-meta:** grade-model registry (one verified row per subject×component, with source URL + evidence tier), `exam_dates` table, per-subject EN↔RO glossary built where lecture language ≠ exam language.
- **Pedagogical instances** (generated fresh numbers/graphs) are exempt from verbatim-quoting but verified for method-consistency and family trace-match.

---

## 6. The lesson form (locked)

A gated **5-beat step-through** (the beat vocabulary is FIXED — never reordered, no 6th kind ever invented):

① **PREDICT** → ② **ATTEMPT** → ③ **REVEAL** (stepped, scrubber-equipped, explanation *fused* to each step) → ④ **NAME** → ⑤ **CHECK**.

- **Gating:** the *Next* control is disabled until the current beat's gate clears (predict committed, reveal animation watched to its final step at least once with a minimum reading dwell, check answered, attempt submitted). Progress pips show beat k-of-N.
- **Legal beat plans (closed set):** FULL `①②③④⑤` (any encounter) · STANDARD `①②③⑤` (minimum for first encounters) · MASTERED-REVISIT `②③⑤` (predict elided) · RE-LESSON `③⑤` (forgetting trigger only, fresh parameter values).
- **Beat variants per concept_type:** numerical → a *skeleton trace* that is visually dominant over prose, one formula-line revealed per click; proof → structured sub-step attempt + proof-skeleton reveal; definition-taxonomy → classify-an-example-*first*; code-trace → predict-the-output + line-by-line execution stepper; timing → predict "can this break?" + clean-vs-broken contrast; comparison → side-by-side stepped contrast.
- **Scrubber requirement:** every animated reveal ships a full scrubber (back / play / forward / reset + "pas k/N"). A one-shot animation that can't be replayed is a gate violation. (The MergeSort demo is the reference; the old Dijkstra ~17s one-shot is the canonical violation it kills.)
- **Everything is graded; everything writes:** every beat with input is graded and writes attempt rows (carrying `beat_type`), mastery (EWMA), and FSRS scheduling. Lesson → drill handoff is wired.

---

## 7. Visualizations (figures)

**The viz principle:** render logic is verified *once per family*; the pipeline supplies *typed DATA only*; agents never draw free-form. A figure in a lesson is `(family_id, instance_data)` and nothing else. (This kills the historical failure mode: 24 bespoke one-off figures, each needing individual review and each breaking individually.)

**The 7 viz families:**
1. graph/tree · 2. sequence/array · 3. matrix/grid · 4. chart/distribution · 5. timeline/protocol · 6. state-machine/code-trace · 7. static-structure/class-diagram (UML class boxes + typed edges; models OOP static structure, not an algorithm run).

**Each family contract:** typed slot schema (nodes/edges/cells/labels/step-deltas/per-step callout) · deterministic layout that measures its own labels (no-clip *by construction*) · callouts anchored to elements (a detached footer paragraph is a contract violation) · callout text references elements by anchor, never by hardcoded color name · plugs into the shared scrubber shell.

**Per-family verification:** a **trace-match harness** (reference execution computed from instance data; every rendered step's state asserted equal to the reference) + **semantic invariant asserts** in rendered pixels (e.g. a reflection family asserts `len(Hx) ≈ len(x)` in rendered px — this catches a lint-passing, legible, plausible figure that contradicts its own teaching point). The class-diagram family uses a **structure-isomorphism** gate instead (parse model → assert the rendered SVG is isomorphic: every class/field/method/edge present with correct type+direction).

**SVG-only policy (locked):** WebGL/WebGPU/Three.js/canvas figures and scroll-driven/mouse-reactive decoration are **banned** (enforced by import-ban lint). Reasons: machine-verifiability (gates read the DOM/SVG tree; canvas collapses to opaque pixels), pedagogy (decoration fights the locked learner-paced form + ADHD), and bundle cost.

---

## 8. Practice surfaces & adaptivity

**The arc:** I-do (lesson reveal) → we-do (scaffolded/step-trace drills) → you-do (free code + mock exams). The queue moves a KC along the arc as mastery rises.

**Five practice surfaces:** (1) structured proof drill (named sub-steps scored separately) · (2) step-trace numeric drill (every intermediate state checked, not just the final answer) · (3) code-practice session (skeleton-fill / free code in the exam's language; reference solution is *attempt-gated* — hidden until a graded attempt) · (4) mock-exam mode (timed, mirrors the real paper's structure & permitted-materials phases; synthetic items are *tagged as synthetic* — honesty over simulation) · (5) deliverable tracker (deadlines + points-at-stake + prep drills; the app prepares, never grades real submissions).

**Tracking:** FSRS (spaced-repetition schedule) and EWMA (mastery estimate) unify into **one per-KC state** that *all* surfaces write via a single writer. **Queue priority = exam proximity × component weight × readiness gap × prerequisite order.** A readiness dashboard shows, per grade component, current readiness vs the subject's verified minimum gates.

**ADHD session shape:** session caps with (non-guilt-trip) break prompts; a cross-subject interleave cap; a recovery-session mode (when broad decay is detected, suppress new material and serve only re-lessons over the decayed cluster); placement that probes *subject* knowledge (not the tutor's own internals).

**Experience-completeness layer (EC1–EC5):** in-lesson live help (ask-in-lesson + prereq-peek, with a guard that refuses to leak a gated drill's solution) · cold-start completeness (no advanced KC reachable without a prerequisite path to an entry KC, or the missing rung is made explicit) · recovery mode · goal/intent layer (learner declares a target past-paper/topic; the queue re-weights toward it without starving a near official exam) · provider health surface (admin-only).

---

## 9. The subjects (the actual domain)

The learner's program is **BScIA** (AI bachelor) at UAIC FII, Year 1 Semester 2. (Note: an old CS/BScINFO material scrape exists in the archive — *different program*; only PA/PS/POO are genuinely shared. Don't assume CS material fits the AI courses.) Five subjects:

| Code | Full name | What it actually is | Grade model (verified) | Pass gate |
|---|---|---|---|---|
| **PA** | Proiectarea Algoritmilor | **Algorithm design** (correctness proofs, complexity, Greedy/DP/NP) — *not* math. Exam: 3 questions × ordered a–d sub-parts; **Alk pseudocode mandatory**; 0.5-pt partial credit. | 70% continuous (2 written tests) + 30% seminar | ≥45/100 |
| **ALO** | Algebra Liniară și Optimizare | **Numerical** linear algebra + optimization (LU/QR/Jacobi, gradient/conjugate descent) — *not* abstract algebra. | `lab(300) + 10×seminar-test + 40×written-exam`, max **800** | exam ≥3/10 **AND** total ≥360/800 |
| **PS** | Probabilități și Statistică | Prob/stats **by-hand** + **R** programming. **Verificare** type — *no sit-down exam*; 6×15-min in-seminar mini-tests. | seminars 60 + labs 60 (= 100) | seminar ≥30/60 **AND** lab ≥30/60; **no re-exam** |
| **POO** | Programare Orientată pe Obiecte | **OOP + C++ coding** (inheritance, polymorphism, templates, STL) — graded on understanding *and* writing correct C++. | T1(30) + T2(30) + final(30) + lab(10) = 100 | ≥45/100 |
| **SORC** | Sisteme de Operare și Rețele de Calculatoare | **OS + Networks combined — ONE subject.** ("RC" is the networks half, **not** a standalone subject.) | `NF = 0.1·SO1 + 0.3·SO2 + 0.1·SO3 + 0.1·RC1 + 0.4·RC2` | RC2 ≥5 **AND** NF ≥5 (RC2 only retakeable) |

**Hard domain facts:** Zero past exam papers exist *officially* for ALO, PS, SO, or RC. PA papers (2015–2018) exist in the corpus; POO papers are missing. SO sub-site is behind HTTP 401 (SO1/SO2/SO3 definitions inaccessible). The most critical content gap is current-year PS seminar sheets. Mock content for subjects with no real paper must be **synthetic-tagged**, never claimed as a real exam.

Exam window for this cohort (IA12): 2026-06-03 → 2026-06-28 (PS has no exam row, consistent with Verificare).

---

## 10. Tech stack

**Backend — Kotlin / Ktor**
- Gradle (`build.gradle.kts`), JVM target Java 21, Kotlin 2.0.21, Ktor 3.0.1. Main class `jarvis.MainKt`; web server on `:8080`.
- SQLite via **Exposed ORM** + `sqlite-jdbc` (WAL mode). DB at `~/.jarvis/tutor.db`.
- **kaml** for the YAML content corpus; **PDFBox** for PDF text extraction; **kotlinx.serialization** for JSON; jakarta.mail (optional daily email), dotenv-kotlin, JNA.
- LLM layer: an `Llm` interface with implementations `ClaudeMaxLlm` (subprocess `claude --print`), `FreeLlmApiLlm` (OpenAI-compatible local), `AnthropicLlm`, `CopilotLlm`, `OpenRouterChatLlm`, `RelayLlm`, `FallbackLlm`. Provider chosen by `JARVIS_LLM` env. `Llm.complete(...)` carries an optional `imagePath` channel.

**Frontend — React 19 / TypeScript (`tutor-web/`)**
- **Vite** bundler + React Router 7; **Tailwind CSS 4**; **Vitest** (unit) + **Playwright** (e2e + visual regression).
- Visualization libs: **@visx**, **d3**, **plotly.js**, **mafs**; **KaTeX** for math; **motion** for animation; **react-pdf** / **pdfjs-dist** for the PDF pane.
- Builds to `src/main/resources/tutor-dist/`, which the Ktor JAR embeds and serves at `/tutor/*` (SPA fallback). One unified self-hosted JVM artifact.

**Also in the repo:** an `android/` Kotlin subproject (Compose) and an unbuilt Rust `daemon/` — both peripheral; the tutor is the JVM web app.

**Hard constraints:** **No paid LLM APIs** — OpenRouter `:free` tier or a local relay only. Self-hosted fonts (no CDN). Local/offline-capable graders (numeric, code-exec) so the learner session keeps working when the LLM is rate-limited (content is pre-generated at digestion, never on the request path).

---

## 11. Codebase map (key paths)

**Backend (`src/main/kotlin/jarvis/`, ~223 .kt files):**
- `Main.kt` / `web/WebMain.kt` — CLI + Ktor entrypoint, auth interceptor, routing.
- `Llm.kt` + provider impls; `LlmFactory`; `Config.kt` (env resolvers).
- `tutor/TutorDb.kt` (SQLite pool), `tutor/TutorMigration.kt` (migrations/backfills).
- `tutor/verify/` — trust-net audit (`VerifyContentCliKt`), `TrustInvariantsCliKt` (read-only machine invariants), gates, graders.
- `content/ContentCliKt` (corpus validator), `content/ContentReconcileCliKt` (source-edit reconcile).
- `web/` route modules: `TutorRoutes`, `PracticeRoutes`, `MockExamRoutes`, `QueueMasteryCalibrationRoutes`, `SessionPlacementExamRoutes`, `VizInstanceRoutes`, `TrustRoutes`, `GraderProviderRoutes`, `CuratorRoutes` (deprecated), `AuthHelpers`.

**Frontend (`tutor-web/src/`, ~180 components):**
- Shell/surfaces: `App.tsx`, `TutorWorkspace.tsx`, `DayOfShell.tsx`, `AppShell.tsx`, `ResourceRail.tsx`, `RailDrawer.tsx`, `PdfPane.tsx`, `Scratchpad.tsx`.
- Lesson engine: `components/lesson/BeatOrchestrator.tsx` + `PredictBeat`/`AttemptBeat`/`RevealBeat`/`NameBeat`/`CheckBeat`, `FigureReveal.tsx`, `LessonFigureShell.tsx`.
- Practice: `DrillStack.tsx`, `DrillCard.tsx`, `practice/{ProofDrill,StepTraceDrill,CodePractice,DeliverableTracker}.tsx`, `MockExamShell.tsx`, `PlacementShell.tsx`, `CalibrationPlot.tsx`.
- Trust/progress: `TrustBadge.tsx`, `MasterySparkline.tsx`, `KnowledgeLedger.tsx`, `MisconceptionRibbon.tsx`, `GroundedExplanationCard.tsx`.
- Viz families: `components/viz/families/{SequenceArrayFamily,SortMergeFamily,GraphTreeFamily,MatrixGridFamily,ChartDistributionFamily,ClassDiagramFamily}.tsx` + matching `*Invariants.ts` checkers + `familyRegistry.ts`.
- Theme system: `door/{DoorWarm,ThemePicker,palettes}.tsx`, `theme/{ThemeProvider,applyTheme}.tsx` (cool default + warm second theme).
- Gate tooling: `tutor-web/tools/{frame-conjunction-gate,svg-overlap-gate,class-diagram-layout-gate}.mjs`.

**Content corpus (`content/<SUBJECT>/`):** per-subject `kcs/` (KC YAML), `problems/`, `misconceptions/`, `viz/`, `_sources/`, optional `edges.yaml` (KC DAG). `subjects.yaml` + `viz-ids.yaml` at the root.

**Build & run:**
- Backend: `gradle run` (→ `:8080`), `gradle test`, `gradle validateContent` / `verifyContent` / `trustInvariants` / `reconcileContent` / `seedE2eDb`.
- Frontend: `npm run dev` (→ `:5173`, proxies `/api`+`/auth` to `:8080`), `npm run build` (→ `tutor-dist/`), `npm run test` (Vitest), `npm run e2e` / `e2e:visual` (Playwright).
- Unified: `npm run build` then `gradle build` packages the SPA into the JAR.

---

## 12. API & data contracts (frozen seams)

All wire deviations must amend the interface-signatures lock in the same commit. Backend snake_case wins; TS clients map at the fetch boundary.

**Key HTTP routes:**
- `GET /api/v1/queue/today` → `{ items: QueueItem[], total_due, day }` (non-faithful items omitted server-side).
- `GET /api/v1/mastery` → per-subject KC sparklines (`MasterySparklineProps`: kcId, ewmaScore 0..1, observations, phase, verificationStatus, lastGradedAt).
- `GET /api/v1/lesson/{kcId}` → `ApiLessonReply` (faithful-gated → 404 if not verified; 401 if no session; carries `beats: ApiLessonBeats`, RO fields, prediction options).
- `GET /api/v1/viz/{instanceId}` → `ApiVizInstanceReply` (`id, subject, family_id, language, data_json`; read-only, no LLM).
- `POST /api/v1/drill/grade` → `ApiDrillGradeReply` (atomic txn; 409 if KC not ACTIVE; verification_status, phase, misconception_payload, item_verdicts, decided_by, degraded_legs_ro).
- `GET /api/v1/practice/problems?subject=&surface={proof|trace|code}` (never includes the reference solution — server-enforced).
- `POST /api/v1/practice/{proof|trace|code}/{id}/...` (proof: substeps → item_verdicts; trace: per-step verdict; code: run → compile/stdout; grade → full chain + attempt-gated `reference_solution_ro`).
- `GET /api/v1/practice/deliverables`, `GET/PUT /api/v1/me/exam-dates`, `POST /api/v1/mock-exam/{id}/phase`, `POST /session/close`, `GET /api/v1/verify/{kcId}/status`, `GET /api/v1/fsrs/due|forecast`, `GET /api/v1/calibration`.

**Key enums/types:** `Phase {intro,practice,retrieval,mastered}`, `VerificationStatus {unverified,pending,faithful,uncertain,failed}`, `NextPhaseAction {advance,hold,remediate}`, `QueueMode {worked,drill,retrieve}`, `ClaimKind`, `NonLlmLegKind {SYMPY,TEST_EXEC,HUMAN_GOLD,NONE}`. Core data: `KnowledgeConcept`, `KcMastery`, `SourceRef(doc,page,span,quote)`, `VerificationClaim`, `CitedClaim` (source required, fail-loud on null), `MisconceptionPayload`, `ItemVerdict`, `QueueItem`.

**Key DB tables:** `kc_verification_status` (kc_id PK, status, content_hash[16], source_span_hash[16], lecture_grounded, last_audit_run_id, updated_at — fail-closed serve gate on hash match), `verification_audit`, `kc_mastery` (ewma_score, observations, phase, entry_phase, last_graded_at), `fsrs_cards` (kc_id nullable, source, FSRS fields; dedup key (userId, sourceType, kcId)), `attempts` (+ beat_type, prediction, first_encounter), `report_wrong`.

---

## 13. Current state (what's built vs pending)

**Built + CI-green on `main`:**
- **Trust-net** ON (backup gate, restore drill, audit verdicts in DB).
- **Knowledge schema** (7 tables + `concept_type` enum). 828 FSRS cards exist (but **0 linked to KCs yet** — backfill pending).
- **5-beat lesson engine** (BeatOrchestrator, closed BeatSelector, per-beat grading) with a few faithful PA KCs carrying authored Romanian beats.
- **Gate chain** on the real lesson route (rendered Playwright gates; caught 2 real defects). `frame-conjunction-gate` is CI-wired & hard-blocking; the svg-overlap and class-diagram gates run locally but are **not** CI-wired yet.
- **5 practice surfaces + 4-leg grader stack** (R/Python/C++/Alk) — but wired to an **empty problem bank (0 rows)**.
- **3 viz families** fully tracked+green (graph/tree, chart/distribution, sequence/array); the others are WIP / partly untracked. 7 families exist in total.
- **Theme system** (cool default + warm second theme).

**Content census:** ~7 real KCs across 2 of 5 subjects (PA `pa-kc-001..006` + ALO Gaussian elimination); POO/PS/SO-RC have **0 real KCs**. The whole digestion/generation half (~50% of the spec) is essentially **zero-code**.

**Not built / pending:** the ingestion→generation firehose (upload door, 9-kind classifier, beat/figure/depth/CHECK co-generation, OCR, checkpoint screen, migrations incl. the 828-card backfill); content across all 5 subjects (the 140 inventoried URLs); the depth artifact (Layer-1) and pedagogical-quality estimator (Layer-2); unified single-writer FSRS+EWMA + exam-aware queue + readiness dashboard + ADHD shape; EC1–EC5; and **deploy**.

**Deploy:** the live VPS still serves an old (SESSION-66) bundle ~140 commits behind; HEAD is several commits ahead of `origin/main` and unpushed. **Deploy is deliberately LAST** (after honest-100% is proven locally) to avoid serving broken features. Nothing built since SESSION-66 is live.

---

## 14. The build roadmap (locked phase sequence)

`0 → A → B → C → [SPIKE] → D → ⛔GATE → E → F → G → H → I → J`

- **0 — Foundation/compliance harness:** make every gate trustworthy (Impeccable lint → blocking; cross-language schema-hash CI; per-gate matrix; a status-tagged gate-COVERAGE registry; gate-self-test where a seeded bad fixture must turn each gate RED).
- **A — Figure-correctness loop:** figures as a deterministic function of algorithm execution, gated on *semantic state* (frame-conjunction), not pixels; build the missing families; migrate the 24 one-offs.
- **B — Depth artifact:** make "is the depth real" a machine-decidable Layer-1 object (invariant+why, complexity *derivation*, correctness, edges, code), split-gated (code-compiles is PROOF; soundness legs stay `verifică sursa` until source-grounded).
- **C — Pedagogical-quality ESTIMATOR (Layer-2):** per-distractor misconception binding + discriminating CHECKs; PROOF legs (binding, non-paraphrase, ATTEMPT-precedes-answer) hard-gated, "separates models" is an honest estimator, never fake-green.
- **SPIKE — generator capability:** empirically measure whether the free/relay model can clear the new depth+pedagogy oracles before opening breadth.
- **D — Collapse the two UI surfaces:** make "premium" a structural property every generated lesson inherits (promote the cinematic shell into production, delete the demo/prod split, verify no-clip at the real 648px content height, add keyboard-traversal + RO aria-live a11y, land the warm theme so dual-skin baselines are captured once).
- **⛔ GATE OF GATES:** A+B+C at their *early* gates (oracle built + self-test green + green on the ~7 hand-authored real KCs) **and** the capability spike measured is the hard precondition for breadth. Until then, generating content broadly is forbidden — every generated lesson is potentially wrong and undetectable.
- **E — Digestion/generation pipeline:** ingest+legibility+OCR, 9-kind classifier, problem-bank register, beat-gen per concept_type, figure/depth/CHECK co-generation, the checkpoint review screen, migrations (incl. fsrs_cards.kc_id backfill).
- **F — Content across all 5 subjects:** the 11-artifact proof-set (one per kind × concept-type) + up to 140 sequential checkpoint cycles; per-subject synthetic-tag fraction displayed.
- **G — Guidance + practice + exam completion:** unified single-writer mastery, priority queue + readiness dashboard, ADHD shape, placement rebuilt, problem bank populated so execution graders go green.
- **H — EC1–EC5** experience completeness.
- **I — Final hardening:** full gate matrix on the real DB + whole-build adversarial council + seeded-violation drills; the **honest 100%** definition.
- **J — ONE verified VPS deploy with a live probe** (open the real URL and confirm the user *sees* the feature; zero 4xx/5xx on first paint; every interactive element clicked; login round-trip; scripted restore drill). Only then is anything "shipped."

**Build workflow rules:** just-in-time plan-writing (the spec is durable; plans are derived per-execution, never far ahead) · two-lane parallel max (Lane B in a git worktree, zero file-intersection, machine-checked) · executors are read-only on the plan (mismatch → block & amend, never self-edit) · delta-recon against HEAD before each execution · full test suite per task, foreground + streamed to a log, **never** `| tail` or polled in the background · whole-build adversarial council mandatory after long autonomous runs · never trust a subagent's "green" — re-run it yourself.

---

## 15. The five honest residuals (named, never hidden)

1. **Teaching effect is unmeasurable without a live learner** (Layer-2) — discriminating-CHECKs *bound* wrong-teaching but don't eliminate it.
2. **Depth-derivation soundness is undecidable without an authored source** (Layer-1) — "the code compiles" is decidable; "this O(n log n) is correct" is not, absent a reference. Such legs stay `verifică sursa` and are excluded from mastery.
3. **Free-tier generator capability vs the new oracles is unknown until the spike measures it** — "100% coverage" may resolve to "100% of what cleared the oracle; the rest flagged + counted."
4. **Synthetic-tagged exam coverage ≠ real-paper coverage** — ALO/PS/RC have zero past papers; the synthetic fraction is displayed per subject.
5. **The human checkpoint judges EXPERIENCE, not TRUTH** — "100%" means "every machine gate green + experience-approved," explicitly *not* "no human ever sees a defect."

A "100%" that names these five as accepted, flagged residuals is honest. Hidden residuals are exactly the failure this whole design exists to prevent.

---

## 16. Source documents (in-repo, for deeper reading)

- **Binding spec:** `docs/superpowers/specs/2026-06-11-one-pass-digestion-teaching-engine-design.md`
- **Locked master plan (the roadmap):** `docs/superpowers/plans/2026-06-15-end-to-end-master-plan-to-100-v2.md`
- **Frozen interface signatures (canonical on conflict):** `docs/superpowers/plans/2026-06-02-interface-signatures-lock.md`
- **Verified subjects / grade models / exam schedule:** `docs/superpowers/findings/2026-06-11-verified-grade-models-exam-schedule.md`
- **Organization standard:** `docs/CONVENTIONS.md`

---

*Working state cited: ~1050 commits on `main`; verification + lesson + practice substrate built & CI-green; ingestion/generation half and full-subject content pending; deploy deliberately last. Single user, single self-hosted JVM, free/local LLMs only.*
