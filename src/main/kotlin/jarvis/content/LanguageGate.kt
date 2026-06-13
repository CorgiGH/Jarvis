package jarvis.content

/**
 * Plan 4b Task 6 — Romanian-language field gate (spec §0.9E / INV-8.4 / audit L7).
 *
 * Runs at ADMISSION/SYNC (ContentValidator.validate → ContentReconcile.reconcile), never at
 * serve time (serve route is Lane B — untouched here, R-4b-Q6).
 *
 * The THREE legs, per §0.9E (checked in order — first match wins):
 *
 *  (1) **EN-vocabulary leg** (any string length, checked FIRST):
 *      tokenize the normalized string; lowercase each token and strip the pinned RO inflection
 *      suffixes; any resulting STEM in [EN_VOCAB] → FAIL regardless of other legs.
 *      Required (plan-fix F1, PM-ratified 2026-06-12): the ruling's own INV-8.2 flagship seed
 *      ("skeletonul rămâne fix") has EN-stopword ratio 0 and may have diacritics — structurally
 *      invisible to the other two legs. The EN-vocab leg is what makes it catchable AT ALL.
 *
 *  (2) **Diacritic leg** (strings ≥ [SHORT_STRING_WORDS] words): any ăâîșțĂÂÎȘȚ anywhere → PASS.
 *      Short strings are exempt from this leg only (the stopword and EN-vocab legs still apply).
 *
 *  (3) **EN-stopword ratio leg** (strings ≥ [SHORT_STRING_WORDS] words):
 *      EN-stopword count / total token count ≥ [STOPWORD_RATIO_THRESHOLD] → FAIL.
 *      Short strings are NOT exempt from this leg ("the heap" is still catchable).
 *
 * Normalization before any leg:
 *  - Strip «…» and ‹…› guillemet-quoted spans (§8.1 — quoted source spans are intentionally EN).
 *  - Strip tokens matching the [glossaryTerms] set (seeded from GlossaryTermsTable, currently empty).
 *  - Strip tokens matching the [exempt] per-call exempt set.
 *  - Strip code-ish tokens: numbers, identifiers containing digits, formulas with operators.
 *
 * TS-side mirror: `tutor-web/src/e2e/helpers/roHeuristic.ts` implements the SAME logic with the
 * SAME pinned constants — cite this file + §0.9E in that file's header. A divergence is a bug.
 * A cross-language schema-hash CI test is carried to Plan 5 (Task 12 Step 6).
 */
object LanguageGate {

    /** Bump this whenever any pinned constant or normalization rule changes. Stored in LanguageCheckTable. */
    const val VERSION = "1"

    // ── Pinned constants (§0.9E) — change only with a plan/PM decision ──────────────────────────

    /**
     * Minimum word count for the diacritic leg to apply. Strings shorter than this are
     * exempt from the diacritic leg only (EN-vocab and stopword legs still apply).
     */
    const val SHORT_STRING_WORDS = 3

    /**
     * EN-stopword ratio threshold. A string of ≥ [SHORT_STRING_WORDS] words with
     * (EN-stopword count / total token count) ≥ this value fails the stopword leg.
     * Calibrated in Task 6 Step 3 over the real corpus (zero false reds on admitted content).
     */
    const val STOPWORD_RATIO_THRESHOLD = 0.18

    /**
     * Pinned RO inflection suffixes to strip before the EN-vocab stem lookup.
     * Order matters: try longer suffixes first to avoid partial-strip bugs.
     * E.g. "skeletonul" → strip "-ul" → "skeleton" ∈ EN_VOCAB → FAIL.
     */
    val RO_SUFFIXES: List<String> = listOf(
        "urile", "ului", "ilor", "ele", "uri", "ii", "ul",
    )

    /**
     * Pinned EN loanword / vocabulary list (§0.9G `enWords` chrome class DEFINITELY class +
     * technical loanwords that appear as EN stems in RO text). Tuned ONCE in Task 6 calibration.
     * Glossary terms + per-call exempt set are stripped BEFORE this leg (normalization), so
     * a legitimately-seeded glossary term never reds.
     *
     * Lower-case stems (post-suffix-strip). Order does not matter (set lookup).
     */
    val EN_VOCAB: Set<String> = setOf(
        // Data structure / algorithm loanwords
        "skeleton", "heap", "array", "stack", "queue", "loop", "string", "tree", "graph",
        "node", "edge", "pointer", "buffer", "cache", "token", "hash", "frame", "slot",
        "batch", "thread", "chunk", "step", "index",
        // UI chrome words (§0.9G DEFINITELY/MAYBE class)
        "loading", "reset", "share", "save", "play", "back", "forward",
        // Common short EN words that appear as bare loanwords in RO drill text
        "input", "output", "sort", "merge", "split", "swap",
    )

    /**
     * Pinned EN stopword list (~40 words, §0.9E). Used for the stopword-ratio leg.
     * These are the MOST FREQUENT function words in English; their presence in a RO field
     * at high density is a strong signal the text is actually English.
     */
    val EN_STOPWORDS: Set<String> = setOf(
        "the", "and", "of", "to", "is", "are", "in", "a", "an", "with", "for", "this",
        "that", "it", "on", "at", "by", "as", "or", "be", "was", "were", "has", "have",
        "not", "from", "but", "they", "we", "he", "she", "you", "i", "its", "their",
        "which", "can", "will", "if", "then", "than",
    )

    // ── Regex for normalization ──────────────────────────────────────────────────────────────────

    /** Strip guillemet-quoted spans («…» and ‹…›). */
    private val GUILLEMET_RE = Regex("«[^»]*»|‹[^›]*›")

    /** Code-ish tokens: numbers, identifiers with digits, simple formulas. */
    private val CODE_TOKEN_RE = Regex(
        "[0-9]+|[a-zA-Z][a-zA-Z0-9_]*[0-9][a-zA-Z0-9_]*|O\\([^)]*\\)|[a-zA-Z]+[_/=+<>*]+[a-zA-Z0-9]*",
    )

    // ── Public API ──────────────────────────────────────────────────────────────────────────────

    /**
     * Check a single learner-visible RO field value.
     *
     * Returns a list of [ValidationIssue]s (empty = pass). Error severity = field contains
     * non-Romanian content; warning severity is not currently used by this gate.
     *
     * @param text        the raw field text to check.
     * @param fieldName   the field name (for error detail, e.g. "name_ro", "reveal_step_text").
     * @param kcId        the KC id (for error detail).
     * @param glossaryTerms stems of glossary-approved terms to strip before the EN-vocab leg.
     * @param exempt      per-call exempt stems (strips before EN-vocab leg, same as glossary).
     */
    fun checkField(
        text: String,
        fieldName: String,
        kcId: String,
        glossaryTerms: Set<String> = emptySet(),
        exempt: Set<String> = emptySet(),
    ): List<ValidationIssue> {
        // Build the combined exempt stems (lowercased)
        val exemptStems = (glossaryTerms + exempt).map { it.lowercase() }.toSet()

        // Step 1: strip guillemet-quoted spans + code-ish tokens from raw text
        val normalized = normalize(text)
        if (normalized.isBlank()) return emptyList()

        // Step 2: tokenize (splits on non-alphanumeric, including hyphens)
        val allTokens = tokenize(normalized)
        if (allTokens.isEmpty()) return emptyList()

        // Step 3: filter out exempt/glossary stems from the token list BEFORE any leg
        val tokens = allTokens.filter { token ->
            val stem = stemOf(token)
            token !in exemptStems && stem !in exemptStems
        }
        // If all tokens are exempt, pass
        if (tokens.isEmpty()) return emptyList()

        // Leg (1): EN-vocabulary leg (any length, checked FIRST — plan-fix F1)
        val enVocabViolation = checkEnVocabLeg(tokens, kcId, fieldName)
        if (enVocabViolation != null) return listOf(enVocabViolation)

        // Legs (2) and (3) apply to multi-word strings
        if (tokens.size < SHORT_STRING_WORDS) return emptyList()

        // Leg (2): diacritic presence leg — check diacritics in the NORMALIZED text
        // (so guillemet-quoted EN spans don't contribute diacritics from stripped spans)
        val hasDiacriticsInNormalized = normalized.any { it in "ăâîșțĂÂÎȘȚ" }
        if (hasDiacriticsInNormalized) return emptyList() // Leg (2): diacritics → PASS

        // Leg (3): EN-stopword ratio
        val stopwordViolation = checkStopwordRatioLeg(tokens, kcId, fieldName)
        if (stopwordViolation != null) return listOf(stopwordViolation)

        return emptyList()
    }

    /**
     * Check all learner-facing RO fields of every KC in a [LoadedSubject].
     *
     * Wired into [ContentValidator.validate] (audit L7 hole closure).
     * Returns [ValidationIssue] errors for any field that contains non-Romanian content.
     *
     * The covered fields are (per §0.9F):
     *  - name_ro, explanation_ro, worked_example_ro
     *  - every misconception label_ro
     *  - every beats.ro.* text field:
     *    predict.prompt, predict.option texts+callbacks,
     *    attempt.statement, attempt.choices text+feedback, attempt.feedback_correct,
     *    attempt.skeleton_rows labels, attempt.trace_steps callouts,
     *    reveal.steps text+callout,
     *    name.definition, name.invariant_statement, name.why_matters,
     *    check.item_stem, check.choices text+feedback
     */
    fun checkRomanianFields(
        sub: LoadedSubject,
        glossaryTerms: Set<String> = emptySet(),
        exempt: Set<String> = emptySet(),
    ): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()

        for (kc in sub.kcs) {
            fun check(text: String?, field: String) {
                if (text.isNullOrBlank()) return
                val fieldIssues = checkField(text, field, kc.id, glossaryTerms, exempt)
                issues += fieldIssues.map { it.copy(subject = sub.subject) }
            }

            check(kc.name_ro, "name_ro")
            check(kc.explanation_ro, "explanation_ro")
            check(kc.worked_example_ro, "worked_example_ro")

            // Beat content — only the "ro" language key (§0.9F "every beats.ro.*")
            val roBeats = kc.beats["ro"]
            if (roBeats != null) {
                // Predict beat
                roBeats.predict?.let { p ->
                    check(p.prompt, "beats.ro.predict.prompt")
                    for ((i, opt) in p.options.withIndex()) {
                        check(opt.text, "beats.ro.predict.options[$i].text")
                        check(opt.callback, "beats.ro.predict.options[$i].callback")
                    }
                }

                // Attempt beat
                roBeats.attempt?.let { a ->
                    check(a.statement, "beats.ro.attempt.statement")
                    check(a.feedback_correct, "beats.ro.attempt.feedback_correct")
                    for ((i, choice) in a.choices.withIndex()) {
                        check(choice.text, "beats.ro.attempt.choices[$i].text")
                        check(choice.feedback, "beats.ro.attempt.choices[$i].feedback")
                    }
                    for ((i, row) in a.skeleton_rows.withIndex()) {
                        check(row.label, "beats.ro.attempt.skeleton_rows[$i].label")
                    }
                    for ((i, step) in a.trace_steps.withIndex()) {
                        check(step.callout, "beats.ro.attempt.trace_steps[$i].callout")
                    }
                }

                // Reveal beat
                roBeats.reveal?.let { r ->
                    for ((i, step) in r.steps.withIndex()) {
                        check(step.text, "beats.ro.reveal.steps[$i].text")
                        check(step.callout, "beats.ro.reveal.steps[$i].callout")
                    }
                }

                // Name beat
                roBeats.name?.let { n ->
                    check(n.definition, "beats.ro.name.definition")
                    check(n.invariant_statement, "beats.ro.name.invariant_statement")
                    check(n.why_matters, "beats.ro.name.why_matters")
                }

                // Check beat
                roBeats.check?.let { c ->
                    check(c.item_stem, "beats.ro.check.item_stem")
                    for ((i, choice) in c.choices.withIndex()) {
                        check(choice.text, "beats.ro.check.choices[$i].text")
                        check(choice.feedback, "beats.ro.check.choices[$i].feedback")
                    }
                }
            }
        }

        // Misconception label_ro fields
        for (m in sub.misconceptions) {
            if (m.label_ro.isNotBlank()) {
                val fieldIssues = checkField(m.label_ro, "misconception.label_ro", m.id, glossaryTerms, exempt)
                issues += fieldIssues.map { it.copy(subject = sub.subject) }
            }
        }

        return issues
    }

    // ── Private helpers ──────────────────────────────────────────────────────────────────────────

    /**
     * Normalize the input string before running the legs:
     *  1. Strip «…»/‹…› guillemet-quoted spans.
     *  2. Strip code-ish tokens (numbers, identifiers with digits, formulas).
     *
     * Exempt/glossary term stripping happens AFTER tokenization in [checkField] (not here),
     * because the tokenizer splits on hyphens and other punctuation — "skeleton-ul" becomes
     * ["skeleton", "ul"] after tokenize(), so the stem-based exempt check must run post-tokenize.
     */
    private fun normalize(text: String): String {
        var s = GUILLEMET_RE.replace(text, " ")
        // Strip code-ish tokens
        s = CODE_TOKEN_RE.replace(s, " ")
        // Collapse whitespace
        return s.replace(Regex("\\s+"), " ").trim()
    }

    /**
     * Tokenize by splitting on non-alphanumeric characters (preserving diacritics).
     * Returns lower-case tokens with length ≥ 1.
     */
    private fun tokenize(s: String): List<String> =
        s.split(Regex("[^\\p{L}\\p{N}]+"))
            .map { it.lowercase() }
            .filter { it.isNotEmpty() }

    /**
     * Strip ONE RO inflection suffix from [token] to get the stem.
     * Tries suffixes in [RO_SUFFIXES] order (longer first) and returns the first match.
     * Returns [token] unchanged when no suffix matches.
     */
    private fun stemOf(token: String): String {
        for (suffix in RO_SUFFIXES) {
            if (token.length > suffix.length && token.endsWith(suffix)) {
                return token.dropLast(suffix.length)
            }
        }
        return token
    }

    /** Leg (1): EN-vocabulary check. Returns a [ValidationIssue] if any stem is in [EN_VOCAB]. */
    private fun checkEnVocabLeg(
        tokens: List<String>,
        kcId: String,
        field: String,
    ): ValidationIssue? {
        for (token in tokens) {
            val stem = stemOf(token)
            if (stem in EN_VOCAB) {
                return ValidationIssue(
                    severity = "error",
                    rule = "language_ro",
                    subject = "", // filled by caller (checkRomanianFields sets subject)
                    detail = "KC '$kcId' field '$field': token '$token' (stem '$stem') is in EN_VOCAB " +
                        "— field must be Romanian (§0.9E EN-vocabulary leg, plan-fix F1). " +
                        "If this is a legitimately-RO loanword, seed it in GlossaryTermsTable or the exempt list.",
                )
            }
        }
        return null
    }

    /** Leg (3): EN-stopword ratio check. Returns a [ValidationIssue] if ratio >= [STOPWORD_RATIO_THRESHOLD]. */
    private fun checkStopwordRatioLeg(
        tokens: List<String>,
        kcId: String,
        field: String,
    ): ValidationIssue? {
        val stopwordCount = tokens.count { it in EN_STOPWORDS }
        val ratio = stopwordCount.toDouble() / tokens.size
        if (ratio >= STOPWORD_RATIO_THRESHOLD) {
            return ValidationIssue(
                severity = "error",
                rule = "language_ro",
                subject = "",
                detail = "KC '$kcId' field '$field': EN-stopword ratio ${
                    "%.2f".format(ratio)
                } >= threshold ${
                    "%.2f".format(STOPWORD_RATIO_THRESHOLD)
                } ($stopwordCount/${tokens.size} tokens are EN stopwords) — field must be Romanian (§0.9E stopword-ratio leg).",
            )
        }
        return null
    }
}
