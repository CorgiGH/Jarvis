package jarvis.content

import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContentValidatorTest {
    private fun kc(id: String, tier: Int = 2, weight: Double = 0.0) = KnowledgeConcept(
        id = id, subject = "PA", name_ro = "ro-$id", name_en = "en-$id",
        cluster = "c", bloom_level = "understand", difficulty = 1, time_minutes = 10,
        exam_weight = weight, tier = tier, source = emptyList(), version = 1,
    )

    @Test
    fun `clean acyclic graph has no cycle issues`() {
        val sub = LoadedSubject(
            "PA",
            kcs = listOf(kc("a", tier = 1, weight = 1.0), kc("b")),
            edges = listOf(PrereqEdge("b", "a", "r")),
            misconceptions = emptyList(),
        )
        val issues = ContentValidator.detectCycles(sub)
        assertTrue(issues.isEmpty())
    }

    @Test
    fun `direct cycle is reported`() {
        val sub = LoadedSubject(
            "PA",
            kcs = listOf(kc("a"), kc("b")),
            edges = listOf(PrereqEdge("a", "b", "r"), PrereqEdge("b", "a", "r")),
            misconceptions = emptyList(),
        )
        val issues = ContentValidator.detectCycles(sub)
        assertEquals(1, issues.size)
        assertEquals("cycle", issues.single().rule)
        assertFalse(issues.single().severity == "warning")
    }

    @Test
    fun `kc within 8 prereq-hops of a tier-1 root is not an orphan`() {
        val sub = LoadedSubject(
            "PA",
            kcs = listOf(kc("root", tier = 1), kc("child")),
            edges = listOf(PrereqEdge("child", "root", "r")),
            misconceptions = emptyList(),
        )
        assertTrue(ContentValidator.detectOrphans(sub).isEmpty())
    }

    @Test
    fun `kc with no path to a tier-1 root is an orphan`() {
        val sub = LoadedSubject(
            "PA",
            kcs = listOf(kc("root", tier = 1), kc("floating", tier = 2)),
            edges = emptyList(),
            misconceptions = emptyList(),
        )
        val issues = ContentValidator.detectOrphans(sub)
        assertEquals(1, issues.size)
        assertEquals("orphan", issues.single().rule)
        assertTrue(issues.single().detail.contains("floating"))
    }

    @Test
    fun `exam weights summing to 1 within tolerance pass`() {
        val sub = LoadedSubject("PA",
            kcs = listOf(kc("a", weight = 0.6), kc("b", weight = 0.41)),
            edges = emptyList(), misconceptions = emptyList())
        assertTrue(ContentValidator.checkExamWeights(sub).isEmpty()) // 1.01 within 0.02
    }

    @Test
    fun `exam weights outside tolerance are reported`() {
        val sub = LoadedSubject("PA",
            kcs = listOf(kc("a", weight = 0.5), kc("b", weight = 0.3)),
            edges = emptyList(), misconceptions = emptyList())
        val issues = ContentValidator.checkExamWeights(sub) // sum 0.8
        assertEquals(1, issues.size)
        assertEquals("exam_weight", issues.single().rule)
    }

    @Test
    fun `kc with both names passes bilingual check`() {
        val sub = LoadedSubject("PA",
            kcs = listOf(kc("a", weight = 1.0, tier = 1)),
            edges = emptyList(), misconceptions = emptyList())
        assertTrue(ContentValidator.checkBilingual(sub).isEmpty())
    }

    @Test
    fun `kc missing a romanian name is reported`() {
        val bad = kc("a", weight = 1.0, tier = 1).copy(name_ro = "  ")
        val sub = LoadedSubject("PA", kcs = listOf(bad), edges = emptyList(), misconceptions = emptyList())
        val issues = ContentValidator.checkBilingual(sub)
        assertEquals(1, issues.size)
        assertEquals("bilingual", issues.single().rule)
    }

    @Test
    fun `misconception missing a label is reported`() {
        val m = Misconception("m1", "a", label_ro = "ok", label_en = "",
            trigger = "t", refutation = "r", source = emptyList(), version = 1)
        val sub = LoadedSubject("PA",
            kcs = listOf(kc("a", weight = 1.0, tier = 1)),
            edges = emptyList(), misconceptions = listOf(m))
        assertEquals(1, ContentValidator.checkBilingual(sub).size)
    }

    private val srcLookup: (String) -> String? = { doc ->
        if (doc == "pa-lecture-01") "An algorithm is a finite\n  sequence of unambiguous steps." else null
    }

    @Test
    fun `quote that is a verbatim substring of the source passes`() {
        val withSrc = kc("a", weight = 1.0, tier = 1)
            .copy(source = listOf(SourceRef("pa-lecture-01", "a finite sequence of unambiguous steps")))
        val sub = LoadedSubject("PA", kcs = listOf(withSrc), edges = emptyList(), misconceptions = emptyList())
        val issues = ContentValidator.checkVerbatimSources(sub, srcLookup)
        assertEquals(1, issues.size)
        assertEquals("warning", issues.single().severity)
        assertTrue(issues.single().detail.contains("ungrounded span"))
    }

    @Test
    fun `quote not present in the source is an error`() {
        val withSrc = kc("a", weight = 1.0, tier = 1)
            .copy(source = listOf(SourceRef("pa-lecture-01", "an algorithm always halts")))
        val sub = LoadedSubject("PA", kcs = listOf(withSrc), edges = emptyList(), misconceptions = emptyList())
        val issues = ContentValidator.checkVerbatimSources(sub, srcLookup)
        assertEquals(1, issues.size)
        assertEquals("verbatim_source", issues.single().rule)
        assertEquals("error", issues.single().severity)
    }

    @Test
    fun `empty source list is an error — every KC must be attributed`() {
        val sub = LoadedSubject("PA",
            kcs = listOf(kc("a", weight = 1.0, tier = 1)),
            edges = emptyList(), misconceptions = emptyList())
        val issues = ContentValidator.checkVerbatimSources(sub, srcLookup)
        assertEquals(1, issues.size)
        assertTrue(issues.single().detail.contains("no source"))
    }

    @Test
    fun `missing source file degrades to a warning, not an error`() {
        val withSrc = kc("a", weight = 1.0, tier = 1)
            .copy(source = listOf(SourceRef("not-on-disk", "anything")))
        val sub = LoadedSubject("PA", kcs = listOf(withSrc), edges = emptyList(), misconceptions = emptyList())
        val issues = ContentValidator.checkVerbatimSources(sub, srcLookup)
        assertEquals(1, issues.size)
        assertEquals("warning", issues.single().severity)
    }

    @Test
    fun `validate aggregates all checks and self-labels the disclaimer`() {
        val good = kc("a", weight = 1.0, tier = 1)
            .copy(source = listOf(SourceRef("pa-lecture-01", "a finite sequence of unambiguous steps")))
        val sub = LoadedSubject("PA", kcs = listOf(good), edges = emptyList(), misconceptions = emptyList())
        val report = ContentValidator.validate(listOf(sub), sourceText = srcLookup)
        assertTrue(report.ok, report.issues.toString())
        assertEquals(ContentValidator.DISCLAIMER, report.disclaimer)
    }

    @Test
    fun `validate returns ok=false when any error issue is present`() {
        val sub = LoadedSubject("PA",
            kcs = listOf(kc("a", weight = 0.3, tier = 1)), // weight sum 0.3 -> error
            edges = emptyList(), misconceptions = emptyList())
        val report = ContentValidator.validate(listOf(sub), sourceText = srcLookup)
        assertFalse(report.ok)
        assertTrue(report.issues.any { it.rule == "exam_weight" })
    }

    // --- E2: span-anchored, diacritic-exact, tier severity ---

    // Raw source-of-record with a Romanian diacritic: "structuri și liste".
    // "structuri" = offsets 0..9 (end-exclusive 9), "și" = offsets 10..12.
    private val roLookup: (String) -> String? = { doc ->
        if (doc == "ro-doc") "structuri și liste" else null
    }

    private fun kcSpan(id: String, refs: List<SourceRef>, tier: String = "standard") =
        kc(id, tier = 1, weight = 1.0).copy(grounding_tier = tier, source = refs)

    @Test
    fun `span-anchored exact quote passes`() {
        val sub = LoadedSubject("PA",
            kcs = listOf(kcSpan("a", listOf(SourceRef("ro-doc", "structuri", page = 1, span = Span(0, 9))))),
            edges = emptyList(), misconceptions = emptyList())
        assertTrue(ContentValidator.checkVerbatimSources(sub, roLookup).isEmpty())
    }

    @Test
    fun `diacritic-collapsed quote at a span containing diacritics is an ERROR`() {
        // quote "si" (no diacritic) cited at the span that actually holds "și".
        val sub = LoadedSubject("PA",
            kcs = listOf(kcSpan("a", listOf(SourceRef("ro-doc", "si", page = 1, span = Span(10, 12))))),
            edges = emptyList(), misconceptions = emptyList())
        val issues = ContentValidator.checkVerbatimSources(sub, roLookup)
        assertEquals(1, issues.size)
        assertEquals("verbatim_source", issues.single().rule)
        assertEquals("error", issues.single().severity)
    }

    @Test
    fun `strict KC with an absent span is an ERROR`() {
        val sub = LoadedSubject("PA",
            kcs = listOf(kcSpan("a", listOf(SourceRef("pa-lecture-01", "a finite sequence of unambiguous steps")), tier = "strict")),
            edges = emptyList(), misconceptions = emptyList())
        assertTrue(ContentValidator.checkVerbatimSources(sub, srcLookup).any { it.severity == "error" })
    }

    @Test
    fun `strict KC with span but non-vision-confirmed provenance is an ERROR`() {
        val sub = LoadedSubject("PA",
            kcs = listOf(kcSpan("a", listOf(SourceRef("ro-doc", "structuri", page = 1, span = Span(0, 9))), tier = "strict")),
            edges = emptyList(), misconceptions = emptyList())
        assertTrue(ContentValidator.checkVerbatimSources(sub, roLookup).any { it.severity == "error" && it.detail.contains("vision-confirmed") })
    }

    @Test
    fun `standard KC with absent span but present quote is a WARNING not error`() {
        val sub = LoadedSubject("PA",
            kcs = listOf(kcSpan("a", listOf(SourceRef("pa-lecture-01", "a finite sequence of unambiguous steps")))),
            edges = emptyList(), misconceptions = emptyList())
        val issues = ContentValidator.checkVerbatimSources(sub, srcLookup)
        assertEquals(1, issues.size)
        assertEquals("warning", issues.single().severity)
    }

    @Test
    fun `span out of bounds is an ERROR`() {
        val sub = LoadedSubject("PA",
            kcs = listOf(kcSpan("a", listOf(SourceRef("ro-doc", "x", page = 1, span = Span(50, 60))))),
            edges = emptyList(), misconceptions = emptyList())
        val issues = ContentValidator.checkVerbatimSources(sub, roLookup)
        assertEquals(1, issues.size)
        assertEquals("error", issues.single().severity)
        assertTrue(issues.single().detail.contains("out of bounds"))
    }

    @Test
    fun `runValidation loads a content dir and reports ok`(@org.junit.jupiter.api.io.TempDir tmp: java.nio.file.Path) {
        tmp.resolve("subjects.yaml").writeText(
            "version: 1\nsubjects:\n  - id: PA\n    name_ro: \"P\"\n    name_en: \"Algorithm Design\"\n")
        val pa = tmp.resolve("PA")
        pa.resolve("kcs").createDirectories()
        pa.resolve("_sources").createDirectories()
        pa.resolve("_sources/pa-lecture-01.md").writeText("An algorithm is a finite sequence of steps.")
        pa.resolve("kcs/pa-kc-001.yaml").writeText(
            "id: pa-kc-001\nsubject: PA\nname_ro: \"A\"\nname_en: \"Algorithm\"\n" +
            "cluster: f\nbloom_level: understand\ndifficulty: 1\ntime_minutes: 10\n" +
            "exam_weight: 1.0\ntier: 1\nversion: 1\n" +
            "source:\n  - doc: pa-lecture-01\n    quote: \"a finite sequence of steps\"\n")
        pa.resolve("edges.yaml").writeText("subject: PA\nedges: []\n")
        val report = ContentCli.runValidation(tmp)
        assertTrue(report.ok, report.issues.toString())
    }

    // --- D4: reject tautological (vacuously-true) invariants at author/audit time ----------------

    @Test
    fun `D4 a tautological invariant t = t is REJECTED`() {
        val sub = LoadedSubject(
            "PA",
            kcs = listOf(kc("a", tier = 1, weight = 1.0).copy(invariant = "t = t")),
            edges = emptyList(), misconceptions = emptyList(),
        )
        val issues = ContentValidator.checkTautologicalInvariants(sub)
        assertEquals(1, issues.size, "a tautological invariant must be rejected")
        assertEquals("tautological_invariant", issues.single().rule)
        assertEquals("error", issues.single().severity)
        assertTrue(issues.single().detail.contains("'a'"))
    }

    @Test
    fun `D4 a zero equals zero invariant is REJECTED`() {
        val sub = LoadedSubject(
            "PA",
            kcs = listOf(kc("z", tier = 1, weight = 1.0).copy(invariant = "0 = 0")),
            edges = emptyList(), misconceptions = emptyList(),
        )
        assertEquals(1, ContentValidator.checkTautologicalInvariants(sub).size, "`0 = 0` is vacuously true ⇒ rejected")
    }

    @Test
    fun `D4 a MEANINGFUL invariant is ACCEPTED (no over-rejection)`() {
        // |n|_unif = 1 is NOT lhs==rhs-identical ⇒ accepted (the syntactic-identity guard is safe).
        val meaningful = LoadedSubject(
            "PA",
            kcs = listOf(kc("m", tier = 1, weight = 1.0).copy(invariant = "|n|_unif = 1")),
            edges = emptyList(), misconceptions = emptyList(),
        )
        assertTrue(ContentValidator.checkTautologicalInvariants(meaningful).isEmpty(), "`|n|_unif = 1` is meaningful ⇒ accepted")

        // 2x = x + x is meaningful (simplify-trivial but NOT syntactically identical) ⇒ accepted.
        val twoX = LoadedSubject(
            "PA",
            kcs = listOf(kc("x", tier = 1, weight = 1.0).copy(invariant = "2x = x + x")),
            edges = emptyList(), misconceptions = emptyList(),
        )
        assertTrue(ContentValidator.checkTautologicalInvariants(twoX).isEmpty(), "`2x = x + x` must NOT be over-rejected")

        // a normal authored invariant is accepted.
        val normal = LoadedSubject(
            "PA",
            kcs = listOf(kc("n", tier = 1, weight = 1.0).copy(invariant = "1 + 1 + 1 = 3")),
            edges = emptyList(), misconceptions = emptyList(),
        )
        assertTrue(ContentValidator.checkTautologicalInvariants(normal).isEmpty(), "`1 + 1 + 1 = 3` is non-trivial ⇒ accepted")

        // a null invariant is a no-op (no error).
        val noInv = LoadedSubject(
            "PA", kcs = listOf(kc("p", tier = 1, weight = 1.0)),
            edges = emptyList(), misconceptions = emptyList(),
        )
        assertTrue(ContentValidator.checkTautologicalInvariants(noInv).isEmpty(), "no invariant ⇒ no tautology error")
    }

    @Test
    fun `D4 validate() FAILS a corpus carrying a tautological invariant`() {
        val sub = LoadedSubject(
            "PA",
            kcs = listOf(kc("a", tier = 1, weight = 1.0).copy(invariant = "x = x")),
            edges = emptyList(), misconceptions = emptyList(),
        )
        val report = ContentValidator.validate(listOf(sub)) { null }
        assertFalse(report.ok, "a tautological invariant must fail validateContent (D4 hard error)")
        assertTrue(report.issues.any { it.rule == "tautological_invariant" })
    }
}
