package jarvis.tutor.verify

import jarvis.content.SourceOfRecord
import jarvis.content.Span

/**
 * Batch-2 leg 3 — the deterministic span↔claim round-trip leg (a DIFFERENT family from the two
 * LLM legs: this one is pure text, no model).
 *
 * For a claim it re-locates `claim.source.quote` in the LIVE [rawSource] via
 * [LiveSourceLocator.locate], slices the resolved RAW span back out via [SourceOfRecord.slice],
 * and confirms the slice **folds-equal** to the quote. A faithful quote round-trips; a mutated /
 * absent quote does not. Fully deterministic — no LLM, no network — so it anchors the acceptance
 * proof (R1: the net must REJECT a corrupted KC).
 *
 * The result type ([RoundTripResult]) is NOT frozen — designed cleanly so Batch-3
 * `VerificationRunner` folds `pass` into the §I `AuditOutcome` (a round-trip fail contributes to
 * DISAGREE_OR_ROUNDTRIP_FAIL_OR_THREW) and carries `pageAnchorStatus`/`span` to `verification_audit`.
 *
 * @param rawSource the LIVE source-of-record text (raw `pdftotext` output, form-feeds preserved).
 */
class SpanClaimRoundTrip(private val rawSource: String) {

    /**
     * Re-locate + round-trip the claim's quote. `pass` is true iff the quote located AND the RAW
     * slice of the resolved span folds-equal to the quote. A claim with no source (or no quote)
     * cannot round-trip ⇒ `pass=false` (FAIL-LOUD, never a silent pass).
     */
    fun check(claim: VerificationClaim): RoundTripResult {
        val quote = claim.source?.quote
        if (quote.isNullOrBlank()) {
            return RoundTripResult(pass = false, pageAnchorStatus = PageAnchorStatus.NONE, span = null)
        }

        val located = LiveSourceLocator.locate(rawSource, quote)
        val span = located.span
            ?: return RoundTripResult(pass = false, pageAnchorStatus = PageAnchorStatus.NONE, span = null)

        val slice = SourceOfRecord.slice(rawSource, span)
            ?: return RoundTripResult(pass = false, pageAnchorStatus = located.pageAnchorStatus, span = null)

        // Deterministic round-trip: the relocated RAW slice must fold-equal the original quote.
        val pass = fold(slice) == fold(quote)
        return RoundTripResult(
            pass = pass,
            pageAnchorStatus = if (pass) located.pageAnchorStatus else PageAnchorStatus.NONE,
            span = if (pass) span else null,
        )
    }

    private companion object {
        fun fold(s: String): String = s.replace(Regex("\\s+"), " ").trim()
    }
}

/**
 * Result of [SpanClaimRoundTrip.check]. NOT frozen — Batch-3 `VerificationRunner` consumes [pass]
 * for the §I `AuditOutcome` and carries [pageAnchorStatus]/[span] to the `verification_audit` row.
 */
data class RoundTripResult(
    /** True iff the quote re-located AND its RAW slice folds-equal to the quote. */
    val pass: Boolean,
    /** The page-anchor quality of the located span (LIVE only when [pass] and the source has
     *  form-feeds); NONE when the quote did not round-trip. */
    val pageAnchorStatus: PageAnchorStatus,
    /** The resolved RAW span when [pass]; null otherwise. */
    val span: Span?,
)
