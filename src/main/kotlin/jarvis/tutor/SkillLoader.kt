package jarvis.tutor

import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText

/**
 * Tutor task-context V1 (Stage C) — SKILL.md packaging convention.
 *
 * Council 2026-05-09 recommended: don't build a parallel SKILL.md
 * runtime; promote the most-used flows into native Claude-Code skills
 * under `.claude/skills/`. We adopt the SHAPE for SERVER-SIDE flows
 * (lab-evaluator, exam-generator, gap-detector) so the same markdown
 * file can be lifted into Claude-Code later without re-authoring.
 *
 * Layout (each skill is a directory under tutor/skills/<name>/):
 *
 *   SKILL.md       — frontmatter (`name`, `description`, optional
 *                    `triggers`, `tool_allowlist`) + system prompt body
 *   TOOLS.md       — optional, additional tool descriptions injected
 *                    after JarvisToolDefs.openAiToolDefs
 *   AGENTS.md      — optional, sub-agent role definitions for skills
 *                    that fan out
 *
 * Frontmatter format mirrors the user's existing memory-files +
 * Anthropic Skills convention:
 *
 *   ---
 *   name: lab-evaluator
 *   description: Grade a user's lab attempt against the rubric
 *   triggers: lab attempt | grade my lab | check my code
 *   tool_allowlist: search_archival, query_graph, get_node
 *   ---
 *
 *   <prompt body — the system-prompt fragment loaded when this skill
 *    is the active flow>
 *
 * Resolution: the chat handler matches user message against `triggers`
 * (case-insensitive substring); first match wins. If no skill matches,
 * the default chat flow runs (generic system prompt + full tool surface).
 */
@Serializable
data class SkillSpec(
    val name: String,
    val description: String,
    val triggers: List<String> = emptyList(),
    val toolAllowlist: List<String> = emptyList(),
    val systemPromptBody: String,
    val sourcePath: String,
)

object SkillLoader {

    /** Default skills root: `tutor/skills/` adjacent to the repo's
     *  archival dir. Override per-call for tests. */
    fun defaultRoot(): Path =
        Path.of(System.getProperty("user.home"), ".jarvis", "skills")

    fun load(root: Path = defaultRoot()): List<SkillSpec> {
        if (!root.exists() || !root.isDirectory()) return emptyList()
        val out = mutableListOf<SkillSpec>()
        Files.list(root).use { stream ->
            stream.filter { it.isDirectory() }
                .forEach { dir ->
                    val skillFile = dir.resolve("SKILL.md")
                    if (!skillFile.exists() || !skillFile.isRegularFile()) return@forEach
                    val raw = try { skillFile.readText(Charsets.UTF_8) } catch (_: Exception) { return@forEach }
                    val spec = parseSkill(dir.name, raw, dir.toString()) ?: return@forEach
                    out += spec
                }
        }
        return out
    }

    /** Pick the first skill whose triggers list contains a (lowercase)
     *  substring of the user's [userMessage]. Null when no skill
     *  matches — caller falls back to default flow. */
    fun resolve(specs: List<SkillSpec>, userMessage: String): SkillSpec? {
        val lower = userMessage.lowercase()
        return specs.firstOrNull { spec ->
            spec.triggers.any { t -> t.isNotBlank() && lower.contains(t.lowercase()) }
        }
    }

    /** Parse SKILL.md format: optional `---`-fenced YAML-ish
     *  frontmatter (one key per line, no nested types), then body.
     *  We accept missing frontmatter and infer `name` from the
     *  directory name, with empty triggers / allowlist. */
    internal fun parseSkill(dirName: String, raw: String, sourcePath: String): SkillSpec? {
        val (front, body) = splitFrontmatter(raw)
        val map = parseSimpleYaml(front)
        val name = map["name"]?.takeIf { it.isNotBlank() } ?: dirName
        val description = map["description"].orEmpty()
        val triggers = map["triggers"].orEmpty()
            .split('|', ',', ';').map { it.trim() }.filter { it.isNotEmpty() }
        val toolAllowlist = map["tool_allowlist"].orEmpty()
            .split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (body.isBlank()) return null  // skill with no system prompt body is useless
        return SkillSpec(
            name = name,
            description = description,
            triggers = triggers,
            toolAllowlist = toolAllowlist,
            systemPromptBody = body.trim(),
            sourcePath = sourcePath,
        )
    }

    private fun splitFrontmatter(raw: String): Pair<String, String> {
        val trimmed = raw.trimStart()
        if (!trimmed.startsWith("---")) return "" to raw
        val rest = trimmed.removePrefix("---").trimStart('\n', '\r')
        val close = rest.indexOf("\n---")
        if (close < 0) return "" to raw
        return rest.substring(0, close) to rest.substring(close + "\n---".length).trimStart('\n', '\r')
    }

    private fun parseSimpleYaml(text: String): Map<String, String> {
        if (text.isBlank()) return emptyMap()
        val out = mutableMapOf<String, String>()
        for (line in text.lineSequence()) {
            val l = line.trim()
            if (l.isEmpty() || l.startsWith("#")) continue
            val idx = l.indexOf(':')
            if (idx <= 0) continue
            val k = l.substring(0, idx).trim()
            val v = l.substring(idx + 1).trim().trim('"', '\'')
            out[k] = v
        }
        return out
    }

    /** Filter [JarvisToolDefs.openAiToolDefs] to the names listed in
     *  the spec's allowlist. Empty allowlist = all tools (default). */
    fun applyAllowlist(spec: SkillSpec, allDefs: kotlinx.serialization.json.JsonArray): kotlinx.serialization.json.JsonArray {
        if (spec.toolAllowlist.isEmpty()) return allDefs
        val allowed = spec.toolAllowlist.toSet()
        return kotlinx.serialization.json.JsonArray(
            allDefs.filter { def ->
                val o = def as? kotlinx.serialization.json.JsonObject ?: return@filter true
                val fn = o["function"] as? kotlinx.serialization.json.JsonObject ?: return@filter true
                val name = (fn["name"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                name in allowed
            },
        )
    }
}
