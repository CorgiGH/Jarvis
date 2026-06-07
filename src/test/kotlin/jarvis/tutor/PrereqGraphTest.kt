package jarvis.tutor

import jarvis.content.PrereqEdge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * TASK 1 — PrereqGraph (interface-signatures-lock §E, line 116).
 *
 * Frozen contract:
 *   class PrereqGraph(private val closure: Map<String, Set<String>>)
 *     fun prereqsOf(kcId: String): Set<String>          // ALL transitive prereqs; empty for root/unknown
 *     fun isUnlocked(kcId: String, masteredKcIds: Set<String>): Boolean
 *     companion object { fun from(edges: List<PrereqEdge>): PrereqGraph }
 *
 * `PrereqEdge(kc, prereq, rationale)` [EXISTS] — `kc` DEPENDS ON `prereq`.
 *
 * Class-killing focus: the transitive closure (A->B->C) and cycle-safety
 * (a malformed cyclic edges.yaml must not infinite-loop).
 */
class PrereqGraphTest {

    private fun edge(kc: String, prereq: String) = PrereqEdge(kc, prereq, "r")

    // ── direct prereqs ──────────────────────────────────────────────────────
    @Test
    fun `direct prereq is reported`() {
        val g = PrereqGraph.from(listOf(edge("B", "A")))
        assertEquals(setOf("A"), g.prereqsOf("B"))
    }

    // ── transitive closure A->B->C (THE class-killer) ───────────────────────
    @Test
    fun `transitive closure includes all ancestors not just direct`() {
        // C depends on B, B depends on A  => prereqsOf(C) == {A, B}
        val g = PrereqGraph.from(listOf(edge("C", "B"), edge("B", "A")))
        assertEquals(setOf("A", "B"), g.prereqsOf("C"))
        assertEquals(setOf("A"), g.prereqsOf("B"))
        assertEquals(emptySet(), g.prereqsOf("A"))
    }

    @Test
    fun `diamond closure dedups shared ancestor`() {
        // D->B, D->C, B->A, C->A : prereqsOf(D) == {A,B,C} (A only once)
        val g = PrereqGraph.from(
            listOf(edge("D", "B"), edge("D", "C"), edge("B", "A"), edge("C", "A")),
        )
        assertEquals(setOf("A", "B", "C"), g.prereqsOf("D"))
    }

    // ── KC with no prereqs => empty ─────────────────────────────────────────
    @Test
    fun `root kc has no prereqs`() {
        val g = PrereqGraph.from(listOf(edge("B", "A")))
        assertEquals(emptySet(), g.prereqsOf("A"))
    }

    // ── unknown KC => empty (degrade, never throw) ──────────────────────────
    @Test
    fun `unknown kc degrades to empty set`() {
        val g = PrereqGraph.from(listOf(edge("B", "A")))
        assertEquals(emptySet(), g.prereqsOf("does-not-exist"))
    }

    @Test
    fun `empty edge list makes every kc a root`() {
        val g = PrereqGraph.from(emptyList())
        assertEquals(emptySet(), g.prereqsOf("anything"))
        assertTrue(g.isUnlocked("anything", emptySet()))
    }

    // ── isUnlocked semantics ────────────────────────────────────────────────
    @Test
    fun `unlocked only when every transitive prereq is mastered`() {
        val g = PrereqGraph.from(listOf(edge("C", "B"), edge("B", "A")))
        assertFalse(g.isUnlocked("C", emptySet()))
        assertFalse(g.isUnlocked("C", setOf("B")))       // A still missing
        assertFalse(g.isUnlocked("C", setOf("A")))       // B still missing
        assertTrue(g.isUnlocked("C", setOf("A", "B")))   // both mastered
    }

    @Test
    fun `a root kc is always unlocked`() {
        val g = PrereqGraph.from(listOf(edge("B", "A")))
        assertTrue(g.isUnlocked("A", emptySet()))
        assertTrue(g.isUnlocked("unknown-root", emptySet()))
    }

    // ── CYCLE-SAFETY (THE class-killer): must NOT hang ──────────────────────
    @Test
    fun `cyclic edges do not hang and degrade deterministically`() {
        // A->B->C->A is a malformed cycle. from() + prereqsOf must terminate.
        val cyclic = listOf(edge("A", "B"), edge("B", "C"), edge("C", "A"))
        val g = PrereqGraph.from(cyclic)
        // Deterministic degrade: each node's closure is the set of all OTHER nodes
        // reachable along prereq edges (cycle visited once, not infinitely).
        assertEquals(setOf("B", "C"), g.prereqsOf("A"))
        assertEquals(setOf("A", "C"), g.prereqsOf("B"))
        assertEquals(setOf("A", "B"), g.prereqsOf("C"))
    }

    @Test
    fun `self-loop edge does not hang`() {
        val g = PrereqGraph.from(listOf(edge("A", "A")))
        // A self-prereq is degenerate; closure must terminate and not list A as its own prereq.
        assertFalse(g.prereqsOf("A").contains("A"))
    }

    // ── memoized over the REAL content/PA/edges.yaml ────────────────────────
    @Test
    fun `real PA edges resolve transitively`() {
        val repo = jarvis.content.ContentRepo(java.nio.file.Paths.get("content"))
        val pa = repo.loadSubject("PA")
        val g = PrereqGraph.from(pa.edges)
        // pa-kc-006 -> pa-kc-005 -> pa-kc-001 (transitive) per content/PA/edges.yaml
        assertEquals(setOf("pa-kc-005", "pa-kc-001"), g.prereqsOf("pa-kc-006"))
        // pa-kc-001 is a root.
        assertEquals(emptySet(), g.prereqsOf("pa-kc-001"))
    }
}
