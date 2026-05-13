# Council review — 1778696411

**Problem:** Final adversarial pass on the COMPLETED student stand-in spec (`docs/superpowers/specs/2026-05-13-student-standin-design.md`). Three prior councils shaped sub-questions in isolation; this pass evaluates the unified spec.

**Timestamp:** 2026-05-13T18:20:11Z

---

## 🔴 Devil's Advocate

AGENT: Devil's Advocate
STANCE: REJECT
REASONING: The three prior councils each demolished one sub-question and the spec dutifully folded each fix in — but the resulting Frankenstein bundles three loosely-related ideas under one "stand-in" umbrella that no longer matches the original problem. Surface X is a trace-grader (judges site behavior, naivety irrelevant); Surface Z is a visual lint tool (judges pixels, naivety irrelevant); Surface Y is the only actual stand-in (judges navigability, naivety load-bearing). They share only a provenance stamp and a findings directory — three independent tools wearing a shared logo. Worse, the claimed X→Z→Y sequencing is a fiction: X requires hand-curated golden fixtures + 80% calibration BEFORE shipping (1-2 days of manual labeling), Y requires hand-authored concept schemas per task, and BOTH require the BLOCKING quota-isolation prereq whose own design admits "if SHARED verdict, automation forbidden during study hours" — which collapses Y into "Alex's existing nightly dogfood with extra steps." The spec is pre-loaded with so many blocking gates that ship probability in 8 days is near-zero. Critically, INV-10 ("grader latency p95 ≤ 12s") is unachievable today: the live grader uses `OpenRouterChatLlm` with `requestTimeoutMillis = 90_000` and a documented `:free` fallback chain that 429-cascades — INV-10 will FAIL on every X run that triggers fallback, training Alex to ignore X verdicts on the very latency invariant the spec hand-authored as new design intent.
KEY CONCERN: The spec inverts cost vs. value. Surface Z (visual lint + layperson LLM) is the cheapest to ship and gives the most concrete short-term win — `LINT-snake-case` catches the motivating `uses_rlaplace_or_inverse_cdf_sampler` bug in ~50 LOC with zero ground-truth bootstrap, zero envelope-capture, zero schema authoring. Surface X requires `TutorEventLog.kt` deployment + 15-25 hand-labeled fixtures + 80% calibration + INV-10 that the current grader stack physically cannot pass. Surface Y requires per-task schemas + the quota-isolation verdict + (per spec) X+Z to ship first. The dependency DAG forces 2-4 days of pre-finals labor on X+Y infrastructure to unlock the trivial Z win that motivated the whole exercise.
PICKED: FLAWED-FIX-INLINE

## 📚 Domain Expert

AGENT: Domain Expert
STANCE: CONDITIONAL
REASONING: The spec demonstrates strong literacy in LLM-as-judge precedent — the calibration-against-gold-set gate (Surface X §"Ground-truth fixture set") mirrors RAGAS's `answer_correctness` and DeepEval's `GEval` reliability bootstrap, where a judge is rejected until inter-annotator agreement with human labels passes a floor (RAGAS uses Cohen's κ; DeepEval uses raw agreement). The 80% threshold is conservative-but-defensible — OpenAI evals typically demand ≥0.7 Spearman, LangSmith ships their "LLM-as-judge" recipes with a recommended ≥75% agreement floor. The MathVC schema-gate implementation in Surface Y (§"Schema-gate filter") is faithful to the arXiv:2404.06711 actual mechanism (external symbolic check + regenerate-with-violation-hint, not just in-prompt instructions), which is a genuinely sophisticated read — many production "naive student" agents skip the external gate entirely and leak knowledge as Milička 2024 predicted. The "ADVISORY ONLY — never blocks deploy" stance on Surface X correctly avoids the well-known Promptfoo / OpenAI-evals anti-pattern where teams disable flaky LLM-judge CI gates within weeks.
KEY CONCERN: The provenance stamp is missing two fields that are non-negotiable in DVC, MLflow, and W&B: (1) the **judge model identity + version** — you stamp the live tutor's `bundle_hash` but NOT which `:free` model resolved for the X/Y/Z judge run; (2) the **prompt template hash** for the grader/persona/critic system prompts themselves (MLflow treats the evaluator prompt as a first-class artifact). The `TutorEventLog` envelope DOES capture `model_resolved` and `system_prompt_sha256` for the *live* tutor — but the *judge surfaces themselves* are flying blind on their own provenance. Add `judge_model_resolved` and `judge_prompt_sha256` to `getStamp()` and the omission closes. Secondary: Datadog/FullStory/Sentry session-replay precedent samples by *session-cohort* and captures *DOM-mutation diffs* rather than full payloads — jsonl-of-full-prompts will balloon faster than 14-day rotation assumes; consider payload-size budget per file (e.g. 50 MB) with rotation-on-size in addition to rotation-on-date.
PICKED: FLAWED-FIX-INLINE

## ⚙️ Pragmatist

AGENT: Pragmatist
STANCE: CONDITIONAL
REASONING: This spec is a thoughtful design but it's a 3-week build dressed as a V1, landing exactly when Alex has 8 days to PS HW and a 28-day finals gauntlet. Realistic LOC: TutorEventLog.kt + hooks ~250 LOC; provenance.mjs ~80; findings-stale-check.mjs ~120; verify-openrouter-quota-isolation.mjs ~150; surface-x.mjs ~400; surface-z.mjs ~500; surface-y.mjs ~700; plus 15-25 hand-labeled fixtures at ~10-20 min each = 4-8 hours pure attention; plus 3 schemas = ~3 more hours; plus dogfooding/calibration loops. End-to-end with tests, this is 25-40 focused hours minimum — half of Alex's remaining pre-HW budget — and historically these undershoot by 1.5-2x once OpenRouter `:free` flakes. Two structural concerns: the schema authoring tax (~8-12 schemas before Y has reusable surface area), and the quota-isolation verdict will probably come back "ambiguous" because OpenRouter doesn't expose per-key quotas in docs.
KEY CONCERN: Surface Y is ~40% of the spec's complexity and ~60% of its leakage risk for the lowest expected yield — beginner-persona LLMs on weak `:free` tier are far more likely to discover "I clicked the wrong button" than the unknown-unknowns the success metric promises. Meanwhile Alex's actual daily dogfood already covers this lens for free. Y is a research project masquerading as tooling.
PICKED: FLAWED-FIX-INLINE

SUGGESTED CUTS:
- Cut Surface Y from V1 entirely. Defer to post-finals. Highest cost, highest leakage, lowest yield; Alex's manual dogfood already covers fresh-eyes lens daily.
- Cut Z piggyback mode (automatic if Y cut; even with Y in, piggyback couples failure modes).
- Cut programmatic visual lints from Z V1 except `LINT-snake-case`. Contrast / font-size / overflow are real but one-off audits.
- Cut mobile-viewport sweep. Single viewport (1280) V1.
- Cut advisory `RUN_LLM_EVAL=1` deploy hook. Pure manual CLI V1.
- Cut `--propose-invariants` from V1 CLI doc.
- Cut Z's vision-model branch. Text-only V1.
- Cut fixture floor 15-25 → 8-12. Statistical floor still acceptable; saves 2-3 hours.
- Cut 14-day rotation from TutorEventLog V1. Append-only first.
- Cut `live_dom_fingerprint` from provenance V1. `git_head` + `bundle_hash` + `ts_utc` enough.
- Cut INV-09 + INV-10 (latency invariants). `:free` chain latency too variable; FAIL rate trains Alex to ignore.
- Trim invariant catalog 10 → 6 (keep INV-01/02/03/05/06/08).

Net V1: pre-flight + Surface X (trimmed) + Surface Z (snake-case-lint + text-only). ~50% of spec's LOC, ~30% of calibration time, ships before PS HW Day 1. Y stays V2.

## 🧱 First Principles

AGENT: First Principles
STANCE: REJECT
REASONING: The three council rounds each accepted the surface-existence premise too early and never asked the P0 question: should Alex be building dogfood automation 8 days before his PS HW deadline at all? The spec explicitly names the constraint — "PS HW 2026-05-21 (8 days). Finals Jun 1 – Jun 21. Scarce attention." — and then proceeds to scope a 3-surface system with a BLOCKING pre-flight quota probe, a 15-25 hand-curated golden fixture set, a Kotlin event-log appender shipped to VPS, hand-authored per-task concept schemas with confusion-tuples, an external schema-gate regenerate loop, and per-surface ship gates that sequence X→Z→Y. That is weeks of engineering, not days. Every one of the council's named failure modes is a problem the system creates by existing; none threaten Alex if the system simply doesn't exist. The motivating bug (`uses_rlaplace_or_inverse_cdf_sampler` rubric chip) is a 5-minute grep + CSS fix that needs zero LLM infrastructure — Alex saw it himself, which is how it became the motivating example. The North Star says "augment, not replace, Alex's daily dogfood" — but the cheapest augmentation of Alex's daily dogfood is 30 minutes of pen-and-paper PS study followed by a written "what felt broken today" note, not 200+ files of automation scaffolding.
KEY CONCERN: This is procrastination dressed as infrastructure. The act of building this system is itself a dodge from the actual study task it claims to support, and the council process — by accepting "we're building 3 surfaces" as the question — never gave Alex the chance to hear "don't build it; go study."
PICKED: WRONG-APPROACH-RESTART

## ⚠️ Risk Analyst

AGENT: Risk Analyst
STANCE: CONDITIONAL
REASONING: The spec sequences X→Z→Y and reasons about each surface's quota/PII/falsifiability in isolation, but the compound failure modes bite at the intersections. Three concrete cross-surface holes:
(1) DOM fingerprint stale-check thrash — provenance stamp hashes `page.content()` including ULID-bearing data attrs (event IDs are ULIDs per TutorEventLog; chip data-keys, drill data-card-ids, session-id cookies bleed into rendered DOM). `live_dom_fingerprint` mismatches on every run because timestamps bake into the hashed surface. Every X/Y/Z finding reads `[STALE: DOM changed]` immediately after generation — Alex trains to ignore [STALE] flag — DA round-2 mitigation collapses entirely.
(2) Z-piggyback + Y persona race on shared Playwright context — Y emits `action: "ask_sidekick"` → fires real `/api/v1/sidekick/ask` against live VPS → DOM mutates mid-stream → Z's screenshot captures partial-stream sidekick reply → layperson critique flags phantom rendering bugs. Worse, sidekick call ALSO writes to `tutor_events.jsonl`, so future Surface X replay grades Y's synthetic ask against real-user invariants and may flag INV-05 citation failures that are Y-induced, not site bugs.
(3) 80%-agreement gate against stochastic LLM-judge with N=25 — Two calibration runs on same 25 fixtures produce different agreement % at temperature>0. With N=25, 95% CI on a 0.80 point estimate is roughly ±0.16. Alex ships "passing" calibration that on re-run reads 0.68 and fail-flips. Compounded: if Y's schema-gate fires consistently on one leaky concept, each step burns 3 calls — Y cap exhausts in 16 STEPS not 50, emits "done" findings after barely touching the task.
Secondary: `llm_input_full` retention. R-code answers (200-2000 chars × ~10 drills/day × 14 days) accumulate ~3-6 MB credential-grade student work product. Spec says "14-day rotation" but doesn't address backup boundary — VPS-backup weekly snapshots make retention backup-cycle bounded, not 14-day bounded.
KEY CONCERN: DOM fingerprint will false-positive [STALE] on every read because spec hashes raw `page.content()` which includes per-render ULIDs/timestamps/session-derived data-attrs. Silently invalidates the entire Devil's Advocate round-2 anti-zombie mitigation. Highest-leverage failure because it converts a designed-in safety mechanism into anti-signal.
PICKED: FLAWED-FIX-INLINE

---

## Sanity Check

SANITY Devil's Advocate: PASS — Concrete: INV-10 named, OpenRouterChatLlm latency claims grounded, ship-gate dependency DAG audited. Verdict (FLAWED-FIX-INLINE) consistent with REJECT stance (criticizes whole spec, suggests collapse to snake_case lint).

SANITY Domain Expert: PASS — Named real frameworks (RAGAS Cohen's κ, DeepEval GEval, LangSmith ≥75% floor, MathVC arXiv:2404.06711, DVC, MLflow, W&B, Promptfoo CI anti-pattern, Datadog Session Replay, FullStory, Sentry Replay). Specific actionable fix (add 2 fields to getStamp). Verifiable.

SANITY Pragmatist: PASS — Realistic LOC + hours estimate. Suggested cuts are specific and actionable. Y-as-research-project frame is grounded in Milička 2024 + the schema-authoring tax math.

SANITY First Principles: PASS — Surfaces the P0 question the prior councils never asked. Names the specific constraint (PS HW 8 days) the spec opens with then ignores. Calls procrastination by name. Verdict consistent with REJECT.

SANITY Risk Analyst: PASS — Three concrete cross-surface failure modes with grounded mechanisms (ULID in DOM, Y-sidekick-call-to-real-API race, stochastic 80% CI math at N=25). Each one is testable. Secondary backup-boundary concern is real and not previously surfaced.

---

## Judge

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
COUNCIL VERDICT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

VERDICT: FLAWED

CORE FINDING:
The unified spec is well-crafted in literature-cited detail but has THREE classes of defect the prior single-question councils didn't catch: (1) cross-surface compound risks (Risk Analyst's DOM fingerprint stale-thrash, Z/Y Playwright race, stochastic 80%-CI on N=25); (2) self-inflicted unsatisfiable invariants (Devil's Advocate's INV-10 + Pragmatist's INV-09 — `:free` chain latency variability makes these FAIL by design); (3) cost-vs-value inversion (Pragmatist's audit shows Y is 40% complexity for least yield; FP's reframe asks whether the whole exercise is procrastination from PS study). Domain Expert ratifies the literature shape but flags two missing provenance fields (judge model + judge prompt hash) that are MLflow-standard. The spec is shippable AFTER inline fixes, OR collapsible to a 1-hour snake_case-lint patch if FP's P0 question is taken seriously.

AGENT CONSENSUS: 1 REJECT-RESTART (FP, "don't build this now"), 4 CONDITIONAL FLAWED-FIX-INLINE (DA, DE, P, RA). Zero APPROVE. Zero flagged.

KEY ISSUES (in priority — fix order if you ship):

1. **FP P0 question — surface to Alex (CRITICAL).** Before any inline fix lands, Alex must consciously confirm: "I want to build this NOW with 8 days to PS HW, not study PS instead." FP's argument: motivating bug is a 5-min CSS fix, manual dogfood already covers the rest, building this IS the procrastination. Alex's answer determines the rest of the path.

2. **DOM fingerprint stale-thrash (CRITICAL — RA).** `page.content()` hashes ULIDs/timestamps/session-derived data-attrs → false-positive `[STALE]` on every read → mitigation collapses into noise. Fix: normalized fingerprint (strip ULIDs, ISO timestamps, session cookies, ephemeral `data-*` before hashing) OR drop `live_dom_fingerprint` from V1 entirely.

3. **Unsatisfiable latency invariants (CRITICAL — DA + P converge).** INV-09 (sidekick p95 ≤ 8s) and INV-10 (grader p95 ≤ 12s) cannot be satisfied on `:free` chain with 90s timeout + 429 fallback cascade. FAIL every X run → Alex disables/ignores X. Fix: drop INV-09 + INV-10 from invariant catalog, OR recast as "latency-budget surface" deferred to V2.

4. **Missing judge-side provenance fields (HIGH — DE).** Add `judge_model_resolved` + `judge_prompt_sha256` to `getStamp()` output schema. Without these, findings produced under different judge model resolutions or prompt versions are silently conflated.

5. **Stochastic ≥80%-agreement gate at N=25 (HIGH — RA).** ±0.16 CI fail-flip risk. Fix: temperature=0 + fixed seed for judge calls during calibration, OR 3-run majority vote, OR recast gate as `≥K exact-match of N` (statistical floor, not point-estimate).

6. **Z-piggyback + Y race on Playwright context (HIGH — RA).** Add `is_synthetic: true` flag to Y-driven `tutor_events.jsonl` entries; Surface X replayer must skip synthetic events when grading real-user invariants. Add `waitForLoadState('networkidle')` guard before Z screenshot capture inside Y session.

7. **R-code answer credential-grade retention boundary (HIGH — RA).** For `event_type=drill_grade` envelope, hash the R-code answer + store hash + first/last 40 chars; drop raw `llm_input_full` body. Closes backup-snapshot leak path.

8. **Y is V2, not V1 (P recommendation).** Cut Y from V1 spec. Defer to post-finals. V1 = pre-flight + X (trimmed) + Z (snake-case-lint + text-only).

RECOMMENDED PATH:
**Two paths offered — Alex picks based on his answer to FP's P0 question:**

**Path A — FP "you should study, not build":** Ship a 1-hour snake_case-lint patch directly. ~60 LOC: detect snake_case in rubric_chip rendering at frontend, humanize via `_.startCase()` or equivalent. Commit + deploy. Defer the whole 3-surface spec to post-finals. Brainstorm complete; writing-plans NOT invoked.

**Path B — Alex confirms intent to build now:** Apply inline fixes 2-7 to spec, cut Y to V2 per fix 8. Updated V1 scope:
- Pre-flight: `tools/verify-openrouter-quota-isolation.mjs`, `tools/lib/provenance.mjs` (without `live_dom_fingerprint` OR with normalized fingerprint), `tools/findings-stale-check.mjs`, `TutorEventLog.kt` (with R-code hash redaction).
- Surface X: 6-invariant catalog (drop INV-09 + INV-10 + INV-04 + INV-07), 8-12 fixture floor, ≥K-of-N exact-match gate (not point-estimate), `judge_model_resolved` + `judge_prompt_sha256` in stamp.
- Surface Z: text-only, single 1280 viewport, snake_case-lint only V1.
- Surface Y: cut to V2.

Either path is acceptable; FP's REJECT is on the WHEN, not the WHAT. The spec itself is sound architectural work — the question is whether it's the right thing to build in the next 8 days.

CONFIDENCE: 8
[Climbs to 9 once Alex answers FP's P0 explicitly. Drops to 6 if Alex picks Path B and refuses cuts #2-7 — the cross-surface risks make a too-faithful build of the current spec dangerous.]
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Output saved to: .claude/council-cache/council-1778696411-standin-full-spec-review.md
