# Council review — 1780230347 (external oracle when no textbook PDFs)

**Problem:** Rule #2 (cross-check taught claims against an external truth oracle, because grounding proves a claim came from the slide, not that the slide is true) intended to use the subject's canonical textbooks (named in the fișă disciplinei, with per-topic mapping). But Alex has NO PDFs of those books — only slides + the fișă (names + mapping). Is "cross-check against the capable model's (Claude's) parametric recollection of the named books" a valid oracle, or does it collapse into "trust the model"?
**Verdict:** Model-memory alone is NOT a valid oracle. Use a tiered oracle; most CS content needs no book.
**Timestamp:** 2026-05-31 (epoch 1780230347)

---

## 🔴 Devil's Advocate — REJECT
Violates the one property rule #2 exists to provide: independence. The "oracle" is the same model family that produced the lesson, so when it misreads a slide it misreads "what Bertsekas says" the same way and confidently self-confirms — correlated error, not a cross-check. The model's recollection of a named text is reconstructive and unfalsifiable here (no PDF to diff), so it can fabricate "yes, Bertsekas agrees, p.213" with full fluency. For a learner who cannot vet, a hallucinated confirmation is strictly worse than silence: it upgrades an honest "single-source, unverified" into a manufactured stamp of external authority — the exact false-assurance failure rule #2 was written to prevent.
KEY CONCERN: No independence + unfalsifiable recall → the fallback manufactures false authority rather than verification. Negative-value.

## 📚 Domain Expert — CONDITIONAL
RAG exists precisely to AVOID parametric reliance (parametric memory confabulates specifics + citations — the hallucinated-reference failure). Using the model's recall of Bertsekas/Ross as the oracle re-introduces the exact failure the oracle exists to catch — self-confirmation, violating source-independence. Field-standard when the corpus isn't in hand, priority order: (a) actually RETRIEVE it — these ~10 books are ubiquitous CS texts, and the per-topic fișă mapping makes targeted chunk retrieval cheap; (b) authoritative WEB retrieval of the specific fact vs a curated closed corpus (Wikipedia/nLab/canonical lecture notes), as production grounded-answering / RARR / Chain-of-Verification do; (c) MULTI-MODEL adjudication across independent families (diversity, not self-confirmation); (d) ABSTAIN + flag "single-source, unverified" when no independent source. Parametric recall is acceptable only as triage / for well-known fundamentals (Bayes' rule, linearity of expectation), unacceptable for theorem hypotheses, constants, bound tightness, edge conditions, notation — exactly the high-value cases where a can't-self-vet learner is most exposed.
KEY CONCERN: Demote parametric recall to a triage signal (fundamentals-only); real retrieval (a/b) primary, multi-model (c) backup, mandatory abstain-with-flag (d) on theorem-specific details when no independent source is in hand.

## 🧱 First Principles — CONDITIONAL
"Is model-memory a good-enough oracle for an absent book?" is the wrong question. Partition the taught claims: (i) FORMALLY CHECKABLE — recurrence solutions (2^(h+1)-1), complexity bounds, algorithm traces, code output, worked numeric examples — the oracle is COMPUTATION/PROOF, no book needed, so "no PDFs" is irrelevant; rule #2 should RE-DERIVE/RUN. (ii) DEFINITIONAL/CONVENTIONAL — notation, exact theorem statements, course conventions — where a canonical source matters, and where a model's memory of an absent book is precisely the failure rule #2 prevents (launders a hallucinated "canonical" form). (iii) PEDAGOGICAL FRAMING — quality, not correctness. Most exam-critical CS content is partition (i). Honest design: VERIFY the checkable by independent re-derivation (no book); CROSS-CHECK the definitional against any independent source and explicitly FLAG single-source when none exists; treat framing as quality.
KEY CONCERN: The real risk isn't "no PDFs" — most exam-critical content is (i) and needs no book — it's conflating "model remembers the book" with "the book verified it." Fix = explicit single-source FLAG on definitional claims, not a fabricated oracle.

## ⚠️ Risk Analyst — CONDITIONAL (CRITICAL)
Parametric recall is safe for broad fundamentals but fails dangerously on the details an oracle exists to police: dropped theorem preconditions (i.i.d. vs merely independent, finite-variance for CLT, aperiodic+irreducible for stationary convergence), exact constants/bounds (Chernoff/Chebyshev tail forms, Master-Theorem constants), edition/notation drift (definitions of "with high probability," index bases). Lethal mode: the model confidently CONFIRMS a wrong slide because its recollection smoothed over a missing precondition; that PASS is then FSRS-burned into a learner who can't catch it — the oracle manufactures false confidence, strictly worse than no oracle. "Claude confirms Claude" is correlated with the generation it audits — not independent.
KEY CONCERN: CRITICAL — single-model parametric recall used as a silent PASS. Mitigation: a cross-check returns PASS only when an independent retrieval source OR a different model family corroborates the specific detail (named book + page/edition cited); any solo-recall result is forced to FLAG, never PASS — taught with a visible "unverified — textbook unconfirmed" caveat and BARRED from entering the FSRS mastery store until corroborated (presentable, not durably reinforced). Composes with rule #1 (audit + retraction).

## ⚙️ Pragmatist — CONDITIONAL
Cheapest ROBUST path = one-time acquisition of the named textbooks — among the most circulated CS texts alive (several have legitimate free author/course PDFs; the rest trivially findable) — converting rule #2 from fragile model-memory into real retrieval. Don't gate on all ten: use the fișă per-topic mapping to acquire only the 2-3 a subject actually cites; fall back to free canonical lecture notes + Wikipedia for standard results at audit time; reserve model-memory as a last-resort flag-don't-assert tier. Wire a tiered oracle (local PDF → free web → model-memory) where the grader cites WHICH tier it used.
KEY CONCERN: Model-memory must never be the silent default — if no PDF/web source backs a claim, downgrade to "unverified, flag for review" rather than assert correctness, else rule #2 becomes theater that launders hallucination as oracle truth.

---

## Sanity Check
SANITY [Devil's Advocate]: PASS — independence + unfalsifiable-recall argument is airtight; "negative value / manufactures false authority" is the correct severity. In lane.
SANITY [Domain Expert]: PASS — named real techniques (RARR, Chain-of-Verification, multi-model adjudication, closed-corpus retrieval); priority ladder is field-standard. In lane.
SANITY [First Principles]: PASS — the checkable-vs-definitional partition is the key reframe; "no PDFs is mostly irrelevant" is correct + load-bearing. In lane.
SANITY [Risk Analyst]: PASS — concrete failure points (CLT finite-variance, aperiodic+irreducible, Chernoff constants); the PASS-only-on-corroboration + bar-from-FSRS mitigation is precise. In lane.
SANITY [Pragmatist]: PASS — one-time acquisition + per-topic-mapping-to-minimize is the proportionate move; tiered oracle with tier-citation is concrete. In lane.

No flags. 1 REJECT + 4 CONDITIONAL — unanimous that model-memory alone is not an oracle; convergent tiered fix.

---

## Judge
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
COUNCIL VERDICT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

VERDICT: FLAWED (model-memory-as-oracle) — but the real fix is small

CORE FINDING:
Claude's recollection of a book Alex doesn't have is NOT an independent oracle — it's the same machinery that teaches, so it self-confirms its own misreadings and can fabricate "the book agrees" with full fluency, which for a learner who can't vet is worse than no check (it manufactures false authority). BUT "no PDFs" is a smaller problem than it looks: most exam-critical CS content is FORMALLY CHECKABLE, where the oracle is computation/proof and no book is needed at all. The fix is a tiered oracle plus an honest flag — never a fabricated confirmation.

AGENT CONSENSUS: 1 REJECT, 4 CONDITIONAL, 0 APPROVE — 0 flagged. Unanimous: model-memory alone fails; tiered oracle + flag.

THE RULE (#2, corrected):
1. **Verify the checkable (no book).** Math derivations, algorithm traces, code, numeric examples → re-derive / run. The oracle is computation/proof. This covers the bulk of exam-critical CS, so "no PDFs" doesn't touch it.
2. **For definitional/conventional claims, use a tiered oracle:** (a) actually obtain the 2-3 books a subject's fișă cites (per-topic mapping keeps it minimal; several have free legit PDFs); (b) free authoritative web (canonical lecture notes / Wikipedia for standard results) at audit time; (c) multi-model adjudication across DIFFERENT model families (independence). The grader records WHICH tier corroborated.
3. **Hard rule — flag, never fake:** solo single-model recall = FLAG, never PASS. A flagged claim may be SHOWN with a visible "unverified — textbook unconfirmed" caveat but is BARRED from the FSRS mastery store until an independent tier corroborates it. So a possibly-wrong definitional claim is never durably burned in. Composes with rule #1 (audit + retraction).

RECOMMENDED PATH:
Rewrite rule #2 in the roadmap as the tiered verify→retrieve→web→multi-model→flag ladder above, with the hard "solo-recall never silently PASSes + barred from FSRS until corroborated" invariant. Add a cheap one-time task: per subject, pull the 2-3 fișă-cited books that are freely available. Most concepts route to tier-1 (verify) anyway.

CONFIDENCE: 9
Convergent, field-grounded; the checkable/definitional partition makes the "no PDFs" worry mostly moot, and the flag-don't-fake invariant is unambiguous. What would change it: confirming how many fișă-cited books per subject are actually freely obtainable (affects how often tier-2a vs tier-2b/c fires).
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
