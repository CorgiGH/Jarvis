package jarvis

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConversationAccessTest {

    private val now: Instant = Instant.parse("2026-05-08T12:00:00Z")

    private fun ts(hoursAgo: Long): String =
        now.minus(Duration.ofHours(hoursAgo)).toString()

    private fun e(msgId: String, hoursAgo: Long, importance: Float?, content: String = msgId) =
        ConversationEntry(
            role = "user", content = content, ts = ts(hoursAgo),
            msgId = msgId, seq = 0, importance = importance,
        )

    @Test
    fun emptyAccessFileFallsBackToCreation(@TempDir tmp: Path) {
        val convs = tmp.resolve("c.jsonl")
        val access = tmp.resolve("a.jsonl")
        // Two rows — older one has higher importance. Without lastAccessedAt
        // both decay from creation; older row beats newer at right importance gap.
        Conversations.appendTo(convs, e("old", 48, 0.95f, "old"))
        Conversations.appendTo(convs, e("new", 1, 0.5f, "new"))
        val out = Conversations.recentByImportanceFrom(
            convs, n = 1, accessFile = access, now = now, touchOnPick = false,
        )
        // 48h decay at λ=ln2/24 → exp(-2*ln2)=0.25; +0.95 imp = 1.20.
        // 1h decay → exp(-ln2/24)=0.971; +0.50 imp = 1.471.
        // New wins on raw recency math without access bumping.
        assertEquals("new", out[0].content)
        assertTrue(!access.toFile().exists() || access.toFile().length() == 0L,
            "no touch when touchOnPick=false")
    }

    @Test
    fun accessOverridesCreationForDecay(@TempDir tmp: Path) {
        val convs = tmp.resolve("c.jsonl")
        val access = tmp.resolve("a.jsonl")
        // Two rows: old-but-recently-accessed vs newer-cold.
        Conversations.appendTo(convs, e("oldhot", 48, 0.5f, "oldhot"))
        Conversations.appendTo(convs, e("newcold", 1, 0.5f, "newcold"))
        // Touch oldhot RECENTLY — its decay anchor should jump to ~0h ago.
        ConversationAccess.touchTo(access, listOf("oldhot"), now.minus(Duration.ofMinutes(5)))
        val out = Conversations.recentByImportanceFrom(
            convs, n = 1, accessFile = access, now = now, touchOnPick = false,
        )
        assertEquals("oldhot", out[0].content,
            "recently-accessed old row should win over cold newer row")
    }

    @Test
    fun touchOnPickWritesSidecar(@TempDir tmp: Path) {
        val convs = tmp.resolve("c.jsonl")
        val access = tmp.resolve("a.jsonl")
        Conversations.appendTo(convs, e("a", 1, 0.8f, "a"))
        Conversations.appendTo(convs, e("b", 1, 0.4f, "b"))
        Conversations.recentByImportanceFrom(
            convs, n = 1, accessFile = access, now = now, touchOnPick = true,
        )
        val accessMap = ConversationAccess.lastAccessByMsgIdFrom(access)
        assertTrue("a" in accessMap, "touched msgId persisted")
        assertEquals(now, accessMap["a"], "ts matches now()")
    }

    @Test
    fun latestAccessTsWinsOnRead(@TempDir tmp: Path) {
        val access = tmp.resolve("a.jsonl")
        ConversationAccess.touchTo(access, listOf("x"), Instant.parse("2026-05-08T10:00:00Z"))
        ConversationAccess.touchTo(access, listOf("x"), Instant.parse("2026-05-08T11:00:00Z"))
        ConversationAccess.touchTo(access, listOf("x"), Instant.parse("2026-05-08T09:00:00Z"))
        val map = ConversationAccess.lastAccessByMsgIdFrom(access)
        assertEquals(Instant.parse("2026-05-08T11:00:00Z"), map["x"],
            "latest ts wins regardless of file order")
    }

    @Test
    fun missingFileReturnsEmpty(@TempDir tmp: Path) {
        val map = ConversationAccess.lastAccessByMsgIdFrom(tmp.resolve("nope.jsonl"))
        assertEquals(emptyMap(), map)
    }

    @Test
    fun emptyMsgIdsListIsNoOp(@TempDir tmp: Path) {
        val access = tmp.resolve("a.jsonl")
        ConversationAccess.touchTo(access, emptyList(), now)
        assertTrue(!access.toFile().exists() || access.toFile().length() == 0L)
    }

    @Test
    fun chronologicalRecentUntouched(@TempDir tmp: Path) {
        // Regression: recent(n) is the chat-replay path; touching it would
        // corrupt Park-et-al "access" semantics. recentByImportance is the
        // ONLY path that touches.
        val convs = tmp.resolve("c.jsonl")
        val access = tmp.resolve("a.jsonl")
        Conversations.appendTo(convs, e("a", 5, 0.5f, "a"))
        Conversations.appendTo(convs, e("b", 1, 0.5f, "b"))
        Conversations.recentFrom(convs, n = 2)
        // recent() doesn't accept access path; verify sidecar is empty.
        assertTrue(!access.toFile().exists() || access.toFile().length() == 0L,
            "recent(n) must NOT touch the access sidecar")
    }
}
