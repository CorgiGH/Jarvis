package jarvis

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.fileSize

/**
 * Council retro 2026-05-08 (Risk Auditor CRITICAL): unbounded JSONL growth.
 * Rotation primitive: when file exceeds [maxBytes], rename to `<name>.1`
 * (overwriting any prior .1) and start fresh. One historical archive only —
 * single-user scale doesn't need multi-generation log retention.
 *
 * Called from each writer's append path under the same lock guarding writes.
 * Cheap: Files.size() is a stat call; rename is atomic on same-volume fs.
 */
object JsonlRotate {

    const val DEFAULT_MAX_BYTES = 10L * 1024 * 1024  // 10 MB

    /** Rotate [file] to `<file>.1` if size ≥ [maxBytes]. No-op otherwise.
     *  Idempotent + safe to call before every append. Preserves history
     *  across multiple rotations: if `.1` already exists, prepends current
     *  primary to it (oldest-first ordering) so no data loss. Returns true
     *  if it rotated, false if skipped. */
    fun maybeRotate(file: Path, maxBytes: Long = DEFAULT_MAX_BYTES): Boolean {
        if (!file.exists()) return false
        val size = try { file.fileSize() } catch (_: Exception) { return false }
        if (size < maxBytes) return false
        val archive = file.resolveSibling("${file.fileName}.1")
        return try {
            if (archive.exists()) {
                // Combine: archive (older) + current (newer) → archive. Then
                // primary truncated. Atomic-ish via temp rename.
                val temp = file.resolveSibling("${file.fileName}.1.tmp")
                Files.copy(archive, temp, StandardCopyOption.REPLACE_EXISTING)
                Files.write(
                    temp,
                    Files.readAllBytes(file),
                    java.nio.file.StandardOpenOption.APPEND,
                )
                Files.move(temp, archive, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                Files.delete(file)
            } else {
                Files.move(file, archive, StandardCopyOption.REPLACE_EXISTING)
            }
            true
        } catch (e: Exception) {
            System.err.println(
                "[JsonlRotate] WARN failed to rotate $file: ${e.message?.take(160)}",
            )
            false
        }
    }
}
