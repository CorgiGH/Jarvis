package jarvis.content

import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Task 11 (H11) — ContentValidator strict rule tests.
 *
 * H11 RULES:
 *  1. grounding_tier=="strict" requires ALL of:
 *       a) anchored span present
 *       b) provenance=="vision-confirmed"
 *       c) invariant != null
 *       d) grader_rules non-empty
 *     Failing conditions are emitted TOGETHER in ONE aggregated issue (not one error per rule).
 *
 *  2. verification_status must be one of the four authored literals:
 *       {unverified, pending, faithful, uncertain}
 *     "failed" is runtime-only and must be rejected in YAML.
 */
class ContentValidatorStrictTest {

    // ── source-text lookup that returns real text for the test doc ───────────

    private val srcLookup: (String) -> String? = { doc ->
        when (doc) {
            "pa-lecture-01" -> "An algorithm is a finite sequence of unambiguous steps."
            else -> null
        }
    }

    // ── KC builder helpers ───────────────────────────────────────────────────

    /** Build a KnowledgeConcept with vision-confirmed anchored span (passes span+provenance checks). */
    private fun strictKcWithGoodSpan(
        id: String = "pa-kc-strict",
        invariant: String? = null,
        graderRules: List<String> = emptyList(),
        verificationStatus: String = "unverified",
    ): KnowledgeConcept {
        // "An algorithm" = offsets 0..12 in "An algorithm is a finite sequence of unambiguous steps."
        val ref = SourceRef(
            doc = "pa-lecture-01",
            quote = "An algorithm",
            page = 1,
            span = Span(0, 12),
            provenance = "vision-confirmed",
        )
        return KnowledgeConcept(
            id = id, subject = "PA", name_ro = "RO", name_en = "EN",
            cluster = "c", bloom_level = "understand", difficulty = 1, time_minutes = 10,
            exam_weight = 1.0, tier = 1,
            grounding_tier = "strict",
            source = listOf(ref),
            version = 1,
            verification_status = verificationStatus,
            invariant = invariant,
            grader_rules = graderRules,
        )
    }

    /** Build a standard (non-strict) KC — changes to strict rules must not affect it. */
    private fun standardKc(id: String = "pa-kc-std", verificationStatus: String = "unverified"): KnowledgeConcept {
        val ref = SourceRef(
            doc = "pa-lecture-01",
            quote = "An algorithm",
            page = 1,
            span = Span(0, 12),
            provenance = "vision-confirmed",
        )
        return KnowledgeConcept(
            id = id, subject = "PA", name_ro = "RO", name_en = "EN",
            cluster = "c", bloom_level = "understand", difficulty = 1, time_minutes = 10,
            exam_weight = 1.0, tier = 1,
            grounding_tier = "standard",
            source = listOf(ref),
            version = 1,
            verification_status = verificationStatus,
        )
    }

    // ── H11 strict-rule tests ────────────────────────────────────────────────

    @Test
    fun `strict KC missing invariant and grader_rules reports both conditions in ONE issue`() {
        val kc = strictKcWithGoodSpan(invariant = null, graderRules = emptyList())
        val sub = LoadedSubject("PA", kcs = listOf(kc), edges = emptyList(), misconceptions = emptyList())

        val issues = ContentValidator.checkStrictGrounding(sub)

        // H11: must emit ONE aggregated error for this KC (not two separate issues)
        val strictIssues = issues.filter { it.severity == "error" && it.rule == "strict_grounding" }
        assertEquals(1, strictIssues.size,
            "Expected exactly ONE aggregated strict_grounding error; got: ${issues.map { it.detail }}")
        // The single issue must name BOTH missing conditions
        val detail = strictIssues.single().detail
        assertTrue(detail.contains("invariant"), "Detail should mention 'invariant'; got: $detail")
        assertTrue(detail.contains("grader_rules"), "Detail should mention 'grader_rules'; got: $detail")
    }

    @Test
    fun `strict KC missing only invariant reports that condition`() {
        val kc = strictKcWithGoodSpan(invariant = null, graderRules = listOf("sympy: check"))
        val sub = LoadedSubject("PA", kcs = listOf(kc), edges = emptyList(), misconceptions = emptyList())

        val issues = ContentValidator.checkStrictGrounding(sub)
        val strictIssues = issues.filter { it.severity == "error" && it.rule == "strict_grounding" }

        assertEquals(1, strictIssues.size)
        assertTrue(strictIssues.single().detail.contains("invariant"))
        assertFalse(strictIssues.single().detail.contains("grader_rules"))
    }

    @Test
    fun `strict KC missing only grader_rules reports that condition`() {
        val kc = strictKcWithGoodSpan(
            invariant = "T(n) = Θ(n log n)",
            graderRules = emptyList(),
        )
        val sub = LoadedSubject("PA", kcs = listOf(kc), edges = emptyList(), misconceptions = emptyList())

        val issues = ContentValidator.checkStrictGrounding(sub)
        val strictIssues = issues.filter { it.severity == "error" && it.rule == "strict_grounding" }

        assertEquals(1, strictIssues.size)
        assertTrue(strictIssues.single().detail.contains("grader_rules"))
        assertFalse(strictIssues.single().detail.contains("invariant"))
    }

    @Test
    fun `strict KC with all conditions passing is clean`() {
        val kc = strictKcWithGoodSpan(
            invariant = "T(n) = Θ(n log n)",
            graderRules = listOf("sympy: result == n*log(n)"),
        )
        val sub = LoadedSubject("PA", kcs = listOf(kc), edges = emptyList(), misconceptions = emptyList())

        val issues = ContentValidator.checkStrictGrounding(sub)
        val strictIssues = issues.filter { it.rule == "strict_grounding" }
        assertTrue(strictIssues.isEmpty(),
            "Expected no strict_grounding issues; got: ${issues.map { it.detail }}")
    }

    @Test
    fun `standard KC is not affected by strict grounding check`() {
        val kc = standardKc()  // no invariant, no grader_rules — still fine for standard
        val sub = LoadedSubject("PA", kcs = listOf(kc), edges = emptyList(), misconceptions = emptyList())

        val issues = ContentValidator.checkStrictGrounding(sub)
        val strictIssues = issues.filter { it.rule == "strict_grounding" }
        assertTrue(strictIssues.isEmpty(),
            "Standard KC must not be flagged by strict grounding check; got: ${strictIssues.map { it.detail }}")
    }

    // ── H11 verification_status enum check tests ─────────────────────────────

    @Test
    fun `verification_status=failed on a KC is rejected as not an authored literal`() {
        val kc = standardKc(verificationStatus = "failed")
        val sub = LoadedSubject("PA", kcs = listOf(kc), edges = emptyList(), misconceptions = emptyList())

        val issues = ContentValidator.checkVerificationStatusEnum(sub)
        val enumIssues = issues.filter { it.severity == "error" && it.rule == "verification_status_enum" }
        assertEquals(1, enumIssues.size,
            "Expected 1 enum error for 'failed'; got: ${issues.map { it.detail }}")
        assertTrue(enumIssues.single().detail.contains("failed"),
            "Detail should mention the invalid literal; got: ${enumIssues.single().detail}")
    }

    @Test
    fun `the three valid authored literals pass enum check for KCs`() {
        // F4-author: `faithful` is NO LONGER an authored literal — authors seed only
        // {unverified, pending, uncertain}; `faithful` is earned at runtime by an audit, never authored.
        for (status in listOf("unverified", "pending", "uncertain")) {
            val kc = standardKc(verificationStatus = status)
            val sub = LoadedSubject("PA", kcs = listOf(kc), edges = emptyList(), misconceptions = emptyList())
            val issues = ContentValidator.checkVerificationStatusEnum(sub)
            val enumErrors = issues.filter { it.severity == "error" && it.rule == "verification_status_enum" }
            assertTrue(enumErrors.isEmpty(),
                "Status '$status' should pass enum check; got errors: ${enumErrors.map { it.detail }}")
        }
    }

    @Test
    fun `F4-author - verification_status=faithful on a KC is rejected as not an authored literal`() {
        // F4-author: a YAML may seed only {unverified, pending, uncertain}. `faithful` certifies an
        // audit that ran — authoring it would be an unearned claim, so the validator now rejects it
        // (same treatment as the runtime-only `failed`).
        val kc = standardKc(verificationStatus = "faithful")
        val sub = LoadedSubject("PA", kcs = listOf(kc), edges = emptyList(), misconceptions = emptyList())

        val issues = ContentValidator.checkVerificationStatusEnum(sub)
        val enumIssues = issues.filter { it.severity == "error" && it.rule == "verification_status_enum" }
        assertEquals(1, enumIssues.size,
            "Expected 1 enum error for 'faithful'; got: ${issues.map { it.detail }}")
        assertTrue(enumIssues.single().detail.contains("faithful"),
            "Detail should mention the invalid literal; got: ${enumIssues.single().detail}")
    }

    @Test
    fun `verification_status=failed on a Misconception is rejected`() {
        val m = Misconception(
            id = "pa-misc-001", kc_id = "pa-kc-001",
            label_ro = "RO", label_en = "EN",
            trigger = "t", refutation = "r",
            source = emptyList(), version = 1,
            verification_status = "failed",
        )
        val sub = LoadedSubject("PA",
            kcs = listOf(standardKc()),
            edges = emptyList(),
            misconceptions = listOf(m),
        )
        val issues = ContentValidator.checkVerificationStatusEnum(sub)
        val enumIssues = issues.filter { it.severity == "error" && it.rule == "verification_status_enum" }
        assertEquals(1, enumIssues.size,
            "Misconception with status 'failed' must be flagged; got: ${issues.map { it.detail }}")
    }

    @Test
    fun `the three valid authored literals pass enum check for Misconceptions`() {
        // F4-author: `faithful` is no longer an authored literal (see the KC test above).
        for (status in listOf("unverified", "pending", "uncertain")) {
            val m = Misconception(
                id = "pa-misc-001", kc_id = "pa-kc-001",
                label_ro = "RO", label_en = "EN",
                trigger = "t", refutation = "r",
                source = emptyList(), version = 1,
                verification_status = status,
            )
            val sub = LoadedSubject("PA",
                kcs = listOf(standardKc()),
                edges = emptyList(),
                misconceptions = listOf(m),
            )
            val issues = ContentValidator.checkVerificationStatusEnum(sub)
            val enumErrors = issues.filter { it.severity == "error" && it.rule == "verification_status_enum" }
            assertTrue(enumErrors.isEmpty(),
                "Misconception status '$status' should pass; got errors: ${enumErrors.map { it.detail }}")
        }
    }

    @Test
    fun `invalid verification_status string is rejected`() {
        val kc = standardKc(verificationStatus = "approved")  // not a valid literal
        val sub = LoadedSubject("PA", kcs = listOf(kc), edges = emptyList(), misconceptions = emptyList())

        val issues = ContentValidator.checkVerificationStatusEnum(sub)
        val enumIssues = issues.filter { it.severity == "error" && it.rule == "verification_status_enum" }
        assertEquals(1, enumIssues.size)
        assertTrue(enumIssues.single().detail.contains("approved"))
    }

    // ── H11 + validate() integration: new checks wired into validate() ────────

    @Test
    fun `validate includes strict_grounding and enum checks`() {
        // A strict KC missing invariant AND grader_rules
        val badKc = strictKcWithGoodSpan(invariant = null, graderRules = emptyList())
        val sub = LoadedSubject("PA", kcs = listOf(badKc), edges = emptyList(), misconceptions = emptyList())

        val report = ContentValidator.validate(listOf(sub), sourceText = srcLookup)
        assertFalse(report.ok, "validate() must return ok=false when strict_grounding errors exist")
        assertTrue(report.issues.any { it.rule == "strict_grounding" },
            "validate() must include strict_grounding issues in the report")
    }

    @Test
    fun `validate runs over all 8 real KC yamls without strict rule errors`() {
        // The 6 real KCs are all grounding_tier=standard; no strict checks should fire.
        // The 2 fixtures (fixture-compute, fixture-recursion) are also standard.
        // Enum check: all have default verification_status="unverified" (valid authored literal).
        val contentRoot = Paths.get("content")
        val repo = ContentRepo(contentRoot)
        val loaded = repo.loadSubject("PA")

        // Source lookup: we don't have extracted text files for all docs in test,
        // but that's already covered by checkVerbatimSources which produces warnings not errors.
        // We only assert that the NEW checks (strict_grounding + verification_status_enum)
        // produce zero errors.
        val strictIssues = ContentValidator.checkStrictGrounding(loaded)
            .filter { it.severity == "error" }
        val enumIssues = ContentValidator.checkVerificationStatusEnum(loaded)
            .filter { it.severity == "error" }

        assertTrue(strictIssues.isEmpty(),
            "All 8 real KC YAMLs should pass strict_grounding check; errors: ${strictIssues.map { it.detail }}")
        assertTrue(enumIssues.isEmpty(),
            "All 8 real KC YAMLs should pass enum check; errors: ${enumIssues.map { it.detail }}")
    }
}
