# Interface Signatures Lock — frozen Kotlin/TS contracts (cross-phase)

**Status:** FROZEN reference. NO prose, NO code-to-build — this is the shape contract the phase plans build against.
**Date:** 2026-06-02 · **HEAD:** `b970585` (branch `door-compare`)
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
    DISAGREE_OR_ROUNDTRIP_FAIL_OR_THREW, // -> failed/uncertain
    REPORT_WRONG,                      // faithful -> pending
}
```

- **Five literals** match `/verify/{kcId}/status` + CHANGE-3's four authored literals `{unverified, pending, faithful, uncertain}` PLUS `failed` (the §2.5 FAILED state; never authored in YAML, only reached at runtime).
- **Wire consistency:** `verification_status` appears in `/queue/today`, `/mastery`, `/calibration`(no), `/drill/grade`, `/fsrs/due`, `/verify/{kcId}/status`, `mock-exam` kc_results, `QueueItem`, `KcCandidate`. ONE enum, every site serializes its lowercase `name`.
- **YAML caveat:** the four-literal subset is the authored seed (CHANGE 3 / `KnowledgeConcept.verification_status: String`); the runtime store can ALSO hold `failed`. The validator's enum check (H11) accepts the four authored literals only; `failed` is runtime-only.
- **Invariant (§2.5, LOCKED):** no path reaches `faithful` without BOTH a non-LLM-leg pass AND families-agree.

### I.1 `kc_verification_status.content_hash` — the D8 staleness fingerprint (FROZEN)

`kc_verification_status` is keyed on `kc_id` ALONE; an audit verdict therefore outlives the content it certified. **D8** adds a content fingerprint so an edited lecture cannot keep a stale `faithful` badge.

```kotlin
// jarvis.tutor.KcVerificationStatusTable (Phase1Tables.kt) — ADD:
val contentHash = varchar("content_hash", 16).nullable()   // sha256_8 of the KC's audited content; NULL ⇒ fail-closed

// jarvis.content.ContentReconcile — ADD (deterministic, reorder-stable):
fun kcContentHash(kc: KnowledgeConcept): String                 // == kcContentHashOf(claimsFor(kc))
fun kcContentHashOf(claims: List<VerificationClaim>): String    // sha256_8 over claimId-sorted (kind|content|doc|page|span) — audit-side entry
```

- **Column (FROZEN):** `content_hash: varchar(16) NULLABLE` on `kc_verification_status`. Additive (live table = 0 rows) ⇒ bare `createMissingTablesAndColumns` ADD COLUMN, no backfill (any pre-existing row reads NULL = the correct fail-closed default). Idempotent on a second migrate.
- **The hash (FROZEN):** `ContentReconcile.kcContentHash(kc)` = `sha256_8` over the KC's audited claim set (the SAME `claimsFor(kc)` the audit consumes), folding each claim's `kind|content|doc|page|span` over a `claimId`-sorted order. Reorder-stable (shuffling `grader_rules`/`source` does not change it); changing any audited claim text OR moving a cited span DOES change it. The runner uses the `kcContentHashOf(claims)` form so the fingerprint reflects exactly what was audited.
- **Writer (FROZEN):** `VerificationRunner.audit` computes the per-KC hash once over the batch and stamps it onto the `kc_verification_status` upsert alongside `status`/`last_audit_run_id`.
- **Serve gate (FROZEN):** `VerifyAdmin.resolveStatus(db, kcId, kc)` (`GET /verify/{kcId}/status`) returns `faithful` ONLY when `row.content_hash != null && kc != null && row.content_hash == kcContentHash(kc)`. A mismatch (lecture edited after audit), a NULL hash (legacy/partial row), or a missing live `kc` falls CLOSED to `unverified` ⇒ `HonestFloor.UNVERIFIED` (§P) ⇒ badge `"unverified"`. The gate applies ONLY to `faithful`; non-faithful statuses pass through verbatim. Staleness / partial-sync / PC-off therefore fail-closed to honest "unverified", never fail-stale to a lying "faithful".

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
enum class ClaimKind { DEFINITION, INVARIANT, GRADER_RULE, MISCONCEPTION_REFUTATION, STEM }

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
  - `misconception: MisconceptionPayload?` — the inline payload above; `null` when no misconception fired for the graded answer.
  - `ladder_rungs: List<LadderRung>` — the ordered L0–L4 rung array (`[{level, text}]`); may be empty for a fully-correct answer that needs no ladder.
  - `self_explanation_prompt: String?` — the **drill-level** self-explanation prompt (CHANGE-3 `KnowledgeConcept.self_explanation_prompt` / the active drill's prompt), read by `DrillStack`'s self-explanation rung (Phase 5; H16 — WIRED, not deferred). Distinct from `MisconceptionPayload.self_explanation_prompt` (which is the per-misconception prompt); both can be present.
- **DECIDED #1 — ONE misconception serve shape (inline only):** the misconception serves INLINE on `/drill/grade` ONLY. `GET /api/v1/misconception/{id}` (master §2.2, line 114) is **NOT a live skeleton endpoint** — it has no consumer (the ribbon already has the payload from the grade reply). It is marked **DROPPED/DEFERRED** (no orphan no-consumer endpoint built). If a read-on-demand fetch is ever needed (e.g. a standalone misconception browser), it is a purely additive endpoint returning this same `MisconceptionPayload` shape — no breaking change.
- **Wire literal:** all field names serialize snake_case exactly as written (`misconception`, `ladder_rungs`, `self_explanation_prompt`, `figure_spec`). The TS client maps `figure_spec → figureSpec` at the fetch boundary (the only casing-bridge site; backend snake_case is the wire source of truth).
- **Consumers (master §3 Phase 5):** `MisconceptionRibbon` (0f) reads `misconception` (incl. `figure_spec`); `FeedbackLadder` (in `DrillStack`) reads `ladder_rungs`; `DrillStack`'s self-explanation rung reads `self_explanation_prompt`. One reply DTO, three Phase-5 consumers — shapes frozen so no late codemod.

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
| grade reply fields (`Phase`, `NextPhaseAction`, `VerificationStatus`, `MisconceptionPayload` §O, `LadderRung` §O) | `POST /drill/grade` | ADD `verification_status, kc_quarantined:bool, phase, next_phase_action:"advance\|hold\|remediate", cross_checked:bool` **+ (gap C, master §2.2) `misconception:{id, refutation, figure_spec, self_explanation_prompt}\|null, ladder_rungs:[{level:Int, text:String}], self_explanation_prompt:String?`** — frozen in §O |
| `recordIn`/`upsertRubricCriterion` (§G/§H) | `POST /drill/grade` atomic txn | ONE `transaction{}`: `recordIn(tx)` + `attempts` insert + `upsertRubricCriterion(tx)`, faithful-gated, 409-if-not-ACTIVE |
| `VerificationStatus` (§I) + `HonestFloor` (§P) + `CitedClaim` (§Q) | `GET /verify/{kcId}/status`, `kc_verification_status` table (B8) | `{ verification_status, badge_text, claims:[CitedClaim], honest_floor:HonestFloor }` — `honest_floor` frozen in §P, `claims[]` element frozen in §Q |
| `LocateResult` (§J) | `verification_audit` (CHANGE 6) | `page`, `page_anchor_status (LIVE\|DEGRADED\|NONE)`, `span_start/end`, `fuzzy_distance` |
| `NonLlmResult` (§K) | `verification_audit` (CHANGE 6) | `nonllm_leg (SYMPY\|TEST_EXEC\|HUMAN_GOLD\|NONE)`, `nonllm_result`, `agree` |
| `FsrsForecastReply` (TS) | `GET /fsrs/forecast` | ADD `dueNow:number` (L3) → `{ dueNow, tomorrow, thisWeek, thisMonth }` |

**`FsrsForecastReply` TS delta (L3, frozen):** current `tutor-web/src/lib/fsrsClient.ts:26` is `{ tomorrow, thisWeek, thisMonth }` — ADD `dueNow: number` so `ForecastDotPlot` reads it directly and `App.tsx` drops its inline-type cast. `forecast` endpoint shape is otherwise LOCKED.
