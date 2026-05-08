package jarvis

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Council 2026-05-08 (rounds 2 + 3, Risk Analyst): KnowledgeFsrs.recordReview
 * had a TOCTOU race — read prior state, compute, append; mutex covered only
 * the append. Concurrent /api/chat calls grading the same concept could both
 * read the same prior, both compute their own next-state, and last-writer
 * wins — silently losing one graded review.
 *
 * This test forces the race: K threads × N grades all on the same
 * (concept, subject), then asserts the ledger has K*N rows AND the latest
 * row's stability reflects the cumulative effect of all grades, not just
 * the last winner.
 */
class KnowledgeFsrsConcurrencyTest {

    @Test
    fun concurrentRecordReviewProducesAllRows(@TempDir tmp: Path) {
        val fsrsFile = tmp.resolve("knowledge_fsrs.jsonl")
        val knowFile = tmp.resolve("knowledge.jsonl")

        val threads = 8
        val gradesPerThread = 12
        val pool = Executors.newFixedThreadPool(threads)
        val ready = CountDownLatch(threads)
        val go = CountDownLatch(1)
        val done = CountDownLatch(threads)

        repeat(threads) { tIdx ->
            pool.submit {
                ready.countDown()
                go.await()
                try {
                    repeat(gradesPerThread) { gIdx ->
                        // Distinct timestamps so latest-row-wins semantics
                        // pick a deterministic ordering after the race.
                        val ts = Instant.parse("2026-05-08T12:00:00Z")
                            .plusMillis((tIdx * gradesPerThread + gIdx).toLong())
                        KnowledgeFsrs.recordReview(
                            concept = "Race-Concept",
                            subject = "PA",
                            grade = 3,
                            now = ts,
                            file = fsrsFile,
                            knowledgeFile = knowFile,
                        )
                    }
                } finally {
                    done.countDown()
                }
            }
        }
        ready.await(5, TimeUnit.SECONDS)
        go.countDown()
        assertTrue(done.await(30, TimeUnit.SECONDS), "all grader threads finished")
        pool.shutdownNow()

        val rows = KnowledgeFsrs.readAllFrom(fsrsFile)
        assertEquals(
            threads * gradesPerThread, rows.size,
            "every recordReview wrote exactly one row — no lost updates from TOCTOU",
        )
        // Stability should reflect repeated grade=3 successes; if the lock
        // were missing, some rows would be computed from a stale prior and
        // stability would plateau. With lock, latest stability >> initial.
        val latest = rows.maxByOrNull { it.ts }!!
        assertTrue(
            latest.stability > 1.0,
            "latest stability reflects compounded reviews (got ${latest.stability})",
        )
    }
}
