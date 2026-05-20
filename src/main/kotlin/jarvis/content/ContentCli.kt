package jarvis.content

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.system.exitProcess

object ContentCli {

    /** Loads every subject in the manifest, validates, and regenerates edges.mmd files. */
    fun runValidation(contentDir: Path): ValidationReport {
        val repo = ContentRepo(contentDir)
        val manifest = repo.loadManifest()
        val subjects = manifest.subjects.map { repo.loadSubject(it.id) }

        // Regenerate each subject's edges.mmd mirror (only if edges.yaml exists).
        for (entry in manifest.subjects) {
            val edgesYaml = contentDir.resolve(entry.id).resolve("edges.yaml")
            if (edgesYaml.exists()) {
                val loaded = repo.loadSubject(entry.id)
                val mmd = MermaidMirror.render(EdgesFile(entry.id, loaded.edges))
                contentDir.resolve(entry.id).resolve("edges.mmd").writeText(mmd + "\n")
            }
        }

        return ContentValidator.validate(subjects) { doc ->
            // doc ids are unique across subjects in practice; search each subject's _sources.
            manifest.subjects.firstNotNullOfOrNull { repo.sourceText(it.id, doc) }
        }
    }
}

fun main(args: Array<String>) {
    val dir = Path.of(args.firstOrNull() ?: "content")
    if (!dir.exists()) {
        System.err.println("[validateContent] content dir not found: $dir")
        exitProcess(2)
    }
    val report = ContentCli.runValidation(dir)
    println("[validateContent] ${report.disclaimer}")
    for (issue in report.issues) {
        println("  [${issue.severity.uppercase()}] ${issue.subject}/${issue.rule}: ${issue.detail}")
    }
    if (report.ok) {
        println("[validateContent] OK — ${report.issues.size} warning(s), 0 errors")
        exitProcess(0)
    } else {
        val errs = report.issues.count { it.severity == "error" }
        System.err.println("[validateContent] FAILED — $errs error(s)")
        exitProcess(1)
    }
}
