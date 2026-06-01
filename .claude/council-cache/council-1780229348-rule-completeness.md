# Council review — 1780229348 (safety rule-set completeness)

**Problem:** Is the tutor's safety RULE-SET complete for a learner who cannot self-vet correctness, or is a rule MISSING? (Asked by Alex while reviewing the north-star roadmap §2/§3.3/§7.)
**Rule-set under review:** Mitigations (1) capable model student-facing, (2) ground every claim / no un-cited claim / "I don't know">confident-wrong, (3) known pedagogy. Guardrails #1 extraction-confidence gate @ingest, #2 cross-model inference-check @ingest, #3 forced-retrieval, #4 mine misconceptions, #5 relay-resilience. Cross-cutting: no un-cited claim, relay-resilience, render-before-claim.
**Verdict:** INCOMPLETE — 5 distinct missing rules.
**Timestamp:** 2026-05-31 (epoch 1780229348)

---

## 🔴 Devil's Advocate — STANCE: REJECT
Every listed rule chains off PROVENANCE, not TRUTH: grounding proves a claim came from his PDF, the inference-check proves it follows from the quote, the extraction gate proves the quote was read cleanly — none touch whether the PDF ITSELF is wrong/ambiguous/contradicted by the exam key (lecture slides for a novice are routinely simplified-to-incorrect or contain typos). A claim faithfully grounded in a flawed slide passes all five guardrails, gets taught, and FSRS burns it into long-term memory — the retention mechanism guarantees a wrong answer is unaided-retrievable on exam day, with no rule to pull it back. The set is also unfalsifiable: no standing spot-audit against an external oracle, no calibration metric, so a systematic error class runs for weeks invisibly.
KEY CONCERN: No retraction/un-learning rule paired with an external-oracle audit. Need a 6th guardrail: standing spot-audit of taught KCs against a source independent of his slides (exam keys, a 2nd textbook, cross-model adjudication), AND a propagation path so a KC found wrong suspends/corrects every FSRS card derived from it and re-teaches the correction. Grounding makes errors traceable; nothing makes them reversible.

## 📚 Domain Expert — STANCE: CONDITIONAL
Strong on generation-side grounding (RAG/attribution, abstention) and forced-retrieval pedagogy (testing effect, spaced retrieval — Roediger/Karpicke, Bjork desirable-difficulties). But a measurement blind spot the field treats as non-negotiable: an automated grader is a RATER; standard practice (Cohen's/Fleiss' kappa, QWK as in ASAP-AES scoring) calibrates it against a human/answer-key gold set before trusting scores — here the learner CANNOT self-vet, so an uncalibrated grader silently miscalibrates with no feedback. Also absent: item analysis (p-value difficulty, point-biserial discrimination) to flag broken drills; content QA with provenance + erratum propagation. Guardrail #2 covers ingest correctness but NOT grader calibration or item quality (distinct downstream failures).
KEY CONCERN: Grade CALIBRATION for the LLM grader against a held-out answer-key/human-scored gold set (kappa/QWK + drift monitoring). A single learner who can't detect a wrong grade means an uncalibrated scorer is the highest-severity unguarded failure — it corrupts the very mastery signal #3 and #4 depend on.

## ⚠️ Risk Analyst — STANCE: CONDITIONAL (CRITICAL)
Every rule is a PREVENTION control at ingest/teach time; none DETECT or RECOVER the residual error that survives prevention — a correctly-grounded-but-misinterpreted claim, or a rare self-consistent-but-wrong grade (both observed this session). Alex can't self-vet and won't dogfood, so no human signal catches it, and FSRS durably reinforces the wrong claim + its cards — a one-time silent error becomes permanently rehearsed false knowledge. Prevention reduces the rate but can't drive it to zero; there is no second line of defense once one slips.
KEY CONCERN: CRITICAL — missing a standing AUDIT + RETRACTION mechanism: a periodic re-verification pass (fresh model / independent grounding, re-checked against re-OCR'd or human-confirmed source to break the circular-grounding loop) over already-taught claims + graded outcomes, that on detecting a wrong claim retracts it AND quarantines/un-reinforces the FSRS cards it seeded.

## 🧱 First Principles — STANCE: REJECT
Harm-categories derived from the raw goal: (a) teaches FALSE — covered (grounding+inference-check); (b) fluency-illusion — covered (forced-retrieval); (d) faithful-but-wrong-source — only adversely "covered" (grounding GUARANTEES faithful propagation of a bad source; no rule flags a source that is itself wrong/outdated/mis-noted); (c) WRONG-THINGS / COVERAGE-ALIGNMENT — true + learned content that isn't on his exam, or exam topics with NO content — ZERO rule maps KCs to the exam blueprint or detects gaps; (e) EFFICACY-OBSERVABILITY — nothing measures whether any rule works before exam day; unfalsifiable from his side. A rule-set fully green while silently omitting half the exam, or faithfully teaching a wrong lecture, satisfies every proposed rule — structural failure, not tuning.
KEY CONCERN: Coverage/exam-alignment (category c) — no rule binds generated content to the actual exam's topic blueprint or surfaces missing coverage; he can pass every grounding/retrieval gate and still walk in with whole exam topics never taught, and with no oracle (e) neither he nor the system would know until too late.

## ⚙️ Pragmatist — STANCE: CONDITIONAL
Several rules are auditable artifacts (extraction gate, inference-check, forced-retrieval, relay-resilience) — functions returning a number/boolean that fail a test loudly. But the two highest-stakes rules — "no un-cited claim reaches the learner" and "capable model student-facing" — are aspirations with NO enforcement point: nothing structurally blocks an un-cited sentence or a cheap-model token from reaching Alex, and with no dogfooding + a mostly-Claude loop, an unenforced rule WILL silently rot. Fix is cheap + mechanical: every student-facing payload carries a machine-checkable citation reference + model-tag; gate emission on a validator that BLOCKS (not warns) on missing/dangling citation or non-capable model tag; log every block. Then "grounded" + "capable-model" become testable and "render-before-claim" asserts against the same tags.
KEY CONCERN: A single enforcement/observability contract — every student-facing claim must carry a machine-checkable citation handle + model tag, and the emission path must hard-block + log anything failing the check; absent that block point, "no un-cited claim" and "capable model student-facing" are unfalsifiable wishes, not rules.

---

## Sanity Check
SANITY [Devil's Advocate]: PASS — provenance≠truth + no-retraction is coherent + concrete; FSRS-burns-it-in is the right severity escalation. In lane.
SANITY [Domain Expert]: PASS — named real measurement standards (kappa/QWK, item analysis); calibration gap is distinct from the ingest checks. In lane.
SANITY [Risk Analyst]: PASS — prevention-vs-detection framing is exactly right; audit+retraction fix specific; ties to this session's observed failures. In lane. (Converges with Devil's Advocate on retraction.)
SANITY [First Principles]: PASS — genuinely derived harm-categories; coverage-alignment (c) is a real whole-category gap no other rule touches. In lane.
SANITY [Pragmatist]: PASS — enforceability angle is distinct + sharp; "unenforced rule = wish" is correct; the citation+model-tag block point is concrete + cheap. In lane.

No flags. 2 REJECT + 3 CONDITIONAL — unanimous the set is INCOMPLETE; 5 distinct missing rules.

---

## Judge
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
COUNCIL VERDICT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

VERDICT: FLAWED (incomplete rule-set)

CORE FINDING:
No — the rules are not enough. The whole set is PREVENTION + PROVENANCE: it stops fabrication and proves a claim traces to his slides, but it cannot tell whether the slide is TRUE, cannot DETECT or UNDO an error that slips through (and FSRS makes any such error permanent), cannot tell whether the grader's scores are CALIBRATED, cannot tell whether the content COVERS his actual exam, and two of the headline rules have NO enforcement teeth. Five distinct gaps, none of which is a tuning issue.

AGENT CONSENSUS: 2 REJECT, 3 CONDITIONAL, 0 APPROVE — 0 flagged. Unanimous: incomplete.

THE 5 MISSING RULES (priority order):
1. **[CRITICAL] Audit + retraction (detection/recovery).** A standing re-verification pass over already-taught claims + grades; on finding one wrong, retract it AND un-reinforce/quarantine the FSRS cards it seeded, then re-teach. Closes the "residual error becomes permanent" gap. (Devil's Advocate + Risk Analyst.)
2. **External truth oracle.** Cross-check taught claims against a source INDEPENDENT of his slides (his PA exam answer-keys already embed rubrics; a textbook; cross-model adjudication) — because grounding proves provenance, not truth, and the slide itself can be wrong. (Devil's Advocate + First Principles.)
3. **Grade calibration + item analysis.** Calibrate the LLM grader against an answer-key/gold set (kappa/QWK + drift); item-analysis to flag broken drills. An uncalibrated grader silently poisons the mastery signal #3/#4 depend on. (Domain Expert.)
4. **Coverage / exam-blueprint alignment.** Bind generated content to the actual exam's topic blueprint; surface missing-coverage so whole exam topics are never silently untaught. (First Principles.)
5. **Enforcement/observability contract.** Every student-facing payload carries a machine-checkable citation handle + model tag; emission HARD-BLOCKS + logs anything un-cited or cheap-model. Turns "no un-cited claim" + "capable model" from wishes into checkable rules — and makes the others observable. (Pragmatist.)

RECOMMENDED PATH:
Add all 5 to the roadmap's rule-set (§2/§3.3/§7). #5 (enforcement contract) and #2 (external oracle, partly free via PA answer-keys) are cheap and make the rest checkable — wire them early, into the slice-1 walking skeleton. #1 (audit+retraction) and #3 (calibration) are standing background processes — design their hooks now (every taught claim + grade is logged with enough provenance to later retract/recalibrate), build the processes during breadth. #4 (coverage) gates "is a subject tutorable."

CONFIDENCE: 9
Convergent, field-grounded, each gap distinct and tied to a concrete failure (several observed this session). What would lower it: if some gaps prove to overlap in implementation (e.g. the audit pass also delivers calibration) — likely, which only makes the additions cheaper.
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
