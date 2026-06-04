package jarvis.tutor.verify

/**
 * P2-RULE1 — a cheap, offline confidence score that the raw `pdftotext` extraction is
 * legible (not mojibake / not a binary-garbled blob) BEFORE any claim is audited against it.
 *
 * This is a GROUNDEDNESS pre-gate, NOT a correctness check: a high score only means "the bytes
 * read like text", which is the precondition for `LiveSourceLocator` / `validateContent` to be
 * able to re-locate quotes at all. M-CSCHEMA: an empty extraction (`extract() == ""`) is an ERROR,
 * not a 0-confidence pass-through.
 */
object ExtractionConfidence {

    /** Below this score the extraction is rejected as GARBLED_EXTRACTION. */
    const val MIN_CONFIDENCE: Double = 0.80

    /** The reason code surfaced to the curate-tutor pipeline / validator when score < MIN_CONFIDENCE. */
    const val GARBLED_EXTRACTION: String = "GARBLED_EXTRACTION"

    /** Unicode replacement character — the canonical mojibake / bad-decode marker. */
    private const val REPLACEMENT_CHAR: Char = '�'

    /**
     * 0.0..1.0 legibility score. 1.0 = clean text; near-0.0 = mojibake / binary garbage.
     *
     * Heuristic (each ratio subtracts from a full score of 1.0):
     *  - replacement-char ratio: fraction of chars that are U+FFFD (bad decode). Weighted heaviest.
     *  - non-printable ratio: fraction of control chars that are NOT ordinary whitespace
     *    (tab/newline/CR/form-feed are page structure, not garble).
     *  - broken-word ratio: fraction of whitespace-split tokens that are "broken" — a single
     *    non-alphanumeric symbol, or a run with no letters/digits at all in a doc that otherwise
     *    has words. Catches the `s t d : : c o u t` letter-spaced shredding a bad extraction
     *    produces, without penalising legitimate math symbols too hard.
     *
     * Empty / blank input ⇒ 0.0 (M-CSCHEMA: an empty extraction is the lowest possible score,
     * and `reason()` maps it to GARBLED_EXTRACTION).
     */
    fun score(rawText: String): Double {
        if (rawText.isBlank()) return 0.0
        val n = rawText.length

        var replacement = 0
        var nonPrintable = 0
        for (c in rawText) {
            if (c == REPLACEMENT_CHAR) replacement++
            // ISO control chars that are NOT structural whitespace count as non-printable garble.
            if (Character.isISOControl(c) && c != '\t' && c != '\n' && c != '\r' && c != '') {
                nonPrintable++
            }
        }
        val replacementRatio = replacement.toDouble() / n
        val nonPrintableRatio = nonPrintable.toDouble() / n

        // Broken-word heuristic over whitespace-split tokens.
        val tokens = rawText.split(Regex("\\s+")).filter { it.isNotBlank() }
        val brokenRatio =
            if (tokens.isEmpty()) {
                1.0
            } else {
                val broken = tokens.count { tok ->
                    val hasAlnum = tok.any { it.isLetterOrDigit() }
                    // single non-alnum symbol token, or a token with zero letters/digits
                    (tok.length == 1 && !tok[0].isLetterOrDigit()) || !hasAlnum
                }
                broken.toDouble() / tokens.size
            }

        // Weighted penalty. Replacement chars are the strongest garble signal.
        val penalty = (replacementRatio * 2.0) + (nonPrintableRatio * 1.5) + (brokenRatio * 0.6)
        return (1.0 - penalty).coerceIn(0.0, 1.0)
    }

    /**
     * Returns the GARBLED_EXTRACTION reason string when the extraction is too garbled to trust
     * (score < [MIN_CONFIDENCE], which includes empty/blank input), or null when it passes.
     * The caller treats a non-null return as an ERROR (M-CSCHEMA): it must NOT proceed to audit
     * claims against an illegible source.
     */
    fun reason(rawText: String): String? =
        if (score(rawText) < MIN_CONFIDENCE) GARBLED_EXTRACTION else null
}
