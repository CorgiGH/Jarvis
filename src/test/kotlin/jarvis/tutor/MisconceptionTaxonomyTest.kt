package jarvis.tutor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MisconceptionTaxonomyTest {

    @Test
    fun `codesFor PS returns 3 stats codes plus OTHER`() {
        val codes = MisconceptionTaxonomy.codesFor("PS")
        val codeStrings = codes.map { it.code }
        assertTrue(codeStrings.contains("L2_ESTIMATOR_CONFUSION"), "must have L2_ESTIMATOR_CONFUSION")
        assertTrue(codeStrings.contains("MINIMAX_CONFUSION"), "must have MINIMAX_CONFUSION")
        assertTrue(codeStrings.contains("MODE_CONFUSION"), "must have MODE_CONFUSION")
        assertTrue(codeStrings.contains("OTHER"), "must have OTHER")
        assertEquals(4, codes.size, "PS should have exactly 4 codes")
    }

    @Test
    fun `codesFor PA contains REDUCTION_DIRECTION_FLIPPED and OTHER`() {
        val codes = MisconceptionTaxonomy.codesFor("PA")
        val codeStrings = codes.map { it.code }
        assertTrue(codeStrings.contains("REDUCTION_DIRECTION_FLIPPED"), "must have REDUCTION_DIRECTION_FLIPPED")
        assertTrue(codeStrings.contains("OTHER"), "must have OTHER")
    }

    @Test
    fun `codesFor unknown subject returns only OTHER`() {
        val codes = MisconceptionTaxonomy.codesFor("UNKNOWN_SUBJECT_XYZ")
        assertEquals(1, codes.size, "unknown subject should return exactly 1 code")
        assertEquals("OTHER", codes[0].code)
    }

    @Test
    fun `every code is UPPER_SNAKE_CASE`() {
        val allSubjects = listOf("PS", "PA", "ALO", "POO", "SO-RC")
        for (subject in allSubjects) {
            for (entry in MisconceptionTaxonomy.codesFor(subject)) {
                assertTrue(
                    entry.code.matches(Regex("[A-Z][A-Z0-9_]*")),
                    "Code '${entry.code}' for subject '$subject' must be UPPER_SNAKE_CASE"
                )
            }
        }
        // Also check unknown subject
        for (entry in MisconceptionTaxonomy.codesFor("UNKNOWN")) {
            assertTrue(
                entry.code.matches(Regex("[A-Z][A-Z0-9_]*")),
                "Code '${entry.code}' for unknown subject must be UPPER_SNAKE_CASE"
            )
        }
    }

    @Test
    fun `every entry has non-blank label_ro and judge_hint_en`() {
        val allSubjects = listOf("PS", "PA")
        for (subject in allSubjects) {
            for (entry in MisconceptionTaxonomy.codesFor(subject)) {
                assertTrue(
                    entry.label_ro.isNotBlank(),
                    "label_ro must be non-blank for code '${entry.code}' subject '$subject'"
                )
                assertTrue(
                    entry.judge_hint_en.isNotBlank(),
                    "judge_hint_en must be non-blank for code '${entry.code}' subject '$subject'"
                )
            }
        }
    }

    @Test
    fun `registry file parses without error`() {
        // MisconceptionTaxonomy loads lazily; calling codesFor triggers the parse
        val codes = MisconceptionTaxonomy.codesFor("PS")
        assertNotNull(codes, "registry must parse without exception")
        assertFalse(codes.isEmpty(), "registry must not be empty for PS")
    }
}
