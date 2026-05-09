package jarvis

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Cross-loop durability harness.
 *
 * Gap addressed: existing concurrency tests cover one writer at a time
 * (MemoryWiki, KnowledgeFsrs). Production runs ProactiveLoop +
 * BlockReminder + ReflectionLoop concurrently against the SAME
 * signals.jsonl with rotation kicking in mid-stream. This harness
 * proves cross-writer rotation safety + StateCache persistence under
 * concurrent refresh.
 */
class IntegrationHarnessTest {

    /**
     * Three writer pools simulate ProactiveLoop / BlockReminder /
     * ReflectionLoop pumping into one signals.jsonl with a 4 KB rotation
     * cap so multiple rotations fire mid-run. Invariant: every appended
     * signal id appears exactly once across primary + archive after the
     * dust settles. No JSON parse errors. No duplicate ids.
     */
    @Test
    fun signalsCrossWriterRotationSurvives(@TempDir tmp: Path) {
        val signalsFile = tmp.resolve("signals.jsonl")
        val perPool = 400
        val pools = listOf("proactive", "reminder", "reflection")
        val total = pools.size * perPool
        val rotateBytes = 4L * 1024  // tiny cap — forces several rotations

        val executor = Executors.newFixedThreadPool(3 * 8)
        val ready = CountDownLatch(pools.size * 8)
        val go = CountDownLatch(1)
        val done = CountDownLatch(pools.size * 8)

        for (poolIdx in pools.indices) {
            val tag = pools[poolIdx]
            // 8 threads per pool; each thread writes perPool / 8 = 50.
            repeat(8) { tIdx ->
                executor.submit {
                    ready.countDown()
                    go.await()
                    try {
                        val baseTs = Instant.parse("2026-05-09T08:00:00Z")
                        repeat(perPool / 8) { i ->
                            val nonce = poolIdx * 10_000 + tIdx * 1_000 + i
                            val ts = baseTs.plusMillis(nonce.toLong())
                            val signal = ProactiveSignal(
                                id = "%s-%05d".format(tag, nonce),
                                ts = ts.toString(),
                                kind = when (tag) {
                                    "reflection" -> "reflection"
                                    "reminder" -> "reminder"
                                    else -> "ctx_model_summary"
                                },
                                importance = 0.75f,
                                sourceTs = ts.toString(),
                                snippet = "$tag payload nonce=$nonce padding ${"x".repeat(40)}",
                                rationale = "harness $tag",
                                status = "emitted",
                            )
                            // appendTo internally calls JsonlRotate.maybeRotate
                            // under the same lock that guards the write.
                            Signals.appendToWithCap(signalsFile, signal, rotateBytes)
                        }
                    } finally {
                        done.countDown()
                    }
                }
            }
        }

        assertTrue(ready.await(10, TimeUnit.SECONDS), "all writer threads ready")
        go.countDown()
        assertTrue(done.await(60, TimeUnit.SECONDS), "all writer threads finished")
        executor.shutdownNow()

        // Read primary + archive and assert full survival. Archive is now
        // gzipped at .1.gz, so parse via JsonlRotate.readArchiveLines and
        // hand-decode each row through the same Signals JSON serializer.
        val primary = Signals.readAllFrom(signalsFile)
        val archived = Signals.readArchiveOf(signalsFile)
        val all = primary + archived
        assertEquals(total, all.size, "every signal survived rotation across writers")
        val ids = all.map { it.id }
        assertEquals(total, ids.toSet().size, "no duplicate ids across primary+archive")
        // Each pool should be represented in roughly equal counts.
        val byPool = all.groupingBy { it.id.substringBefore('-') }.eachCount()
        for (tag in pools) {
            assertEquals(perPool, byPool[tag], "pool $tag count")
        }
        // No row should have status=error (the test never injects one).
        assertTrue(all.none { it.status == "error" }, "no error rows from harness")
    }

    /**
     * StateCache.persist + ensureLoaded correctness under concurrent
     * refresh-mock. Writes K distinct UserStates from K threads to the
     * same file, then resets cell + ensureLoaded. Asserts the file
     * parses cleanly (no torn JSON) and the loaded state matches one of
     * the writers.
     */
    @Test
    fun stateCacheConcurrentPersistNeverTearsJson(@TempDir tmp: Path) {
        val file = tmp.resolve("state_cache.json")
        val k = 32
        val pool = Executors.newFixedThreadPool(k)
        val ready = CountDownLatch(k)
        val go = CountDownLatch(1)
        val done = CountDownLatch(k)

        repeat(k) { i ->
            pool.submit {
                ready.countDown()
                go.await()
                try {
                    val s = UserState(
                        ts = Instant.parse("2026-05-09T10:00:00Z").plusSeconds(i.toLong()).toString(),
                        text = "writer-$i ${"y".repeat(80)}",
                        model = "m-$i",
                    )
                    StateCache.persist(s, file)
                } finally {
                    done.countDown()
                }
            }
        }
        assertTrue(ready.await(5, TimeUnit.SECONDS))
        go.countDown()
        assertTrue(done.await(15, TimeUnit.SECONDS), "all persist threads finished")
        pool.shutdownNow()

        // File must exist and parse to a valid UserState — the truncate +
        // write sequence is not atomic at OS level, so this proves we
        // never observe a half-written file when K threads contend.
        assertTrue(file.exists(), "file written")
        StateCache.resetForTests()
        StateCache.ensureLoaded(file)
        val loaded = StateCache.current(Instant.parse("2026-05-09T10:00:00Z"))
        assertNotNull(loaded, "loaded state parsed")
        assertTrue(
            loaded!!.text.startsWith("writer-"),
            "loaded one of the writers, got: ${loaded.text.take(40)}",
        )
        assertTrue(loaded.model.startsWith("m-"), "model field intact")
    }

    /**
     * Kitchen-sink: signals + FSRS + state-cache writers all racing on
     * the same temp dir. Verifies no cross-file corruption (tempfiles
     * from one writer don't clobber another's file) and no exceptions
     * leak out of any writer.
     */
    @Test
    fun mixedWritersNoCrossCorruption(@TempDir tmp: Path) = runBlocking {
        val signalsFile = tmp.resolve("signals.jsonl")
        val fsrsFile = tmp.resolve("knowledge_fsrs.jsonl")
        val knowFile = tmp.resolve("knowledge.jsonl")
        val stateFile = tmp.resolve("state_cache.json")

        val pool = Executors.newFixedThreadPool(12)
        val ready = CountDownLatch(12)
        val go = CountDownLatch(1)
        val done = CountDownLatch(12)

        // 4 signals writers
        repeat(4) { i ->
            pool.submit {
                ready.countDown(); go.await()
                try {
                    repeat(50) { j ->
                        Signals.appendTo(
                            signalsFile,
                            ProactiveSignal(
                                id = "sig-%02d-%02d".format(i, j),
                                ts = Instant.parse("2026-05-09T08:00:00Z").plusMillis((i * 100 + j).toLong()).toString(),
                                kind = "ctx_model_summary",
                                importance = 0.6f,
                                sourceTs = "src",
                                snippet = "p$i-$j",
                                rationale = "mix",
                            ),
                        )
                    }
                } finally { done.countDown() }
            }
        }
        // 4 FSRS writers
        repeat(4) { i ->
            pool.submit {
                ready.countDown(); go.await()
                try {
                    repeat(20) { j ->
                        KnowledgeFsrs.recordReview(
                            concept = "Concept-$i",
                            subject = "PA",
                            grade = ((j % 4) + 1),
                            now = Instant.parse("2026-05-09T08:00:00Z").plusMillis((i * 100 + j).toLong()),
                            file = fsrsFile,
                            knowledgeFile = knowFile,
                        )
                    }
                } finally { done.countDown() }
            }
        }
        // 4 StateCache writers
        repeat(4) { i ->
            pool.submit {
                ready.countDown(); go.await()
                try {
                    repeat(10) { j ->
                        StateCache.persist(
                            UserState(
                                ts = Instant.parse("2026-05-09T08:00:00Z").plusSeconds((i * 10 + j).toLong()).toString(),
                                text = "mix-$i-$j",
                                model = "m",
                            ),
                            stateFile,
                        )
                    }
                } finally { done.countDown() }
            }
        }

        assertTrue(ready.await(10, TimeUnit.SECONDS))
        go.countDown()
        assertTrue(done.await(60, TimeUnit.SECONDS), "all mixed writers finished")
        pool.shutdownNow()

        // Each file holds the right shape — no cross-bleed.
        val sigs = Signals.readAllFrom(signalsFile)
        assertEquals(200, sigs.size, "signals count")
        val fsrs = KnowledgeFsrs.readAllFrom(fsrsFile)
        assertEquals(80, fsrs.size, "fsrs count")
        // state_cache.json parses to a single valid record.
        StateCache.resetForTests()
        StateCache.ensureLoaded(stateFile)
        assertNotNull(StateCache.current(Instant.parse("2026-05-09T08:00:00Z")))
        // Tempfiles from KnowledgeFsrs / JsonlRotate must be cleaned up.
        val leftover = Files.list(tmp).use { it.toList() }
            .filter { it.fileName.toString().contains(".tmp") }
        assertTrue(leftover.isEmpty(), "no .tmp files left behind: $leftover")
    }
}
