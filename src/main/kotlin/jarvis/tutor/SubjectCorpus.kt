package jarvis.tutor

import jarvis.Config
import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

/**
 * Tutor task-context (V1 follow-up) — exposes per-subject materials
 * Jarvis was previously blind to.
 *
 * Layout discovery, NOT content owned by ConceptCatalog. Each subject
 * under `archival/_extras/<SUBJECT>/` has subdirs:
 *   courses/      — lecture notes (md + pdf)
 *   seminars/     — seminar exercises
 *   labs/         — lab assignments
 *   hw/           — homework / Tema (alo_t1, alo_t2, ...)
 *   practice/     — practice problems
 *   study_guide/  — curated study materials (tests/ subdirs hold past
 *                   exam JSONs, source/ holds raw model partials)
 *   local_extras/ — user-private notes
 *
 * ConceptCatalog blocklists both `_extras/` and `tests/` so its
 * `## ` heading walker never indexed any of this. SubjectCorpus
 * provides a parallel substring-search path that ignores the
 * blocklist — the LLM gets to reach past-exam material when the
 * user asks "give me a similar partial from 2017".
 *
 * Lookup ranks: filename matches > snippet hit count.
 */
object SubjectCorpus {

    /** Subject mapping + alias resolution — `_extras` uses short codes
     *  that don't always match the schedule subject naming. */
    private val SUBJECT_ALIASES = mapOf(
        "PA" to "PA", "PS" to "PS", "POO" to "POO", "ALO" to "ALO",
        "SO" to "SO", "SO&RC" to "SO", "RC" to "RC",
        "OS" to "SO",
    )

    /** Recognized kind subdirs (case-insensitive). Empty kind = all. */
    private val KNOWN_KINDS = setOf(
        "courses", "seminars", "labs", "hw", "practice",
        "study_guide", "study_guide_curated",
        "tests", "source", "local_extras",
    )

    @Serializable
    data class CorpusHit(
        val subject: String,
        val kind: String,        // courses / seminars / hw / tests / ...
        val relPath: String,     // archival-relative path
        val score: Int,          // weighted: 100 for filename hit, +1 per snippet substring
        val snippet: String,
    )

    /** Walk the subject directory and grep across markdown content. */
    fun search(
        subjectQuery: String,
        query: String,
        kindFilter: String? = null,
        k: Int = 5,
        archivalRoot: Path = Config.archivalDir,
    ): List<CorpusHit> {
        val subj = resolveSubject(subjectQuery) ?: return emptyList()
        val root = archivalRoot.resolve("_extras").resolve(subj)
        if (!root.exists() || !root.isDirectory()) return emptyList()
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val kindLower = kindFilter?.lowercase()?.takeIf { it.isNotBlank() }

        val out = mutableListOf<CorpusHit>()
        Files.walk(root).use { stream ->
            stream.filter { it.isRegularFile() && it.extension.equals("md", true) }
                .forEach { p ->
                    val rel = runCatching { archivalRoot.relativize(p).toString().replace('\\', '/') }
                        .getOrElse { return@forEach }
                    val kind = inferKind(rel) ?: return@forEach
                    if (kindLower != null && kind != kindLower) return@forEach
                    val text = try { p.readText(Charsets.UTF_8) } catch (_: Exception) { return@forEach }
                    val low = text.lowercase()
                    val nameHit = if (p.fileName.toString().lowercase().contains(q)) 100 else 0
                    val snippetHits = countSubstring(low, q)
                    if (nameHit + snippetHits == 0) return@forEach
                    out += CorpusHit(
                        subject = subj,
                        kind = kind,
                        relPath = rel,
                        score = nameHit + snippetHits,
                        snippet = extractSnippet(text, q).take(600),
                    )
                }
        }
        return out.sortedByDescending { it.score }.take(k)
    }

    /** List the kind subdirs the subject actually has (so LLM knows
     *  whether to ask for `kind=tests` or `kind=hw` etc). */
    fun listKinds(subjectQuery: String, archivalRoot: Path = Config.archivalDir): List<String> {
        val subj = resolveSubject(subjectQuery) ?: return emptyList()
        val root = archivalRoot.resolve("_extras").resolve(subj)
        if (!root.exists() || !root.isDirectory()) return emptyList()
        val out = mutableSetOf<String>()
        Files.list(root).use { stream ->
            stream.filter { it.isDirectory() }.forEach { d ->
                val name = d.fileName.toString().lowercase()
                if (name in KNOWN_KINDS) out += name
                // Recurse one level deeper for study_guide → tests
                if (name == "study_guide" || name == "study_guide_curated") {
                    runCatching {
                        Files.list(d).use { inner ->
                            inner.filter { it.isDirectory() }.forEach { sub ->
                                val n = sub.fileName.toString().lowercase()
                                if (n in KNOWN_KINDS) out += "$name/$n"
                            }
                        }
                    }
                }
            }
        }
        return out.toList().sorted()
    }

    internal fun resolveSubject(raw: String): String? {
        val trimmed = raw.trim()
        SUBJECT_ALIASES[trimmed]?.let { return it }
        SUBJECT_ALIASES[trimmed.uppercase()]?.let { return it }
        return null
    }

    /** Infer the kind from path components. Specific subkinds (tests,
     *  source) take precedence over their parents (study_guide,
     *  study_guide_curated) so a file under `study_guide/tests/` is
     *  classified `tests`, not `study_guide`. */
    internal fun inferKind(rel: String): String? {
        val parts = rel.split('/').map { it.lowercase() }
        val priority = listOf(
            "tests", "source", "labs", "hw", "seminars",
            "practice", "courses", "local_extras",
            "study_guide_curated", "study_guide",
        )
        for (kind in priority) if (kind in parts) return kind
        return null
    }

    private fun countSubstring(haystack: String, needle: String): Int {
        if (needle.isEmpty()) return 0
        var count = 0; var idx = 0
        while (true) {
            val found = haystack.indexOf(needle, idx)
            if (found < 0) break
            count++; idx = found + needle.length
        }
        return count
    }

    private fun extractSnippet(text: String, needle: String): String {
        if (needle.isEmpty()) return text.take(400)
        val idx = text.lowercase().indexOf(needle)
        if (idx < 0) return text.lineSequence()
            .filter { it.isNotBlank() }.take(6).joinToString("\n")
        val start = (idx - 200).coerceAtLeast(0)
        val end = (idx + needle.length + 400).coerceAtMost(text.length)
        return (if (start > 0) "…" else "") + text.substring(start, end) + (if (end < text.length) "…" else "")
    }
}
