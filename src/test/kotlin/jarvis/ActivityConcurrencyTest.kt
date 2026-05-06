package jarvis

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Concurrent appends to activity.jsonl must produce N intact JSON-per-line entries.
 * Both /api/activity (server, multi-coroutine) and the PC logger fallback hit the
 * same appender shape; mutex must serialize them within a JVM.
 */
class ActivityConcurrencyTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun concurrentAppendsProduceNCleanJsonLines(@TempDir tmp: Path) {
        val log = tmp.resolve("activity.jsonl")
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
                    Activity.appendTo(
                        log,
                        ActivityEntry(
                            ts = "2026-05-07T12:34:56.${"%03d".format(i)}Z",
                            title = "title-$i",
                            process = "proc-$i",
                            pid = i.toLong(),
                        ),
                    )
                } finally {
                    done.countDown()
                }
            }
        }
        ready.await(10, TimeUnit.SECONDS)
        go.countDown()
        assertTrue(done.await(30, TimeUnit.SECONDS), "all $n appends finished")
        pool.shutdown()

        val lines = Files.readAllLines(log, Charsets.UTF_8).filter { it.isNotEmpty() }
        assertEquals(n, lines.size, "line count after concurrent append")

        val seen = mutableSetOf<Long>()
        for (line in lines) {
            val entry = json.decodeFromString(ActivityEntry.serializer(), line)
            val pid = entry.pid ?: error("missing pid in line: ${line.take(120)}")
            assertTrue(seen.add(pid), "duplicate pid $pid")
            assertEquals("title-$pid", entry.title, "title mangled at pid $pid")
            assertEquals("proc-$pid", entry.process, "process mangled at pid $pid")
        }
        assertEquals((0 until n).map { it.toLong() }.toSet(), seen, "every pid 0..$n landed exactly once")
    }
}
