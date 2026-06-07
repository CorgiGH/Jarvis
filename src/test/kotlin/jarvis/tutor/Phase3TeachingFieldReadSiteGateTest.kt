package jarvis.tutor

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * P1-3(d) — MECHANICAL read-site gate for every Phase-3 teaching field + selection helper.
 *
 * The ScaffoldPlanner ghost (council finding): `ScaffoldPlanner.planFor` shipped with ZERO production
 * callers (only its own definition + a KDoc mention). The green suite + bundle did not catch it because
 * a component-in-isolation test passes whether or not anything CALLS the component. This gate is the
 * class-killing invariant: it walks production source (`src/main/kotlin`) and FAILS if any gated symbol
 * has no real CODE read-site (a non-comment access/call) OUTSIDE the file that DEFINES/DECLARES it.
 *
 * "Read-site" = a non-comment, non-blank line that references the symbol, in a file OTHER than the
 * symbol's home (definition for a helper; the schema declaration for a field). Comment/KDoc lines
 * (`//`, `*`, `/*`, `*/`) are stripped FIRST — a KDoc mention is exactly the false-green that hid the
 * ghost, so it must never satisfy the gate.
 *
 * If a future change deletes the only caller of a teaching field/helper (re-ghosting it), this test
 * goes RED — the mechanical proof the loop is wired, not just present.
 */
class Phase3TeachingFieldReadSiteGateTest {

    /** One gated symbol: the token to find + the file basename that DEFINES it (excluded from read-sites). */
    private data class Gated(val token: String, val homeFile: String, val description: String)

    private val mainSrc = File("src/main/kotlin")

    /** All production .kt files, with comment lines stripped so a KDoc mention can't satisfy the gate. */
    private data class CodeFile(val name: String, val codeLines: List<String>)

    private fun productionCodeFiles(): List<CodeFile> {
        assertTrue(mainSrc.isDirectory, "src/main/kotlin not found from ${File(".").absolutePath}")
        return mainSrc.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { f ->
                val code = f.readLines().map { stripComment(it) }
                CodeFile(f.name, code)
            }
            .toList()
    }

    /**
     * Strip a single-line `//` comment tail and drop a line that is wholly a block-comment / KDoc line
     * (starts with `*`, `/*`, or `*/` after trimming). NOT a full Kotlin parser — deliberately
     * conservative: it must never let a comment-only mention count as a read-site (the ghost's KDoc).
     */
    private fun stripComment(line: String): String {
        val t = line.trim()
        if (t.startsWith("*") || t.startsWith("/*") || t.startsWith("*/") || t.startsWith("//")) return ""
        val idx = line.indexOf("//")
        return if (idx >= 0) line.substring(0, idx) else line
    }

    private fun hasReadSiteOutsideHome(token: String, homeFile: String, files: List<CodeFile>): Boolean =
        files.any { cf -> cf.name != homeFile && cf.codeLines.any { it.contains(token) } }

    @Test
    fun `every Phase-3 teaching field and selection helper has a production read-site outside its home file`() {
        val files = productionCodeFiles()
        val gated = listOf(
            // Selection helper — the actual ghost the council found. Must be CALLED, not just defined.
            Gated("ScaffoldPlanner.planFor", "ScaffoldPlanner.kt", "the phase-plan selection helper (planFor)"),
            // CHANGE-3 teaching FIELDS — each declared on KnowledgeConcept in ContentSchema.kt; a real
            // read-site must exist in a SERVE/selection file, not just the schema declaration.
            Gated(".phase_plan", "ContentSchema.kt", "KnowledgeConcept.phase_plan"),
            Gated(".far_transfer_stem", "ContentSchema.kt", "KnowledgeConcept.far_transfer_stem"),
            Gated(".self_explanation_prompt", "ContentSchema.kt", "self_explanation_prompt"),
        )
        val ghosts = gated.filterNot { hasReadSiteOutsideHome(it.token, it.homeFile, files) }
        if (ghosts.isNotEmpty()) {
            fail(
                "Phase-3 teaching symbols with NO production read-site outside their home file (GHOST — " +
                    "present but never consumed on the serve/selection path):\n" +
                    ghosts.joinToString("\n") { "  - ${it.token} (${it.description}); home=${it.homeFile}" },
            )
        }
    }

    /**
     * Self-check: the gate would actually CATCH a ghost. A bogus symbol that no production file reads
     * MUST be reported as missing — proving the gate is not vacuously green.
     */
    @Test
    fun `the gate flags a symbol that has no production read-site (self-check)`() {
        val files = productionCodeFiles()
        assertTrue(
            !hasReadSiteOutsideHome("ThisSymbolHasNoReadSiteAnywhere_P1_3_d", "Nonexistent.kt", files),
            "self-check failed: a non-existent symbol was reported as having a read-site",
        )
    }
}
