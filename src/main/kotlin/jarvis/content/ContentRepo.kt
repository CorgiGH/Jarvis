package jarvis.content

import com.charleskorn.kaml.Yaml
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.streams.toList

/** All authored content for one subject, loaded from disk. */
data class LoadedSubject(
    val subject: String,
    val kcs: List<KnowledgeConcept>,
    val edges: List<PrereqEdge>,
    val misconceptions: List<Misconception>,
)

/** Reads the git-tracked content/ corpus into schema objects. [root] is the content/ dir. */
class ContentRepo(private val root: Path) {

    fun loadManifest(): SubjectsManifest {
        val f = root.resolve("subjects.yaml")
        require(f.exists()) { "subjects.yaml not found under $root" }
        return Yaml.default.decodeFromString(SubjectsManifest.serializer(), f.readText())
    }

    fun loadSubject(subject: String): LoadedSubject {
        val dir = root.resolve(subject)
        val kcs = yamlFilesIn(dir.resolve("kcs"))
            .map { Yaml.default.decodeFromString(KnowledgeConcept.serializer(), it.readText()) }
            .sortedBy { it.id }
        val misc = yamlFilesIn(dir.resolve("misconceptions"))
            .map { Yaml.default.decodeFromString(Misconception.serializer(), it.readText()) }
            .sortedBy { it.id }
        val edgesFile = dir.resolve("edges.yaml")
        val edges = if (edgesFile.exists())
            Yaml.default.decodeFromString(EdgesFile.serializer(), edgesFile.readText()).edges
        else emptyList()
        return LoadedSubject(subject, kcs, edges, misc)
    }

    /** Extracted source text for a `_sources/{doc}.md` file, or null if absent. */
    fun sourceText(subject: String, doc: String): String? {
        val f = root.resolve(subject).resolve("_sources").resolve("$doc.md")
        return if (f.exists()) f.readText() else null
    }

    private fun yamlFilesIn(dir: Path): List<Path> {
        if (!dir.exists()) return emptyList()
        return Files.list(dir).use { stream ->
            stream.filter { it.extension == "yaml" && it.name != ".gitkeep" }.sorted().toList()
        }
    }
}
