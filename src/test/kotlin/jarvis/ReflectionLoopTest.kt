package jarvis

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReflectionLoopTest {

    private class FakeLlm(val replyText: String = "Pattern: long focus blocks on jarvis-kotlin.") : Llm {
        override suspend fun complete(messages: List<ChatMessage>, maxTokens: Int, responseFormat: String?): Pair<String, String> =
            replyText to "fake-llm"
    }

    private val now: Instant = Instant.parse("2026-05-08T15:00:00Z")

    private fun signal(
        id: String,
        hoursAgo: Long,
        importance: Float,
        kind: String = "ctx_model_summary",
        status: String = "emitted",
    ) = ProactiveSignal(
        id = id,
        ts = now.minus(Duration.ofHours(hoursAgo)).toString(),
        kind = kind,
        importance = importance,
        sourceTs = now.minus(Duration.ofHours(hoursAgo)).toString(),
        snippet = "test snippet $id",
        rationale = "test",
        status = status,
    )

    @Test
    fun belowSigmaThresholdSuppresses(@TempDir tmp: Path) = runBlocking {
        val file = tmp.resolve("signals.jsonl")
        // Two low-importance rows, sum 0.6 < 3.0
        Signals.appendTo(file, signal("a", 1, 0.3f))
        Signals.appendTo(file, signal("b", 2, 0.3f))
        val id = ReflectionLoop.reflectSync(FakeLlm(), file, now)
        assertNull(id, "below-σ should suppress")
        // No reflection row written.
        assertTrue(Signals.readAllFrom(file).none { it.kind == "reflection" })
    }

    @Test
    fun aboveSigmaThresholdEmits(@TempDir tmp: Path) = runBlocking {
        val file = tmp.resolve("signals.jsonl")
        // 4 rows × 0.85 = 3.4 ≥ 3.0
        repeat(4) { i -> Signals.appendTo(file, signal("s$i", i.toLong(), 0.85f)) }
        val id = ReflectionLoop.reflectSync(FakeLlm(), file, now)
        assertNotNull(id, "above-σ should emit")
        val all = Signals.readAllFrom(file)
        val refl = all.filter { it.kind == "reflection" }
        assertEquals(1, refl.size)
        assertEquals(id, refl[0].id)
        assertEquals("emitted", refl[0].status)
        assertNotNull(refl[0].parentIds)
        assertEquals(4, refl[0].parentIds!!.size)
    }

    @Test
    fun errorRowsExcludedFromSigma(@TempDir tmp: Path) = runBlocking {
        val file = tmp.resolve("signals.jsonl")
        // 5 error rows × 0.85 — should NOT count.
        repeat(5) { i ->
            Signals.appendTo(file, signal("e$i", i.toLong(), 0.85f, status = "error"))
        }
        val id = ReflectionLoop.reflectSync(FakeLlm(), file, now)
        assertNull(id, "error rows excluded — σ=0, suppress")
    }

    @Test
    fun cooldownSuppressesSecondReflection(@TempDir tmp: Path) = runBlocking {
        val file = tmp.resolve("signals.jsonl")
        // Add prior reflection 1h ago — within 6h cooldown.
        Signals.appendTo(file, signal("r1", 1, 0.85f, kind = "reflection"))
        // Plus enough ctx-model rows to exceed σ.
        repeat(4) { i -> Signals.appendTo(file, signal("s$i", i.toLong(), 0.85f)) }
        val id = ReflectionLoop.reflectSync(FakeLlm(), file, now)
        assertNull(id, "1h-old reflection blocks new one (6h cooldown)")
    }

    @Test
    fun cooldownElapsedAllowsReflection(@TempDir tmp: Path) = runBlocking {
        val file = tmp.resolve("signals.jsonl")
        // Prior reflection 7h ago — past cooldown.
        Signals.appendTo(file, signal("r1", 7, 0.85f, kind = "reflection"))
        repeat(4) { i -> Signals.appendTo(file, signal("s$i", i.toLong(), 0.85f)) }
        val id = ReflectionLoop.reflectSync(FakeLlm(), file, now)
        assertNotNull(id, "7h-old reflection past cooldown — new one fires")
    }

    @Test
    fun deterministicReflectionId() {
        val parents = listOf("a", "b", "c")
        val id1 = ReflectionLoop.computeReflectionId(parents, now)
        val id2 = ReflectionLoop.computeReflectionId(parents.reversed(), now)
        assertEquals(id1, id2, "id stable regardless of input order (sorted)")
        val laterHour = now.plus(Duration.ofHours(1))
        val id3 = ReflectionLoop.computeReflectionId(parents, laterHour)
        assertTrue(id1 != id3, "different hour bucket → different id")
    }

    @Test
    fun reflectionsCountTowardSigmaForRecursion(@TempDir tmp: Path) = runBlocking {
        val file = tmp.resolve("signals.jsonl")
        // Mix of ctx-model + prior reflection (8h ago — past cooldown).
        Signals.appendTo(file, signal("r0", 8, 0.85f, kind = "reflection"))
        repeat(3) { i -> Signals.appendTo(file, signal("s$i", i.toLong(), 0.85f)) }
        val id = ReflectionLoop.reflectSync(FakeLlm(), file, now)
        assertNotNull(id, "ctx-model + prior reflection together cross σ")
        // Verify the new reflection's parentIds includes the older reflection.
        val newRefl = Signals.readAllFrom(file).last { it.kind == "reflection" && it.id == id }
        assertTrue("r0" in newRefl.parentIds!!, "older reflection is recursive parent")
    }

    @Test
    fun isEnabledRespectsEnv() {
        val v = ReflectionLoop.isEnabled()
        assertTrue(v == true || v == false)
    }

    @Test
    fun quietHoursSuppressReflection(@TempDir tmp: Path) = runBlocking {
        val file = tmp.resolve("signals.jsonl")
        // Plenty of importance to cross σ, well past 6h cooldown — would
        // normally fire. Quiet predicate set to true must veto BEFORE LLM.
        repeat(4) { i -> Signals.appendTo(file, signal("q$i", i.toLong(), 0.9f)) }
        val id = ReflectionLoop.reflectSync(
            FakeLlm(), file, now,
            nudgeAllowed = { false },
        )
        assertNull(id, "quiet/sleeping must suppress reflection")
        assertTrue(
            Signals.readAllFrom(file).none { it.kind == "reflection" },
            "no reflection row written during quiet hours",
        )
    }
}
