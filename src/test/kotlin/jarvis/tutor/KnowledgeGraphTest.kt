package jarvis.tutor

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KnowledgeGraphTest {

    private fun seedArchival(tmp: Path): Path {
        val pa = tmp.resolve("PA"); pa.createDirectories()
        pa.resolve("greedy.md").writeText("""
            ## Greedy algorithm

            A greedy algorithm picks the local optimum at each step.
            Pairs naturally with Activity selection problem.

            ## Activity selection problem

            Sort by end time, pick non-overlapping greedily.
        """.trimIndent())
        pa.resolve("dp.md").writeText("""
            ## Dynamic programming

            Decomposes a problem into overlapping subproblems.
            Often replaces a Greedy algorithm when optimal substructure
            holds but greedy choice property fails.
        """.trimIndent())
        val ps = tmp.resolve("PS"); ps.createDirectories()
        ps.resolve("markov.md").writeText("""
            ## Markov chains

            Memoryless transition between states.
        """.trimIndent())
        return tmp
    }

    @Test
    fun buildExtractsConceptsBySubject(@TempDir tmp: Path) {
        val root = seedArchival(tmp)
        val g = KnowledgeGraphBuilder.build(root)
        val ids = g.nodes.map { it.id }.toSet()
        assertTrue("PA/Greedy algorithm" in ids, ids.toString())
        assertTrue("PA/Activity selection problem" in ids)
        assertTrue("PA/Dynamic programming" in ids)
        assertTrue("PS/Markov chains" in ids)
    }

    @Test
    fun siblingEdgesAdjacentSections(@TempDir tmp: Path) {
        val root = seedArchival(tmp)
        val g = KnowledgeGraphBuilder.build(root)
        val sib = g.edges.filter { it.kind == "sibling" }
        assertTrue(sib.any { it.from == "PA/Greedy algorithm" && it.to == "PA/Activity selection problem" })
    }

    @Test
    fun mentionEdgesPickUpSubstringRefs(@TempDir tmp: Path) {
        val root = seedArchival(tmp)
        val g = KnowledgeGraphBuilder.build(root)
        // DP body mentions "Greedy algorithm" → mention edge.
        val mentions = g.edges.filter { it.kind == "mentions" }
        assertTrue(mentions.any { it.from == "PA/Dynamic programming" && it.to == "PA/Greedy algorithm" },
            "DP→Greedy mention missing: ${mentions}")
    }

    @Test
    fun subjectEdgesGroupSameSubject(@TempDir tmp: Path) {
        val root = seedArchival(tmp)
        val g = KnowledgeGraphBuilder.build(root)
        val sameSubject = g.edges.filter { it.kind == "subject" }
        // PA has 3 nodes → 3 choose 2 = 3 subject edges (undirected one-pass build).
        assertEquals(3, sameSubject.count { it.from.startsWith("PA/") })
        // PS has 1 node → 0 subject edges.
        assertEquals(0, sameSubject.count { it.from.startsWith("PS/") })
    }

    @Test
    fun persistAndLoadRoundtrip(@TempDir tmp: Path) {
        val root = seedArchival(tmp)
        val g = KnowledgeGraphBuilder.build(root)
        val path = KnowledgeGraphBuilder.persist(g, root)
        assertTrue(Files.exists(path))
        val loaded = KnowledgeGraphBuilder.load(root)
        assertNotNull(loaded)
        assertEquals(g.nodes.size, loaded!!.nodes.size)
        assertEquals(g.edges.size, loaded.edges.size)
    }

    @Test
    fun queryByConceptName(@TempDir tmp: Path) {
        val root = seedArchival(tmp)
        val g = KnowledgeGraphBuilder.build(root)
        val hits = KnowledgeGraphQuery.query(g, "greedy", k = 3)
        assertTrue(hits.any { it.id == "PA/Greedy algorithm" }, hits.toString())
    }

    @Test
    fun queryRanksByHitCount(@TempDir tmp: Path) {
        val root = seedArchival(tmp)
        val g = KnowledgeGraphBuilder.build(root)
        // "greedy" appears in: Greedy algorithm (name) + DP (snippet)
        val hits = KnowledgeGraphQuery.query(g, "greedy", k = 5)
        // Name match scores 100, beats body-only matches.
        assertEquals("PA/Greedy algorithm", hits[0].id)
    }

    @Test
    fun getNodeById(@TempDir tmp: Path) {
        val root = seedArchival(tmp)
        val g = KnowledgeGraphBuilder.build(root)
        val n = KnowledgeGraphQuery.getNode(g, "PS/Markov chains")
        assertNotNull(n)
        assertEquals("PS", n!!.subject)
    }

    @Test
    fun getNodeCaseInsensitive(@TempDir tmp: Path) {
        val root = seedArchival(tmp)
        val g = KnowledgeGraphBuilder.build(root)
        val n = KnowledgeGraphQuery.getNode(g, "ps/markov chains")
        assertNotNull(n)
    }

    @Test
    fun neighborsByEdgeKind(@TempDir tmp: Path) {
        val root = seedArchival(tmp)
        val g = KnowledgeGraphBuilder.build(root)
        val mentions = KnowledgeGraphQuery.neighbors(g, "PA/Dynamic programming", kind = "mentions")
        assertTrue(mentions.any { (n, _) -> n.id == "PA/Greedy algorithm" })
        // Subject-edge neighbors include other PA nodes.
        val subj = KnowledgeGraphQuery.neighbors(g, "PA/Dynamic programming", kind = "subject")
        assertTrue(subj.isNotEmpty())
    }

    @Test
    fun shortestPathSameNode(@TempDir tmp: Path) {
        val root = seedArchival(tmp)
        val g = KnowledgeGraphBuilder.build(root)
        val p = KnowledgeGraphQuery.shortestPath(g, "PA/Greedy algorithm", "PA/Greedy algorithm")
        assertEquals(listOf("PA/Greedy algorithm"), p)
    }

    @Test
    fun shortestPathOneHop(@TempDir tmp: Path) {
        val root = seedArchival(tmp)
        val g = KnowledgeGraphBuilder.build(root)
        // Within PA, all pairs connected via 'subject' edge.
        val p = KnowledgeGraphQuery.shortestPath(g, "PA/Greedy algorithm", "PA/Activity selection problem")
        assertEquals(2, p.size)
    }

    @Test
    fun shortestPathDisconnectedSubjects(@TempDir tmp: Path) {
        val root = seedArchival(tmp)
        val g = KnowledgeGraphBuilder.build(root)
        // PA and PS share no edges → no path.
        val p = KnowledgeGraphQuery.shortestPath(g, "PA/Greedy algorithm", "PS/Markov chains")
        assertEquals(emptyList(), p)
    }

    @Test
    fun queryEmptyReturnsNothing(@TempDir tmp: Path) {
        val root = seedArchival(tmp)
        val g = KnowledgeGraphBuilder.build(root)
        assertEquals(emptyList(), KnowledgeGraphQuery.query(g, "", 5))
    }
}
