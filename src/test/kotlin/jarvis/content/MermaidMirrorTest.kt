package jarvis.content

import kotlin.test.Test
import kotlin.test.assertTrue

class MermaidMirrorTest {
    @Test
    fun `renders a flowchart with one edge per prereq`() {
        val edges = EdgesFile("PA", listOf(
            PrereqEdge("pa-kc-002", "pa-kc-001", "r"),
            PrereqEdge("pa-kc-003", "pa-kc-001", "r"),
        ))
        val mmd = MermaidMirror.render(edges)
        assertTrue(mmd.startsWith("flowchart TD"), mmd)
        assertTrue(mmd.contains("pa-kc-001 --> pa-kc-002"))
        assertTrue(mmd.contains("pa-kc-001 --> pa-kc-003"))
    }

    @Test
    fun `empty edge list still renders a valid header`() {
        val mmd = MermaidMirror.render(EdgesFile("PA", emptyList()))
        assertTrue(mmd.startsWith("flowchart TD"))
    }
}
