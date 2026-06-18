# Question well-posedness gate — approach brief for council review (2026-06-18, SESSION-82)

**Reviewers: OPEN the cited artifacts. Do not review from this prose alone.** Verifier-refute-bias warning: last session 3/5 council agents charitably dismissed Alex's REAL cuts-vs-depth bug. A correctness finding needs a computational test, not a charitable reading. Do not default-to-refute.

## The problem

The tutor generates questions (lesson beats + drills + mock-exam items). NO machine gate checks a *generated question* is **well-posed**: that its keyed answer is the unique-or-bounded correct one, is present among the options, and that no second defensible reading of the stem yields a different answer. The "cuts-vs-depth" bug fell straight through this hole.

SESSION-81 verification law (binding): **LLM-as-judge cannot be trusted for correctness** — it rationalizes. Correctness must anchor to a NON-LLM oracle + correct-by-construction + best-of-N; LLM only for non-decidable taste + a thin human spot-check. BUT: the user (Alex) **cannot be that human spot-check** — he cannot vet subject content (no-oracle-inversion). So the linguistic residue must be *withheld/labelled*, never routed to him.

Artifacts: research `build-review/2026-06-18-trustworthy-ai-verification-research.md`; prior councils `.claude/council-cache/council-1781781668.md` + `council-1781789477.md` (both rated the earlier fixes FLAWED); spec `docs/superpowers/specs/2026-06-11-one-pass-digestion-teaching-engine-design.md` §9 (no well-posedness gate exists); code `src/main/kotlin/jarvis/tutor/DrillGenerator.kt:133-144` (self-solve + critic are BOTH LLM-only), `ContentValidator.kt` (zero question-answer checks), grader chain `src/main/kotlin/jarvis/tutor/grader/` (NumericOracleGrader, ExecutionGrader, RubricGrader; machine-leg-first), `verify/NonLlmLegs.kt:204` (SymPy, PA-only).

## Grounded findings — REAL past-exam material (this session, read verbatim)

Real exam corpus surveyed: PA (Proiectarea Algoritmilor) solved exams + baremes; ALO consultatii 2026; "Seria I/II" (= PA written test 10 Jun 2026, RO+EN); POO grilă + practicals. Extracted to `C:\Users\User\Downloads\_material_survey\` (PA, POO) + `Downloads\ALO_consultatii_2026.pdf`, `Downloads\Seria_*.pdf`.

1. **Real exams are almost all FREE-FORM**, not MCQ:
   - PA / Seria: 100% free-form — algorithm trace, design+implement (ALK), formal spec, correctness/optimality proof, complexity, NP design. ZERO MCQ.
   - ALO: 100% free-form numeric traces (iterative methods, gradient/Newton, spectral radius). Unique intermediate values; mild tolerance only on a spectral-radius approximation. ZERO MCQ.
   - POO: the ONLY MCQ subject — "grilă" single-correct radio (4–14 options), ~50% code-output ("ce se întâmplă la execuție"), ~20% UML-to-code, ~15% keyword; PLUS open-design C++ practical (T2). NO multi-select, NO "toate/niciuna" seen.
2. **POO lab exam 2 (photo, 2026)**: "read N, then N ints (repeats); using a freq map: (1) read+count, (2) print each distinct number with its frequency, most-frequent first." → **tie order UNSPECIFIED**: equal-frequency numbers have undefined output order ⇒ output is NOT unique ⇒ an execution oracle comparing stdout is UNSOUND unless output is canonicalized or the tie-break is pinned in the stem. Well-posedness bites free-form code, not just MCQ.
3. **Real baremes use additive partial credit and explicitly accept multiple valid answers** ("se punctează și alte demonstrații corecte, similar"; example-construction "există multe alte soluții"). Open answer classes are normal and expected.
4. **The ambiguous conceptual-MCQ class (cuts-vs-depth) is largely TUTOR-INVENTED.** Real exams don't use conceptual MCQ; the tutor manufactures MC options from free-form content for its gated 5-beat lessons. That manufacturing step is where the bug is born.
5. **Ambiguity can be deliberate**: PA reexam Q1 (find_smallest_double) embeds a two-reading ambiguity AS the learning objective (detect the flaw). A gate must distinguish "ambiguous by design" from "ambiguous by drafting error."

## Proposed approach (the thing to review)

**Spine: route each generated question to a check by ANSWER TYPE, mirroring the grader chain (machine-first).**

**Tier 1 — Computable answer → NON-LLM oracle, HARD gate.**
Covers: POO code-output MCQ, ALO/PS numeric+code, PA algorithm-trace + reduction-computation. Mechanism: run the real oracle (C++ compiler/exec, SymPy, algorithm simulator) to derive the answer correct-by-construction; assert (a) keyed answer matches the oracle, (b) answer present in options, (c) uniqueness — no distractor also satisfies the constraint. **Output non-uniqueness rule** (the tie-order case): when the spec underspecifies ordering/ties, either canonicalize the output before comparing, or require the stem to pin the tie-break; never compare raw stdout. Best-of-N: sample K candidates, keep those that pass.

**Tier 2 — Open-class answer → structural-property check or decompositional partial-credit; NO single key.**
Covers: proofs, algorithm design, example-construction, prose, POO open-design practical. Mechanism: verify a computable structural property where one exists (a proposed counterexample actually breaks the algorithm; an example satisfies the stated constraint; an output spec captures all named constraints; a class diagram is isomorphism-checkable), else accept additive partial-credit per named component. Honest rubric; matches real baremes.

**Tier 3 — Tutor-invented conceptual MCQ → MINIMIZE, then guard.**
First lever: prefer exam-faithful forms (which are more verifiable) over manufacturing conceptual MCQ; keep MCQ only as low-stakes intuition scaffolding. Where kept: correct-by-construction (derive options + key from ONE verified source span) + CoVe blind re-answer flag (independent LLM answers the stem with NO key/context; divergence ⇒ flag) + honest `neverificat`/withhold for residue. NEVER faked-green; NEVER routed to Alex.

**Division (law):** machines verify TRUTH; the user approves EXPERIENCE; the user is never asked to judge correctness. Residue → withhold/label.

## Questions for the council

1. Is type-routed (3-tier) the right spine, or is there a simpler/sounder structure? Is it correct-by-construction enough, or still too "post-hoc validator" (the failure mode both prior councils flagged)?
2. Is "minimize tutor-invented conceptual MCQ, mirror real exam forms" right — or does it throw away genuine pedagogical value (cheap intuition checks) the exams don't need but learning does?
3. Output non-uniqueness (tie-order, unordered containers, float tolerance): canonicalize-and-compare vs require-the-stem-to-pin-it vs property-check — which, and is canonicalization always decidable?
4. "Ambiguous by design vs by drafting error" — can a machine tell them apart, or must the author mark intent? Where does that live?
5. The residue boundary: exactly what gets WITHHELD vs SERVED-WITH-LABEL? Is a labelled-but-served ambiguous item ever acceptable under P1 (no-spec-cut)?
6. Blind spots — what class of well-posedness failure does this approach NOT catch?
