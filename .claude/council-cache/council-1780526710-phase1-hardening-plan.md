# Council review — 1780526710

**Problem:** Is the orchestrator's proposed "Phase-1 hardening commit" (6 fixes + 2 product decisions) + harden→push→Phase-2 ordering the BEST option, or is there a smarter path? (Second council; the first — 1780525901 — recommended a hardening pass; this checks the SPECIFIC plan.)
**Proposed approach:** (1) hardening commit: (a) implement PhaseModel NO-regression floor max(computed,current) + test + KDoc; (b) ~100-NULL-card index regression test; (c) parameterize the string-interp SQL; (d) worked_example_first stays schema-false + serving-degrades-true; (e) delete dead MigrationResult.Failure; (f) fix the M-PARTIAL "off-box" label. (2) re-:check + push. (3) THEN extract PA PDF + Phase 2.
**Project context:** Kotlin/Ktor + Exposed/SQLite single-user tutor; keystone = Phase-2 trust-net; frozen signatures-lock; FSRS-5-lite (a forgetting/decay model) runs in-repo; Phase 3 owns the teaching loop (ScaffoldPlanner, §4.1 phase-gate, NextPhaseAction{advance,hold,remediate}); Alex can't vet pedagogy; Phase 2 relay-dependent.
**Timestamp:** 2026-06-04T~04:05Z

---

## 🔴 Devil's Advocate — CONDITIONAL
The plan commits the exact sin the prior council warned against. Prior verdict #1 was a DISJUNCTION — "DECIDE the demotion rule, then implement no-regression OR delete `current`." The plan silently picks the heavier branch (implement no-regression) WITHOUT recording the decision, and that branch invents a product rule that exists nowhere in the frozen contract: §A keeps `current` but specifies only "null current ⇒ intro" — no floor — and demotion semantics (NextPhaseAction.remediate, §4.1) live in PHASE 3. Floor-ing now hard-codes "a learner can never drop a phase" before the teaching loop that gives the rule meaning exists — gold-plating under a "fix" banner; forecloses the `remediate` path Phase 3 owns.
KEY CONCERN: The no-regression BEHAVIOR is premature/unspecified — defer to Phase 3. Phase-1's PhaseModel task should do only: (a) delete the misleading `max(computed,current)` KDoc/test comments so green tests stop lying, (b) add the computed<current boundary test asserting the FROZEN behavior. Everything else in the commit is correctly scoped + ordered.

## 📚 Domain Expert — CONDITIONAL
Items (b)(c)(e)(f) are textbook incremental-hardening (low blast radius, harden-contract-then-proceed) — NOT gold-plating, the NULL-distinct + un-parameterized backfill are genuine latent defects Phase 2/3 build on. Item (a) diverges: a hard `max(computed,current)` floor contradicts the mastery-learning literature. Bloom/Khan treat MASTERY-ACHIEVED as monotonic, but the models the roadmap names (BKT P(forget), PFA negative-evidence) carry DECAY, and FSRS — which this repo runs — is fundamentally a forgetting model. A coarse 4-bucket phase from a decaying EWMA ≠ "mastery attained", so a blanket floor wrongly traps a KC at `retrieval` after an accuracy collapse. The KDoc (PhaseModel.kt:50-52) already documents "regression IS allowed" — the defensible default. The one floor that's right (`mastered` don't-un-master) is ALREADY enforced (`if (mastered) return Phase.mastered`, line 56). Item (d) = correct Sweller/Renkl worked-example fading (never strand a novice).
KEY CONCERN: Item (a)'s hard floor is the wrong fix + over-reaches into Phase-3. The real defect = the `current` param is dead + doc/test inconsistent. Correct hardening: DECIDE the demotion rule, then either (i) a SCOPED floor = never demote below `entry_phase` (the §4.1 placement floor — the monotonicity actually wanted) OR (ii) delete `current` + record "phase may regress with decay" — NOT a blanket monotonic floor that fights FSRS. Add the computed<current test either way. Sequencing is correct; don't gold-plate into a full code-review (this council served that).

## ⚙️ Pragmatist — CONDITIONAL
The two HIGH items carry the value + must ship before Phase 2/3 consume them: the ~100-NULL-card migration test (the whole 828-corpus rides on SQLite NULL-distinct — real data-loss gap, cheap, non-negotiable) + an explicit RECORDED decision on PhaseModel regression. But the KDoc is ALREADY reconciled with the impl (lines 50-52 say regression intentional), so item (a) collapses from "fix a contradiction" to "delete dead `current` OR keep it + write the one missing test + record the call" — 10 min, not a redesign. The other four are zero-blast-radius hygiene; worked_example_first is a NON-divergence (data-model-lock §358 already defines false as content default, degrade-to-true is the unbuilt serving fallback) — one-line comment, nothing to reconcile. Bundle into ONE "hardening" commit then re-:check + push — this is EXECUTION of the 2nd council, not a 3rd; stopping to deliberate the trivia further IS the over-processing risk.
KEY CONCERN: Biggest risk is OVER-processing, not under-fixing. Do the two HIGH items as the load-bearing change + sweep the four trivia into the SAME commit (no separate "should we relabel?" deliberation). Only two things must be written down: the allow-regression product decision + a one-line worked_example_first note — those could silently teach wrong later; everything else is keystrokes, not judgment.

## 🧱 First Principles — CONDITIONAL
Verified directly: the production KDoc (PhaseModel.kt:50-52) + every Group-6 test BODY already tell the truth (threshold recompute, regression allowed); the LONE lie is the stale test-FILE-HEADER comment (PhaseModelTest.kt:17-22) claiming "result=max(computed,current)" — no code implements it, no test asserts it. `current` is a FROZEN signature field but no-regression is nowhere in §A, and its only consumer (the §4.1 gate) is Phase-3 teaching logic that doesn't exist yet. Implementing `max(computed,current)` NOW manufactures a pedagogy commitment (phase can never decay) Phase 3 may want different, on a learner Alex can't vet — wrong thing under a "harden" banner.
KEY CONCERN: Item 1(a) inverts the right move — it codes an unspecified, un-consumed Phase-3 rule into a frozen leaf (locking out future demotion) when the actual defect is purely a stale comment. Fix = delete the stale header comment so KDoc/tests/impl agree (regression allowed), keep `current` in the signature (frozen, harmless, leaves the door open), explicitly DEFER the demote-vs-floor decision to Phase 3. The genuine-safety items (b/c/e/f) + the (d) reconciliation all ship now. APPROVE the plan minus 1(a)-as-written.

## ⚠️ Risk Analyst — CONDITIONAL
CRITICAL (new risk the plan INTRODUCES): item (a) no-regression floor contradicts the frozen contract + current code. §A (canonical) never mandates no-regression; PhaseModel.kt:50-52 explicitly says "regression IS allowed... retrieval→practice after bad runs." A `max(computed,current)` floor pins a learner at retrieval/mastered after accuracy collapse → FSRS/scaffold keeps serving closed-book retrieval to someone who's decayed — the exact bug Phase-3's gate prevents. This is the freeze-violation in reverse: a behavior baked under a green test that Phase 3 consumes as truth. Fix = "DECIDE demotion explicitly (default: allow regression, mastered-flag still wins), then align KDoc/test" — do NOT default to the floor.
HIGH (papered-over): item (f) "fix the LABEL" is insufficient — db-backup.py writes ./backups on the SAME disk; both the M-DB precondition AND the M-PARTIAL hook call it. Relabel makes the comment honest but leaves Phase 2 (which mutates more schema) with NO real off-disk net — a disk-loss/half-ALTER that also corrupts ./backups loses the 828-card corpus. Label-only is tolerable for Phase 1 (done, DB verified healthy) but the REAL off-disk copy (scp/rsync to a 2nd location) must be wired — or a gated pre-Phase-2 off-disk dump — BEFORE Phase 2's first mutation.
KEY CONCERN: Item (a) as written invents a behavior the contract never specified + the code disclaims; reframe to DECIDE-then-align (default regression-allowed). And item (f) must become a REAL off-disk backup before Phase 2, not just a relabel.

---

## Sanity Check
SANITY Devil's Advocate: PASS — grounded in §A + the prior council's disjunction; the "don't manufacture a Phase-3 rule" attack is valid + corroborated by all 4 others.
SANITY Domain Expert: PASS — strongest reasoning; correct that FSRS is a forgetting model + `mastered` floor already enforced; the `entry_phase`-floor refinement is a genuine improvement.
SANITY Pragmatist: PASS — correct that the KDoc is already honest (only the test header lies) + the over-processing warning is on-point.
SANITY First Principles: PASS — verified the exact lie location (test header vs honest KDoc/bodies); minimal-correct-plan reasoning sound.
SANITY Risk Analyst: PASS — the CRITICAL (a)-introduces-a-new-risk framing + the HIGH (off-box-label-insufficient-before-Phase-2) both hold.

---

## Judge
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
COUNCIL VERDICT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

VERDICT: FLAWED (the plan is ~80% right; ONE item is actively wrong, ONE is insufficient — both with clear fixes)

CORE FINDING:
The hardening plan is right in shape and scope — EXCEPT item (a). All five agents independently say: do NOT implement a `max(computed,current)` no-regression floor. It invents a pedagogy rule (phase can never decay) that the frozen contract never specifies, that FSRS's own forgetting/decay model argues AGAINST, and that belongs to Phase-3's teaching loop (NextPhaseAction.remediate / §4.1) which doesn't exist yet — baking it now hands Phase 3 a wrong phase-gate (decayed learners pinned at retrieval) under a green test. The actual defect is tiny: ONE stale test-file-header comment lies; the production KDoc + test bodies already tell the truth (regression allowed, `mastered` wins). Second correction: item (f) — relabeling the "off-box" backup is not enough; before Phase 2 mutates more schema, a REAL off-disk backup must be wired.

AGENT CONSENSUS: 0 APPROVE, 0 REJECT, 5 CONDITIONAL — unanimous: plan good minus item (a)-as-written; (f) needs upgrading.

KEY ISSUES:
- Item (a): FLIP from "implement no-regression floor" to "tell the truth + defer the rule." Do: delete the stale PhaseModelTest.kt header comment; keep the honest KDoc; add the missing computed<current boundary test asserting the FROZEN behavior (regression allowed, `mastered` still wins via the existing line-56 floor); RECORD the product decision ("phase may regress with mastery decay; mastered is the only floor; entry_phase-floor + demotion semantics = Phase 3"); keep `current` in the frozen signature.
- Item (f): make the off-box backup REAL before Phase 2 — wire defaultBackupHook (and/or a gated pre-Phase-2 step) to scp/rsync the dump off-disk (the VPS, already reachable), not just fix the wording.
- Items (b)(c)(d)(e): confirmed correct — keep. (b) NULL-index test is non-negotiable. (d) worked_example_first needs only a one-line comment (not even a divergence).
- Process: this is the FINAL council — execute, don't convene a third. Sweep the trivia into the one commit.

RECOMMENDED PATH:
Execute the revised commit: (a)-corrected [truth + defer + boundary test + recorded decision], (b), (c), (d as a comment), (e), (f-upgraded to a real off-disk copy). Re-:check, push. Then extract the PA PDF + start Phase 2. Ordering confirmed correct (close the foundation before the relay-dependent Phase 2).

CONFIDENCE: 9
Unanimous, grounded in the actual code (the exact lie location verified), corroborated across all five lenses. The only residual judgment is whether to also pin the `entry_phase`-floor refinement now — defer it to Phase 3 with the rest of the demotion semantics.
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Output saved to: .claude/council-cache/council-1780526710-phase1-hardening-plan.md
