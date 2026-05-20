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
}
