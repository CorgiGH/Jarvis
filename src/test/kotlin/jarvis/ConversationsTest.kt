package jarvis

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConversationsTest {

    @Test
    fun appendThenReadRoundTripsAllFields(@TempDir tmp: Path) {
        val file = tmp.resolve("conversations.jsonl")
        Conversations.appendTo(
            file,
            ConversationEntry(
                role = "user",
                content = "what time is it",
                ts = "2026-05-07T12:00:00Z",
                model = null,
                msgId = "m1",
            ),
        )
        Conversations.appendTo(
            file,
            ConversationEntry(
                role = "assistant",
                content = "around noon",
                ts = "2026-05-07T12:00:01Z",
                model = "claude-opus-4-7",
                msgId = "m2",
            ),
        )

        val all = Conversations.readAllFrom(file)
        assertEquals(2, all.size)
        assertEquals("user", all[0].role)
        assertEquals("what time is it", all[0].content)
        assertEquals("m1", all[0].msgId)
        assertEquals("chat", all[0].kind, "kind defaults to chat")
        assertEquals(1, all[0].v, "schema version defaults to 1")
        assertEquals("assistant", all[1].role)
        assertEquals("claude-opus-4-7", all[1].model)
    }

    @Test
    fun recentReturnsLastNInOrder(@TempDir tmp: Path) {
        val file = tmp.resolve("conversations.jsonl")
        repeat(10) { i ->
            Conversations.appendTo(
                file,
                ConversationEntry(
                    role = if (i % 2 == 0) "user" else "assistant",
                    content = "msg-$i",
                    ts = "2026-05-07T12:00:${"%02d".format(i)}Z",
                    msgId = "m$i",
                ),
            )
        }
        val tail = Conversations.recentFrom(file, n = 3)
        assertEquals(3, tail.size)
        assertEquals(listOf("msg-7", "msg-8", "msg-9"), tail.map { it.content })
    }

    @Test
    fun recentOnEmptyFileReturnsEmpty(@TempDir tmp: Path) {
        val file = tmp.resolve("conversations.jsonl")
        assertEquals(emptyList(), Conversations.recentFrom(file, n = 5))
    }

    @Test
    fun corruptLinesAreSkippedNotFatal(@TempDir tmp: Path) {
        val file = tmp.resolve("conversations.jsonl")
        Conversations.appendTo(
            file,
            ConversationEntry(role = "user", content = "ok", ts = "2026-05-07T12:00:00Z", msgId = "m1"),
        )
        java.nio.file.Files.writeString(
            file,
            "this is not json\n",
            Charsets.UTF_8,
            java.nio.file.StandardOpenOption.APPEND,
        )
        Conversations.appendTo(
            file,
            ConversationEntry(role = "assistant", content = "good", ts = "2026-05-07T12:00:01Z", msgId = "m2"),
        )
        val all = Conversations.readAllFrom(file)
        assertEquals(2, all.size, "corrupt middle line skipped, valid entries kept")
        assertEquals(listOf("ok", "good"), all.map { it.content })
    }

    @Test
    fun concurrentAppendsProduceNCleanLines(@TempDir tmp: Path) {
        val file = tmp.resolve("conversations.jsonl")
        val n = 200
        val pool = Executors.newFixedThreadPool(32)
        val ready = CountDownLatch(n)
        val go = CountDownLatch(1)
        val done = CountDownLatch(n)

        repeat(n) { i ->
            pool.submit {
                ready.countDown()
                go.await()
                try {
                    Conversations.appendTo(
                        file,
                        ConversationEntry(
                            role = if (i % 2 == 0) "user" else "assistant",
                            content = "concurrent-$i payload " + "x".repeat(200),
                            ts = "2026-05-07T12:00:00.${"%03d".format(i)}Z",
                            msgId = "m$i",
                        ),
                    )
                } finally {
                    done.countDown()
                }
            }
        }
        ready.await(10, TimeUnit.SECONDS)
        go.countDown()
        assertTrue(done.await(30, TimeUnit.SECONDS), "all $n concurrent appends finished")
        pool.shutdown()

        val all = Conversations.readAllFrom(file)
        assertEquals(n, all.size, "every concurrent append landed as a valid line")
        val seen = all.map { it.msgId }.toSet()
        assertEquals((0 until n).map { "m$it" }.toSet(), seen, "every msgId 0..$n landed exactly once")
    }
}
