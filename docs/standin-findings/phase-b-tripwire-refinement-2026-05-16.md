# Phase B acceptance — Y tripwire refinement (council 1778881175)

**Date:** 2026-05-16
**Commits:**
- `4f33cda` — feat(surface-y): tripwire AND-gates suspect on findings_count==0
- `490218b` — feat(surface-y): pass findings_count to tripwire + emit confidence-band frontmatter
- `82c7717` — feat(surface-y-persona): append 2 hand-authored authentic-naive exemplars
- This doc (HEAD pending)

**Phase A precursor:** `8f7f34c` (Phase A shipped + deployed to VPS 2026-05-16T13:28Z).

## Council 1778881175 verdict compliance

| Item | Status | Evidence |
|---|---|---|
| B.1 — `flagSuspectRun` AND-gates suspect on `findings_count == 0` (First Principles 2x2 refinement) | ✓ shipped | `4f33cda` — tools/surface-y-tripwire.mjs:32-69; +3 unit tests covering competent-with-findings clean, competent-zero-findings suspect, naive-irrelevant-of-findings clean |
| B.2 — DRAFT-Y frontmatter includes `tripwire_confidence_band: thin_corpus_n2` (Devil's Advocate: "name the guarantee") | ✓ shipped | `490218b` — tools/surface-y.mjs:325-340 lifts findings; emits `tripwire_findings_count` + `tripwire_confidence_band` in frontmatter |
| B.3 — Persona prompt appended with 2 hand-authored exemplars (NOT from n=2 calibration corpus — avoids Risk Analyst's circular-calibration risk) | ✓ shipped | `82c7717` — tools/surface-y-persona.mjs:65-79; +1 test asserting exemplar markers + "SHAPE references only" warning string |
| (b) LLM-judge tripwire | DEFERRED indefinitely | Council verdict: silent-skip regression risk (`:free` quota exhaustion → no tripwire signal at all, strictly worse than always-on heuristic) + self-judge weakness (judges drift competence-favoring per MT-Bench literature). |
| (c) Schema-constrained generation | DEFERRED indefinitely | Council verdict: most `:free` OpenRouter models lack grammar-level constrained decoding → silent no-op; forced `confusion_signal` token destroys naturalism. |
| Corpus growth to n≥5 authentic + n≥3 adversarial | DEFERRED as durable carry-over | Documented below. Happens organically as Y runs accumulate. |

## Test deltas

- `tools/surface-y-tripwire.test.mjs`: 5 → 8 (+3 AND-gate behaviors).
- `tools/surface-y.test.mjs`: 17 (unchanged count; existing "suspect for instant-submit hyper-competent run" test fixture updated to use `observation: ""` — the "ready" observation counted as 1 finding under the new AND-gate semantics, which would have cleared the suspect flag).
- `tools/surface-y-persona.test.mjs`: 9 → 10 (+1 exemplar presence + warning string).
- **Tools suite total: 114 (post Phase A) → 118 (post Phase B). 0 regressions.**

## Live-run acceptance

A live Y run was NOT executed as part of this acceptance (no fresh Y session triggered post-Phase-B). The acceptance criterion is **schema-level**:

- Frontmatter assertion in the existing "instant-submit hyper-competent" test (`tools/surface-y.test.mjs:413+`) confirms the new fields land in DRAFT-Y output: `tripwire_findings_count: 0` + `tripwire_confidence_band: thin_corpus_n2`.
- Tripwire AND-gate behavior validated via 3 unit-level tests covering all three cells of the 2x2 (competent + nonzero findings = CLEAN; competent + zero findings = SUSPECT; naive = CLEAN regardless).
- Persona prompt exemplar presence validated via `buildPersonaPrompt(...)` return-value match.

When the next Y run is triggered (e.g. `node tools/surface-y.mjs --task=01KR6K07T6PATPRR5KH1JXYF8E`), DRAFT-Y.md will carry the new frontmatter fields automatically. The corpus-growth path is unblocked.

## Carry-overs

- **Corpus growth (durable):** log future Y runs as authentic-naive samples. Threshold re-tuning (`min_steps_for_zero_confusion_flag: 6`, `submit_step_index_max_for_flawless_flag: 3`) gated on n≥5 authentic + n≥3 adversarial-leaked. Bump `tripwireCorpusN` in `tools/surface-y.mjs:339` when corpus grows.
- **Adversarial-leaked fixture authoring (durable):** splice Claude-tier persona transcripts OFFLINE (do NOT run Claude-CLI live on Y per binding council 1778839098). Suggested approach: hand-author 3-5 "competence-leaked" transcripts that mimic what a Claude-CLI Y run WOULD produce (zero `ask_sidekick`, submit at step 2, full R answer first try). Use as fixture data in `tools/surface-y-tripwire.test.mjs` to compute TPR/FPR for future threshold tuning.

## Risk surfaces still open (acknowledged, non-blocking)

- **Empty-array footgun in `filterEvents(status_in=[])`** (carried from Phase A.4 review). Not exploitable via current CLI; defensible YAGNI. Document if it ever surfaces.
- **`--all-statuses` flag not in USAGE block** (carried from Phase A.4 review). Cosmetic discoverability gap.

Both can be folded into a small follow-up commit when convenient.
