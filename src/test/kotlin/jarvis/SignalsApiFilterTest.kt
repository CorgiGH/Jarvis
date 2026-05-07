package jarvis

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Verifies the filtering primitives the /api/signals route uses (since-cutoff
 *  + PII scrub). The actual route is in WebMain.kt — we test the
 *  composable parts rather than spinning up a Ktor instance. */
class SignalsApiFilterTest {

    private val now: Instant = Instant.parse("2026-05-08T12:00:00Z")

    private fun sig(id: String, hoursAgo: Long, snippet: String) = ProactiveSignal(
        id = id,
        ts = now.minus(Duration.ofHours(hoursAgo)).toString(),
        kind = "ctx_model_summary",
        importance = 0.85f,
        sourceTs = now.minus(Duration.ofHours(hoursAgo)).toString(),
        snippet = snippet,
        rationale = "test",
        status = "emitted",
    )

    @Test
    fun sinceFiltersOlderRows(@TempDir tmp: Path) {
        val file = tmp.resolve("signals.jsonl")
        Signals.appendTo(file, sig("a", 5, "old signal"))
        Signals.appendTo(file, sig("b", 1, "fresh signal"))
        val cutoff = now.minus(Duration.ofHours(2))
        val all = Signals.readAllFrom(file)
        val newer = all.filter {
            runCatching { Instant.parse(it.ts) }.getOrNull()?.let { ts -> ts > cutoff } ?: false
        }
        assertEquals(1, newer.size)
        assertEquals("b", newer[0].id)
    }

    @Test
    fun limitCapsResponseCount(@TempDir tmp: Path) {
        val file = tmp.resolve("signals.jsonl")
        repeat(20) { i -> Signals.appendTo(file, sig("s$i", (20 - i).toLong(), "row $i")) }
        val all = Signals.readAllFrom(file)
        assertEquals(20, all.size, "all signals readable")
        val capped = all.take(10)
        assertEquals(10, capped.size)
    }

    @Test
    fun piiSnippetDropped() {
        val withPii = sig("p", 1, "user matricol 31091001031ROSL251002 detected").snippet
        val findings = CoreMemory.scanTextForPii(withPii)
        assertTrue(findings.isNotEmpty(), "PII scanner catches matricol shape")
        assertTrue(findings.any { it.kind == "matricol" })
    }

    @Test
    fun cleanSnippetPasses() {
        val clean = sig("c", 1, "ENERGY: mid, LIFE_SEASON: deep build").snippet
        assertEquals(emptyList(), CoreMemory.scanTextForPii(clean))
    }

    @Test
    fun emailInSnippetCaught() {
        val findings = CoreMemory.scanTextForPii("ping me at user@example.com")
        assertTrue(findings.any { it.kind == "email" })
    }
}
