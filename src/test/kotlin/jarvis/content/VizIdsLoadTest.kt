package jarvis.content

import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VizIdsLoadTest {
    @Test fun `loadVizIds reads the id set`() {
        val dir = Files.createTempDirectory("content")
        dir.resolve("viz-ids.yaml").writeText("viz_ids:\n  - recursion-tree\n  - bayes-tree\n")
        val ids = ContentRepo(dir).loadVizIds()
        assertEquals(setOf("recursion-tree", "bayes-tree"), ids)
    }

    @Test fun `loadVizIds returns empty when file absent`() {
        val dir = Files.createTempDirectory("content")
        assertTrue(ContentRepo(dir).loadVizIds().isEmpty())
    }
}
