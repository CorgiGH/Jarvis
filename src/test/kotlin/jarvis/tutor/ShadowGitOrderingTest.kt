package jarvis.tutor

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Council R3 fix #1 (council-1778325410-layer-b.md) — pre-build gate
 * for the daemon. Shadow-git pre/post-seal MUST happen synchronously
 * around the effector mutation; rollback after a stale pre-seal MUST
 * restore the exact original bytes.
 */
class ShadowGitOrderingTest {

    @Test
    fun preSealCapturesOriginalBytes(@TempDir tmp: Path) {
        val target = tmp.resolve("a.kt")
        target.writeText("fun original() {}\n")
        val shadow = tmp.resolve("shadow")
        val snap = ShadowGit.preSeal("file:///${target.toString().replace('\\','/')}", shadow)
        assertTrue(shadow.resolve("${snap.id}.before").exists())
        assertTrue(shadow.resolve("${snap.id}.meta").exists())
        assertEquals(false, snap.landed)
        // Snapshot bytes equal the file contents pre-mutation.
        val captured = Files.readString(shadow.resolve("${snap.id}.before"))
        assertEquals("fun original() {}\n", captured)
    }

    @Test
    fun postSealFlipsLandedFlag(@TempDir tmp: Path) {
        val target = tmp.resolve("a.kt")
        target.writeText("a")
        val shadow = tmp.resolve("shadow")
        val snap = ShadowGit.preSeal("file:///${target.toString().replace('\\','/')}", shadow)
        val sealed = ShadowGit.postSeal(snap.id, shadow)
        assertEquals(true, sealed.landed)
        // Re-reading the meta confirms persistence.
        val raw = Files.readString(shadow.resolve("${snap.id}.meta"))
        assertTrue(raw.contains("\"landed\":true"))
    }

    @Test
    fun rollbackRestoresExactOriginalBytes(@TempDir tmp: Path) {
        val target = tmp.resolve("file.txt")
        val original = "line one\nline two\n"
        target.writeText(original)
        val shadow = tmp.resolve("shadow")
        val uri = "file:///${target.toString().replace('\\','/')}"
        val snap = ShadowGit.preSeal(uri, shadow)
        // Mutate the file as a daemon effector would.
        target.writeText("DESTROYED\n")
        // Watchdog rolls back.
        val rolled = ShadowGit.rollback(snap.id, shadow)
        assertEquals(snap.id, rolled.id)
        assertEquals(original, Files.readString(target),
            "rollback must restore exact pre-effector bytes")
    }

    @Test
    fun rollbackIdempotent(@TempDir tmp: Path) {
        val target = tmp.resolve("file.txt")
        target.writeText("orig\n")
        val shadow = tmp.resolve("shadow")
        val uri = "file:///${target.toString().replace('\\','/')}"
        val snap = ShadowGit.preSeal(uri, shadow)
        target.writeText("changed\n")
        ShadowGit.rollback(snap.id, shadow)
        ShadowGit.rollback(snap.id, shadow)  // should not throw / corrupt
        assertEquals("orig\n", Files.readString(target))
    }

    @Test
    fun listStaleFindsPreSealedRowsPastDeadline(@TempDir tmp: Path) {
        val target = tmp.resolve("a.kt"); target.writeText("x")
        val shadow = tmp.resolve("shadow")
        val uri = "file:///${target.toString().replace('\\','/')}"
        val snap = ShadowGit.preSeal(uri, shadow)
        // Pretend 5s passed. Watchdog with 2s threshold flags it.
        val later = Instant.now().plus(Duration.ofSeconds(5))
        val stale = ShadowGit.listStale(shadow, Duration.ofSeconds(2), later)
        assertEquals(1, stale.size)
        assertEquals(snap.id, stale[0].id)
        assertEquals(false, stale[0].landed)
    }

    @Test
    fun listStaleSkipsLandedSnapshots(@TempDir tmp: Path) {
        val target = tmp.resolve("a.kt"); target.writeText("x")
        val shadow = tmp.resolve("shadow")
        val uri = "file:///${target.toString().replace('\\','/')}"
        val snap = ShadowGit.preSeal(uri, shadow)
        ShadowGit.postSeal(snap.id, shadow)
        val later = Instant.now().plus(Duration.ofSeconds(60))
        assertEquals(0, ShadowGit.listStale(shadow, Duration.ofSeconds(2), later).size)
    }

    @Test
    fun discardRemovesBothFiles(@TempDir tmp: Path) {
        val target = tmp.resolve("a.kt"); target.writeText("x")
        val shadow = tmp.resolve("shadow")
        val uri = "file:///${target.toString().replace('\\','/')}"
        val snap = ShadowGit.preSeal(uri, shadow)
        ShadowGit.discard(snap.id, shadow)
        assertTrue(!shadow.resolve("${snap.id}.before").exists())
        assertTrue(!shadow.resolve("${snap.id}.meta").exists())
    }

    @Test
    fun snapshotIdsUniquePerFileAndTime(@TempDir tmp: Path) {
        val target = tmp.resolve("a.kt"); target.writeText("identical")
        val shadow = tmp.resolve("shadow")
        val uri = "file:///${target.toString().replace('\\','/')}"
        val s1 = ShadowGit.preSeal(uri, shadow)
        Thread.sleep(2) // ensure ts differs
        val s2 = ShadowGit.preSeal(uri, shadow)
        // Same content but different capture time → different id.
        // (Defends against a snapshot collision when the user reverts a
        // file then re-edits identically: each pre-seal must be unique
        // so the watchdog can match attempts 1:1.)
        assertTrue(s1.id != s2.id, "concurrent snapshots must be uniquely IDed")
    }

    @Test
    fun preSealRejectsNonFileUri() {
        try {
            ShadowGit.preSeal("https://example.com/x", java.nio.file.Files.createTempDirectory("sh"))
            error("should have thrown")
        } catch (e: IllegalArgumentException) {
            assertNotNull(e.message)
            assertTrue(e.message!!.contains("file://"))
        }
    }

    @Test
    fun preSealHandlesMissingTargetByCapturingEmpty(@TempDir tmp: Path) {
        val target = tmp.resolve("never-existed.txt")
        val shadow = tmp.resolve("shadow")
        val uri = "file:///${target.toString().replace('\\','/')}"
        val snap = ShadowGit.preSeal(uri, shadow)
        // Empty before-image — rollback restores file-not-existing as
        // an empty file (acceptable: user can delete after rollback).
        assertEquals(0, snap.originalBytes)
    }
}
