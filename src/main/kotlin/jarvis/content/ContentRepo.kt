package jarvis.content

import com.charleskorn.kaml.Yaml
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.readText

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

    /** E3: load content/viz-ids.yaml into a set. Empty if the file is absent. */
    fun loadVizIds(): Set<String> {
        val f = root.resolve("viz-ids.yaml")
        if (!f.exists()) return emptySet()
        return Yaml.default.decodeFromString(VizIdsFile.serializer(), f.readText()).viz_ids.toSet()
    }

    /**
     * Plan-2 Task 3 — load content/{subject}/viz/ YAML files into typed [VizInstance] rows, mirroring
     * the KC loader (one YAML per instance, sorted by id). An absent/empty viz/ dir yields an empty
     * list (every subject today). Unknown family_id is ALLOWED here (the family registry lands in
     * Plan 3); a duplicate instance id WITHIN the subject is a load error (instance ids must be unique).
     */
    fun loadVizInstances(subject: String): List<VizInstance> {
        val dir = root.resolve(subject).resolve("viz")
        val instances = yamlFilesIn(dir)
            .map { Yaml.default.decodeFromString(VizInstance.serializer(), it.readText()) }
            .sortedBy { it.id }
        val firstDup = instances.groupingBy { it.id }.eachCount().entries.firstOrNull { it.value > 1 }
        check(firstDup == null) {
            "duplicate viz instance id '${firstDup!!.key}' in subject '$subject' " +
                "(${firstDup.value} files share it) — instance ids must be unique per subject"
        }
        // Parse-validate data_json at load (§0.9D contract): malformed JSON = load error naming the instance.
        for (inst in instances) {
            runCatching { inst.instance.dataElement() }.getOrElse {
                error("viz instance '${inst.id}' (subject '$subject') has malformed data_json: ${it.message}")
            }
        }
        return instances
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
