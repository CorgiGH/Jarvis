# B5-RESHAPE decision log — SESSION-54 (2026-06-04)

Running log of judgment calls taken while executing the B5-RESHAPE (per-claim-kind `faithful` + badge decoupled from `faithful`). Spec: `../plans/2026-06-02-correctness-engine.md` → "B5-RESHAPE". Councils: `.claude/council-cache/council-1780598020`, `-1780600422-coa-post-spike`, `-1780601847-claim-model-reshape`.

## Decisions

- **D-R0 (Alex, GO):** chose council option A (per-claim-kind routing + badge-decoupled-from-faithful). Blessed re-scoping the LOCKED §2.5 invariant: "non-LLM leg" = SymPy for equational claims / live span round-trip for prose. Recorded with sign-off, not a silent widen.
- **D-R1 (model):** keep `MoritzLaurer/DeBERTa-v3-base-mnli-fever-anli` (3-class, proven in spike, native UNCLEAR/neutral band) as the NLI family for now; MiniCheck (binary, no native UNCLEAR) deferred behind the same adapter. (Domain-expert + judge of coa council.)
- **D-R2 (B5r-1, build agent judgment, REVIEWED+ACCEPTED):** route the faithful split on `claim.kind` (`isEquationalKind` = INVARIANT/GRADER_RULE with non-blank invariant), NOT on resolved leg-kind — so an equational claim whose SymPy could not run still floors to `uncertain` (case-4), never substituting round-trip for the missing math check. Safer reading; keeps NONLLM_LEG_NONE live.
- **D-R3 (B5r-1):** for prose the LLM/NLI vote can only **veto** (agreed-REFUTED via `agreedNonSupported`), never **promote** — round-trip is the sole positive anchor (self-entailment on content==quote is zero-signal). Self-entailment guard test added.
- **D-R4 (B5r-1):** a prose DEFINITION whose round-trip FAILS resolves to `failed` (fail-loud), not `uncertain` — matches the R1 "mutated quote REJECTED" acceptance.

- **D-R5 (B5r-2, badge):** badge "matches your lecture" = KC is **lecture-grounded** (every cited quote relocates LIVE) AND no claim `failed`. `faithful` is the stronger tier → "matches your lecture / faithful to your source". Implemented via a NEW additive-nullable `lecture_grounded` boolean on `kc_verification_status` (mirrors the D8 `content_hash` additive pattern) — NOT a new `VerificationStatus` enum value (keeps the frozen enum intact, per spec build-decision). Written by `finalizeKc`.
- **D-R6 (B5r-2):** a `failed` claim SUPPRESSES the grounded badge (a contradicted claim ≠ "matches your lecture"); only `uncertain` (not-yet-cross-checked, e.g. an equational claim awaiting its NL restatement) still allows grounded. The D8 `content_hash` staleness gate wraps `lecture_grounded` exactly as it wraps `faithful` — stale/NULL ⇒ fail-closed to `unverified`.

## Open / deferred

- (filled as steps land)
