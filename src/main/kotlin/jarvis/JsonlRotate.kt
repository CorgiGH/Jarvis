package jarvis

import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.io.path.exists
import kotlin.io.path.fileSize

/**
 * Council retro 2026-05-08 (Risk Auditor CRITICAL): unbounded JSONL growth.
 * Rotation primitive: when file exceeds [maxBytes], compress + concatenate
 * into `<name>.1.gz`. JSONL gzips ~10x — single archive holds many
 * generations of history without unbounded disk creep.
 *
 * 2026-05-09 user-listed compaction: rotation alone never compressed, so
 * the .1 archive grew at the same rate as primary. Now archives are
 * gzipped on write.
 *
 * Called from each writer's append path under the same lock guarding writes.
 * Cheap: Files.size() is a stat call; gzip cost amortizes at the rotation
 * boundary (rare in steady state).
 */
object JsonlRotate {

    const val DEFAULT_MAX_BYTES = 10L * 1024 * 1024  // 10 MB

    /** Returns the canonical archive path for [file]: `<file>.1.gz`. */
    fun archivePath(file: Path): Path =
        file.resolveSibling("${file.fileName}.1.gz")

    /** Rotate [file] to gzipped `<file>.1.gz` if size ≥ [maxBytes]. No-op
     *  otherwise. Idempotent + safe to call before every append. Preserves
     *  history across multiple rotations: if archive exists, append-then-
     *  recompress so no data loss. Returns true if it rotated. */
    fun maybeRotate(file: Path, maxBytes: Long = DEFAULT_MAX_BYTES): Boolean {
        if (!file.exists()) return false
        val size = try { file.fileSize() } catch (_: Exception) { return false }
        if (size < maxBytes) return false
        val archive = archivePath(file)
        val temp = file.resolveSibling("${file.fileName}.1.gz.tmp")
        return try {
            // Decompress prior archive (if any), append current primary,
            // recompress to temp, atomic-move into place. Combined buffer
            // is in memory — single-user scale, primary capped at maxBytes
            // and archive decompresses to ~similar order of magnitude.
            val buf = ByteArrayOutputStream()
            GZIPOutputStream(buf).use { gz ->
                if (archive.exists()) {
                    Files.newInputStream(archive).use { fis ->
                        GZIPInputStream(fis).use { gzin ->
                            gzin.copyTo(gz)
                        }
                    }
                }
                Files.newInputStream(file).use { it.copyTo(gz) }
            }
            Files.write(
                temp,
                buf.toByteArray(),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            )
            Files.move(temp, archive, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            Files.delete(file)
            true
        } catch (e: Exception) {
            System.err.println(
                "[JsonlRotate] WARN failed to rotate $file: ${e.message?.take(160)}",
            )
            try { Files.deleteIfExists(temp) } catch (_: Exception) {}
            false
        }
    }

    /** Read archive contents as decompressed text. Returns null if no
     *  archive exists. Used by tests + any future replay path. */
    fun readArchiveText(file: Path): String? {
        val archive = archivePath(file)
        if (!archive.exists()) return null
        return try {
            Files.newInputStream(archive).use { fis ->
                GZIPInputStream(fis).use { gzin ->
                    gzin.readBytes().toString(Charsets.UTF_8)
                }
            }
        } catch (e: Exception) {
            System.err.println(
                "[JsonlRotate] WARN failed to read archive $archive: ${e.message?.take(160)}",
            )
            null
        }
    }

    /** Convenience: line-oriented archive read for JSONL. */
    fun readArchiveLines(file: Path): List<String> =
        readArchiveText(file)?.lineSequence()?.filter { it.isNotBlank() }?.toList()
            ?: emptyList()
}
