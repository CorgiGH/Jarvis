# Council review — round 2 (re-briefed with Alex's feedback + the updated plan)

**Base:** council-1780407101-teaching-design.md (round 1 = FLAWED).
**Ground rules given (OUT OF BOUNDS this round):** UI surfaces are the necessary engine↔student link (not displacement); the 4 empty subjects WILL be authored via curate-tutor (not a flaw); NO timing/deadline/sequencing/scope/build-cost commentary. Judge teaching-design QUALITY only.
**Plan updates reviewed:** §2.5 correctness & trust system; §4.1 worked-example-first faded by PFA mastery + phase-count gated by entry mastery + interleaving + transfer probe.
**Timestamp:** 2026-06-02 (round 2).

All 5 agents stayed in-bounds (no banned issues raised) and moved to CONDITIONAL (from 2 REJECT / 3 CONDITIONAL).

## 🔴 Devil's Advocate — CONDITIONAL
The §2.5 gate verifies the claim against `source.quote` — but that quote is itself an extraction artifact the curate pipeline already chose (e.g. `content/PA/kcs/pa-kc-002.yaml` carries hard-coded quote strings, one a mid-sentence fragment). Both verifier legs read that same chosen span, so a mis-selected / truncated / mis-attributed span passes with two confident agreeing families — proves "claim faithful to the stored span," never "the stored span is the right span." Free-LLM "different families" share upstream training (OpenRouter `:free` + one Claude-Max relay) → correlated blind spot the design names but can't escape on this infra. The "or N correct attempts" escape hatch routes around the gate via an oracle-blind student.
KEY CONCERN: trust anchor is the stored quote, not the source document — re-locate & re-read the live span (page+offset locator, fuzzy-match guard); delete the "or N correct attempts" auto-clear.

## 📚 Domain Expert — CONDITIONAL
R1 fixes correctly specified: study→completion→full-problem fade = Renkl-Atkinson backward fading over Sweller's worked-example effect; fade keyed to PFA = faithful Kalyuga expertise-reversal; interleaving placed at the scheduler = Rohrer-Taylor method-discrimination. Two real gaps remain: (1) no **self-explanation prompts (Chi/Renkl)** — faded examples without a "why does this step hold" prompt under-deliver for novices, degrade to passive reading; (2) the transfer probe as "novel phrasing" = near transfer only; far transfer (Barnett-Ceci / Gick-Holyoak) needs surface varied, deep structure held.
KEY CONCERN: add self-explanation prompting (cheapest high-value lever); name dual-coding (Mayer) + generation effect already latent.

## ⚙️ Pragmatist — CONDITIONAL
The four mechanisms are buildable but specced as BEHAVIORS without the STATE MODEL, and the current schema can't carry them: `FsrsCardsTable` has no `kc_id` and no quarantined/paused flag (keyed on free-text front/back + sourceRef=task id) → no join from a KC verdict to the cards it must pause. `KnowledgeConcept` has `grounding_tier` but no verification-status enum and lacks `invariant`/`grader_rules`/`stem_template` (the fields re-derivation compares); `ContentValidator` self-disclaims "not groundedness-verified." `KcMastery` is flat EWMA, no `phase` column. The mastery-keyed fade is the clean part (reads one float).
KEY CONCERN: define persisted state (kc_id FK + quarantine flag on fsrs_cards; verification_status enum + compared fields on KC; phase column) BEFORE wiring, else the gate degrades to a confidence flag — the exact prior "verification net" already rejected. Resolve seams: what "agree" means, what resurfaces a quarantined KC, what authority clears the audit.

## 🧱 First Principles — CONDITIONAL
With no trusted human oracle ever in the loop, a closed stack of LLMs checking each other can only measure *consensus + source-faithfulness*, never *truth*; correlated errors and a source that is itself wrong/loose pass every gate. The one genuine anchor is the lecture source span as ground truth, so the only honest claim is "faithful to your material," NOT "correct" — these diverge exactly when the source is wrong, and an oracle-blind learner can't tell. The gate sits at the right fulcrum (faithfulness IS the maximal honest guarantee; quarantine + fail-safe correctly bias toward withholding) — but the "verified" badge over-claims correctness.
KEY CONCERN: badge must read "faithful to your source / matches your lecture," never "verified/correct"; surface source-internal error as the explicit boundary of the guarantee.

## ⚠️ Risk Analyst — CONDITIONAL
The gate genuinely shrinks the R1 CRITICAL risk (round-trip catches dropped-not/≤-vs-<; FSRS-quarantine breaks the "highest-confidence error → most durable" amplifier). Worst residual = correlated-family agreement (HIGH, not closed): free LLMs share pretraining, so on a commonly-mistaught fact two "different families" re-derive the same wrong claim and the round-trip checker (itself a foolable LLM) confirms entailment because the span supports the popular-wrong reading → fails SILENT into a "verified" badge. "N correct attempts by an oracle-blind cohort" = consistency not truth → launders a wrong KC into the cohort corpus.
KEY CONCERN: add ≥1 non-LLM verification leg (symbolic / unit-test execution / small human-cleared gold span) so legs can't share bias; downgrade "N attempts" to a confidence signal that never promotes to the cohort corpus; badge defaults to UNCERTAIN when 429-fallback collapses two families to one.

---

## Synthesis verdict (round 2)
VERDICT: APPROVED with conditions (was FLAWED). All 5 in-bounds, all CONDITIONAL, converged. The teaching design is sound; fold in 5 named upgrades:
1. Verify against the LIVE source span (locator + fuzzy guard), not the stored quote. [Devil + Risk]
2. ≥1 NON-LLM verification leg + UNCERTAIN-on-429-collapse (decorrelate the oracle). [Risk]
3. Drop "N correct attempts" auto-clear; consistency ≠ truth, never promotes to cohort. [Devil + Risk]
4. Honest badge: "matches your lecture / faithful to your source," never "verified/correct"; surface source-internal error as the boundary. [First Principles]
5. Self-explanation prompts (Chi/Renkl) + far-transfer (vary surface, hold deep structure). [Domain]
Plus build-prereq: define the persisted state model (kc_id FK + quarantine flag; verification_status enum + invariant/grader_rules/stem_template fields; phase column) before wiring. [Pragmatist]

All 5 folded into the design spine §2.5 / §4.1 / §7 this session.

Output saved to: .claude/council-cache/council-1780407101-teaching-design-r2.md
