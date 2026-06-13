package jarvis.tutor.grader

/**
 * The surface archetype-class a problem belongs to (drives the chain shape).
 * Distinct from the free-text `archetype` string on the problem row — this is the
 * normalized routing key.
 */
enum class ArchetypeClass { PROOF, TRACE, CODE, NUMERIC, PROSE }

/**
 * DATA-DRIVEN routing table (NOT branching logic): (subject × examLanguage × archetypeClass)
 * → the ordered list of [GraderLegKind] the chain runs.
 *
 * Invariants the table guarantees (checked by GraderRoutingTotalityTest + LlmNeverAloneTest):
 *  - every produced chain's FIRST leg is non-LLM (INV-6.1),
 *  - no chain is `[LLM_JUDGE]` alone (INV-6.3).
 *
 * Explicit rows (§0.9 / R-6-Q8):
 *  - (SO, bash|posix-c, code) → [RUBRIC, LLM_JUDGE]   — rubric IS the first leg; NO bash runner, no WSL.
 *  - default code              → [EXECUTION, RUBRIC, LLM_JUDGE]
 *  - numeric                   → [NUMERIC_ORACLE, LLM_JUDGE]
 *  - trace                     → [NUMERIC_ORACLE, RUBRIC, LLM_JUDGE]  (per-step numeric compare, §0.9-K)
 *  - proof / prose             → [RUBRIC, LLM_JUDGE]
 */
object GraderRouting {

    /** Languages with NO execution runner — routed to rubric first (R-6-Q8). */
    private val NO_RUNNER_LANGUAGES = setOf("bash", "posix-c")

    /**
     * Resolve the ordered leg kinds for a problem. [examLanguage] is the R-LANG id
     * ("alk"|"r"|"cpp"|"bash"|"posix-c"|null); [archetypeClass] is the normalized surface key.
     */
    fun chainFor(
        subject: String?,
        examLanguage: String?,
        archetypeClass: ArchetypeClass,
    ): List<GraderLegKind> = when (archetypeClass) {
        ArchetypeClass.NUMERIC ->
            listOf(GraderLegKind.NUMERIC_ORACLE, GraderLegKind.LLM_JUDGE)

        ArchetypeClass.TRACE ->
            listOf(GraderLegKind.NUMERIC_ORACLE, GraderLegKind.RUBRIC, GraderLegKind.LLM_JUDGE)

        ArchetypeClass.CODE -> {
            val lang = examLanguage?.lowercase()
            if (lang != null && lang in NO_RUNNER_LANGUAGES) {
                // R-6-Q8: bash/POSIX-C have no runner → rubric is the first leg.
                listOf(GraderLegKind.RUBRIC, GraderLegKind.LLM_JUDGE)
            } else {
                listOf(GraderLegKind.EXECUTION, GraderLegKind.RUBRIC, GraderLegKind.LLM_JUDGE)
            }
        }

        ArchetypeClass.PROOF, ArchetypeClass.PROSE ->
            listOf(GraderLegKind.RUBRIC, GraderLegKind.LLM_JUDGE)
    }

    /**
     * Normalize a free-text problem [archetype] string + [examLanguage] into an
     * [ArchetypeClass]. The seeds carry a `# Surface archetype-class:` comment naming
     * the class; this maps the runtime archetype field to the same classes.
     */
    fun classify(archetype: String, examLanguage: String?): ArchetypeClass {
        val a = archetype.lowercase()
        return when {
            "proof" in a || "complet" in a || "demonstr" in a || "reduc" in a -> ArchetypeClass.PROOF
            "trace" in a || "step" in a || "elimin" in a || "trasare" in a -> ArchetypeClass.TRACE
            !examLanguage.isNullOrBlank() -> ArchetypeClass.CODE
            "numeric" in a || "compute" in a || "calcul" in a -> ArchetypeClass.NUMERIC
            else -> ArchetypeClass.PROSE
        }
    }

    /** Every chain shape the table can emit — used by INV-6.3's table-level sweep. */
    fun allChainShapes(): List<List<GraderLegKind>> {
        val subjects = listOf<String?>(null, "PA", "PS", "ALO", "POO", "SO-RC")
        val langs = listOf<String?>(null, "alk", "r", "cpp", "bash", "posix-c")
        val classes = ArchetypeClass.entries
        return buildList {
            for (s in subjects) for (l in langs) for (c in classes) add(chainFor(s, l, c))
        }.distinct()
    }
}
