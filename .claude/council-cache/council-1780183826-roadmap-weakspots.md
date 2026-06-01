# Council review — 1780183826 (roadmap weak-spot hunt)

**Problem:** Find the weak spots in the dependency-ORDER/STRUCTURE of the full does-everything-tutor roadmap (vision already approved by council 1780181270 — this review attacks the build sequencing only). Builder will NOT dogfood incrementally (won't use it until fully built); cannot self-vet subject correctness.
**Proposed approach (the roadmap under review):** E1 trustworthy grader+mastery [ROOT] → E2 real grounded content (+ extraction-gate #1, mine misconceptions #4) → E3 generate drills (marked DONE) → E4 teach + study loop + conversational teaching + forced-retrieval #3 + inference-check #2 → leaves (auto-detect+scrapers, multi-subject, voice, curator). Cross-cutting: relay-resilience #5, no un-cited claim reaches student.
**Timestamp:** 2026-05-30T23:30:26Z

---

## 🔴 Devil's Advocate
AGENT: Devil's Advocate
STANCE: REJECT
REASONING: The roadmap's load-bearing claim — "E3 generate drills — DONE, proven on real model" — is verifiably false as a dependency-satisfied edge. The only real-relay proof (E3RealRelayProofTest.kt:62-80) drives generation off a single hand-authored in-test fixture (PROOF_KC, a self-contained binary-tree node-count fact), explicitly commented "a test proof-fixture, NOT real curated subject content." The actual corpus is one subject deep: content/PA/ has 6 real KCs + 2 misconceptions + 1 lecture source; ALO/POO/PS/SO-RC are empty .gitkeep shells. So E3 was proven against a clean, deterministic, self-grounding fixture precision-built to dodge the exact failure modes this session surfaced (chatty-model false-reject, grader self-inconsistency) — and that proof is being inherited as "DONE" by the whole order. The real risk lives at the E2→E3 seam that was never exercised: when E2's noisy multi-quote KCs (see pa-kc-001.yaml's three competing partial definitions of "algorithm") feed E3, the generator must ground in ambiguous, non-self-solving prose, and the false-reject rate the test masks with count=3 becomes a content-starvation failure that no later gate catches.
KEY CONCERN: The E2→E3 edge is mislabeled "satisfied." E3 is not DONE — it is proven only against a synthetic fixture engineered to be groundable and self-solvable. E2 and E3 must be re-sequenced as co-dependent and re-validated together: E2's first real grounded KCs fed through E3's live generator with the real (not deterministic-fake) grader before either is marked done — otherwise the false-reject/grader-inconsistency rate silently starves the E4 study loop and Alex (can't self-vet, won't dogfood) never finds out.

## 📚 Domain Expert
AGENT: Domain Expert
STANCE: CONDITIONAL
REASONING: The horizontal layer-by-layer order (E1-fully → E2-fully → E3 → E4) is the classic mistake the walking-skeleton / vertical-slice literature exists to prevent: it maximizes integration risk and defers the first end-to-end signal to the very end — exactly the failure this learner already lived through (the 2026-05-11 ghost-component lesson: every layer green in isolation, nothing wired, user saw the old screen). For a solo builder who will NOT dogfood until "everything" is done, the absence of incremental usage actually strengthens the case for a thin slice: with no human catching integration gaps, the only thing that proves the stages compose is running one real concept through E1→E2→E3→E4 first, then fanning out breadth. "Everything personalizes off E1" is also a mis-rooting: the ITS learner-model literature (knowledge-component models, BKT) is unambiguous that the KC graph + prereq edges + grounded corpus are the substrate the mastery model indexes into — the grader/mastery rule is parameterized by KCs, so E2's KC corpus is the true root and E1 is a consumer; a deterministic grader with no validated KCs to attach observations to has nothing to personalize. The roadmap's content is right and dependency-honest otherwise (closed-corpus RAG grounding precedes generation; non-circular vision-confirm; FSRS+mastery as E4 consumer), so this is a re-sequencing fix, not a redesign.
KEY CONCERN: Strict horizontal layering defers the first end-to-end integration proof to the final epic for a builder with no dogfood loop — invert to a vertical walking-skeleton through one PA concept across all four stages first, and re-root the dependency graph on E2's KC/prereq corpus rather than treating E1's grader as the thing everything personalizes off.

## ⚙️ Pragmatist
AGENT: Pragmatist
STANCE: CONDITIONAL
REASONING: The real build cost of THIS order is low-to-moderate per node (E1/E2/E3 are largely already in main — GradeScoring.kt, KcMastery.kt, server-side mastery wiring, the E3 generator all committed), but the order buys almost no reality-check until the very end, which is the expensive part for this builder. Every gate's "proof" today is unit/integration tests plus one Playwright smoke that stubs every API and feeds hand-written fixtures (drill-viz-paint.spec.ts) — so "green + paints" has never once meant "a real PA problem went in, got really graded, and really moved a kc_mastery row." The single honest end-to-end checkpoint that doesn't need Alex (E2 plan Task 6: ingest one real PA exam problem → drill it → watch kc_mastery move on the running app) sits buried after E2's full build and shows no sign of having been run, while the first teaching surface he'd judge is E4, the last node before the leaves. So a wrong assumption in the grader, the EWMA rule, or the grounding gate stays invisible behind green tests until everything is done — maximally expensive to unwind. The cheap fix is a pure reorder: pull E2-Task-6 forward and make it the gate after E1, with one real PA exam problem ingested via prep-authored and drilled on a gradle runWeb instance (Claude drives it, not Alex), and pin a 5-question "does this grade/teach sanely?" rubric Claude self-checks before E2/E4 build on top.
KEY CONCERN: The whole roadmap's only non-stubbed "is it actually teaching/grading?" checkpoint (E2 Task 6) is sequenced after the build that depends on it being right, behind tests that have never exercised a real ingest→grade→mastery path end-to-end; reorder it to run on real data right after E1 so a baked-in wrong assumption surfaces when it's cheap.

## 🧱 First Principles
AGENT: First Principles
STANCE: CONDITIONAL
REASONING: From the raw goal up: the system's value is unaided exam-day reproduction, and every signal personalizes off a grade — but a grade is meaningless without (a) something correct to grade against and (b) a KC structure for the mastery rule to range over. The "E1 grader is the ROOT" edge is partly backwards by the plans' own admission: E1 Task 5 records mastery off client-supplied conceptIds (e1...md:530), and E2 Task 4 exists only to rip that out and re-point to server-side Problem.kcIds (e2...md:510-715) — those kcIds, the grounded corpus, the rubrics, and canonical answers are all E2 artifacts, and the corpus is empty for 4 of 5 subjects today. So the honest dependency is content-and-KC-structure (E2) co-precedes the grader (E1): the deterministic-scoring mechanics of E1 can be built first (pure functions over a rubric), but the grader cannot produce a trustworthy mastery signal until E2 supplies grounded KCs + server-side rubrics. Second backwards edge: the stated TRUE goal is retrieval-forcing, yet the loop-level forced-retrieval mechanism (withhold-until-attempt, schedule recall, measure unaided perf) is buried last in E4 — a mechanism the whole vision's value rests on is not a late add-on; "frictionless tutoring now, retrieval-forcing later" risks shipping a fluency-illusion engine an ADHD learner rides to blank exam-day recall. (Mitigant already in repo: the prediction/giveUp predict-before-reveal anchor exists in DrillGrader.kt:164-282.)
KEY CONCERN: The single foundational thing the whole vision rests on — and that the ordering treats as derivative — is the grounded KC corpus + prereq-edge structure (E2). The grader, mastery rule, misconception-diagnosis, prereq-gating, and retrieval-scheduling are ALL downstream of "do we have correct, structured, exam-linked content for this subject?" — true today only for PA. "Grader is the root" is the false edge; the true root is trustworthy content + KC graph, with the deterministic-scoring half of E1 buildable in parallel but the mastery-signal half strictly dependent on E2. Pull the retrieval-forcing contract forward as a first-class invariant, not an E4 tail task.

## ⚠️ Risk Analyst
AGENT: Risk Analyst
STANCE: CONDITIONAL
REASONING: The order makes E2 ingest a whole corpus — grounding, (page,span,provenance), KC edges, exam→rubric — on top of raw machine-extracted text, while the #1 extraction-confidence gate sits LATER inside the same E2 and the cross-model inference-check (#2) sits all the way out in E4; combined with the known circular-grounding hazard (validator checks quotes against the very garbled extraction it should distrust) and zero incremental dogfooding, garbage-extracted subjects will pass validation by self-agreement and silently encode wrong KCs, spans, and rubrics. E3 is DONE, so drills are already generated against whatever E2 produces, meaning wrong content propagates one layer deeper before any guardrail lands. Because Alex cannot vet correctness and won't touch the system until fully built, the first signal that a corpus is poisoned arrives at the very end, after re-ingest/re-ground/re-generate cost is maximal — a classic late-binding trust failure. Manageable with one ordering fix: promote #1 to the FIRST task of E2 (extraction gate = ingest precondition, blocking garbled PDFs from ever being ground against), and run #2 cross-model inference-check as a per-batch corpus audit during E2 ingest, not an E4 teach-time feature; at minimum gate the FIRST subject through both before mass ingest.
KEY CONCERN: CRITICAL — the extraction-confidence gate (#1) and non-circular vision-confirm must run BEFORE any grounding/KC/rubric generation in E2, not mid-E2; otherwise a full corpus is built on un-gated garbled text and the poisoning is discovered only after E3-generated drills already depend on it, with no dogfood signal to catch it early.

---

## Sanity Check
SANITY [Devil's Advocate]: PASS — code-verified (read the proof test + corpus); the "E3 DONE is synthetic-only" finding is concrete and the E2→E3 co-validation fix is specific. In lane.
SANITY [Domain Expert]: PASS — named the walking-skeleton/vertical-slice pattern + BKT learner-model rooting; tied it to the project's own 2026-05-11 ghost-component history. Concrete fix. In lane.
SANITY [Pragmatist]: PASS — code-verified (E1/E2/E3 in main; Playwright stubs every API); the "only real checkpoint = E2 Task 6, sequenced too late" finding is specific + the reorder fix needs no new scope + no dogfooding. In lane.
SANITY [First Principles]: PASS — actually stripped the framing; the false-root finding is plan-cited (E1 Task 5 → E2 Task 4 rip-out) and the retrieval-forcing-is-foundational point is sharp. In lane.
SANITY [Risk Analyst]: PASS — concrete late-binding-trust failure, ranked CRITICAL, precise ordering fix (promote #1 to ingest precondition). Overlaps Devil's Advocate on corpus-poisoning but the guardrail-ordering angle is distinct. In lane.

No agents flagged. 1 REJECT + 4 CONDITIONAL — unanimous that the ORDER is structurally wrong, with a convergent fix.

---

## Judge
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
COUNCIL VERDICT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

VERDICT: FLAWED

CORE FINDING:
The vision is sound (already approved); THIS build ORDER is structurally flawed in a way that stays invisible until the very end — the worst case for a builder who won't dogfood and can't self-vet. Three code-verified, convergent errors: (1) WRONG ROOT — E2 (grounded content + KC/prereq graph) is the true root, not E1; the grader's mastery signal is parameterized by E2's kcIds/rubrics (the plans' own E1-Task-5 → E2-Task-4 rip-out proves it), so only E1's deterministic-scoring *mechanics* can precede E2, not its trustworthy signal. (2) WRONG STRATEGY — strict horizontal layering defers the first real end-to-end signal to the end (the exact ghost-component trap this project hit on 2026-05-11); the fix is a vertical walking-skeleton through ONE real concept. (3) GUARDRAILS TOO LATE — the extraction-gate (#1), inference-check (#2), and forced-retrieval (#3) are sequenced *after* the content/drills/teaching they exist to protect, so a poisoned corpus or a fluency-illusion engine is discovered only at the end. Bonus: "E3 DONE" is overstated — proven only on a synthetic in-test fixture, never on real E2 content.

AGENT CONSENSUS: 1 REJECT, 4 CONDITIONAL, 0 APPROVE — 0 flagged. Unanimous that the order is wrong; convergent on the fix.

KEY ISSUES (priority order):
- **Re-sequence to a vertical walking-skeleton.** Run ONE real PA concept all the way through ingest → ground → generate → grade → mastery → teach, end-to-end on the live app, FIRST — then fan out to breadth (more concepts, more subjects). Claude drives it; no dogfooding required. (Domain Expert, Pragmatist, Devil's Advocate.)
- **Re-root on E2 (content + KC graph).** E1's deterministic-scoring mechanics can be built in parallel, but the trustworthy mastery signal depends on E2's kcIds/rubrics. Stop calling E1 "the root." (First Principles, Domain Expert.)
- **Move the guardrails to the front of their gate.** Extraction-confidence gate (#1) = the FIRST step of E2 / an ingest *precondition* (block garbled PDFs before grounding). Inference-check (#2) = an ingest-time corpus audit, not an E4 feature. Forced-retrieval (#3) = a first-class invariant from the first slice (the predict/giveUp anchor in DrillGrader already exists to build on), not an E4 tail. (Risk Analyst [CRITICAL], First Principles.)
- **Re-label E3.** Not "DONE" — "proven on a synthetic fixture; needs real-E2-content validation," which IS the walking-skeleton's E2→E3 leg. (Devil's Advocate.)

RECOMMENDED PATH:
Keep every component and the full vision. Change the ORDER from "horizontal layers, root = grader, guardrails late, validate at the end" to: **vertical walking-skeleton on one real PA concept, rooted on content (E2), guardrails as ingest preconditions, forced-retrieval as a first-class invariant, validated end-to-end on the live app early — then breadth.** This is a re-sequencing, not a redesign; most components already exist in main.

CONFIDENCE: 9
Multiple agents independently code-verified the findings (proof-test fixture, empty subject dirs, E1→E2 rip-out, stubbed Playwright, in-main components) and converged. What would change it: only if the deterministic grader turns out to need no KC structure at all (it does), or if a vertical slice proves infeasible to run headless (the relay + gradle runWeb make it feasible).
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
