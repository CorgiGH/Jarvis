# Council review — 1780568136 (checker-first-readiness, whole-plan context)

**Problem:** Is Claude's recommendation — "checker-first: build the trust-net (Phase 2) now, prove it end-to-end on ONE lecture authored fresh from a real PDF, then breadth" — the right next move in the context of the WHOLE frozen plan, given the session discovery that the content corpus's source-of-record looked broken?
**Timestamp:** 2026-06-04 (epoch 1780568136)

**Context given to agents:** full plan phase-chain (0 done · 1 done · 2 trust-net keystone · 3 teaching · 4 frontend · 5–6 surfaces · §7 breadth parked; "verification-net FIRST"). Discovery as Claude understood it at convene time: 8 PA KCs on a "hand-typed paraphrase" of a missing lecture; plan's named PDF (`lecture01_comppb.pdf`) a different lecture, 0/15 quotes match; 828 legacy cards quality-culled but not faithfulness-verified; raw PDFs for all 6 subjects on disk; ContentValidator grounding gate on `:check`.

---

## 🔴 Devil's Advocate — CONDITIONAL
Directionally correct, but the sequence buries the fact that invalidates the plan's gate: `LiveSourceLocator` resolves `doc:`→`_sources/{doc}.md`, and plan line 199 lets a paraphrase-backed KC reach FAITHFUL via the DEGRADED offset+fuzzy path with the page-anchor (the only leg touching a real PDF) inert — a green skeleton that proved the paraphrase loop, not source fidelity.
KEY CONCERN: Redefine Phase-2 acceptance before building — "≥1 KC FAITHFUL" is gameable by the DEGRADED path. Require the FAITHFUL KC to clear with `page_anchor_status=LIVE` against a real pdftotext PDF, AND retire the stale KCs from eligibility in the same step. [Operated partly on the "paraphrase is self-referential" premise, which FP/RA later corrected — but the acceptance-hardening point stands.]

## 📚 Domain Expert — CONDITIONAL
"Checker-first, prove on one fresh real lecture, then breadth" is the textbook tracer-bullet / walking-skeleton (Hunt & Thomas) and matches how SR systems + RAG-eval stacks (RAGAS, TruLens, DeepEval) bootstrap: a tiny human-trusted gold slice + working evaluator FIRST, then fan out; bulk-author-then-verify is the unverified-at-scale anti-pattern. Dodges the "verifier with no corpus" trap (1000+ PDFs on disk; curate-tutor Stage-1 writes `_sources`, validator hard-errors = live oracle).
KEY CONCERN: Promote author-one-real-lecture to an explicit Phase-1.5 EXIT GATE before the verifier is declared "proven" — freeze the test fixture before the system under test; don't let the checker be green only because tuned against its co-developed lecture (gold-set contamination). Gate the lecture's groundedness independently first.

## ⚙️ Pragmatist — CONDITIONAL
Checker-first is the right strategic bet (the tutor exists because Alex can't vet correctness; a trust-net pays off across 6 subjects). BUT §6.1/D2 confirms the 828 quality-culled cards are ALREADY served by `FsrsDueQueue` — the risk isn't "nothing to study," it's that this studyable surface is buried behind the OLD bundle while the fattest block in the plan (Phase 2: ~46 lines, H3–H10) consumes weeks, with finals looming + relay asleep when the PC is off.
KEY CONCERN: Time-to-visible-value — surface the 828 already-studyable cards on the live tutor NOW (thin due-queue page, honestly badged "unverified"), so the months-long Phase-2→6 keystone never leaves Alex with nothing to revise during exams.

## 🧱 First Principles — CONDITIONAL (corrected the framing)
The right primitive for "faithful teaching for someone who can't self-verify" IS a source-grounding pipeline whose terminal gate is a faithfulness check — which is what the plan is. **The "verifier has nothing to verify" premise is empirically WRONG:** `Curs 1 PA.pdf` (the .md's cited source) exists on disk and the sampled pa-kc-001 quotes match it verbatim; the "0/15" came from diffing the WRONG PDF (`lecture01_comppb.pdf`, a different Feb-2026 lecture). The real degradation is narrow: the `.md` was extracted without form-feeds → page-anchor inert (already M-PAGE/DEGRADED). Ingestion is already first-class (curate-tutor + P2-RULE1 + M-CSCHEMA). Checker-first + prove-one + breadth is the correct order.
KEY CONCERN: Re-extract `pa-lecture-01.md` WITH form-feeds from `Curs 1 PA.pdf` as Phase-2's literal first task (kills the self-imposed DEGRADED floor). And the 828 unverified-but-studyable cards need a louder honest "unverified" badge NOW — a learner drilling uncited cards under a "faithful to your source" brand is the live integrity gap, not the empty KC corpus.

## ⚠️ Risk Analyst — CONDITIONAL (corrected the framing)
**CI-red is a non-event:** `validateContent` reads the committed `_sources/*.md`, not the live PDF — stays green until you ATOMICALLY swap source+KCs in one commit (controllable, not ambient). **Chicken-and-egg dissolves:** `Curs 1 PA.pdf` is physically present; the verifier's first subject bootstraps via curate-tutor, no external unblock. **Live-DB blast radius ~nil:** PDFs untracked, 828 cards have no kc_id, kc_verification_status empty, Phase 2 is an offline batch off the request-path — retiring stale KCs cascades nowhere. **Real CRITICAL is epistemic:** "≥1 KC FAITHFUL" on lecture-01 (clean English prose, quotes that already locate) is the easy case and over-claims pipeline-scale confidence for multi-page diagram-heavy RO PDFs.
KEY CONCERN: One clean-prose lecture as sole proof gives false scale-confidence. Redefine acceptance: the net must PASS one faithful KC AND correctly REJECT a deliberately-mistranslated/relocated KC (prove it can say "not faithful"); make the proof lecture a RO, multi-page, figure-bearing source — not the easy one.

---

## Sanity Check
SANITY [Devil's Advocate]: FLAG — reasoned from the "paraphrase is a self-referential hand-typed artifact" premise, which FP+RA empirically refuted (the .md is a faithful extraction of a real on-disk PDF). Its acceptance-hardening recommendation still aligns with clean agents, so it is kept as a weighted point, not a verdict driver.
SANITY [Domain Expert]: PASS — shared the "paraphrase" wording but its recommendation (independent fixture-freeze / anti-contamination) is sound regardless of the source dispute.
SANITY [Pragmatist]: PASS — grounded in plan §6.1/D2 + the live FSRS queue; the 828-reachability point is independent of the source dispute.
SANITY [First Principles]: PASS — independently verified ground truth and corrected the convene-time framing.
SANITY [Risk Analyst]: PASS — independently verified CI mechanics, file presence, and DB blast radius.

---

## Judge

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
COUNCIL VERDICT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

VERDICT: APPROVED (proceed checker-first) — with three mandatory refinements and one corrected premise

CORE FINDING:
The "checker-first" direction is right and unanimously endorsed (5/5 CONDITIONAL, none reject the direction) — it's the textbook walking-skeleton / verify-before-scale order, and it's the correct response to a learner who can't self-verify. The convene-time alarm that the corpus is "broken" is FALSE: two agents independently verified that `Curs 1 PA.pdf` (the cards' real cited source) exists and 14/15 KC quotes locate in it verbatim (re-confirmed by Claude). The earlier "0/15" diffed the wrong file (`lecture01_comppb.pdf`, a different lecture the PLAN misnamed). The corpus is sound; the only real defect is a missing-form-feed extraction (page-anchor DEGRADED) — already known and cheaply fixed.

AGENT CONSENSUS: 5 CONDITIONAL, 1 flagged (Devil's Advocate — wrong premise, valid sub-point). No agent rejects the direction.

KEY ISSUES (refinements, in priority order):
1. Corpus is NOT broken. Re-extract `pa-lecture-01.md` WITH form-feeds from the real `Curs 1 PA.pdf` as Phase-2 task 0 → page-anchor becomes LIVE. Fix the plan's misnamed source file (`lecture01_comppb.pdf` → `Curs 1 PA.pdf`).
2. Harden Phase-2 acceptance — "≥1 KC FAITHFUL" alone is gameable/weak. Require: (a) page_anchor_status=LIVE against a real extraction; (b) the net also correctly REJECTS a deliberately-corrupted KC ("prove it can say NOT faithful"); (c) prove on a real multi-page (ideally RO, figure-bearing) lecture, not only the easy English one. Gate the proof lecture's groundedness independently (anti-contamination).
3. Front-load study value NOW — surface the 828 already-studyable, FsrsDueQueue-served cards on the live tutor with an honest "unverified" badge, so exams aren't blocked behind the multi-phase keystone.

RECOMMENDED PATH:
Proceed checker-first. Phase-2 task 0 = re-extract the real lecture with form-feeds + fix the plan's filename. Build the trust-net; redefine acceptance to (page_anchor LIVE) + (pass-one AND reject-one) + (a real multi-page proof lecture, groundedness gated independently). In parallel / first, ship a thin "828 cards reachable + honestly badged unverified" slice for immediate exam value.

CONFIDENCE: 9
Empirically grounded; the one factual dispute was resolved by direct re-verification (14/15 quotes match Curs 1 PA.pdf). Residual: 1 KC quote (pa-kc-006 #3) needs a trivial wording reconcile; the cross-subject scale risk remains until proven on a harder lecture.
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Output saved to: .claude/council-cache/council-1780568136-checker-first-readiness.md
