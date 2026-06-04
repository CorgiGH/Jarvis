# Council review — 1780533251 (phase2-readiness)

**Problem:** Readiness gate — are we ready to START Phase 2 (Correctness & Trust Engine / trust-net)? Confirm GO or surface blockers.
**Proposed approach:** Offline per-KC audit — re-locate claim against LIVE PDF → two LLM families agree → ≥1 non-LLM leg → span↔claim round-trip — before SR entry; `CitationGuard` per-emit chokepoint; `ExtractionConfidence` ingest gate; FAIL-LOUD; badge "faithful to your source" never "verified correct". Acceptance: ≥1 KC FAITHFUL end-to-end.
**Project context:** Single-user personal AI tutor (Alex, can't vet content). Kotlin/Ktor + SQLite (FSRS) + React/Vite. No-paid-APIs (OpenRouter :free + home-PC claude-max relay). Plan FROZEN (master-impl-plan-v2 + interface-signatures-lock). Phase 0+1 DONE, pushed `main @ 82b5809`, CI green (958 tests), live DB migrated 871→828, relay healthy.
**Timestamp:** 2026-06-04T00:34:11Z

---

## 🔴 Devil's Advocate
STANCE: REJECT
REASONING: Phase 2's sole acceptance bar ("≥1 KC FAITHFUL end-to-end") is unreachable with the current corpus, and pre-flight (a) understates the blocker. Extracted the real PDF (`lecture01_comppb.pdf`, 15 form-feeds, clean) and tested `pa-kc-001`: all three source quotes are absent from the real source — "well-ordered" never occurs; the real PDF says "a well-defined, step-by-step sequence of instructions," which the KC author rewrote into "a well-ordered collection of unambiguous and effectively computable operations." Every KC was authored against a 0-form-feed hand-typed paraphrase; the instant pre-flight (a) swaps it for the real PDF, the trust-net correctly DENY/UNCERTAINs the first KC.
KEY CONCERN: Pre-flight (a) is mislabeled as a ~5-min pdftotext run; the real unscheduled blocker is a re-authoring of the PA KC corpus against the extracted real PDF (quotes → verbatim locatable spans). Starting Phase 2 first means building the whole trust-net with zero KCs it can pass — can't tell "engine bug" from "content genuinely unfaithful."

## 📚 Domain Expert
STANCE: CONDITIONAL
REASONING: Architecture is textbook-correct closed-stack faithfulness verification — composes RAG groundedness, ensemble-of-judges self-consistency, NLI-style attribution (AIS / attributable-to-identified-sources), citation-required generation (the no-SourceRef-THROWS chokepoint), span-grounded verification — and correctly certifies faithfulness-not-truth (the only honest claim a retrieval-grounded system can make). Family-collapse is only partially mitigated: family A (claude-max) and family B (:free, mostly Llama/Qwen/Mistral derivatives) are deployment-diverse but training-correlated, so two-family agreement is weaker than the binary ALLOW implies — acceptable here only because the badge never claims truth and definitional claims pin UNCERTAIN. Verified `_sources/` holds only a paraphrase (self-labeled "Extracted verbatim", no PDF / no pdftotext output present) → the locator would round-trip a claim against the paraphrase that produced it (circular ground truth).
KEY CONCERN: Pre-flight (a) is a hard gate, not a nicety: until the locator maps against the genuine raw PDF, the span-grounding leg is self-referential and `SpanClaimRoundTrip` can report FAITHFUL while grounding on the artifact that generated the claim.

## ⚙️ Pragmatist
STANCE: CONDITIONAL
REASONING: Build cost is proportionate and the plan is honest about its seams: the page-anchor gap is absorbed architecturally (DEGRADED is FAITHFUL-eligible, pdftotext on PATH), components map 1:1 to a frozen twice-hardened plan — execution, not redesign. One genuine over-build for a single-user skeleton is `SpanClaimRoundTrip` + family-collapse detection: with family A asleep half the time the audit UNCERTAIN-floors anyway, so build locator + gate + two-family deriver first and prove ≥1 KC FAITHFUL before wiring round-trip. The maintenance risk isn't code, it's the silent-DEGRADED handoff: nothing forces a human to notice the slice passed on a paraphrase.
KEY CONCERN: Acceptance "≥1 KC FAITHFUL" can pass on a paraphrase with `page_anchor_status=DEGRADED` — make slice-1 acceptance assert ≥1 KC FAITHFUL with `page_anchor_status=LIVE` against a real pdftotext extraction (the binary is installed) before declaring the keystone proven.

## 🧱 First Principles
STANCE: CONDITIONAL
REASONING: From the raw goal — a learner structurally incapable of verification must never be handed an unfalsifiable claim — the only honest primitive a closed free-model system can deliver is "traceable to a source you trust," NOT "correct." The team correctly refused the unreachable target and refused to launder it through the learner; badge wording, mandatory-citation-or-throw, and the UNCERTAIN floor are exactly what a from-scratch design lands on. Keystone-first ordering is right: gating SR-entry before the teaching loop exists is cheaper than retrofitting trust. One structural divergence: "faithful to a possibly-wrong lecture still misleads" is a gap the primitive cannot close by construction — must be explicitly owned (acceptable only because Alex-as-oracle is forbidden).
KEY CONCERN: The acceptance bar is too weak and will be reached on a degraded substrate — `_sources/pa-lecture-01.md` has 0 form-feeds and is a paraphrase, so slice-1 certifies FAITHFUL via offset+fuzzy only. Don't let Phase 2 EXIT on the paraphrase; the real `Curs 1 PA.pdf` extraction must land and ≥1 KC reach FAITHFUL with `page_anchor_status=LIVE` before Phase 2 is done.

## ⚠️ Risk Analyst
STANCE: CONDITIONAL
REASONING: CRITICAL — the page-anchor foundation is hollow: `_sources/` holds only the paraphrase (0 form-feeds, confirmed), while the genuine `lecture01_comppb.pdf` exists, pdftotext is installed, and it extracts cleanly with 15 form-feed page breaks; the first KC would fail location or page-anchor to garbage, and the whole ACCEPTANCE bar rests on the one degraded file. HIGH — FAIL-LOUD's own state tables (`kc_verification_status`, `verification_audit`) are missing FKs (the carried "missing FKs on 3 Gate-2 tables") — the tables the gate reads and the audit writes; a dangling kc_id is a silent path around FAIL-LOUD at the storage layer. MEDIUM — relay/free-tier flap and family-collapse are real but correctly mitigated by design (offline batch + thrown→UNCERTAIN + resolve-before-write + collapse→UNCERTAIN); they degrade throughput, not correctness.
KEY CONCERN: Extract the real PDF (15 form-feeds) into `_sources/` and add FKs to the two trust tables BEFORE the first audit run — otherwise the page-anchor + round-trip legs validate against a 0-page paraphrase and FAIL-LOUD has an un-keyed back door at the table layer. Pre-flight (b) fresh LOCAL off-box dump also not yet captured (local newest 20260603-003631; the 063621 dump is VPS-only).

---

## Sanity Check
SANITY [Devil's Advocate]: PASS — empirically verified (ran pdftotext + grep); found a real, decisive defect. Confirmed independently by the judge.
SANITY [Domain Expert]: PASS — named real patterns (RAG groundedness, AIS attribution, ensemble-of-judges, self-consistency, span-grounding); concrete substrate check.
SANITY [Pragmatist]: PASS — human/maintenance-cost focus; specific (DEGRADED escape, round-trip over-build); the "FAITHFUL-on-paraphrase" gap is the strongest actionable.
SANITY [First Principles]: PASS — stripped the framing, reasoned from the raw goal, explicitly owned the irreducible faithful-to-wrong-lecture gap.
SANITY [Risk Analyst]: PASS — ranked, concrete, verified form-feed counts + table FK absence; the kc_id "FK" framing is loose (KCs are YAML-backed, no kc table to reference) but the trust-table hardening point stands.

---

## Judge

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
COUNCIL VERDICT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

VERDICT: FLAWED (readiness — fixable; the approach itself is sound)

CORE FINDING:
The trust-net architecture is right and praised by 4 of 5 agents (faithfulness-not-truth, keystone-first, citation chokepoint, FAIL-LOUD). But you are NOT ready to GO: the corpus the engine would run against is not faithful to the real lecture. Verified directly — the real PDF (15 pages) does NOT contain `pa-kc-001`'s authored quote ("well-ordered… effectively computable"); it says "a well-defined, step-by-step sequence of instructions." The quotes live only in a 0-form-feed hand-typed paraphrase that isn't even the lecture. Start now and the engine either (correctly) certifies nothing, or rides the plan's "DEGRADED is FAITHFUL-eligible" escape to bless a paraphrase — the exact un-self-verifiable channel the project exists to kill.

AGENT CONSENSUS: 1 REJECT, 4 CONDITIONAL — 0 flagged. Unanimous on the load-bearing blocker (real PDF must land + acceptance must require page_anchor=LIVE).

KEY ISSUES:
1. (CRITICAL) `content/PA/_sources/pa-lecture-01.md` is a non-faithful paraphrase; the real `lecture01_comppb.pdf` is unextracted. Pre-flight (a) is load-bearing, not a nicety, and is larger than "pdftotext" — the PA KC quotes must be re-grounded as verbatim locatable spans from the real extraction (system-derived; NEVER routed to Alex).
2. (HIGH) Acceptance "≥1 KC FAITHFUL" is too weak — it passes on a DEGRADED paraphrase. Tighten to: ≥1 KC FAITHFUL with `page_anchor_status=LIVE` against the real extraction.
3. (HIGH) `kc_verification_status` + `verification_audit` need their FK/integrity hardening before the gate reads / audit writes them (maps to the carried Gate-2 FK item).

RECOMMENDED PATH:
Do NOT start the build cold. Run a short pre-Phase-2 punch list FIRST: (1) extract the real PA PDF → `_sources/`; (2) author the first FAITHFUL KC against the REAL extraction and require page_anchor=LIVE; (3) audit existing PA KC quotes — legacy non-locating KCs stay `unverified` (locked decision), must not ride DEGRADED→FAITHFUL; (4) harden the two trust tables' FK/integrity; (5) capture a fresh LOCAL off-box DB dump. Then GO — the engine itself is approved.

CONFIDENCE: 8
Empirically grounded (council + judge both ran pdftotext/grep/schema checks). What would raise it: confirming the exact FK topology on the two trust tables, and a decision on whether the first FAITHFUL KC is a re-authored pa-kc-001 or a fresh pa-kc-005/006.
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Output saved to: .claude/council-cache/council-1780533251-phase2-readiness.md
