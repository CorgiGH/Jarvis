package jarvis

import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId

/**
 * Build a directive snippet for a drift signal — concrete "get back to X,
 * here is what to do" payload that the user-facing surfaces (PC modal,
 * Android heads-up notification, Telegram push) render verbatim.
 *
 * User feedback 2026-05-09: a popup that says "off-track" without telling
 * the user where to go and what to study is useless. This module replaces
 * the ctx-model-output snippet for drift-class signals with a deterministic
 * directive built from the schedule + assignments + concept catalog.
 *
 * The output stays under 200 chars (matches [ProactiveSignal] snippet cap).
 *
 * Format:
 *   GET BACK TO {SUBJECT}{topic?}. NEXT: {assignment} (due {YYYY-MM-DD} {Nd}). [OPEN: {path}.] [[lesson: {SUBJECT}]]
 *
 * If no open assignment exists for the subject, falls back to the weakest
 * stale concept; if neither, falls back to a "study any {SUBJECT}" hint.
 */
object DriftDirective {

    fun build(
        drift: ActiveDoc.Drift,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.of("Europe/Bucharest"),
        archivalRoot: Path = Config.archivalDir,
    ): String {
        val subject = drift.expectedSubject
        val topic = drift.expectedTopic?.takeIf { it.isNotBlank() }
        val assignment = nearestActiveAssignmentFor(subject, now, zone)
        val concept = if (assignment == null) weakestStaleFor(subject) else null
        val openPath = when {
            drift.actualConcept != null -> null  // user already saw a concept; no helpful "open"
            concept != null -> concept.sourcePath
            else -> firstArchivalForSubject(subject, archivalRoot)
        }

        val sb = StringBuilder()
        sb.append("GET BACK TO ").append(subject)
        if (topic != null) sb.append("/").append(topic)
        sb.append(". ")
        when {
            assignment != null -> {
                val due = assignment.dueDate ?: "no-due"
                val daysTag = assignment.daysToDue?.let { d ->
                    when {
                        d < 0 -> "OVERDUE ${-d}d"
                        d == 0L -> "DUE TODAY"
                        d == 1L -> "1d"
                        else -> "${d}d"
                    }
                } ?: ""
                sb.append("NEXT: ").append(assignment.title.take(60))
                sb.append(" (").append(due)
                if (daysTag.isNotEmpty()) sb.append(" ").append(daysTag)
                sb.append("). ")
            }
            concept != null -> {
                sb.append("STUDY: ").append(concept.name.take(50)).append(". ")
            }
            else -> {
                sb.append("STUDY: any ").append(subject).append(" lecture. ")
            }
        }
        if (openPath != null && openPath.isNotBlank()) {
            sb.append("OPEN: ").append(openPath.take(60)).append(". ")
        }
        sb.append("Chat: [[lesson: ").append(subject).append("]]")
        // Hard cap to 200 chars; ProactiveSignal.snippet field convention.
        return sb.toString().take(200)
    }

    private fun nearestActiveAssignmentFor(
        subject: String, now: Instant, zone: ZoneId,
    ): AssignmentView? {
        val active = try {
            Assignments.current(now, zone).filter { it.subject == subject && it.status != "done" }
        } catch (_: Exception) { emptyList() }
        return active.sortedBy { it.daysToDue ?: Long.MAX_VALUE }.firstOrNull()
    }

    private fun weakestStaleFor(subject: String): ConceptCatalog.Concept? {
        return try {
            val stale = KnowledgeState.staleConcepts(confidenceFloor = 0.6f, minStaleDays = 1L)
                .firstOrNull { it.subject.equals(subject, ignoreCase = true) }
            stale?.let { st ->
                ConceptCatalog.all().firstOrNull {
                    it.subject.equals(subject, ignoreCase = true) && it.name == st.concept
                }
            } ?: ConceptCatalog.all().firstOrNull {
                it.subject.equals(subject, ignoreCase = true)
            }
        } catch (_: Exception) { null }
    }

    private fun firstArchivalForSubject(subject: String, archivalRoot: Path): String? {
        val sub = archivalRoot.resolve("_extras/$subject")
        if (!java.nio.file.Files.isDirectory(sub)) return null
        return try {
            java.nio.file.Files.walk(sub).use { stream ->
                stream
                    .filter { java.nio.file.Files.isRegularFile(it) }
                    .filter { it.fileName.toString().endsWith(".md", ignoreCase = true) }
                    .map { archivalRoot.relativize(it).toString().replace('\\', '/') }
                    .findFirst().orElse(null)
            }
        } catch (_: Exception) { null }
    }
}
