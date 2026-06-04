package jarvis.tutor.verify

import jarvis.content.SourceRef
import kotlinx.serialization.Serializable

/**
 * Phase-2 trust-net leaf types. FROZEN by interface-signatures-lock §K (`VerificationClaim`,
 * `ClaimKind`, `NonLlmLegKind`, `NonLlmResult`, `NonLlmLeg`) and §L (`LegFamily`).
 * Match names/params/return types EXACTLY — a builder mismatch breaks downstream batches.
 */

/**
 * §K — ONE auditable claim extracted from a KC by curate-tutor Stage-9: the unit the
 * verification engine audits and `verification_audit` records (one row per claim per run).
 * Consumed by `NonLlmLeg.check`, `TwoFamilyDeriver`, `SpanClaimRoundTrip`,
 * `VerificationRunner.audit`, and `CitationGuard.attach` (§Q). NOT a wire type.
 * Fields 1:1 with the `verification_audit` columns (master-plan CHANGE 6) + M-CLAIM.
 */
@Serializable
data class VerificationClaim(
    /** `"{kcId}:{kind}:{sha256_8(content)}"` — content-hash, reorder-stable (M-CLAIM);
     *  == verification_audit.claim_id (varchar(96)). NOT a positional ordinal. */
    val claimId: String,
    /** verification_audit.kc_id; matches KnowledgeConcept.id. */
    val kcId: String,
    /** verification_audit.subject. */
    val subject: String,
    /** verification_audit.claim_kind. */
    val kind: ClaimKind,
    /** The asserted text the legs re-derive (definition body / invariant / grader rule /
     *  refutation / stem). What `sha256_8` hashes for claimId. */
    val content: String,
    /** The KC's precise invariant (CHANGE-3 `KnowledgeConcept.invariant`), when kind needs it
     *  (INVARIANT / GRADER_RULE); null for purely definitional claims (⇒ DEFINITIONAL_NO_GOLD_SPAN). */
    val invariant: String?,
    /** The verbatim citation backing this claim. `source.span` (RAW offsets) is what
     *  `LiveSourceLocator.locate` re-locates + `SpanClaimRoundTrip` round-trips;
     *  null span ⇒ no gold span ⇒ definitional ⇒ UNCERTAIN floor (§2.5). */
    val source: SourceRef?,
)

/** §K — what a claim asserts. Wire literal == name (UPPER). == verification_audit.claim_kind. */
enum class ClaimKind { DEFINITION, INVARIANT, GRADER_RULE, MISCONCEPTION_REFUTATION, STEM }

/**
 * §K — domain of the non-LLM verification leg. Domain-scoped: PA ⇒ SYMPY;
 * PS/SO-RC/POO/ALO ⇒ NONE (no runnable checker yet ⇒ UNCERTAIN floor).
 * HUMAN_GOLD/TEST_EXEC are system-derived from the PDF, NEVER routed through Alex.
 */
enum class NonLlmLegKind { SYMPY, TEST_EXEC, HUMAN_GOLD, NONE }

/**
 * §K — the non-LLM verification leg. A `fun interface` so a checker can be supplied
 * as a lambda or a class. Domain-scoped by the impl (SymPyLeg / NoneLeg, Batch 2).
 */
fun interface NonLlmLeg {
    fun check(claim: VerificationClaim): NonLlmResult
}

/**
 * §K — output of a non-LLM leg run.
 * `kind == NONE ⇒ ran == false ⇒ pass ignored ⇒ UNCERTAIN floor` (§2.5 invariant, FAIL-LOUD H5):
 * never-ran is NOT disagreed.
 */
data class NonLlmResult(
    val kind: NonLlmLegKind,
    /** false iff kind == NONE (FAIL-LOUD: never-ran ≠ disagreed). */
    val ran: Boolean,
    /** Meaningful only when ran == true. */
    val pass: Boolean,
    /** e.g. SymPy simplification, or null. */
    val detail: String?,
)

/**
 * §L — each verification leg is tagged with its CONFIGURED family. Collapse = two legs
 * share a family (NOT a model-string compare — both clients substitute a non-blank id).
 * Family A = `RelayLlm` (RELAY), family B = `OpenRouterChatLlm :free` (OPENROUTER) on the VPS
 * request path, or `NliEntailmentLlm` (NLI) on the PC-side offline audit path (D6/D-R12 — a LOCAL
 * NLI entailment model, a TRULY-independent family that is not network-throttled and not a 2nd
 * :free LLM). non-LLM leg = `NONLLM`. Used by `TwoFamilyDeriver` to detect FAMILY_COLLAPSE.
 *
 * NOTE: not a frozen wire literal (it is carried only into the human-readable audit detail string),
 * so adding NLI does not break the §L lock; but the exhaustive-shape unit test in
 * `VerificationTypesTest` is updated in lockstep.
 */
enum class LegFamily { RELAY, OPENROUTER, NONLLM, NLI }
