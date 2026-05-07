package jarvis

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeedbackTest {

    @Test
    fun roundTrip(@TempDir tmp: Path) {
        val file = tmp.resolve("feedback.jsonl")
        Feedback.appendTo(file, FeedbackEntry(
            signalId = "s1", ts = "2026-05-08T12:00:00Z", action = "pinned",
        ))
        Feedback.appendTo(file, FeedbackEntry(
            signalId = "s2", ts = "2026-05-08T12:01:00Z", action = "dismissed",
        ))
        val all = Feedback.readAllFrom(file)
        assertEquals(2, all.size)
        assertEquals("s1", all[0].signalId)
        assertEquals("pinned", all[0].action)
        assertEquals(1, all[0].v, "default schema version")
    }

    @Test
    fun missingFileEmpty(@TempDir tmp: Path) {
        assertEquals(emptyList(), Feedback.readAllFrom(tmp.resolve("nope.jsonl")))
    }

    @Test
    fun corruptLineSkipped(@TempDir tmp: Path) {
        val file = tmp.resolve("feedback.jsonl")
        Feedback.appendTo(file, FeedbackEntry("s1", "2026-05-08T12:00:00Z", "useful"))
        java.nio.file.Files.writeString(
            file,
            "this is not json\n",
            java.nio.file.StandardOpenOption.APPEND,
        )
        Feedback.appendTo(file, FeedbackEntry("s2", "2026-05-08T12:01:00Z", "noise"))
        val all = Feedback.readAllFrom(file)
        assertEquals(2, all.size, "corrupt middle line skipped")
    }

    @Test
    fun allowedActionsSet() {
        assertEquals(
            setOf("dismissed", "pinned", "useful", "noise"),
            Feedback.ALLOWED_ACTIONS,
        )
    }

    @Test
    fun unknownVersionSkipped(@TempDir tmp: Path) {
        val file = tmp.resolve("feedback.jsonl")
        Feedback.appendTo(file, FeedbackEntry("s1", "2026-05-08T12:00:00Z", "pinned"))
        java.nio.file.Files.writeString(
            file,
            """{"signalId":"s2","ts":"2026-05-08T12:01:00Z","action":"pinned","v":2}""" + "\n",
            java.nio.file.StandardOpenOption.APPEND,
        )
        val all = Feedback.readAllFrom(file)
        assertEquals(1, all.size, "v=2 row skipped on read")
        assertTrue(all.all { it.v == 1 })
    }
}
