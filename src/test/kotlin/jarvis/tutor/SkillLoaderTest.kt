package jarvis.tutor

import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SkillLoaderTest {

    private fun seed(tmp: Path) {
        val a = tmp.resolve("lab-evaluator"); a.createDirectories()
        a.resolve("SKILL.md").writeText("""
            ---
            name: lab-evaluator
            description: Grade lab attempt against rubric
            triggers: grade my lab|check this lab|lab attempt
            tool_allowlist: search_archival, query_graph
            ---

            You are the lab evaluator. Read the user's submission and grade against the rubric.
        """.trimIndent())

        val b = tmp.resolve("exam-gen"); b.createDirectories()
        b.resolve("SKILL.md").writeText("""
            ---
            name: exam-gen
            description: Generate exam questions
            triggers: practice exam, quiz me
            ---

            You are the exam generator. Produce one exam-style question.
        """.trimIndent())

        // Skill with no body — should be filtered.
        val c = tmp.resolve("empty"); c.createDirectories()
        c.resolve("SKILL.md").writeText("""
            ---
            name: empty
            ---
        """.trimIndent())

        // Non-skill directory — no SKILL.md.
        tmp.resolve("just-a-dir").createDirectories()
    }

    @Test
    fun loadsAllValidSkills(@TempDir tmp: Path) {
        seed(tmp)
        val skills = SkillLoader.load(tmp)
        assertEquals(2, skills.size, "lab-evaluator + exam-gen; empty + non-skill ignored")
        val byName = skills.associateBy { it.name }
        assertNotNull(byName["lab-evaluator"])
        assertNotNull(byName["exam-gen"])
    }

    @Test
    fun parsesTriggersAndAllowlist(@TempDir tmp: Path) {
        seed(tmp)
        val lab = SkillLoader.load(tmp).first { it.name == "lab-evaluator" }
        assertEquals(listOf("grade my lab", "check this lab", "lab attempt"), lab.triggers)
        assertEquals(listOf("search_archival", "query_graph"), lab.toolAllowlist)
    }

    @Test
    fun parsesBodyAfterFrontmatter(@TempDir tmp: Path) {
        seed(tmp)
        val lab = SkillLoader.load(tmp).first { it.name == "lab-evaluator" }
        assertTrue(lab.systemPromptBody.startsWith("You are the lab evaluator"), lab.systemPromptBody)
    }

    @Test
    fun resolveBySingleTriggerSubstring(@TempDir tmp: Path) {
        seed(tmp)
        val skills = SkillLoader.load(tmp)
        val r = SkillLoader.resolve(skills, "Please check this lab for me")
        assertEquals("lab-evaluator", r?.name)
    }

    @Test
    fun resolveCaseInsensitive(@TempDir tmp: Path) {
        seed(tmp)
        val skills = SkillLoader.load(tmp)
        val r = SkillLoader.resolve(skills, "QUIZ ME on PA topics")
        assertEquals("exam-gen", r?.name)
    }

    @Test
    fun resolveReturnsNullWhenNoMatch(@TempDir tmp: Path) {
        seed(tmp)
        val skills = SkillLoader.load(tmp)
        assertNull(SkillLoader.resolve(skills, "tell me a joke"))
    }

    @Test
    fun applyAllowlistFiltersTools() {
        val allDefs = buildJsonArray {
            add(buildJsonObject {
                put("type", JsonPrimitive("function"))
                put("function", buildJsonObject { put("name", JsonPrimitive("search_archival")) })
            })
            add(buildJsonObject {
                put("type", JsonPrimitive("function"))
                put("function", buildJsonObject { put("name", JsonPrimitive("shortest_path")) })
            })
            add(buildJsonObject {
                put("type", JsonPrimitive("function"))
                put("function", buildJsonObject { put("name", JsonPrimitive("get_node")) })
            })
        }
        val spec = SkillSpec(
            name = "x", description = "", triggers = emptyList(),
            toolAllowlist = listOf("search_archival", "get_node"),
            systemPromptBody = "...", sourcePath = "/tmp/x",
        )
        val filtered = SkillLoader.applyAllowlist(spec, allDefs)
        assertEquals(2, filtered.size)
        // shortest_path filtered out.
        val names = filtered.mapNotNull { (it as? kotlinx.serialization.json.JsonObject)?.get("function")
            ?.let { fn -> (fn as? kotlinx.serialization.json.JsonObject)?.get("name") }
            ?.let { (it as? JsonPrimitive)?.content } }
        assertTrue("search_archival" in names && "get_node" in names && "shortest_path" !in names, names.toString())
    }

    @Test
    fun emptyAllowlistKeepsAllTools() {
        val allDefs = buildJsonArray {
            add(buildJsonObject {
                put("type", JsonPrimitive("function"))
                put("function", buildJsonObject { put("name", JsonPrimitive("a")) })
            })
        }
        val spec = SkillSpec("x", "", emptyList(), emptyList(), "...", "/tmp/x")
        assertEquals(allDefs, SkillLoader.applyAllowlist(spec, allDefs))
    }

    @Test
    fun missingFrontmatterUsesDirNameAsSkillName(@TempDir tmp: Path) {
        val a = tmp.resolve("bare-skill"); a.createDirectories()
        a.resolve("SKILL.md").writeText("you are a bare skill with no frontmatter")
        val skills = SkillLoader.load(tmp)
        assertEquals(1, skills.size)
        assertEquals("bare-skill", skills[0].name)
        assertTrue(skills[0].systemPromptBody.startsWith("you are a bare skill"))
    }

    @Test
    fun missingRootDirReturnsEmpty(@TempDir tmp: Path) {
        assertEquals(emptyList(), SkillLoader.load(tmp.resolve("nope")))
    }
}
