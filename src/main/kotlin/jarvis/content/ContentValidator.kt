package jarvis.content

import kotlinx.serialization.Serializable

@Serializable
data class ValidationIssue(
    val severity: String,   // "error" | "warning"
    val rule: String,       // "cycle" | "orphan" | "exam_weight" | "bilingual" | "verbatim_source"
    val subject: String,
    val detail: String,
)

@Serializable
data class ValidationReport(
    val ok: Boolean,
    val disclaimer: String,
    val issues: List<ValidationIssue>,
)

/**
 * Structural validator for the content/ corpus. Per council 1779311876 amendment 1,
 * this validator proves the graph is well-FORMED (acyclic, connected, weights sum,
 * bilingual, attributed) — it does NOT prove the content is TRUE. Semantic
 * groundedness is the human curator's job (and the Gate 5 sidecar's).
 */
object ContentValidator {

    const val DISCLAIMER = "structural checks only — content not groundedness-verified"

    /** Three-color DFS cycle detection over the prerequisite graph (edge prereq -> kc). */
    fun detectCycles(sub: LoadedSubject): List<ValidationIssue> {
        val adj: Map<String, List<String>> = sub.edges.groupBy({ it.prereq }, { it.kc })
        val color = HashMap<String, Int>() // 0 = white, 1 = gray, 2 = black
        val nodes = (sub.kcs.map { it.id } + sub.edges.flatMap { listOf(it.kc, it.prereq) }).toSet()
        val issues = mutableListOf<ValidationIssue>()

        fun dfs(node: String): Boolean {
            color[node] = 1
            for (next in adj[node].orEmpty()) {
                val c = color[next] ?: 0
                if (c == 1) return true            // back-edge to a gray node = cycle
                if (c == 0 && dfs(next)) return true
            }
            color[node] = 2
            return false
        }

        for (n in nodes) {
            if ((color[n] ?: 0) == 0 && dfs(n)) {
                issues += ValidationIssue("error", "cycle", sub.subject,
                    "prerequisite graph contains a cycle reachable from KC '$n'")
                break // one cycle issue is enough — the curator fixes and re-runs
            }
        }
        return issues
    }

    /**
     * Orphan = a KC that is NOT a tier-1 root and has no prerequisite chain of
     * <= [MAX_HOPS] edges reaching a tier-1 KC. Walks edges kc -> prereq.
     */
    const val MAX_HOPS = 8

    fun detectOrphans(sub: LoadedSubject): List<ValidationIssue> {
        val tier1: Set<String> = sub.kcs.filter { it.tier == 1 }.map { it.id }.toSet()
        // prereqsOf[x] = the KCs that x directly depends on.
        val prereqsOf: Map<String, List<String>> = sub.edges.groupBy({ it.kc }, { it.prereq })
        val issues = mutableListOf<ValidationIssue>()

        for (kc in sub.kcs) {
            if (kc.id in tier1) continue
            // BFS up the prerequisite chain, bounded by MAX_HOPS.
            val seen = hashSetOf(kc.id)
            var frontier = listOf(kc.id)
            var reached = false
            var hop = 0
            while (frontier.isNotEmpty() && hop < MAX_HOPS && !reached) {
                val next = mutableListOf<String>()
                for (n in frontier) for (p in prereqsOf[n].orEmpty()) {
                    if (p in tier1) { reached = true }
                    if (seen.add(p)) next += p
                }
                frontier = next
                hop++
            }
            if (!reached) {
                issues += ValidationIssue("error", "orphan", sub.subject,
                    "KC '${kc.id}' has no prerequisite path (<= $MAX_HOPS hops) to a tier-1 root")
            }
        }
        return issues
    }

    const val WEIGHT_TOLERANCE = 0.02

    /** Per spec §13: the exam_weight of a subject's KCs must sum to 1.0 +/- 0.02. */
    fun checkExamWeights(sub: LoadedSubject): List<ValidationIssue> {
        if (sub.kcs.isEmpty()) return emptyList()
        val sum = sub.kcs.sumOf { it.exam_weight }
        if (kotlin.math.abs(sum - 1.0) > WEIGHT_TOLERANCE) {
            return listOf(ValidationIssue("error", "exam_weight", sub.subject,
                "exam_weight sum is %.4f — must be 1.0 +/- %.2f".format(sum, WEIGHT_TOLERANCE)))
        }
        return emptyList()
    }

    /** Every KC and misconception must carry non-blank RO and EN text. */
    fun checkBilingual(sub: LoadedSubject): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        for (kc in sub.kcs) {
            if (kc.name_ro.isBlank() || kc.name_en.isBlank()) {
                issues += ValidationIssue("error", "bilingual", sub.subject,
                    "KC '${kc.id}' missing name_ro or name_en")
            }
        }
        for (m in sub.misconceptions) {
            if (m.label_ro.isBlank() || m.label_en.isBlank()) {
                issues += ValidationIssue("error", "bilingual", sub.subject,
                    "misconception '${m.id}' missing label_ro or label_en")
            }
        }
        return issues
    }

    /**
     * Runs every structural check across all [subjects]. [sourceText] resolves a
     * doc id to its extracted source text (see checkVerbatimSources).
     * ok = true iff there are no "error"-severity issues; warnings do not fail.
     */
    fun validate(
        subjects: List<LoadedSubject>,
        sourceText: (doc: String) -> String?,
    ): ValidationReport {
        val issues = mutableListOf<ValidationIssue>()
        for (sub in subjects) {
            issues += detectCycles(sub)
            issues += detectOrphans(sub)
            issues += checkExamWeights(sub)
            issues += checkBilingual(sub)
            issues += checkVerbatimSources(sub, sourceText)
        }
        val ok = issues.none { it.severity == "error" }
        return ValidationReport(ok = ok, disclaimer = DISCLAIMER, issues = issues)
    }

    private fun normalizeWs(s: String): String = s.replace(Regex("\\s+"), " ").trim()

    /**
     * Council 1779311876 amendment 1. For every KC and misconception:
     *  - at least one SourceRef is required (empty source = error);
     *  - each SourceRef.quote, after whitespace normalization, must be a
     *    verbatim substring of [sourceText] of its doc;
     *  - if the doc's extracted text is absent, the check degrades to a
     *    warning (cannot verify) rather than an error.
     * [sourceText] maps a doc id to its extracted text, or null if absent.
     */
    fun checkVerbatimSources(
        sub: LoadedSubject,
        sourceText: (doc: String) -> String?,
    ): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()

        fun checkOne(ownerKind: String, ownerId: String, refs: List<SourceRef>) {
            if (refs.isEmpty()) {
                issues += ValidationIssue("error", "verbatim_source", sub.subject,
                    "$ownerKind '$ownerId' has no source attribution")
                return
            }
            for (ref in refs) {
                val text = sourceText(ref.doc)
                if (text == null) {
                    issues += ValidationIssue("warning", "verbatim_source", sub.subject,
                        "$ownerKind '$ownerId': source '${ref.doc}' has no extracted text on disk — quote unverifiable")
                    continue
                }
                if (!normalizeWs(text).contains(normalizeWs(ref.quote))) {
                    issues += ValidationIssue("error", "verbatim_source", sub.subject,
                        "$ownerKind '$ownerId': quote not found verbatim in source '${ref.doc}'")
                }
            }
        }

        for (kc in sub.kcs) checkOne("KC", kc.id, kc.source)
        for (m in sub.misconceptions) checkOne("misconception", m.id, m.source)
        return issues
    }
}
