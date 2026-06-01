# North-Star Roadmap — the does-everything personal tutor (design, rev 2)

**Date:** 2026-05-31 · **rev 2** folds 4 councils + a whole-system integration check.
**Status:** approved shape (Alex). Supersedes the stale 11-gate order in `2026-05-17-jarvis-full-tutor-redesign-design.md §14`.
**Councils:** vision `1780181270` (APPROVED w/ guardrails) · structure `1780183826` (order was FLAWED → corrected) · rule-completeness `1780229348` (+5 rules) · oracle-without-PDFs `1780230347` · knowledge-storage `1780231377` (activate dormant, don't build) · whole-system check `wf_1f946ca4-bcb` (cohere-with-fixes; all fixes folded below).
**Rule baked in:** NO time/effort estimates. Order by dependency only. Every claim below re-verified against repo HEAD `441d42b`.

---

## 1. North-star (the goal — do NOT scope-cut)

A full personal AI professor/tutor that does ALL the lifting and actively **tutors** Alex — auto-detect tasks, build curriculum, TEACH (explain · Socratic · worked examples, 1:1), generate + grade practice, schedule via spaced repetition — personalized to his knowledge state + ADHD, across all his real university subjects. Council-approved (`1780181270`); the earlier "flawed" ruling applied only to an un-mitigated framing.

Learner: Alex — UAIC AI-bachelor, unmedicated ADHD, low programming confidence, **cannot independently vet subject correctness**, and **will not dogfood incrementally** (uses it when whole). Both facts drive every decision.

## 2. Locked decisions (this session)

- **D1 — Claude everywhere (student-facing).** Generator + grader + critic all run Claude via the relay (today's `main` runs free-Llama on generator+grader — the inverse of the rule; corrected). Consequence: **pre-generate + persist all drills** so the live serve never calls the relay; **grading** uses deterministic scoring for computational shapes (`GradeScoring.answerMatches` exists) and an **async "grade pending"** path for open-response (never block the live loop on a relay call). The `model_tag` is **derived from the relay's returned model string**, never the current hardcoded `criticUsed="relay/claude"` literal (`TutorRoutes.kt:1520`).
- **D2 — local multilingual embedder.** Replace the paid OpenAI embedder (`EmbeddingsClient.kt:32`, no free tier — violates no-paid) with a **local, free, multilingual** model (e.g. `multilingual-e5-small`; keep the existing 384-dim store). Multilingual because the corpus is bilingual RO/EN (English-only MiniLM would degrade on Romanian). This enables the dormant **semantic** leg of `HybridRetriever` (today `semanticEmbed=null` at 5 routes) — navigation ONLY.

## 3. The safety rule-set (10 rules, corrected + given homes)

Each rule: what it catches · where it lives · status `[EXISTS]`/`[WIRE]`/`[NEW]`.

1. **Extraction-confidence gate** `[NEW]` — ingest **step 0**: score each `_sources` doc; garbled/low-confidence → ContentValidator **ERROR** (not today's warning) + "unverified, do not teach as fact"; never ground against it. *(M1: build first, re-run over the existing PA ingest — `pa-lecture-01` predates the gate.)*
2. **Provenance gate** `[EXISTS]` — `ContentValidator.checkVerbatimSources` (`:185`) byte/diacritic-exact span-match of each claim's quote vs raw `_sources`. **This proves the claim came from the slide — NOT that it is true.** (Renamed from "verification" per H2; it is the *prerequisite* to the truth-check, not the truth-check.)
3. **Truth-oracle (verify the claim is correct)** `[NEW]` — because the slide itself can be wrong:
   - **(a) verify the checkable by computation/proof** — math/algorithms/code/numeric (re-derive/run). No book needed; covers the bulk of exam-critical CS.
   - **(b) cross-check definitional claims against the textbook KB** — the subject's fișă-bibliography books (verbatim, tier-0 canonical lane, §below) — NOT a model's memory of a book (council `1780230347`: that self-confirms/fabricates).
   - **(c) flag-don't-fake** — a single-model self-assertion never silently PASSES; a flagged claim may be shown with a visible caveat but is **barred from the FSRS store until an independent source corroborates**. Wire the oracle into the **generation accept-gate** (a drill's grounding must resolve to a real `SourceRef` span ContentValidator confirms, not the critic's `grounded:bool`).
4. **Forced-retrieval** `[WIRE→server gate]` — first-class invariant from slice 1: **no answer reveal until an attempt/prediction is recorded** (today the predict/giveUp anchor exists at `DrillGrader.kt:164` but is optional + frontend-only); persist an **"unaided" flag** on the mastery observation; route `FsrsDueQueue` to drive recall (schedule recall, not re-reading). *(M4)*
5. **Misconception mining** `[NEW]` — per subject, mined from the corpus (Claude, so it inherits grounding); gate "tutorable" on misconception + prereq-edge coverage (today PA=2, others empty).
6. **Coverage / exam-blueprint alignment** `[NEW, blocked on fișă]` — bind content to the **fișă-disciplinei topic list** (the syllabus blueprint); surface missing-coverage. `exam_weight` (sums to 1.0) is weight-balance, not coverage — not a substitute. *(H4)*
7. **Relay-resilience** `[EXISTS for gen / NEW for grade]` — pre-generate + persist so the live loop never blocks; the **grade** leg must not block on a relay call (D1: deterministic + async). *(H3)*
8. **Enforcement/observability contract** `[NEW — the keystone]` — every student-facing payload carries a **machine-checkable citation handle (`SourceRef` doc+span) + model_tag**; a single `emit()` chokepoint **HARD-BLOCKS + logs** (before `call.respond`) anything un-cited, cheap-model, or flagged. Today the citation is dropped (`sources=kc.source.map{it.quote}` flattens it, `:1459`; `DrillContentDto` has no field) and the envelope is built *after* respond (`:2045`→`:2051`, observe-only). *(C4)*
9. **Audit + retraction** `[NEW, blocked on #8]` — standing re-verification of already-taught claims; on finding one wrong, retract it AND **un-reinforce/quarantine the FSRS cards it seeded**. Impossible today: `FsrsCard.sourceRef` holds a gap id, never a claim/KC id; no quarantine op. New modules `ClaimRegistry` + `RetractionSweep` (NOT `jarvis.tutor.Audit`, which is a grade-outcome hash-chain — name collision). *(C2)*
10. **Grade calibration** `[NEW]` — calibrate the LLM grader against a small per-subject answer-key gold set before trusting its scores; the grader is an uncalibrated rater today. *(Sequenced as a gate on the skeleton's grade leg, §6.)*

**Non-negotiable:** no un-cited claim reaches the learner ("I don't know" > confident-wrong) — made real by rule #8.

## 4. The keystone primitive

The whole right-hand side of the system hangs on one missing thing: a **persisted claim → citation → card provenance handle**. Carry `SourceRef(doc+span)` + `model_tag` onto `Problem`/`DrillContentDto`, and key FSRS cards seeded from content by claim id. Without it, rules #3(grade-time re-verify), #8(citation contract), and #9(retraction) all have nowhere to attach. **Build it as the first task after the E2 root.** *(C4)*

## 5. Corrected build STRUCTURE

- **Re-root on content (E2), not the grader.** The grader's mastery signal is parameterized by E2's `kcIds`/rubrics (the plans' own E1→E2 rip-out proves it). E1's deterministic-scoring *mechanics* build in parallel; its trustworthy *signal* depends on E2. *(council `1780183826`)*
- **Vertical walking-skeleton before breadth.** One real concept end-to-end first (§6) — strict horizontal layering is the ghost-component trap this project already hit (2026-05-11).
- **Guardrails are ingest preconditions**, not late features (rule #1 first; #3 from slice 1).
- **E3 is NOT "done"** — proven only on a synthetic fixture (`E3RealRelayProofTest.kt:62`); the real proof is the skeleton's E2→E3 leg on real content.
- **Knowledge storage = activate the dormant infra, don't build new** (council `1780231377`): the verbatim oracle is the existing `ContentValidator`/`SourceOfRecord` exact-span path; navigation is the existing KC concept-graph + `PrereqEdge` edges + the (to-be-wired) `HybridRetriever`/`VectorStore`. **Strict separation, type-enforced** (M2): grounding accepts ONLY a `SourceRef`-with-span resolved via `SourceOfRecord.slice` — NEVER a `HybridHit.snippet`; add a test asserting a `HybridHit` cannot reach the grounding path. Do NOT repurpose the per-user `WikiPage.kt` journal as reference truth. **Port ~nothing from os-study-guide** (category mismatch, prior council `1780007694`); at most steal prompt-template ideas as notes.

## 6. SLICE 1 — the walking skeleton (first build target)

ONE real PA concept (`pa-kc-001`), end-to-end on the live app, **Claude-driven, no dogfooding**. Hops with plug-points:

1. **Ingest** `pa-lecture-01` → **extraction-confidence gate (#1) runs first** (ERROR on garble); provenance gate (#2 `[EXISTS]`).
2. **Build the keystone handle (§4)** — `SourceRef`+`model_tag` fields on `Problem`/`DrillContentDto`.
3. **Wire navigation** — index the KC + `_sources` into `VectorStore` via the local multilingual embedder (D2); turn the semantic leg on. *(navigation only; never verification.)*
4. **Generate** a drill via the relay (Claude, D1) — pre-generated + persisted; the accept-gate requires a ContentValidator-confirmed `SourceRef` span (#3c), not the critic's bool.
5. **Serve** with **forced-retrieval (#4)** as a server gate (no reveal before attempt; unaided flag).
6. **Grade** — deterministic for the computational shape (no live relay); the **truth-oracle (#3)** re-verifies the claim against `_sources`; emission passes the `emit()` chokepoint (#8).
7. **Mastery + FSRS** — confident grade records `KcMastery` **AND seeds/touches an `FsrsCard`** keyed by the claim id (C3: fixes the phantom join; uses the §4 handle).
8. **Calibration gate** — the skeleton is not "done" until the grader is calibrated on a small PA gold set (#10).
9. **Verify on the live app** — a real PA problem flows ingest→generate→grade→mastery→FSRS, observed on the running URL (not the all-stubbed Playwright smoke). Spec the `[data-testid]` selectors + zero-4xx/5xx + click-through-no-error gates (per `CLAUDE.md`).

## 7. Breadth (after slice 1 green)

More PA KCs/lectures over the same pipe → other subjects (ALO/POO/PS/SO, today `.gitkeep` shells; PS needs vision/MinerU) → **misconception mining (#5)** per subject → **fișă ingest (H4)**: ingest each subject's fișă-disciplinei as a first-class artifact (topic list + bibliography) — feeds coverage (#6) and the textbook KB (#3b). **Retraction (#9) = the first breadth task** (it only has work once >1 claim is taught and the keystone handle exists). The **textbook KB** is a tier-0 canonical lane (M3): higher extraction-confidence bar + human spot-check + marked immutable; the ONLY corpus #3b may treat as authority.

## 8. Leaves (nothing depends on them)

Auto-detect + real course-scrapers + always-on (skeleton parked) · multi-subject breadth · voice · curator tool (demoted).

## 9. Cross-cutting invariants

- No un-cited claim reaches the learner (enforced by the #8 chokepoint, not hoped).
- Verification reads ONLY verbatim `_sources` via `SourceRef`+span; embeddings/wiki are navigation-only, type-barred from verification (M2).
- Render-before-claim-done: every "shipped" claim verified on the live URL.
- Free-tier resilience: generation/grading/embedding must not all share one paid/throttled key; a 429 storm must not take down the live surface.

## 10. Sequencing rules (so standing processes aren't orphaned)

- **Calibration (#10)** = a gate on the skeleton's grade leg (§6.8).
- **Retraction (#9)** = the first breadth task (§7).
- **The keystone handle (§4)** = the first task after the E2 root; #3/#8/#9 attach to it.
- **Extraction-gate (#1)** = ingest step 0, built before the skeleton grounds against anything.

## 11. Component inventory (exists / wire / new) — corrected by the integration check

**`[EXISTS]` reuse:** GradeScoring (deterministic, defers self-inconsistent) + KcMastery (ewma≥0.8 & ≥3 obs) ; ContentValidator provenance span-match + SourceOfRecord ; DrillGenerator safeguards ; the generate→grade→mastery route ; FSRS scheduler (but disconnected — C3) ; HybridRetriever lexical+entity (semantic dormant) ; relay (Claude, proven this session).
**`[WIRE]` dormant→active:** index KC corpus + `_sources` into VectorStore (new producer mirroring `EmbeddingsPipeline`) ; the local embedder (D2) ; forced-retrieval server gate (D-anchor exists) ; flip the 5 `semanticEmbed=null` callsites.
**`[NEW]`:** extraction-confidence gate (#1) ; truth-oracle compute-verifier + textbook-KB (#3) ; the keystone citation handle (§4) ; the `emit()` chokepoint (#8) ; `ClaimRegistry`+`RetractionSweep` + claim→card link (#9) ; grade calibration harness + gold set (#10) ; fișă-ingest artifact + coverage diff (#6) ; FsrsCard seeding from graded drills (C3) ; model_tag derived from relay (replace hardcoded literal).

## 12. Acceptance

- **Slice 1:** a real PA concept, ingested behind the extraction-gate, generated by Claude (pre-gen), graded (deterministic + truth-oracle re-verify), records `KcMastery` AND seeds an `FsrsCard`, taught with forced-retrieval, grader calibrated on a PA gold set — all observed on the live app, citation+model_tag present on every payload, the `emit()` chokepoint blocking any un-cited/cheap-model output. The slice-1 **plan** MUST carry the `[data-testid]` selectors + zero-4xx/5xx + click-through-no-error gates (`CLAUDE.md`).
- **Per-subject (breadth):** extraction-gate + truth-oracle pass at ingest; fișă ingested; misconception + prereq-edge coverage present before "tutorable."
- No time estimates appear in this roadmap or its plans.

## 13. Open notes (defer to the plan, non-blocking)

- Conversational teaching surface: its own component or folded into E4 + sidekick? Depends on E2 content + E1 grader either way; resolve when planning breadth.
- Textbook-KB scope: one corpus vs per-subject index (affects index design, not the order).
