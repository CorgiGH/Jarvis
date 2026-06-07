package jarvis.tutor

import jarvis.content.PrereqEdge

/**
 * Prerequisite graph for one subject's KCs — interface-signatures-lock §E.
 *
 * Built ONCE at content load from `EdgesFile.edges` [EXISTS] (`content/{subject}/edges.yaml`)
 * and memoized in `TutorContext` (M-NEXTKC); `from` does all the work, instances are immutable.
 *
 * The closure is precomputed (transitive) so a KC is gated behind ALL its ancestors, not just
 * its direct prereqs — `prereqsOf` and `isUnlocked` are then O(1) / O(prereqs) lookups and the
 * selector stays cheap per candidate.
 *
 * CYCLE-SAFE: a malformed `edges.yaml` with a cycle (A->B->C->A) does NOT infinite-loop — the
 * closure walk visits each node at most once and degrades deterministically (each node's closure
 * = the set of OTHER nodes reachable along prereq edges; a node never lists itself).
 *
 * @param closure kcId -> the set of ALL its transitive prereq kcIds.
 */
class PrereqGraph(
    private val closure: Map<String, Set<String>>,
) {
    /** All prereq kcIds (transitive) of [kcId]; empty set for a root OR an unknown KC (degrade). */
    fun prereqsOf(kcId: String): Set<String> = closure[kcId] ?: emptySet()

    /**
     * True iff EVERY transitive prereq of [kcId] is in [masteredKcIds] (or [kcId] is a root /
     * unknown — a KC with no prereqs is always unlocked).
     */
    fun isUnlocked(kcId: String, masteredKcIds: Set<String>): Boolean =
        masteredKcIds.containsAll(prereqsOf(kcId))

    companion object {
        /**
         * Pure builder. Closes over `PrereqEdge(kc, prereq, rationale)` [EXISTS] where `kc`
         * DEPENDS ON `prereq`, precomputing the transitive closure per KC.
         *
         * Cycle-safe by construction: the DFS from each node tracks a `visited` set, so a cyclic
         * edge set terminates (each reachable node is expanded at most once). A node never appears
         * in its own closure (self-loops + cycles are stripped of the start node).
         */
        fun from(edges: List<PrereqEdge>): PrereqGraph {
            // Direct adjacency: kc -> its DIRECT prereqs.
            val direct: Map<String, Set<String>> =
                edges.groupBy({ it.kc }, { it.prereq })
                    .mapValues { (_, v) -> v.toSet() }

            val closure = HashMap<String, Set<String>>(direct.size)
            for (kc in direct.keys) {
                // Iterative DFS over the prereq edges, visiting each node once (cycle-safe).
                val acc = LinkedHashSet<String>()
                val stack = ArrayDeque<String>()
                // Seed with the DIRECT prereqs; we never push `kc` itself, so a self/cycle
                // edge that comes back to `kc` is filtered out below (acc.add only for != kc).
                direct[kc]?.let { stack.addAll(it) }
                while (stack.isNotEmpty()) {
                    val node = stack.removeLast()
                    if (node == kc) continue          // strip self / cycle-back to start
                    if (!acc.add(node)) continue      // already expanded -> cycle, skip (no hang)
                    direct[node]?.forEach { stack.addLast(it) }
                }
                closure[kc] = acc
            }
            return PrereqGraph(closure)
        }
    }
}
