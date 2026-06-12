# Interface Signatures Lock — frozen Kotlin/TS contracts (cross-phase)

**Status:** FROZEN reference. NO prose, NO code-to-build — this is the shape contract the phase plans build against.
**DURABLE RULE (P1-4, 2026-06-08):** any forced deviation from a frozen lock (renamed wire field, newly-frozen inner shape, changed server formula) MUST amend the lock doc in the SAME commit that lands the code — never leave code and lock silently contradicting. If the deviation would weaken a Phase-2 trust guarantee, STOP and escalate to the owner. See §R.
**Date:** 2026-06-02 · **HEAD:** `b970585` (branch `door-compare`)
**RE-FREEZE 2026-06-05 (RF1/RF2/RF3, APPROVED):** §I.1 re-frozen to a two-hash scheme (`content_hash` v2 formula + `source_span_hash`, BOTH `sha256_8`), full audit-column allowlist enumerated, and the live `lecture_grounded` drift closed (lock froze 4 cols; live = 6). §I2 records RF2 (gate stays admission-only; serve dispute-refusal via inline `hasOpenReportWrong`) + RF3 (single-owner dispute resolution). Memo: `build-review/2026-06-05-trustnet-refreeze-decisions.md`. (No live re-key here — that is a separate human-gated checkpoint.)
**Parent:** `docs/superpowers/plans/2026-06-02-master-impl-plan-v2.md` (§2.2 wire tables, §2.3 interfaces, §2.5 state machine).
**Why this doc exists:** master-plan §2.3 lists these interfaces as prose arg-lists with NO return types / NO field shapes. They feed Phase-5's 6 `MasterySparkline` consumers + the atomic grade txn. A late shape change = 6-site codemod. This doc freezes the concrete return types + field shapes ONCE so the model swap (PFA/Thompson) and the 6 consumers never force a caller refactor.
**Ground truth read:** `jarvis.content.ContentSchema` (`KnowledgeConcept`/`Misconception`/`SourceRef`/`Span`/`PrereqEdge`), `jarvis.tutor.FsrsCards` (`FsrsState`/`TutorCard`/`FsrsSource`), `jarvis.tutor.KcMastery`, `jarvis.content.SourceOfRecord`, `tutor-web/src/lib/fsrsClient.ts`. **None of the interfaces below exist in code yet — this is their CONTRACT.** Existing types reused are marked `[EXISTS]`.

---

## DECISIONS THAT NEED ALEX

These I could NOT self-determine from the plan + wire-shapes. Everything else below I locked myself (justification inline). Per the no-oracle-inversion rule these are infra/shape decisions, NOT content-vetting — safe to ask.

1. **NONE — zero true content-or-product decisions surfaced.** Every shape below resolved from the master plan's own wire tables + §2.3/§2.5 + existing code. The two items the master plan itself flags as Alex-gated (`§5`) are *raw-file* and *one-time yes/no* inputs, NOT signature shapes:
   - Brand/theme §2.1 confirm (brutalist-default) — gates Phase-4 T5, not any signature here.
   - Raw lecture PDFs — gate Phase-2 content, not any signature here.
2. **`MasterySparkline` prop = `band` — CONFIRMED (no Alex gate).** Render mode `band`, NOT `series`. Forced by the wire, not a preference: `/mastery` returns ONE scalar `ewma_score` + `observations` per KC per call — no time-series array, and none of the 10 schema changes add an `ewma_history` column or a `/mastery/history` endpoint. So `band` (render the single EWMA as a filled bar within a 0..1 mastery band, threshold tick at 0.8 = `KcMastery.MASTERY_THRESHOLD`) is the ONLY shape buildable from the frozen `/mastery` wire with zero new endpoint. This is THE 6-consumer freeze (0a/0b/5/0h/15) — see §M below. A future true sparkline is a purely additive endpoint + an optional `history?` prop, NOT a breaking change — so nothing here forecloses it and nothing needs Alex.

---

## A. `PhaseModel.transition(...)` — sole writer of `kc_mastery.phase`

```kotlin
/** Phase of a KC for one learner. Stored in kc_mastery.phase as the lowercase
 *  `name` (VARCHAR(16), never native enum). Backfill + queue/today + grade reply
 *  all read these literals. */
enum class Phase { intro, practice, retrieval, mastered }   // wire literal == name (lowercase)

object PhaseModel {
    /** Pure. SOLE owner of kc_mastery.phase. Called INSIDE KcMasteryRepo.recordIn(tx,…)
     *  in the same txn (B3), and replayed over (ewma_score, observations) at the CHANGE-2 backfill.
     *  @param current the row's existing phase, or null for a never-phased / pre-migration row. */
    fun transition(
        ewma: Double,
        observations: Int,
        mastered: Boolean,            // == KcMastery.mastered (ewma≥0.8 && obs≥3)
        current: Phase?,
    ): Phase
}
```

- **Returns:** `Phase` (never null — a null `current` resolves to `intro`).
- **Field source:** `ewma`/`observations`/`mastered` are exactly `KcMastery.ewmaScore`/`.observations`/`.mastered` `[EXISTS]`.
- **Consistency:** the returned `Phase.name` is the literal written to `kc_mastery.phase` (CHANGE 2) AND echoed in `/queue/today.items[].phase` AND `/drill/grade.phase` AND `/mastery …kcs[].phase`. One enum, four wire sites.

## B. `Phase` type + payload

- Already declared in §A. **No payload object** — `Phase` is a bare enum; the per-KC phase context (entry_phase, next action) rides on `QueueItem` / the grade reply, NOT on `Phase` itself.
- `entry_phase` (CHANGE-2 column) is the SAME `Phase` enum, nullable at rest (`NULL ⇒ treat as intro`, master-plan CHANGE 2).
- `next_phase_action` (grade reply, §2.2) is a SEPARATE small enum, not part of `Phase`:
```kotlin
enum class NextPhaseAction { advance, hold, remediate }   // wire literal == name
```

## C. `QueueItem` — must match `/queue/today` wire JSON exactly

```kotlin
@Serializable
data class QueueItem(
    val kc_id: String,
    val kc_name_ro: String,
    val kc_name_en: String,
    val subject: String,
    val phase: Phase,                  // serialized as lowercase name
    val mastery_ewma: Double,
    val fsrs_card_id: String?,         // null when no card seeded yet
    val verification_status: VerificationStatus,
    val worked_example_first: Boolean, // SERVER decision (CHANGE-3 field), surfaced here
    val mode: QueueMode,
)
enum class QueueMode { worked, drill, retrieve }   // wire literal == name
```

- **Returned by:** `NextKcSelector.select(...)` (one item) AND the `/queue/today` handler aggregates a `List<QueueItem>` into `{ items, total_due, day }`.
- **Wire envelope** (`GET /api/v1/queue/today`, §2.2): `{ items: QueueItem[], total_due: Int, day: String /* ISO date */ }`. Quarantined / non-faithful items OMITTED server-side (never sent).
- **Field-by-field vs wire JSON §2.2:** `kc_id, kc_name_ro, kc_name_en, subject, phase, mastery_ewma, fsrs_card_id|null, verification_status, worked_example_first:bool, mode:"worked|drill|retrieve"` — 1:1, names frozen.
- **Bilingual:** `kc_name_ro`/`kc_name_en` come straight from `KnowledgeConcept.name_ro`/`.name_en` `[EXISTS]`.

## D. `NextKcSelector.select(...)` — deterministic now, swap-safe

```kotlin
interface NextKcSelector {
    /** Returns the single next QueueItem, or null when no eligible KC exists
     *  (0-KC subject ⇒ null, no crash — M-NEXTKC). Same signature a future
     *  ThompsonSelector implements (the swap never touches callers). */
    fun select(
        userId: String,
        subject: String?,                 // null = across all subjects
        candidates: List<KcCandidate>,    // pre-filtered eligible KCs (faithful + ACTIVE-card or seedable)
        recentShapes: List<String>,       // last-N served `shape` strings, for the interleave-cap
    ): QueueItem?
}

/** Input row the selector scores. Assembled by the queue handler from kc_mastery
 *  + the content corpus + kc_verification_status. NOT a wire type. */
data class KcCandidate(
    val kc: KnowledgeConcept,             // [EXISTS] jarvis.content.KnowledgeConcept
    val mastery: KcMastery?,              // [EXISTS] null = never attempted (cold)
    val phase: Phase,                     // resolved (kc_mastery.phase or entry_phase or intro)
    val verificationStatus: VerificationStatus,
    val fsrsCardId: String?,
)
```

- **Return type:** `QueueItem?` (master-plan §2.3 said `: QueueItem`; tightened to nullable per M-NEXTKC's "0-KC ⇒ empty/no crash").
- **Scoring (locked, not Thompson):** prereq-gated (via `PrereqGraph`) → lowest-mastery → interleave-cap on `recentShapes`. The future `ThompsonSelector implements NextKcSelector` with the identical signature.
- **Memoization:** `candidates` are built from content memoized in `TutorContext` (`ContentRepo` singleton), per M-NEXTKC — `select` itself is pure over its args.

## E. `PrereqGraph` — transitive-closure helper

```kotlin
/** Built once at content load from EdgesFile.edges [EXISTS], memoized in TutorContext.
 *  Transitive closure so a KC is gated behind ALL ancestors, not just direct prereqs. */
class PrereqGraph(
    private val closure: Map<String, Set<String>>,   // kcId -> all transitive prereq kcIds
) {
    /** All prereq kcIds (transitive) of [kcId]; empty set for a root or unknown KC. */
    fun prereqsOf(kcId: String): Set<String>
    /** True iff every prereq of [kcId] is in [masteredKcIds] (or [kcId] is a root). */
    fun isUnlocked(kcId: String, masteredKcIds: Set<String>): Boolean

    companion object {
        /** Pure builder; closes over PrereqEdge(kc, prereq, rationale) [EXISTS]. */
        fun from(edges: List<PrereqEdge>): PrereqGraph
    }
}
```

- **Shape frozen:** `Map<String, Set<String>>` (kcId → transitive prereq kcIds). NOT adjacency-list-only — closure precomputed so `select()` stays O(1) per candidate.
- **Source:** `EdgesFile.edges: List<PrereqEdge>` `[EXISTS]` (`content/{subject}/edges.yaml`).
- **0-edge subject:** `from(emptyList())` ⇒ every KC is a root (`prereqsOf == emptySet`, `isUnlocked == true`).

## F. `ScaffoldPlanner.planFor(...)` — ordered phase plan for one KC

```kotlin
object ScaffoldPlanner {
    /** `kc.phase_plan ∩ not-yet-mastered`, honoring entry_phase (the §4.1 gate).
     *  Reads CHANGE-3 `phase_plan: List<String>?` (default = all 4 phases). */
    fun planFor(kc: KnowledgeConcept, mastery: KcMastery?): List<Phase>
}
```

- **Returns:** `List<Phase>` — ordered subset of `[intro, practice, retrieval, mastered]`, already trimmed below the learner's current phase floor (`entry_phase`) and above mastered.
- **Reads:** CHANGE-3 `KnowledgeConcept.phase_plan: List<String>?` (parsed to `List<Phase>`; `null ⇒ all four`). `mastery` is `[EXISTS] KcMastery?`.
- **Empty result:** a fully-mastered KC ⇒ `emptyList()` (planner is the only place this is computed — no caller re-derives).

## G. `KcMasteryRepo.recordIn(tx, …)` + `record()` wrapper (B3)

```kotlin
class KcMasteryRepo(private val db: Database) {
    /** NEW, txn-LESS. Caller owns the transaction (the grade route opens ONE
     *  transaction{} → loops recordIn + attempts insert + upsertRubricCriterion).
     *  Computes the EWMA AND the new Phase (via PhaseModel.transition) and upserts
     *  both kc_mastery.ewma_score/observations/last_graded_at AND .phase in-txn. */
    fun recordIn(
        tx: Transaction,                  // org.jetbrains.exposed.sql.Transaction
        userId: String,
        kcId: String,
        score: Double,
        now: Instant = Instant.now(),
    ): KcMastery                          // [EXISTS] return type unchanged

    /** Thin wrapper: opens one transaction(db){ recordIn(this, …) }. Back-comp for
     *  callers that grade a single KC. The OLD self-txn record() is REPLACED by this. */
    fun record(userId: String, kcId: String, score: Double, now: Instant = Instant.now()): KcMastery
}
```

- **`tx` type:** `org.jetbrains.exposed.sql.Transaction` (the receiver inside `transaction(db){ … }`).
- **Returns:** `KcMastery` `[EXISTS]` — same shape callers already use; `record()` and `recordIn()` return identically.
- **Side effects in-txn:** writes `ewma_score, observations, last_graded_at` (existing cols) PLUS `phase` (CHANGE-2 col, value = `PhaseModel.transition(...)`). `PhaseModel` is called ONLY here.
- **Atomic-grade consistency:** the grade route's single `transaction{}` calls `recordIn(tx,…)` then `attempts` insert then `upsertRubricCriterion(tx,…)` — all-or-nothing (B1/B3).

**Note (Plan 3, 2026-06-12, additive — NOT a new pin):** `AttemptsTable` (`Phase1Tables.kt`) gains 3
additive nullable columns for the lesson-completion contract (spec §4.4, audit T3): `beat_type`
varchar(12), `prediction` text, `first_encounter` bool — all nullable (drill attempts write NULL).
These are NOT in the frozen-pin set: `SignatureLockPinTest` lists `attempts` only in its
frozen-table-NAME additivity set (no column-set pin on `attempts`), so adding columns breaks no pin.
The 3-column delta is pinned by `AttemptsBeatColumnsMigrationTest` (the live-shape replica test),
mirroring `LiveShapeColumnsMigrationTest`. INV-3.1: this is pending DDL ⇒ the backup gate FIRES on
both live DBs; operational apply is Plan-3 Tasks 12 (PC) / 13 (VPS), never a unit test.

## H. `FsrsCardRepo.upsertRubricCriterion(tx, …)` (B2)

```kotlin
class FsrsCardRepo(private val db: Database) {
    /** NEW. SELECT-then-UPDATE-or-INSERT inside the CALLER's txn (no own transaction).
     *  front = stem_template ?: name_en ; back = canonicalAnswer. Needs the kc_id
     *  column (CHANGE 1) + a (userId, sourceType, kcId) index. 1:N over Problem.kcIds. */
    fun upsertRubricCriterion(
        tx: Transaction,
        userId: String,
        kcId: String,
        front: String,
        back: String,
        state: FsrsState,                 // [EXISTS] jarvis.tutor.FsrsState
    ): TutorCard                          // [EXISTS] the upserted card
}
```

- **`tx` type:** `org.jetbrains.exposed.sql.Transaction` (same txn as `recordIn`).
- **`state` type:** `FsrsState(difficulty, stability, retrievability, dueAt, lastReviewedAt, lapses)` `[EXISTS]`.
- **Returns:** `TutorCard` `[EXISTS]` (the row after upsert) — `source = FsrsSource.RUBRIC_CRITERION` `[EXISTS enum]`, `sourceRef` = the rubric/stem id, and the NEW `kc_id` column = `kcId`.
- **Dedup key:** the `(userId, sourceType, kcId)` index (CHANGE 1) — one RUBRIC_CRITERION card per (user, kc); re-grade UPDATEs, never duplicates (the B2 bug).
- **kc_id column note:** GAP_PROMOTION cards keep `kc_id = NULL` (master-plan CHANGE 1); only RUBRIC_CRITERION sets it.

## I. `VerificationStatus` enum + `transition(...)` (§2.5)

```kotlin
/** A KC's resolved trust status. Runtime source of truth = kc_verification_status table
 *  (B8), NOT the YAML seed. Wire literal == name. */
enum class VerificationStatus { unverified, pending, faithful, uncertain, failed }

object VerificationStatus_ {   // (companion-style holder; pure transition per §2.5)
    fun transition(from: VerificationStatus, outcome: AuditOutcome): VerificationStatus
}

/** What one audit run concluded (drives the §2.5 state machine). */
enum class AuditOutcome {
    ALL_AGREE_ROUNDTRIP_NONLLM_PASS,   // -> faithful
    FAMILY_COLLAPSE,                   // both legs same configured family -> uncertain
    DEFINITIONAL_NO_GOLD_SPAN,         // -> uncertain
    NONLLM_LEG_NONE,                   // domain has no checker -> uncertain (floor)
    EQUATIONAL_LLM_UNCONFIRMED,        // B5r-3/D-R9: SymPy+round-trip pass, LLM family merely UNCLEAR -> uncertain
    PROSE_LLM_UNCONFIRMED,             // MF-1/D-R17: prose anchor round-trips, LLM family not bothSupported -> uncertain
    DISAGREE_OR_ROUNDTRIP_FAIL_OR_THREW, // -> failed/uncertain
    REPORT_WRONG,                      // faithful -> pending
}
```

- **Five literals** match `/verify/{kcId}/status` + CHANGE-3's four authored literals `{unverified, pending, faithful, uncertain}` PLUS `failed` (the §2.5 FAILED state; never authored in YAML, only reached at runtime).
- **`AuditOutcome` post-lock additions (SESSION-57 sync):** `EQUATIONAL_LLM_UNCONFIRMED` (B5r-3 / D-R9) + `PROSE_LLM_UNCONFIRMED` (MF-1 / D-R17) are internal driver values, BOTH mapping to `uncertain`. They were ratified during the Phase-2 B5-RESHAPE / false-faithful work and do NOT widen the frozen `VerificationStatus` wire surface (still the 5 literals). Canonical code: `VerificationStatus.kt:46-62`.
- **Wire consistency:** `verification_status` appears in `/queue/today`, `/mastery`, `/calibration`(no), `/drill/grade`, `/fsrs/due`, `/verify/{kcId}/status`, `mock-exam` kc_results, `QueueItem`, `KcCandidate`. ONE enum, every site serializes its lowercase `name`.
- **Mock-exam `kind` semantics (P2-5, council fix 2026-06-08):** the §2.2 `kind:"deterministic"|"open"` field is FROZEN as a wire shape, but the runtime ONLY ever emits `"open"`. A KC's `invariant` is the verification re-derivation SEED (a math statement the two-family audit checks), **NOT** a student answer key — equating an invariant-match with answer correctness scored every "deterministic" question against the wrong oracle. There is NO `canonical_answer` field in `KnowledgeConcept`, so there is nothing to score a closed-form answer against; every mock question therefore degrades to OPEN (LLM-graded → UNCERTAIN). **Unlock (future):** add an AUTHORED `KnowledgeConcept.canonical_answer: String?` (distinct from `invariant`); a question MAY be `deterministic` iff `canonical_answer` is non-blank, scored via `GradeScoring.answerMatches(canonical_answer, response)` — never via `invariant`. See master-impl-plan-v2 §6 Deferred. Canonical code: `MockExamRoutes.toQuestion`.
- **YAML caveat:** the four-literal subset is the authored seed (CHANGE 3 / `KnowledgeConcept.verification_status: String`); the runtime store can ALSO hold `failed`. The validator's enum check (H11) accepts the four authored literals only; `failed` is runtime-only.
- **Invariant (§2.5, LOCKED):** no path reaches `faithful` without BOTH a non-LLM-leg pass AND families-agree.

### I.1 `kc_verification_status` audit columns — the D8/D1 staleness fingerprints + lecture-grounded signal (FROZEN; **RE-FROZEN 2026-06-05, RF1**)

`kc_verification_status` is keyed on `kc_id` ALONE; an audit verdict therefore outlives the content it certified. **D8** adds a content fingerprint so an edited lecture cannot keep a stale `faithful` badge. **RF1 (re-freeze 2026-06-05, per `build-review/2026-06-05-trustnet-refreeze-decisions.md` D-RF1)** widens this to a TWO-HASH scheme, pins the `content_hash` v2 formula, and enumerates the full audit-column set.

> **DRIFT FIX (2026-06-05):** this lock previously froze a 4-column table and `content_hash` was the only audit-column it named. **Live reality is 6 columns** (`Phase1Tables.kt:108-150`): `kc_id · status · last_audit_run_id · content_hash · lecture_grounded · updated_at`. `lecture_grounded` (B5r-2 / D-R5/D-R6) is **live + served** (the served "matches your lecture" badge lights on `status==faithful` OR `lecture_grounded==true`, both behind the staleness gate) but was **absent from both lock docs**. It is now frozen here (and in `data-model-lock` CHANGE-10). The lock is canonical-on-conflict; it must not under-state the live schema.

```kotlin
// jarvis.tutor.KcVerificationStatusTable (Phase1Tables.kt) — the full audit-column set (FROZEN, RF1):
val contentHash    = varchar("content_hash", 16).nullable()      // sha256_8, v2 formula below; NULL ⇒ fail-closed
val sourceSpanHash = varchar("source_span_hash", 16).nullable()  // sha256_8 (RF1: MIRRORS content_hash width); D1; NULL ⇒ fail-closed
val lectureGrounded = bool("lecture_grounded").nullable()        // B5r-2/D-R5/D-R6; NULL ⇒ fail-closed
// (existing: kcId PK, status, lastAuditRunId, updatedAt)

// jarvis.content.ContentReconcile — (deterministic, reorder-stable):
fun kcContentHash(kc: KnowledgeConcept): String                 // == kcContentHashOf(claimsFor(kc))
fun kcContentHashOf(claims: List<VerificationClaim>): String    // sha256_8 over claimId-sorted, v2 formula below — audit-side entry
```

- **Audit-column allowlist (FROZEN, RF1):** the set D9 surgically upserts PC→VPS is exactly `{status, content_hash, lecture_grounded, source_span_hash, last_audit_run_id}`. D9 NEVER syncs `report_wrong` rows and NEVER the whole `tutor.db`. These five plus the PK `kc_id` + bookkeeping `updated_at` are the table.
- **Two-hash scheme (FROZEN, RF1 — D-RF1.2):** `content_hash` is the PRIMARY trust fingerprint AND the D9 surgical-upsert match KEY; `source_span_hash` is the SECONDARY source-of-record co-factor (D1). **BOTH are `sha256_8` / `varchar(16)`.** RF1 OVERTURNED the original "full-sha256 `source_span_hash`" proposal: a 256-bit secondary behind a 64-bit primary gate is internally inconsistent and buys nothing on a single-user pre-launch corpus (the threat is self-inflicted re-OCR / source churn, NOT adversarial preimage forgery). Decide width for BOTH together: mirror at `varchar(16)` (chosen), OR widen BOTH to full sha256 only if a 256-bit trust tier is genuinely wanted. **Never ship one 64-bit and one 256-bit trust hash.**
  - `content_hash` (D8 + D2) — rides the SHARED `kcContentHash` (a YAML-derived value both audit and serve recompute identically). Covers the equational input.
  - `source_span_hash` (D1) — a SEPARATE, **PC-computed-only** column. It CANNOT ride `kcContentHash`: the VPS is serve-only (D7) and has no `_sources/{doc}.md` bytes, and a folded-in stored value would be self-referential (serve recompute would always match). Computed PC-side over the round-trip's normalized located slice; the serve gate treats it as presence/version (NULL ⇒ fail-closed); the actual source-edit DETECTION is a PC-side reconcile step. See `build-review/2026-06-05-trustnet-fix-plan.md` D1.
- **`content_hash` formula (FROZEN, RF1 — v2):** `ContentReconcile.kcContentHashOf(claims)` = `sha256_8` over a `claimId`-sorted set, canonicalizing each claim as **`v2:kind|content|invariant|doc|page|spanStart|spanEnd`** (`joinToString("")`). Versus the live v1 (ContentReconcile.kt:285-300, which folds `kind|content|doc|page|spanStart|spanEnd`), v2 ADDS:
  1. a **`v2:` schema-version prefix** so every legacy v1 row deterministically MISMATCHES and fails CLOSED (also closes the M1 legacy stale-grounded defect for free); and
  2. the raw **`c.invariant ?: ""`** term (D2) — the SymPy equation SymPy actually checks. NOTE the KDoc at ContentReconcile.kt:268-272 states `invariant_statement` is ALREADY folded transitively via `claimsFor` content; D2 adds the DIFFERENT raw `VerificationClaim.invariant` field (the SymPy equation), NOT a double-count of `invariant_statement` — RF1 requires that KDoc be REWRITTEN to remove the self-contradiction + a build-time check that the raw `invariant` carries something `claimsFor` does not already put in `content`.
  Reorder-stable for `grader_rules` (shuffling grader rules does not change the hash). `source` reorder is stable for **DEFINITION** claims / DEFINITION-only KCs (DEFINITION claims fold each ref's own span, so no ref is "first"). For **anchorRef-bound kinds** (INVARIANT / GRADER_RULE / EXPLANATION / WORKED_EXAMPLE), `kcContentHashOf` folds the **first span-bearing ref** (`anchorRef = kc.source.firstOrNull { it.span != null }`) as the claim's source; promoting a different span-bearing ref to first (via a source-list reorder) changes `doc|page|spanStart|spanEnd` for those claims and therefore changes the hash. Changing any audited claim text, the invariant equation, OR moving a cited span DOES change it. The runner uses the `kcContentHashOf(claims)` form so the fingerprint reflects exactly what was audited, preserving the FROZEN identity `kcContentHash(kc) == kcContentHashOf(claimsFor(kc))` (audit-side == serve-side).
- **Writer (FROZEN):** `VerificationRunner.audit`/`finalizeKc` computes the per-KC `content_hash` + `source_span_hash` + `lecture_grounded` once over the batch and stamps them onto the `kc_verification_status` upsert alongside `status`/`last_audit_run_id`, in ONE transaction.
- **Serve gate (FROZEN):** `VerifyAdmin.resolveStatus(db, kcId, kc)` (`GET /verify/{kcId}/status`) lights the served badge ONLY when (`row.content_hash != null && kc != null && row.content_hash == kcContentHash(kc)`) AND `row.source_span_hash != null` AND (`status == faithful` OR `lecture_grounded == true`). A content-hash mismatch (lecture edited after audit), a NULL `content_hash`/`source_span_hash`/`lecture_grounded` (legacy/partial row), or a missing live `kc` falls CLOSED to `unverified` ⇒ `HonestFloor.UNVERIFIED` (§P) ⇒ badge `"unverified"`. Staleness / partial-sync / PC-off therefore fail-closed to honest "unverified", never fail-stale to a lying badge.
- **Re-key ordering (FROZEN, RF1):** because `content_hash` is SIMULTANEOUSLY the D8 staleness fingerprint AND the D9 upsert KEY, the v2 flip MUST land in this order — flip formula (v2 prefix + invariant term) + add `source_span_hash` column → ONE re-audit while all 3 trust tables are EMPTY (0 rows, verified) → THEN author D9/B6 against the widened keyspace + the audit-column allowlist. A D9 shipped before the flip would upsert on the old keyspace and silently match ZERO rows (no-op orphan). Two pre-flip fences (RF1): a HARD build-time abort on "0 live `content_hash` rows at flip time" (fence out audit runs during the Step-0 window), and an audit-env gate (`JARVIS_PYTHON3`/NLI present) BEFORE the ONE re-audit (FAIL-LOUD turns a never-ran audit into DISAGREED). The live v2 re-key + ONE re-audit is a SEPARATE gated checkpoint a human runs later — NOT part of this doc edit.

## I2. `VerificationGate.gate(kc)` — the single trust chokepoint (B8)

```kotlin
/** Binary gate outcome. Wire-irrelevant (server-internal); ALLOW lets a KC enter
 *  SR / cold-start seed / GapPromotion / the B1 card upsert, DENY blocks it. */
enum class GateDecision { ALLOW, DENY }

object VerificationGate {
    /** The ONE chokepoint for SR-entry / cold-start seed / GapPromotion.promote /
     *  the B1 RUBRIC_CRITERION card upsert. Pure over its inputs.
     *  ALLOW iff `status == faithful` (resolved from the kc_verification_status table,
     *  B8 — NOT the YAML seed) AND no OPEN report_wrong for this kc. IGNORES student
     *  attempt counts (no laundering by consistency, master-plan §2.3). */
    fun gate(
        kc: KnowledgeConcept,             // [EXISTS] the KC being gated (kc.id is the lookup key)
        status: VerificationStatus,       // resolved from kc_verification_status (B8), NOT YAML; falls back to YAML seed only for a KC with no row yet
        hasOpenReportWrong: Boolean,      // true iff an OPEN report_wrong row exists for kc.id
    ): GateDecision
}
```

- **Param shape (frozen):** `gate` takes the `KnowledgeConcept` `[EXISTS]` PLUS the two pieces of runtime state it decides on — the **resolved** `VerificationStatus` (§I) and the OPEN-`report_wrong` flag. The master plan's prose `gate(kc)` is widened to `gate(kc, status, hasOpenReportWrong)` so the fn stays **pure** (the caller does the B8-table read + the report-wrong lookup; the gate never touches the DB). This keeps the chokepoint trivially unit-testable and the swap-free.
- **Return:** `GateDecision` (`ALLOW | DENY`) — the master plan's `ALLOW|DENY`, materialized as a 2-literal enum (not a `Boolean`, so call sites read `== ALLOW`, not a bare bool).
- **ALLOW rule (LOCKED):** `status == VerificationStatus.faithful && !hasOpenReportWrong`. Every other status (`unverified|pending|uncertain|failed`) ⇒ DENY. **Ignores attempt counts** — consistency never launders trust.
- **Callers (§2.3):** SR-entry, cold-start seed, `GapPromotion.promote`, and the B1 `upsertRubricCriterion` site (§H) each gate through this BEFORE writing. M-GATE-PATHS exemptions (`FsrsSeedMain`, `GapPromotion` early-return, kc-less legacy cards) never reach `gate` — they badge `unverified` and are not blocked here.
- **VerificationGate stays ADMISSION-ONLY (FROZEN, RF2 — D-RF2, 2026-06-05):** the D3 serve-time OPEN-dispute refusal is NOT routed through this gate. The frozen caller-set above is admission-only; `gate`'s ALLOW rule is `status==faithful`, which does NOT match the serve badge predicate (`faithful OR lecture_grounded`, §I.1/`servedHonestFloor`); and F2 ships default-OFF. Routing a SAFETY refusal through a default-OFF toggleable gate = the fail-safe/feature-flag anti-pattern. Instead, the serve path consults OPEN `report_wrong` INLINE: `servedHonestFloor`/`resolveStatus`, within their already-open txn, return `UNVERIFIED` when an OPEN `report_wrong` row exists for the kc. **Extract ONE pure helper `hasOpenReportWrong(db, kcId): Boolean` (fail-closed-on-throw: a query throw ⇒ assume OPEN ⇒ UNVERIFIED)** and have the gate's caller, `servedHonestFloor`, AND the future Phase-3 queue/today filter all call it — de-dups the shared INPUT across its 3 call sites without coupling the two different OUTPUT predicates. See `build-review/2026-06-05-trustnet-refreeze-decisions.md` D-RF2 + `…-trustnet-fix-plan.md` D3.
- **Dispute owner = the single-user admin/verify owner (FROZEN, RF3 — D-RF3, 2026-06-05):** the `report_wrong` closing transitions (`OPEN → {REVERIFIED_FAITHFUL | RETRACTED}`, §2.5) are OWNER-ONLY, where "owner" = the single-user admin/verify owner — NOT a new multi-actor moderation role (multi-user is locked-deferred; building a moderation actor now = YAGNI against plan-fully-then-build). The state machine is actor-agnostic; multi-user later = an authz layer on the SAME edges, not a redesign. **Recorded here in the lock (not only in a review doc) per the load-bearing "record it" clause.** Provenance schema fix is in `data-model-lock` CHANGE-7 (the `resolved_by`/`resolved_at` columns, added NOW while `report_wrong` is empty — the un-backfillable cost).
- **NOT the same as the citation chokepoint (§Q):** `gate(kc)` is the binary ALLOW|DENY **KC-admission** gate at SR-entry. The per-claim, per-emit **citation** chokepoint (`TASK P2-RULE8`) is `CitationGuard.attach(claim): CitedClaim` (§Q) — a different unit (claim), point (serialization boundary), and signature. Both are FAIL-LOUD chokepoints; do not conflate them.

## J. `LiveSourceLocator` — folded-index → raw-index map (H7)

```kotlin
/** Fuzzy-locate a claim's quote in the WHITESPACE-FOLDED source text, then translate
 *  the matched range back to RAW global offsets (SourceOfRecord.slice uses raw offsets).
 *  Test anchor: pa-kc-005's "\n  " quote. */
object LiveSourceLocator {
    fun locate(rawSource: String, quote: String): LocateResult
}

/** Output. `span` is in RAW offsets (ready to store on SourceRef.span [EXISTS]).
 *  `pageAnchorStatus` distinguishes LIVE / DEGRADED / NONE (M-PAGE);
 *  DEGRADED (offset+fuzzy pass, no form-feed) is still FAITHFUL-eligible. */
data class LocateResult(
    val span: Span?,                       // [EXISTS] jarvis.content.Span (RAW offsets) — null = not located
    val page: Int?,                        // SourceOfRecord.pageOf(rawSource, span.start); null when no form-feed
    val pageAnchorStatus: PageAnchorStatus,
    val fuzzyDistance: Int,                // Levenshtein on the folded match (guard threshold)
)
enum class PageAnchorStatus { LIVE, DEGRADED, NONE }
```

- **The "folded→raw map" the master plan names** is the function `locate`, whose output `LocateResult.span` carries RAW offsets — the fold is an internal index; only the raw span is exposed (so `SourceOfRecord.slice(rawSource, span)` `[EXISTS]` round-trips).
- **`span` type:** `jarvis.content.Span(start, end)` `[EXISTS]` — RAW global offsets.
- **`page`/`pageAnchorStatus`:** feed `verification_audit.page` + `.page_anchor_status` (CHANGE 6). `page == null` when the source has 0 form-feeds (the current `pa-lecture-01.md` DEGRADED case, M-PAGE/M-CSCHEMA).

## K. `NonLlmLeg` — domain-scoped machine checker (H9)

```kotlin
/** The non-LLM verification leg. Domain-scoped: PA ⇒ SYMPY; PS/SO-RC/POO/ALO ⇒ NONE
 *  (no runnable checker yet) ⇒ UNCERTAIN floor. HUMAN_GOLD/TEST_EXEC are system-derived
 *  from the PDF, NEVER routed through Alex (no oracle inversion). */
enum class NonLlmLegKind { SYMPY, TEST_EXEC, HUMAN_GOLD, NONE }

/** ONE auditable claim extracted from a KC by curate-tutor Stage-9 — the unit the
 *  verification engine audits and `verification_audit` records (one row per claim per run).
 *  Frozen here because `NonLlmLeg.check`, `TwoFamilyDeriver`, `SpanClaimRoundTrip`,
 *  `VerificationRunner.audit`, AND `CitationGuard.attach` (§Q, the emit-chokepoint that
 *  narrows it to a CitedClaim) all consume THIS shape. Fields derived 1:1 from
 *  the `verification_audit` columns (master-plan CHANGE 6) + M-CLAIM. NOT a wire type. */
data class VerificationClaim(
    /** `"{kcId}:{kind}:{sha256_8(content)}"` — content-hash, reorder-stable (M-CLAIM);
     *  == verification_audit.claim_id (varchar(96)). NOT a positional ordinal. */
    val claimId: String,
    val kcId: String,                      // verification_audit.kc_id; matches KnowledgeConcept.id [EXISTS]
    val subject: String,                   // verification_audit.subject
    val kind: ClaimKind,                   // verification_audit.claim_kind
    /** The asserted text the legs re-derive (definition body / invariant / grader rule /
     *  refutation / stem). What `sha256_8` hashes for claimId. */
    val content: String,
    /** The KC's precise invariant (CHANGE-3 `KnowledgeConcept.invariant`), when kind needs it
     *  (INVARIANT / GRADER_RULE); null for purely definitional claims (⇒ DEFINITIONAL_NO_GOLD_SPAN). */
    val invariant: String?,
    /** The verbatim citation backing this claim. `source.span` (RAW offsets) is what
     *  `LiveSourceLocator.locate` re-locates + `SpanClaimRoundTrip` round-trips;
     *  null span ⇒ no gold span ⇒ definitional ⇒ UNCERTAIN floor (§2.5). */
    val source: SourceRef?,                // [EXISTS] jarvis.content.SourceRef (carries doc/quote/page/span)
)

/** What a claim asserts. Wire literal == name (UPPER). == verification_audit.claim_kind. */
enum class ClaimKind { DEFINITION, INVARIANT, GRADER_RULE, MISCONCEPTION_REFUTATION, STEM, EXPLANATION, WORKED_EXAMPLE }

fun interface NonLlmLeg {
    fun check(claim: VerificationClaim): NonLlmResult
}

/** Output type. `kind == NONE` ⇒ ran == false ⇒ the §2.5 NONLLM_LEG_NONE outcome (UNCERTAIN floor). */
data class NonLlmResult(
    val kind: NonLlmLegKind,
    val ran: Boolean,                      // false iff kind == NONE (FAIL-LOUD: never-ran ≠ disagreed)
    val pass: Boolean,                     // meaningful only when ran == true
    val detail: String?,                  // e.g. SymPy simplification, or null
)
```

- **The "output type" the master plan names** = `NonLlmResult` (above). `kind == NONE ⇒ ran=false ⇒ pass ignored ⇒ UNCERTAIN floor` (§2.5 invariant, FAIL-LOUD H5).
- **Wire to `verification_audit`:** `nonllm_leg` column = `kind.name` (`SYMPY|TEST_EXEC|HUMAN_GOLD|NONE`), `nonllm_result` text = `detail`, `agree` partly driven by `pass` (CHANGE 6).
- **`VerificationClaim` input (frozen above):** the unit every audit leg consumes. `claimId` == `verification_audit.claim_id` (content-hash, M-CLAIM); `kind` == `verification_audit.claim_kind` (the `ClaimKind` enum, 5 literals matching CHANGE 6's `DEFINITION|INVARIANT|GRADER_RULE|MISCONCEPTION_REFUTATION|STEM`); `source: SourceRef?` `[EXISTS]` carries the RAW span `LiveSourceLocator`/`SpanClaimRoundTrip` (§J) operate on. A null `source.span` ⇒ no gold span ⇒ the §2.5 `DEFINITIONAL_NO_GOLD_SPAN` outcome (UNCERTAIN floor). Emitted by curate-tutor Stage-9 reconcile; never a wire type.
- **AMENDMENT (grounded-teaching layer, council 1780928193 — deviation-amends-lock):** `ClaimKind` gains two literals `EXPLANATION` + `WORKED_EXAMPLE` (authored plain-words explanation + worked example on `KnowledgeConcept.{explanation_ro, worked_example_ro}`). Both are PROSE claims (`invariant = null`) anchored on the KC's first span-bearing source ref; they route through `VerificationRunner.decideOutcome` EXACTLY like an existing prose `GRADER_RULE` (case 3pr faithful / case 4p `PROSE_LLM_UNCONFIRMED` floor) — `isEquationalKind` returns false for them, so they NEVER reach the equational or DEFINITION branches. They are additive wire literals → `verification_audit.claim_kind`; emitted only when the corresponding field is non-blank, so existing KCs' `content_hash` is unchanged (no re-audit cascade).

## L. LLM-leg family enum (H3) — collapse detection

```kotlin
/** Each verification leg is tagged with its CONFIGURED family. Collapse = two legs
 *  share a family (NOT a model-string compare — both clients substitute a non-blank id). */
enum class LegFamily { RELAY, OPENROUTER, NONLLM }
```

- Used by `TwoFamilyDeriver` to detect `FAMILY_COLLAPSE` (§I `AuditOutcome`). Family A = `RelayLlm` (`RELAY`), family B = `OpenRouterChatLlm :free` (`OPENROUTER`), non-LLM leg = `NONLLM`.

---

## M. `MasterySparkline` prop — THE 6-consumer freeze (Phase-5 T1)

**TS prop shape (frozen here; `/mastery` is its only data source):**

```ts
// One KC's mastery datum, straight from GET /api/v1/mastery → subjects[].kcs[].
// Frozen: 6 consumers (0a learner-queue, 0b subject-map, 5 session-wrap, 0h ledger,
// 15 fsrs-review, + the shared mount) read THIS shape. Adding a required prop = 6-site codemod.
export interface MasterySparklineProps {
  kcId: string;
  ewmaScore: number;          // 0..1, the single scalar /mastery returns per KC
  observations: number;       // drives the "n=" / cold-start ('—' at 0 obs) state
  phase: "intro" | "practice" | "retrieval" | "mastered";
  verificationStatus: "unverified" | "pending" | "faithful" | "uncertain" | "failed";
  lastGradedAt: string | null; // ISO; null ONLY at 0 observations (M-MASTERY-SHAPE)
  // RENDER MODE = "band": fill a 0..1 mastery band with a marker at ewmaScore and a
  // threshold tick at 0.8 (KcMastery.MASTERY_THRESHOLD). NOT a time-series sparkline —
  // /mastery returns no history array. See "DECISIONS THAT NEED ALEX" #2.
}
```

- **PICKED `band`, justified (1 line, per task):** `/mastery` returns one scalar `ewma_score` + `observations` per KC — there is NO history array and NO migration adds one, so `band` (single-value gauge against the 0.8 threshold) is the only shape buildable from the frozen wire today; `series` would need a net-new `/mastery/history` endpoint + column.
- **Must stay consistent with:** `GET /api/v1/mastery` → `subjects[].kcs[].{kc_id, kc_name_ro, kc_name_en, phase, ewma_score, observations, last_graded_at, verification_status}` (§2.2, "the single shape `MasterySparkline` reads", M-MASTERY-SHAPE). Field renames here = wire renames there.
- **6 mount sites** (master-plan §4.E/§4.F + §2.2): `LearnerQueueList`(0a) · `SubjectCard`(0b) · `SessionWrapPane`(5) · `LedgerRow`(0h) · `FsrsReview`/`TrustBadge`(15) · the shared Phase-5-T1 inline mount. Each mounts the SAME component; no per-site shape.
- **Additivity rule:** any future history view = OPTIONAL `history?: number[]` prop + an additive endpoint — never a required-prop break across the 6.

---

## O. `/drill/grade` served teaching payload — `MisconceptionPayload` + `LadderRung` + `self_explanation_prompt` (gap C, Phase-3 `TASK P3-MISC-SERVE`/`P3-LADDER-SERVE` → Phase-5 consumers)

The master plan (§2.2, the `POST /api/v1/drill/grade` row + §2.3 prose) extends the grade reply with three served teaching fields beyond the original 5 scalars (`verification_status, kc_quarantined, phase, next_phase_action, cross_checked`). They wire stored refutation/figure + L0–L4 rung text + the self-explanation prompt to the client so Phase-5 `MisconceptionRibbon` / `FeedbackLadder` / `DrillStack` render from **served** data, not local re-derivation. **Frozen here because the lock is canonical-on-conflict — without this section those three Phase-5 components have no frozen shape to build against.**

```kotlin
/** The misconception payload that rides INLINE on the /drill/grade reply (gap C, DECIDED #1).
 *  Present (non-null) ONLY when the graded answer triggered a known misconception for one of
 *  the server-resolved kcIds; `null` otherwise. The ribbon only ever needs the misconception
 *  for the answer JUST graded, so it rides the grade reply — NO separate round-trip, NO
 *  GET /misconception/{id} fetch (that endpoint is DROPPED as a live serve path; see note below).
 *  Fields 1:1 with the CHANGE-3 `Misconception` columns (id + refutation + figure_spec +
 *  self_explanation_prompt). Read by `MisconceptionRibbon` (Surface 0f, mounted at ladder L4). */
@Serializable
data class MisconceptionPayload(
    val id: String,                          // Misconception.id [EXISTS] — the per-KC misconception id (M-MISC-ENUM)
    val refutation: String,                  // Misconception.refutation [EXISTS] — the refutation body text
    /** CHANGE-3 `Misconception.figure_spec: String?`. SNAKE_CASE on the wire (backend casing wins,
     *  per the casing-skew reconciliation). The spine names this `figureSpec` (camelCase) on Surface 0f;
     *  the TS client maps wire `figure_spec` → prop `figureSpec` at the fetch boundary (one mapping site).
     *  `null` ⇒ the ribbon renders its `FIG. NOT YET WIRED` degraded state (spine §280). */
    val figure_spec: String?,
    /** CHANGE-3 `Misconception.self_explanation_prompt: String?` (Chi/Renkl). The per-misconception
     *  self-explanation prompt shown alongside the refutation; `null` ⇒ no prompt for this misconception. */
    val self_explanation_prompt: String?,
    /** P0-2 (re-open fix, 2026-06-08) — the verbatim citation backing this served refutation, attached
     *  by the `CitationGuard.attach` chokepoint (§Q write-site 2 / P2-RULE8). NON-NULL on the cited serve
     *  path (`MisconceptionPayload.fromCited`): a misconception with no resolved `SourceRef` never becomes
     *  a payload (attach THROWS first — FAIL-LOUD, never ships un-cited). ADDITIVE field, default `null` so
     *  the legacy `from` builder + existing callers are untouched. Serializes as the §2.2 nested
     *  `source:{doc, page|null, span:{start,end}|null, quote}` (L2). AMENDMENT below. */
    val source: SourceRef? = null,
)

/** One rendered feedback-ladder rung (L0–L4). The grade reply carries the FULL ordered rung array
 *  so `FeedbackLadder` (mounted in `DrillStack`, Phase 5) renders the L0→L4 reveal from served text
 *  rather than re-deriving rung copy client-side (gap C, `TASK P3-LADDER-SERVE`). */
@Serializable
data class LadderRung(
    val level: Int,                          // 0..4 — the scaffold/reveal level (L0 = lightest nudge … L4 = full)
    val text: String,                        // the rendered rung copy for this level
)
```

- **Grade-reply served fields (FROZEN, added to the §2.2 reply DTO):**
  - `misconception_payload: MisconceptionPayload?` — the inline payload above; `null` when no misconception fired for the graded answer. **WIRE-NAME RECONCILIATION (2026-06-08, P1-4 / build-log D-G7-1 — code is reality):** the field on the live `/drill/grade` reply DTO is **`misconception_payload`** (object), NOT `misconception`. The reply already carries a *distinct* scalar **`misconception: String?`** = the grader's free-text misconception CODE (`GradeResult.misconception`, e.g. `"L2_ESTIMATOR_CONFUSION"` / `"OTHER"`) frozen in the original E1/E2 G2 reply shape; renaming that to hold this object would BREAK the live frontend (`tutor-web/src/components/DrillStack.tsx:248` binds `misconception: String?`). So the structured §O payload rides under the additive **`misconception_payload`** field while `misconception:String?` is left untouched — both coexist (different types, different consumers). **Phase-5 read-site:** `MisconceptionRibbon` (Surface 0f, mounted at ladder L4) reads `misconception_payload.{refutation, figure_spec, self_explanation_prompt, source}` (TS maps wire `figure_spec → figureSpec`). Code: `MisconceptionPayload` (`src/main/kotlin/jarvis/tutor/GradeTeachingPayload.kt`); served at `TutorRoutes.kt` (`misconception_payload = served.misconception`).
  - `ladder_rungs: List<LadderRung>` — the ordered L0–L4 rung array (`[{level, text}]`); may be empty for a fully-correct answer that needs no ladder.
  - `self_explanation_prompt: String?` — the **drill-level** self-explanation prompt (CHANGE-3 `KnowledgeConcept.self_explanation_prompt` / the active drill's prompt), read by `DrillStack`'s self-explanation rung (Phase 5; H16 — WIRED, not deferred). Distinct from `MisconceptionPayload.self_explanation_prompt` (which is the per-misconception prompt); both can be present.
- **DECIDED #1 — ONE misconception serve shape (inline only):** the misconception serves INLINE on `/drill/grade` ONLY. `GET /api/v1/misconception/{id}` (master §2.2, line 114) is **NOT a live skeleton endpoint** — it has no consumer (the ribbon already has the payload from the grade reply). It is marked **DROPPED/DEFERRED** (no orphan no-consumer endpoint built). If a read-on-demand fetch is ever needed (e.g. a standalone misconception browser), it is a purely additive endpoint returning this same `MisconceptionPayload` shape — no breaking change.
- **Wire literal:** all field names serialize snake_case exactly as written (`misconception`, `ladder_rungs`, `self_explanation_prompt`, `figure_spec`). The TS client maps `figure_spec → figureSpec` at the fetch boundary (the only casing-bridge site; backend snake_case is the wire source of truth).
- **Consumers (master §3 Phase 5):** `MisconceptionRibbon` (0f) reads `misconception_payload` (incl. `figure_spec`); `FeedbackLadder` (in `DrillStack`) reads `ladder_rungs`; `DrillStack`'s self-explanation rung reads `self_explanation_prompt`. One reply DTO, three Phase-5 consumers — shapes frozen so no late codemod. (The scalar `misconception: String?` grader CODE is a SEPARATE field, consumed by the existing DrillStack misconception-code binding, not by the ribbon's structured payload.)
- **AMENDMENT (2026-06-08, P0-2 re-open fix — §O reconciled with §Q write-site 2):** the served misconception is a learner-facing claim, so it MUST cross the `CitationGuard.attach` chokepoint (§Q, P2-RULE8) before it ships — the original §O shape (no citation) let the `/drill/grade` serve build `MisconceptionPayload.from(matched)` and skip the chokepoint entirely (the re-opened P0-2 hole). The fix adds an **additive** `source: SourceRef? = null` field to `MisconceptionPayload` and a `MisconceptionPayload.fromCited(m)` builder that routes the refutation through `attach` (carrying `Misconception.source.first()`), so the served misconception **ships CITED** and **FAIL-LOUD** (`attach` THROWS, the `/drill/grade` handler 500s) when a matched misconception has no resolved `SourceRef` — never silently un-cited. The legacy `from` (citation-skipping) builder is RETAINED for non-learner-facing / test call-sites only; the serve path uses `fromCited`. The new field is optional + defaults `null`, so this is purely additive (no breaking rename of the four original fields; the existing G2 reply shape is untouched).
- **AMENDMENT (trust-leak fix, council 1780928193 — deviation-amends-lock):** `DrillContentDto` (`src/main/kotlin/jarvis/tutor/DrillContentDto.kt`) gains an additive nullable `provenance: DrillProvenanceDto? = null` field, where `DrillProvenanceDto = { type: String ("authored"|"generated"), hasBeenFaithfulChecked: Boolean }`. It marks whether the DTO's `worked`/`definition` prose is authored+faithful-checked or generated+unaudited, so the faithful "matches your lecture" badge can never visually span generated content. `DrillGenerator.generate` + `DrillGenerator.farTransfer` stamp `provenance = {generated, false}` at generate time. Frontend consumer (Phase-5, SEPARATE plan): `DrillContent` in `tutor-web/src/components/DrillStack.tsx` adds optional `provenance.{type, hasBeenFaithfulChecked}` + renders a distinct `data-testid="provenance-badge"` ("AI practice — not checked against your lecture") for `type="generated"`. Additive + nullable ⇒ legacy `task_prep.drills_json` decodes unchanged; `kcContentHash` is unaffected (it is folded from `claimsFor`, which never reads `DrillContentDto`). CI invariant: `DrillContentProvenanceInvariantTest` asserts `hasBeenFaithfulChecked` is true only for `type="authored"` and that `DrillGenerator` output is always `{generated,false}`.

## P. `HonestFloor` — the trust-badge cap while the calibration gold-set gate is deferred (DECIDED #2; `/verify/{kcId}/status`)

```kotlin
/** Caps what the trust badge may CLAIM while the formal calibration gold-set gate is deferred
 *  (master §6.1 Calibration row / §7 item 2). A field on the `/verify/{kcId}/status` reply.
 *  Wire literal == name (UPPER). DECIDED #2. */
enum class HonestFloor {
    /** The KC's claims are re-located + two-family-agreed + non-LLM-leg-passed against the LIVE
     *  source (VerificationStatus.faithful, §I/§2.5): the badge may say "matches your lecture /
     *  faithful to your source" — but NEVER "verified correct" / "grade-calibrated", because the
     *  gold-set grade-trust gate (§7 item 2) does NOT yet exist. This is the CURRENT ceiling. */
    FAITHFUL_TO_SOURCE,
    /** Not (yet) cleared to faithful (unverified|pending|uncertain|failed): the badge pins at
     *  "unverified" and may claim NOTHING about correctness or source-faithfulness. */
    UNVERIFIED,
}
```

- **Type (FROZEN):** `enum HonestFloor { FAITHFUL_TO_SOURCE, UNVERIFIED }`. A field `honest_floor: HonestFloor` on the `/verify/{kcId}/status` reply (master §2.2, line 119). Wire literal == lowercase-or-upper `name` — **frozen as UPPER `name`** (`FAITHFUL_TO_SOURCE`/`UNVERIFIED`), consistent with `ClaimKind`/`NonLlmLegKind` UPPER-name convention in §K.
- **Producer (FROZEN):** DERIVED from the resolved `kc_verification_status.status` (CHANGE-10 / B8 table, §I), NOT authored, NOT stored separately. Mapping: `VerificationStatus.faithful ⇒ HonestFloor.FAITHFUL_TO_SOURCE`; every other status (`unverified|pending|uncertain|failed`) ⇒ `HonestFloor.UNVERIFIED`. Computed in the `/verify/{kcId}/status` response-assembly path alongside `badge_text`.
- **Rule (LOCKED):** the badge text may **NEVER** render "verified correct" or imply grade-calibration while `honest_floor` is below the calibrated level (i.e. always, until §7 item 2 ships). At `FAITHFUL_TO_SOURCE` the badge pins at "matches your lecture / faithful to your source"; at `UNVERIFIED` it pins at "unverified". This is the honest-language ceiling the master plan's trust-language verdict (§1 "NEVER 'verified' or 'correct'") demands, made a concrete reply field rather than a bare unspecified token.
- **Future widen (additive):** when the gold-set grade-trust gate (§7 item 2) ships, a `GRADE_CALIBRATED` literal is ADDED above `FAITHFUL_TO_SOURCE` and the producer mapping extends — a purely additive enum value, never a breaking rename of the two existing literals.
- **`TrustBadge` consumer (Phase 5, Surface 15):** reads `honest_floor` to choose badge copy; it NEVER renders "verified correct" regardless of `verification_status` while `honest_floor ∈ {FAITHFUL_TO_SOURCE, UNVERIFIED}`.

## Q. `CitedClaim` + `CitationGuard.attach(...)` — the per-emit citation chokepoint (`TASK P2-RULE8`)

```kotlin
/** A claim that has PASSED the citation chokepoint: it carries a resolved SourceRef and is the
 *  ONLY claim shape allowed to be serialized into a client envelope (the `/verify/{kcId}/status`
 *  reply `claims[]` element + the `/drill/grade` served misconception/figure payload). Frozen with
 *  the same treatment as `VerificationClaim` (§K) — the input to the verification engine; this is
 *  the OUTPUT shape at the serialization boundary. `TASK P2-RULE8`, master §2.2 / Area B. */
@Serializable
data class CitedClaim(
    val claimKind: ClaimKind,                // §K — DEFINITION|INVARIANT|GRADER_RULE|MISCONCEPTION_REFUTATION|STEM
    val status: VerificationStatus,          // §I — the claim's resolved trust status
    /** The verbatim citation backing this claim. NON-NULL by construction: a claim with no
     *  resolved SourceRef NEVER becomes a CitedClaim — `CitationGuard.attach` throws first.
     *  Serialized as the §2.2 nested `source:{doc, page|null, span:{start,end}|null, quote}` (L2). */
    val source: SourceRef,                   // [EXISTS] jarvis.content.SourceRef — REQUIRED (doc + span|page + quote)
)

/** The per-claim, per-emit citation chokepoint (`TASK P2-RULE8`, roadmap rule #8). DISTINCT from
 *  `VerificationGate.gate(kc)` (§I2): that is the binary ALLOW|DENY KC-admission gate at SR-entry;
 *  THIS is the serialization-boundary guard run on EVERY claim before it ships to the learner, so
 *  no un-cited claim ever reaches the surface. Pure over its input. */
object CitationGuard {
    /** Attach + REQUIRE a resolved SourceRef. FAIL-LOUD: a claim whose `source` is null/unresolved
     *  THROWS (never ships un-cited). **F3 — the 1-arg form is NEVER faithful-capable:** with no
     *  audited status in hand it ALWAYS pins the carried status to `uncertain` (span-presence alone
     *  certifies nothing — a gold span is necessary but not sufficient for faithfulness). The audited-
     *  status overload below is the ONLY path that may ever carry `faithful` onto a `CitedClaim`. */
    fun attach(claim: VerificationClaim): CitedClaim

    /** The faithful-capable form. Carries the RUNNER's audited `VerificationStatus` (the §I status the
     *  `VerificationRunner.audit` wrote to `kc_verification_status`, B8) onto the `CitedClaim` verbatim.
     *  Same FAIL-LOUD null-source THROW. Called at the response-assembly path that builds the
     *  `/verify/{kcId}/status` reply `claims[]` AND the `/drill/grade` served misconception/figure
     *  payload — the single emit-chokepoint for every learner-facing claim. */
    fun attach(claim: VerificationClaim, status: VerificationStatus): CitedClaim
}
```

- **The "emit-chokepoint" the master plan names** (`TASK P2-RULE8`, master §2.2 grade-reply prose + Area B) = `CitationGuard.attach`. It takes a `VerificationClaim` (§K, which has a **nullable** `source: SourceRef?`) and returns a `CitedClaim` with a **non-nullable** `source: SourceRef` — the type system makes "shipped un-cited" unrepresentable: the null→non-null narrowing is exactly where the FAIL-LOUD throw lives.
- **F3 — only the audited-status overload may carry `faithful` (FROZEN):** there are TWO overloads. `attach(claim)` (1-arg) carries `status = uncertain` UNCONDITIONALLY (it has no audited status; span-presence is necessary-but-not-sufficient and must not launder trust into faithful). `attach(claim, status)` (2-arg) carries the RUNNER's audited `VerificationStatus` verbatim and is the SOLE faithful-capable path. The serve path (`/verify/{kcId}/status`) MUST use the 2-arg form with the per-claim audited status (F5). Test: a span-anchored, never-audited claim through the 1-arg path is NEVER `faithful` (pins `uncertain`).
- **Distinct from `VerificationGate.gate` (§I2):** `gate(kc): ALLOW|DENY` is the KC-admission chokepoint (SR-entry / cold-start / GapPromotion / B1 upsert). `CitationGuard.attach(claim): CitedClaim` is the per-emit, per-claim guard at the serialization boundary. Different unit (KC vs claim), different point (admission vs emit), different signature.
- **Write-sites (FROZEN):** (1) the `/verify/{kcId}/status` response-assembly that builds `claims: List<CitedClaim>`; (2) the `/drill/grade` response-assembly that builds the served `MisconceptionPayload` (§O) / figure payload. Every claim crossing either boundary passes through `attach` first.
- **Wire shape of `CitedClaim`:** serializes to the §2.2 `claims[]` element `{claim_kind, status, source:{doc, page|null, span:{start,end}|null, quote}}` (L2 nested `source`; `page==0 ⇒ null`). `source` is REQUIRED on the wire — a missing/unresolved `source` is impossible by construction (it threw at `attach`).
- **Test (master §2.2):** a `VerificationClaim` with `source == null` → `attach` THROWS (serialization never reached); a faithful claim → returns a `CitedClaim` carrying `{doc, span|page, quote}`.

---

## N. Backing wire-shapes these freeze against (quick index — all from master-plan §2.2)

| Signature | Stays consistent with | Wire envelope (frozen names) |
|---|---|---|
| `QueueItem` (§C) | `GET /queue/today` | `{ items:[QueueItem], total_due:Int, day:String }` |
| `MasterySparklineProps` (§M) | `GET /mastery` | `{ subjects:[{subject_id, subject_name_ro, subject_name_en, kcs:[{kc_id, kc_name_ro, kc_name_en, phase, ewma_score, observations, last_graded_at, verification_status}]}] }` |
| (calibration; Surface 6, NOT MasterySparkline) | `GET /calibration` | `{ buckets:[{student_confidence, attempts, correct, accuracy}], total_attempts }` |
| grade reply fields (`Phase`, `NextPhaseAction`, `VerificationStatus`, `MisconceptionPayload` §O, `LadderRung` §O) | `POST /drill/grade` | ADD `verification_status, kc_quarantined:bool, phase, next_phase_action:"advance\|hold\|remediate", cross_checked:bool` **+ (gap C, master §2.2) `misconception_payload:{id, refutation, figure_spec, self_explanation_prompt, source}\|null, ladder_rungs:[{level:Int, text:String}], self_explanation_prompt:String?`** — frozen in §O. **WIRE NAME = `misconception_payload`** (object); the pre-existing scalar `misconception:String?` (grader code) is a SEPARATE coexisting field (D-G7-1 reconciliation, 2026-06-08). |
| `recordIn`/`upsertRubricCriterion` (§G/§H) | `POST /drill/grade` atomic txn | ONE `transaction{}`: `recordIn(tx)` + `attempts` insert + `upsertRubricCriterion(tx)`, faithful-gated, 409-if-not-ACTIVE |
| `VerificationStatus` (§I) + `HonestFloor` (§P) + `CitedClaim` (§Q) | `GET /verify/{kcId}/status`, `kc_verification_status` table (B8) | `{ verification_status, badge_text, claims:[CitedClaim], honest_floor:HonestFloor }` — `honest_floor` frozen in §P, `claims[]` element frozen in §Q |
| `LocateResult` (§J) | `verification_audit` (CHANGE 6) | `page`, `page_anchor_status (LIVE\|DEGRADED\|NONE)`, `span_start/end`, `fuzzy_distance` |
| `NonLlmResult` (§K) | `verification_audit` (CHANGE 6) | `nonllm_leg (SYMPY\|TEST_EXEC\|HUMAN_GOLD\|NONE)`, `nonllm_result`, `agree` |
| `FsrsForecastReply` (TS) | `GET /fsrs/forecast` | ADD `dueNow:number` (L3) → `{ dueNow, tomorrow, thisWeek, thisMonth }` |

**`FsrsForecastReply` TS delta (L3, frozen):** current `tutor-web/src/lib/fsrsClient.ts:26` is `{ tomorrow, thisWeek, thisMonth }` — ADD `dueNow: number` so `ForecastDotPlot` reads it directly and `App.tsx` drops its inline-type cast. `forecast` endpoint shape is otherwise LOCKED.

---

## R. Phase-3 autonomously-frozen inner shapes + server formulas (PROMOTED 2026-06-08, P1-4)

These were frozen DURING the unattended Phase-3 build (`build-review/2026-06-07-phase3-build-log.md`) because §2.2 left the inner shapes unspecified, but were never promoted into a canonical lock — leaving code and lock silently out of sync. They are promoted here verbatim from the shipped code so the lock is canonical-on-conflict. Each cross-links its build-log decision id.

- **`ApiMasteryDelta` (session/close `mastery_deltas[]` element) — FROZEN (build-log D-G4-7).** Code: `src/main/kotlin/jarvis/web/SessionPlacementExamRoutes.kt`.
  ```kotlin
  @Serializable
  data class ApiMasteryDelta(
      val kc_id: String,        // the KC the delta is for
      val attempts: Int,        // in-window attempts on this KC for this user
      val correct: Int,         // in-window correct attempts
      val ewma_after: Double,   // the KC's CURRENT ewma (post-window authoritative value)
      val phase: Phase,         // the KC's current resolved phase (serialized lowercase)
  )
  ```
  The outer `POST /session/close` reply keys (`session_id, narrative, cards_reviewed, kcs_moved_to_mastered, mastery_deltas`) are verbatim from master §2.2; this freezes the `mastery_deltas[]` INNER element. **Phase-6 consumers (`SessionWrapPane`) align to these inner names** (D-G4-7 downstream flag). Server recomputes deltas authoritatively (L1; the request carries none).
- **`ApiExamDatesReply` (GET `/me/exam-dates`) — FROZEN (build-log D-G4-7 + D-G4-6).** Code: `SessionPlacementExamRoutes.kt`.
  ```kotlin
  @Serializable data class ApiExamDatesReply(val exam_dates: List<ApiExamDate>)
  @Serializable data class ApiExamDate(val subject: String, val start_at: String)   // start_at = ISO
  @Serializable data class ApiExamDatePut(val subject: String, val start_at: String) // PUT body, ISO
  ```
  Route = GET/PUT `/api/v1/me/exam-dates` (CHANGE-9:97 canonical; route-table omits it). One row per `(user, subject)`; PUT upserts (no dup); inherits the `/me/` auth whitelist (D-G4-6). **Phase-6 consumers (`DayOf`, `SettingsMe` exam-date picker, H14) align to `{subject, start_at}`.**
- **`worked_example_first` SERVER formula — FROZEN (build-log D-G1-3, refined by P1-3b).** The non-nullable `QueueItem.worked_example_first` (§C) is a SERVER decision resolved in `LockedNextKcSelector` (`src/main/kotlin/jarvis/tutor/NextKcSelector.kt`), NOT a raw passthrough of the KC field:
  ```kotlin
  // worked-example-first for a novice (the SERVE phase = intro) OR when the KC authored it true.
  fun workedFirst(servePhase: Phase): Boolean = kc.worked_example_first || servePhase == Phase.intro
  ```
  The SERVE phase is the first remaining phase of `ScaffoldPlanner.planFor(kc, mastery)` at-or-above the learner's resolved phase (P1-3b; not the raw `KcCandidate.phase`). This interprets the data-model-lock "degrade to true for novices" as intro-phase. `mode`/`worked_example_first` are resolved ONLY here (single source of truth, no second downstream resolver).

---

## §NEW-L. `GET /api/v1/lesson/{kcId}` — faithful-gated first-encounter lesson (Task T7-1, 2026-06-09)

**Gate:** IDENTICAL to `GET /api/v1/teaching/{kcId}` — `VerifyAdmin.resolveStatus` (D8 staleness + D1 content-hash) returns `faithful` AND `ReportWrongQuery.hasOpenReportWrong` returns false. Non-faithful / disputed / unknown ⇒ 404 (OMIT, never a degraded payload). No session ⇒ 401.

**Route:** `GET /api/v1/lesson/{kcId}` (implemented in `QueueMasteryCalibrationRoutes.kt`, mounted via `installTutorRoutes`)

**Reply shape (`ApiLessonReply`):**
```kotlin
@Serializable
data class ApiLessonReply(
    val kcId: String,                      // KnowledgeConcept.id
    val kc_name_ro: String,                // KnowledgeConcept.name_ro
    val kc_name_en: String,                // KnowledgeConcept.name_en
    val concrete_question_ro: String?,     // KnowledgeConcept.stem_template or null
    val echo_source_ro: String?,           // KnowledgeConcept.source[0].quote or null
    val prediction_options: List<String>,  // 2-4 RO options; ALWAYS empty list (no option source on KC yet)
    val term_ro: String,                   // = kc_name_ro (primary Romanian label)
    val definition_ro: String?,            // ALWAYS null today (no dedicated KC definition field; honest-null, not a dup of explanation_ro)
    val explanation_ro: String?,           // KnowledgeConcept.explanation_ro
    val worked_example_ro: String?,        // KnowledgeConcept.worked_example_ro
    val provenance: DrillProvenanceDto,    // always {type="authored", hasBeenFaithfulChecked=true}
)
```

**Field-mapping notes:**
- `definition_ro` is ALWAYS `null` today — `KnowledgeConcept` has no dedicated `definition` field, and we do NOT duplicate `explanation_ro` into it (trust-first: never imply an authored definition that doesn't exist). Field kept nullable for forward-compat if a definition source is added.
- `prediction_options` is always `emptyList()` — honest-degraded; DO NOT fabricate. Future: populate from a KC options source when one exists.
- `provenance` is `{type="authored", hasBeenFaithfulChecked=true}` — the gate guarantees faithfulness before 200 is returned.

**Consistency:** uses the same `VerifyAdmin.resolveStatus` + `ReportWrongQuery.hasOpenReportWrong` helpers as `GET /teaching/{kcId}` (same faithful-gate contract, §I.2 + D-RF2).

> **DURABLE RULE (P1-4, applies to ALL of this lock + the data-model-lock + the master-plan freeze tables):** any forced deviation from a frozen lock — a renamed wire field, a newly-frozen inner shape, a changed server formula — **MUST amend the lock doc in the SAME commit** that lands the code, so code and lock never silently contradict (the failure mode P1-4 caught). If amending would weaken a Phase-2 trust guarantee, STOP and escalate to the owner instead of carving out a silent exception. Build-log decisions (`build-review/*-build-log.md`) are a journal, NOT a lock — promote the decision here (or cross-link its id) before claiming the contract frozen.
