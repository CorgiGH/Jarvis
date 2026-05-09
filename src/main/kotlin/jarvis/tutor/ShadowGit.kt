package jarvis.tutor

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Instant
import kotlin.io.path.absolute
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

/**
 * Tutor Layer B1 — shadow-git rollback substrate.
 *
 * Council R3 fix #1 (council-1778325410-layer-b.md): the spec said
 * "shadow-git pre-commit blocks effector return" but didn't specify
 * the state machine. Without an explicit pre/post-seal pair every
 * effector dispatch is a TOCTOU between "snapshot taken" and "commit
 * landed", which is exactly when crash + retry produces orphaned
 * shadow refs that can't roll back the live edit.
 *
 * This module gives the dispatcher three primitives:
 *   1. preSeal(targetUri)  — snapshot the file BEFORE the daemon
 *      touches it. Returns a snapshotId (sha256 of bytes + ts).
 *   2. postSeal(snapshotId) — record that the effector landed
 *      successfully; pair the snapshot with a "landed" marker.
 *   3. rollback(snapshotId) — restore the original bytes to the
 *      target path. Used by the watchdog when an attempt is left
 *      pre_sealed but never post_sealed for >2s.
 *
 * Layout:
 *   <ledgerDir>/shadow/<snapshotId>.before  — original file bytes
 *   <ledgerDir>/shadow/<snapshotId>.meta    — JSON {targetUri, ts, landed}
 *
 * Council R3 also said the shadow root should be a separate git repo,
 * not a worktree of the user's project. We DON'T spawn `git` here —
 * a content-addressed snapshot directory is sufficient for rollback
 * (and avoids depending on `git` being on PATH inside the daemon).
 * The "git" name is metaphor; real git can be layered on top later
 * for browse-history UX.
 */
object ShadowGit {

    data class Snapshot(
        val id: String,
        val targetUri: String,
        val capturedAt: Instant,
        val landed: Boolean,
        val originalBytes: Int,
    )

    /** Snapshot the current bytes of [targetUri] before an effector
     *  fires. Returns the snapshotId callers persist on the
     *  EffectorAttempt row so the watchdog can find it later. */
    fun preSeal(targetUri: String, shadowRoot: Path): Snapshot {
        require(targetUri.startsWith("file://")) {
            "shadow-git only handles file:// URIs in B1; got $targetUri"
        }
        val target = uriToPath(targetUri)
        val bytes = if (target.exists()) Files.readAllBytes(target) else ByteArray(0)
        val id = makeSnapshotId(targetUri, bytes, Instant.now())
        shadowRoot.createDirectories()
        Files.write(shadowRoot.resolve("$id.before"), bytes)
        val meta = """{"targetUri":${quote(targetUri)},"ts":${quote(Instant.now().toString())},"landed":false}"""
        Files.write(shadowRoot.resolve("$id.meta"), meta.toByteArray(Charsets.UTF_8))
        return Snapshot(id, targetUri, Instant.now(), false, bytes.size)
    }

    /** Mark the snapshot as landed — effector fired + the live file
     *  now reflects the edit. Subsequent rollback still possible. */
    fun postSeal(snapshotId: String, shadowRoot: Path): Snapshot {
        val metaPath = shadowRoot.resolve("$snapshotId.meta")
        require(metaPath.exists()) { "no shadow snapshot $snapshotId in $shadowRoot" }
        val cur = Files.readString(metaPath, Charsets.UTF_8)
        val updated = cur.replace("\"landed\":false", "\"landed\":true")
        Files.writeString(metaPath, updated, Charsets.UTF_8)
        val parsed = parseMeta(snapshotId, updated)
        val sizeOnDisk = Files.size(shadowRoot.resolve("$snapshotId.before")).toInt()
        return parsed.copy(originalBytes = sizeOnDisk)
    }

    /** Restore the original bytes captured at preSeal back to the
     *  target file. Idempotent — a second rollback is a no-op (the
     *  bytes already match). Used by the watchdog after a 2s timeout
     *  on pre_sealed-but-never-post_sealed attempts, and by manual
     *  user "undo last APPLY" actions. */
    fun rollback(snapshotId: String, shadowRoot: Path): Snapshot {
        val metaPath = shadowRoot.resolve("$snapshotId.meta")
        val beforePath = shadowRoot.resolve("$snapshotId.before")
        require(metaPath.exists() && beforePath.exists()) {
            "no shadow snapshot $snapshotId in $shadowRoot"
        }
        val meta = parseMeta(snapshotId, Files.readString(metaPath, Charsets.UTF_8))
        val target = uriToPath(meta.targetUri)
        target.parent?.createDirectories()
        Files.copy(beforePath, target, StandardCopyOption.REPLACE_EXISTING)
        return meta
    }

    /** Watchdog scan: returns ids of snapshots stuck in pre_sealed
     *  state past [staleAfter]. Caller is expected to feed each into
     *  [rollback] then delete the shadow row, OR escalate to user. */
    fun listStale(
        shadowRoot: Path,
        staleAfter: java.time.Duration,
        now: Instant = Instant.now(),
    ): List<Snapshot> {
        if (!shadowRoot.exists() || !shadowRoot.isDirectory()) return emptyList()
        val out = mutableListOf<Snapshot>()
        Files.list(shadowRoot).use { stream ->
            stream.filter { it.fileName.toString().endsWith(".meta") }
                .forEach { metaPath ->
                    val id = metaPath.fileName.toString().removeSuffix(".meta")
                    val meta = parseMeta(id, Files.readString(metaPath, Charsets.UTF_8))
                    if (meta.landed) return@forEach
                    val age = java.time.Duration.between(meta.capturedAt, now)
                    if (age >= staleAfter) out += meta
                }
        }
        return out
    }

    /** Delete a snapshot — only safe AFTER the EffectorAttempt row
     *  has terminal status (success/rolled_back/failed). */
    fun discard(snapshotId: String, shadowRoot: Path) {
        Files.deleteIfExists(shadowRoot.resolve("$snapshotId.before"))
        Files.deleteIfExists(shadowRoot.resolve("$snapshotId.meta"))
    }

    private fun makeSnapshotId(targetUri: String, bytes: ByteArray, ts: Instant): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(targetUri.toByteArray(Charsets.UTF_8))
        md.update(bytes)
        md.update(ts.toString().toByteArray(Charsets.UTF_8))
        return md.digest().joinToString("") { "%02x".format(it) }.take(16)
    }

    private fun parseMeta(id: String, json: String): Snapshot {
        val target = parseField(json, "targetUri") ?: error("$id: missing targetUri")
        val tsStr = parseField(json, "ts") ?: error("$id: missing ts")
        val landed = json.contains("\"landed\":true")
        return Snapshot(
            id = id,
            targetUri = target,
            capturedAt = Instant.parse(tsStr),
            landed = landed,
            originalBytes = 0, // filled by callers that care
        )
    }

    private fun parseField(json: String, key: String): String? {
        val needle = "\"$key\":\""
        val start = json.indexOf(needle)
        if (start < 0) return null
        val from = start + needle.length
        val end = json.indexOf('"', from)
        if (end < 0) return null
        return json.substring(from, end)
    }

    private fun quote(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun uriToPath(uri: String): Path {
        require(uri.startsWith("file://")) { "expected file:// URI; got $uri" }
        // file:///c/path on Windows / file:///path on POSIX. Strip scheme.
        var stripped = uri.removePrefix("file://")
        // file:///C:/foo on Windows: leading slash before drive letter needs
        // to come off so Path.of("/C:/foo") doesn't trip on the colon.
        if (stripped.length >= 4 && stripped[0] == '/' && stripped[2] == ':') {
            stripped = stripped.substring(1)
        }
        return Path.of(stripped).absolute()
    }
}
