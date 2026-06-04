package jarvis.tutor.verify

import jarvis.content.SourceOfRecord
import jarvis.content.Span

/**
 * §J / H7 — fuzzy-locate a claim's quote in the WHITESPACE-FOLDED source text, then translate
 * the matched range back to RAW global offsets (SourceOfRecord.slice uses raw offsets).
 *
 * The "folded→raw map" the master plan names is the function [locate], whose output
 * [LocateResult.span] carries RAW offsets — the fold is an internal index; only the raw span is
 * exposed so `SourceOfRecord.slice(rawSource, span)` round-trips.
 *
 * Test anchor: pa-kc-005's quote crosses a line break (`"…must be\n  mentioned."`) — it only
 * relocates after folding the `\n  ` to a single space.
 */
object LiveSourceLocator {

    /**
     * Whitespace-folds [rawSource], fuzzy-locates [quote], and maps the match back to RAW offsets.
     *
     * - LIVE     = located exactly (fuzzyDistance == 0 after fold) AND the source carries
     *              form-feed page breaks (so a real page anchor exists).
     * - DEGRADED = located (exact or near, within [FUZZY_MAX]) BUT the source has no form-feed,
     *              OR located only after a non-zero fuzzy distance. Still FAITHFUL-eligible (M-PAGE).
     * - NONE     = not located (no folded window within [FUZZY_MAX]).
     */
    fun locate(rawSource: String, quote: String): LocateResult {
        // Fold the quote the same way the source is folded.
        val foldedQuote = foldQuote(quote)
        if (foldedQuote.isEmpty()) {
            return LocateResult(span = null, page = null, pageAnchorStatus = PageAnchorStatus.NONE, fuzzyDistance = -1)
        }

        // Build the folded source + a parallel folded->raw offset map.
        val folded = StringBuilder(rawSource.length)
        // foldedToRaw[i] = the RAW index of folded char i; one extra trailing entry = rawSource.length.
        val foldedToRaw = ArrayList<Int>(rawSource.length + 1)
        var i = 0
        var pendingSpace = false        // a run of whitespace collapses to one space
        var pendingSpaceRawStart = -1   // raw index where the collapsed-whitespace run began
        var sawNonSpace = false         // suppress leading whitespace (mirrors trim())
        while (i < rawSource.length) {
            val c = rawSource[i]
            if (c.isWhitespace()) {
                if (!pendingSpace) {
                    pendingSpace = true
                    pendingSpaceRawStart = i
                }
            } else {
                if (pendingSpace && sawNonSpace) {
                    // Emit exactly one space for the collapsed run; anchor it at the run's start.
                    folded.append(' ')
                    foldedToRaw.add(pendingSpaceRawStart)
                }
                pendingSpace = false
                folded.append(c)
                foldedToRaw.add(i)
                sawNonSpace = true
            }
            i++
        }
        // Trailing whitespace is dropped (trim()). Sentinel maps folded.length -> rawSource.length.
        foldedToRaw.add(rawSource.length)

        val foldedSource = folded.toString()
        val hasFormFeed = rawSource.indexOf(SourceOfRecord.PAGE_BREAK) >= 0

        // Locate the folded quote in the folded source: exact first, then bounded fuzzy.
        val (foldedStart, distance) = locateFolded(foldedSource, foldedQuote)
        if (foldedStart < 0) {
            return LocateResult(span = null, page = null, pageAnchorStatus = PageAnchorStatus.NONE, fuzzyDistance = -1)
        }

        // Map the folded match window [foldedStart, foldedEnd) back to RAW offsets.
        val foldedEnd = foldedStart + foldedQuote.length
        val rawStart = foldedToRaw[foldedStart]
        // end is exclusive: raw index just past the last matched folded char.
        val rawEnd = foldedToRaw[foldedEnd]
        val span = Span(rawStart, rawEnd)

        val status = when {
            distance == 0 && hasFormFeed -> PageAnchorStatus.LIVE
            else -> PageAnchorStatus.DEGRADED
        }
        val page = if (hasFormFeed) SourceOfRecord.pageOf(rawSource, rawStart) else null

        return LocateResult(span = span, page = page, pageAnchorStatus = status, fuzzyDistance = distance)
    }

    /** Max acceptable Levenshtein distance (on the folded window) before we declare NONE. */
    const val FUZZY_MAX: Int = 8

    /**
     * Fold a quote for matching: collapse every whitespace run (incl. the authored `\n  `) to a
     * single space and trim. Mirrors the source fold so `…must be\n  mentioned.` matches
     * `…must be mentioned.` in the folded source.
     */
    private fun foldQuote(q: String): String = q.replace(Regex("\\s+"), " ").trim()

    /**
     * Find [foldedQuote] in [foldedSource]. Returns (startIndex, distance):
     *  - exact substring ⇒ (idx, 0);
     *  - else the best fixed-width window (|quote| long) by Levenshtein, if ≤ [FUZZY_MAX];
     *  - else (-1, -1).
     */
    private fun locateFolded(foldedSource: String, foldedQuote: String): Pair<Int, Int> {
        val exact = foldedSource.indexOf(foldedQuote)
        if (exact >= 0) return exact to 0
        if (foldedQuote.length > foldedSource.length) return -1 to -1

        var bestIdx = -1
        var bestDist = Int.MAX_VALUE
        val qLen = foldedQuote.length
        // Slide a |quote|-wide window; cheap enough for lecture-sized sources.
        var start = 0
        val last = foldedSource.length - qLen
        while (start <= last) {
            val window = foldedSource.substring(start, start + qLen)
            val d = levenshtein(window, foldedQuote, bestDist)
            if (d < bestDist) {
                bestDist = d
                bestIdx = start
                if (bestDist == 0) break
            }
            start++
        }
        return if (bestIdx >= 0 && bestDist <= FUZZY_MAX) bestIdx to bestDist else -1 to -1
    }

    /**
     * Levenshtein distance with an early-exit ceiling: if every cell in a row exceeds [ceiling],
     * abandon (returns a value > ceiling). Two-row DP, O(|a|·|b|) worst case.
     */
    fun levenshtein(a: String, b: String, ceiling: Int = Int.MAX_VALUE): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            var rowMin = curr[0]
            val ca = a[i - 1]
            for (j in 1..b.length) {
                val cost = if (ca == b[j - 1]) 0 else 1
                curr[j] = minOf(
                    prev[j] + 1,        // deletion
                    curr[j - 1] + 1,    // insertion
                    prev[j - 1] + cost, // substitution
                )
                if (curr[j] < rowMin) rowMin = curr[j]
            }
            if (rowMin > ceiling) return ceiling + 1
            val tmp = prev; prev = curr; curr = tmp
        }
        return prev[b.length]
    }
}

/**
 * §J — output of [LiveSourceLocator.locate]. [span] is in RAW offsets (ready to store on
 * `SourceRef.span`). [pageAnchorStatus] distinguishes LIVE / DEGRADED / NONE (M-PAGE);
 * DEGRADED (offset+fuzzy pass, no form-feed) is still FAITHFUL-eligible.
 */
data class LocateResult(
    /** RAW offsets; null = not located. `SourceOfRecord.slice(rawSource, span)` round-trips. */
    val span: Span?,
    /** `SourceOfRecord.pageOf(rawSource, span.start)`; null when the source has no form-feed. */
    val page: Int?,
    val pageAnchorStatus: PageAnchorStatus,
    /** Levenshtein on the folded match (guard threshold). 0 = exact-after-fold; -1 = not located. */
    val fuzzyDistance: Int,
)

/** §J — page-anchor quality of a located quote. Feeds `verification_audit.page_anchor_status`. */
enum class PageAnchorStatus { LIVE, DEGRADED, NONE }
