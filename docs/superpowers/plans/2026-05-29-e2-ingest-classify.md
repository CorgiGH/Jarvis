# E2 — Ingest + Classify — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn raw lecture PDFs + past exams into a trustworthy, git-tracked KC corpus (grounded KCs + prereq edges + exam problems linked to KCs with rubrics) whose signal can drive E1's grader + mastery store, with a non-circular grounding gate. General pipeline; PA runs first.

**Architecture:** Hybrid. Kotlin does the deterministic mechanical parts (per-page `pdftotext` source-of-record with page+offset+provenance; the re-pointed span-anchored validator; schema; the server-side grade-route lookup). Claude in-session does judgment (KC discovery, `kcIds` assignment, vision-confirm of extraction vs the rendered page on the Claude Max 20x sub — free, no new Kotlin). Grounding becomes non-circular by: committing deterministic `pdftotext` (not an LLM paraphrase) as the source-of-record, anchoring each citation to a `(page, span)` with provenance, and requiring `vision-confirmed` provenance for `grounding_tier: strict` KCs.

**Tech Stack:** Kotlin 2.0.21 / Ktor 3.0.1 / Exposed (SQLite) / kaml (YAML) / PDFBox 2.0.30 / poppler `pdftotext` v4.00 / kotlin.test + JUnit5. Spec: `docs/superpowers/specs/2026-05-29-e2-ingest-classify-design.md`.

**Test command (no gradle wrapper in repo — verified this session):**
`/c/Tools/gradle-8.10/bin/gradle :test --tests "FULLY.Qualified.ClassName"` (the root `:test` qualifier is required, else the `:android` subproject swallows `--tests`). Full suite: `/c/Tools/gradle-8.10/bin/gradle :test`. Corpus gate: `/c/Tools/gradle-8.10/bin/gradle validateContent`. Baseline before starting: **827 pass / 0 fail**.

---

## File Structure

**Modified (Kotlin):**
- `src/main/kotlin/jarvis/content/ContentSchema.kt` — extend `SourceRef` (`page`, `span`, `provenance`); add `Span`; add `grounding_tier` to `KnowledgeConcept`.
- `src/main/kotlin/jarvis/content/ContentValidator.kt` — re-point `checkVerbatimSources` to span-anchored diacritic-exact confirm + tier severity + strict-provenance rule; add diacritic helpers.
- `src/main/kotlin/jarvis/tutor/PdfProblemExtractor.kt` — extend `Problem` (`kcIds`, `rubricItems`, `referenceSolution`, `canonicalAnswer`, `shape`).
- `src/main/kotlin/jarvis/web/TutorRoutes.kt` — (Task 4) grade route `/api/v1/drill/grade`: load persisted `Problem` by `(taskId, problemId)`, record mastery on server-side `Problem.kcIds`, server canonical answer, surface silent no-record; (Task 4b) add `POST /api/v1/task/{id}/prep-authored` + `ApiPrepAuthoredRequest` DTO.

**Created:**
- `src/main/kotlin/jarvis/content/SourceOfRecord.kt` — per-page `pdftotext` extractor + page/offset helpers (the source-of-record producer).
- `.gitattributes` (repo root) — pin `content/**/_sources/*.md` to LF (offset stability).

**Created/Modified (tests):**
- `src/test/kotlin/jarvis/content/SourceOfRecordTest.kt` — new (pure helpers).
- `src/test/kotlin/jarvis/content/ContentValidatorTest.kt` — add span/diacritic/tier cases; amend 1 pre-existing test.
- `src/test/kotlin/jarvis/content/ContentSchemaTest.kt` — new (additive-defaults round-trip).
- `src/test/kotlin/jarvis/tutor/DrillGradeServerSideTest.kt` — new, package `jarvis.tutor` (server-side mastery wiring + silent-no-record).
- `src/test/kotlin/jarvis/tutor/DrillGradeMasteryRouteTest.kt` — migrate 2 tests to persist a kcId-bearing Problem (Task 4 Step 4).
- `src/test/kotlin/jarvis/tutor/PrepAuthoredRouteTest.kt` — new (Task 4b write-path round-trip).

**Corpus (authored in-session, Tasks 6-7) + migrated (Task 3b):**
- `content/PA/_sources/pa-lecture-01.md` — regenerated from `pdftotext`.
- `content/PA/kcs/*.yaml`, `content/PA/misconceptions/*.yaml` — re-grounded with `page`/`span`/`provenance`; formula KCs marked `grounding_tier: strict`.

**Skill (out-of-repo, Task 5):**
- the `curate-tutor` skill doc — harden the authoring flow (KC discovery + vision-confirm + exam-ingest → `kcIds`/rubric/canonical).

---

## Task 1: Schema extensions (additive)

**Files:**
- Modify: `src/main/kotlin/jarvis/content/ContentSchema.kt`
- Modify: `src/main/kotlin/jarvis/tutor/PdfProblemExtractor.kt:17-24`
- Test: `src/test/kotlin/jarvis/content/ContentSchemaTest.kt` (create)

- [ ] **Step 1: Write the failing test** — `ContentSchemaTest.kt`. Asserts (a) a legacy KC YAML with NO new fields still decodes (defaults apply), and (b) a SourceRef with page/span/provenance round-trips.

```kotlin
package jarvis.content

import com.charleskorn.kaml.Yaml
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ContentSchemaTest {
    @Test
    fun `legacy KC yaml without new source fields still decodes with defaults`() {
        val yaml = """
            id: pa-kc-001
            subject: PA
            name_ro: "A"
            name_en: "Algorithm"
            cluster: f
            bloom_level: understand
            difficulty: 1
            time_minutes: 10
            exam_weight: 1.0
            tier: 1
            source:
              - doc: pa-lecture-01
                quote: "a finite sequence of steps"
            version: 1
        """.trimIndent()
        val kc = Yaml.default.decodeFromString(KnowledgeConcept.serializer(), yaml)
        assertEquals("standard", kc.grounding_tier)
        val ref = kc.source.single()
        assertEquals(0, ref.page)
        assertNull(ref.span)
        assertEquals("pdftotext", ref.provenance)
    }

    @Test
    fun `source ref with page span provenance round-trips`() {
        val yaml = """
            doc: pa-lecture-01
            quote: "x"
            page: 3
            span:
              start: 100
              end: 101
            provenance: vision-confirmed
        """.trimIndent()
        val ref = Yaml.default.decodeFromString(SourceRef.serializer(), yaml)
        assertEquals(3, ref.page)
        assertEquals(100, ref.span?.start)
        assertEquals(101, ref.span?.end)
        assertEquals("vision-confirmed", ref.provenance)
    }
}
```

- [ ] **Step 2: Run it, verify it fails**

Run: `/c/Tools/gradle-8.10/bin/gradle :test --tests "jarvis.content.ContentSchemaTest"`
Expected: FAIL to compile — `grounding_tier`, `span`, `provenance`, `Span` don't exist yet.

- [ ] **Step 3: Implement the schema changes** — in `ContentSchema.kt`, replace the `SourceRef` block and add `Span`; add one field to `KnowledgeConcept`.

```kotlin
/** Char offsets into the committed source-of-record text (_sources/{doc}.md). */
@Serializable
data class Span(val start: Int, val end: Int)

/** A verbatim citation. [quote] must appear in the source-of-record of [doc].
 *  When [span] is present it is the authoritative anchor (raw offsets); [page]
 *  is 1-indexed (0 = unspecified). [provenance] is "pdftotext" (machine) or
 *  "vision-confirmed" (Claude re-read the rendered page and confirmed the span). */
@Serializable
data class SourceRef(
    val doc: String,
    val quote: String,
    val page: Int = 0,
    val span: Span? = null,
    val provenance: String = "pdftotext",
)
```

In `KnowledgeConcept`, add (after `tier`, before `source`):

```kotlin
    val tier: Int,
    /** "standard" | "strict". strict KCs (formula/algorithm) require every
     *  source ref to carry a span AND provenance == "vision-confirmed". */
    val grounding_tier: String = "standard",
    val source: List<SourceRef> = emptyList(),
```

- [ ] **Step 4: Extend `Problem`** — in `PdfProblemExtractor.kt:17-24`, add fields (all defaulted = additive; existing `parseLlmJson` keeps working):

```kotlin
@Serializable
data class Problem(
    val problemId: String,
    val page: Int,
    val statement: String,
    val equationRefs: List<String> = emptyList(),
    val dataGivens: List<String> = emptyList(),
    // E2: server-side authoritative grading inputs (populated at ingest, never client-trusted).
    val kcIds: List<String> = emptyList(),
    val rubricItems: List<String> = emptyList(),
    val referenceSolution: String? = null,
    val canonicalAnswer: String? = null,
    val shape: String? = null,   // descriptive: computational|proof-derivation|design-implement|analysis-trace|fact-conceptual
)
```

- [ ] **Step 5: Run the test, verify it passes**

Run: `/c/Tools/gradle-8.10/bin/gradle :test --tests "jarvis.content.ContentSchemaTest"`
Expected: PASS.

- [ ] **Step 6: Run the full suite — confirm still green (additive change)**

Run: `/c/Tools/gradle-8.10/bin/gradle :test` and `/c/Tools/gradle-8.10/bin/gradle validateContent`
Expected: 828 pass / 0 fail (827 + the 2 new ContentSchemaTest minus none removed — exact count may differ; the gate is 0 FAIL). validateContent: OK.

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/jarvis/content/ContentSchema.kt src/main/kotlin/jarvis/tutor/PdfProblemExtractor.kt src/test/kotlin/jarvis/content/ContentSchemaTest.kt
git commit -m "feat(e2): additive schema — SourceRef page/span/provenance, KC grounding_tier, Problem grading fields"
```

---

## Task 2: Source-of-record extractor (`SourceOfRecord.kt`)

**Files:**
- Create: `src/main/kotlin/jarvis/content/SourceOfRecord.kt`
- Test: `src/test/kotlin/jarvis/content/SourceOfRecordTest.kt` (create)

The committed `_sources/{doc}.md` is the raw `pdftotext` output, which uses form-feed (``) as the page separator. `Span` offsets index into that raw text globally; `page` is derived from how many form-feeds precede the offset. The pure helpers (`pageOf`, `slice`) are TDD'd here; the `pdftotext` shell-out (`extract`) is a thin wrapper exercised in Task 6 (needs a real PDF).

- [ ] **Step 1: Write the failing test** — `SourceOfRecordTest.kt`.

```kotlin
package jarvis.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SourceOfRecordTest {
    // Two pages separated by a form feed.
    private val text = "page one alphapage two beta"

    @Test
    fun `pageOf returns 1-indexed page for a global offset`() {
        assertEquals(1, SourceOfRecord.pageOf(text, 0))     // 'p' of page one
        assertEquals(1, SourceOfRecord.pageOf(text, 13))    // within page one
        assertEquals(2, SourceOfRecord.pageOf(text, 15))    // first char after the form feed
    }

    @Test
    fun `slice returns the exact raw substring for an in-bounds span`() {
        // "alpha" starts at index 9, length 5.
        assertEquals("alpha", SourceOfRecord.slice(text, Span(9, 14)))
    }

    @Test
    fun `slice returns null for an out-of-bounds span`() {
        assertNull(SourceOfRecord.slice(text, Span(100, 110)))
        assertNull(SourceOfRecord.slice(text, Span(10, 5)))  // start > end
    }
}
```

- [ ] **Step 2: Run it, verify it fails**

Run: `/c/Tools/gradle-8.10/bin/gradle :test --tests "jarvis.content.SourceOfRecordTest"`
Expected: FAIL — `SourceOfRecord` does not exist.

- [ ] **Step 3: Implement `SourceOfRecord.kt`**

```kotlin
package jarvis.content

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

/** Produces and queries the committed source-of-record extraction.
 *  The committed `_sources/{doc}.md` is raw `pdftotext` output; pages are
 *  separated by form-feed (). Span offsets index the raw text globally. */
object SourceOfRecord {
    const val PAGE_BREAK = ''

    /** 1-indexed page containing global [offset] (counts form-feeds before it). */
    fun pageOf(text: String, offset: Int): Int {
        if (offset <= 0) return 1
        val end = offset.coerceAtMost(text.length)
        var page = 1
        for (i in 0 until end) if (text[i] == PAGE_BREAK) page++
        return page
    }

    /** Exact raw substring for [span], or null if out of bounds / inverted. */
    fun slice(text: String, span: Span): String? {
        if (span.start < 0 || span.end > text.length || span.start > span.end) return null
        return text.substring(span.start, span.end)
    }

    /** Resolve the pdftotext binary: env override, then PATH, then known install. */
    fun resolveBin(): String =
        System.getenv("JARVIS_PDFTOTEXT_BIN")
            ?: sequenceOf("pdftotext", "C:\\tools\\poppler\\pdftotext.exe")
                .firstOrNull { it == "pdftotext" || Path.of(it).exists() }
            ?: "pdftotext"

    /** Shell out to pdftotext, returning raw text with  page breaks.
     *  Empty string on any failure (caller decides severity). */
    fun extract(pdf: Path, bin: String = resolveBin()): String {
        if (!Files.isRegularFile(pdf)) return ""
        return try {
            val proc = ProcessBuilder(bin, "-enc", "UTF-8", pdf.toString(), "-")
                .redirectErrorStream(false)
                .start()
            val out = proc.inputStream.readBytes().toString(Charsets.UTF_8)
            proc.waitFor()
            // Normalize to LF (round-4 gap #6): span offsets must index the SAME
            // bytes validateContent reads back. CRLF vs LF mismatch silently breaks
            // every span. Commit _sources as LF (see .gitattributes step below).
            if (proc.exitValue() == 0) out.replace("\r\n", "\n") else ""
        } catch (e: Exception) {
            System.err.println("[source-of-record] $pdf: ${e.javaClass.simpleName}: ${e.message?.take(120)}")
            ""
        }
    }
}
```

- [ ] **Step 4: Run the test, verify it passes**

Run: `/c/Tools/gradle-8.10/bin/gradle :test --tests "jarvis.content.SourceOfRecordTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Pin LF newlines for `_sources` (round-4 gap #6).** Create/append `.gitattributes` at repo root so git never rewrites `_sources` newlines (else CRLF↔LF flips invalidate every committed span):

```
content/**/_sources/*.md text eol=lf
```

`ContentRepo.sourceText` reads via `Path.readText()` (UTF-8, preserves on-disk bytes); combined with LF-only extraction + this `.gitattributes`, author-computed offsets and validator-read offsets index identical bytes.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/jarvis/content/SourceOfRecord.kt src/test/kotlin/jarvis/content/SourceOfRecordTest.kt .gitattributes
git commit -m "feat(e2): SourceOfRecord — per-page pdftotext extractor + page/offset helpers; pin _sources to LF"
```

---

## Task 3a: Re-point the validator (span-anchored, diacritic-exact, tier severity)

**Files:**
- Modify: `src/main/kotlin/jarvis/content/ContentValidator.kt:146-186`
- Test: `src/test/kotlin/jarvis/content/ContentValidatorTest.kt` (add cases)

New `checkVerbatimSources` behavior, per source ref:
1. `sourceText(doc) == null` → **warning** (unchanged: cannot verify).
2. `span != null` → confirm `SourceOfRecord.slice(text, span)` equals `ref.quote` **diacritic-exactly** (exact bytes). Mismatch / out-of-bounds → **error** (rule `verbatim_source`).
3. `span == null` → candidate-find: diacritic-INSENSITIVE, whitespace-normalized substring. Found → for `grounding_tier == "strict"` still an **error** ("strict KC requires an anchored span"); for standard a **warning** ("ungrounded span"). Not found → **error** (quote absent).
4. After per-ref checks, for `grounding_tier == "strict"` KCs: every ref must have `provenance == "vision-confirmed"`, else **error**.
5. Empty source list → **error** (unchanged).

- [ ] **Step 1: Write the failing tests** — append to `ContentValidatorTest.kt`. Note the `srcLookup` fixture already in the file (`pa-lecture-01` → `"An algorithm is a finite\n  sequence of unambiguous steps."`). Add a Romanian-diacritic fixture + cases.

```kotlin
    // --- E2: span-anchored, diacritic-exact, tier severity ---

    // Raw source-of-record with a Romanian diacritic: "și" (s-comma + i) at a known offset.
    private val roLookup: (String) -> String? = { doc ->
        if (doc == "ro-doc") "structuri și liste" else null  // "și" at offsets 10..12
    }

    private fun kcSpan(id: String, refs: List<SourceRef>, tier: String = "standard") =
        kc(id, tier = 1, weight = 1.0).copy(grounding_tier = tier, source = refs)

    @Test
    fun `span-anchored exact quote passes`() {
        // "structuri" is offsets 0..9 in roLookup("ro-doc").
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
        val issues = ContentValidator.checkVerbatimSources(sub, srcLookup)
        assertTrue(issues.any { it.severity == "error" })
    }

    @Test
    fun `strict KC with span but non-vision-confirmed provenance is an ERROR`() {
        // span correct, but provenance defaults to "pdftotext".
        val sub = LoadedSubject("PA",
            kcs = listOf(kcSpan("a", listOf(SourceRef("ro-doc", "structuri", page = 1, span = Span(0, 9))), tier = "strict")),
            edges = emptyList(), misconceptions = emptyList())
        val issues = ContentValidator.checkVerbatimSources(sub, roLookup)
        assertTrue(issues.any { it.severity == "error" && it.detail.contains("vision-confirmed") })
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
```

NOTE (round-4 correction): exactly ONE pre-existing test edits here — `quote that is a verbatim substring of the source passes` (line ~122) calls `checkVerbatimSources(...).isEmpty()`; under the new rule a span-less standard quote yields a WARNING, so change it to `assertTrue(ContentValidator.checkVerbatimSources(sub, srcLookup).none { it.severity == "error" })`. The `validate aggregates …` test (line ~161) asserts only `report.ok` (still true since warnings don't fail) — leave it unchanged. The error/warning tests at ~126 and ~147 still hold under new logic (span-less not-found → error; null text → warning).

- [ ] **Step 2: Run, verify failure**

Run: `/c/Tools/gradle-8.10/bin/gradle :test --tests "jarvis.content.ContentValidatorTest"`
Expected: FAIL — new behavior not implemented; `Span`/`grounding_tier` referenced.

- [ ] **Step 3: Implement** — replace `normalizeWs` + `checkVerbatimSources` in `ContentValidator.kt` (lines 146-186) with:

```kotlin
    private fun normalizeWs(s: String): String = s.replace(Regex("\\s+"), " ").trim()

    /** Strip Unicode combining marks (Romanian ș/ț/ă/â/î → s/t/a/a/i). */
    private fun stripDiacritics(s: String): String =
        java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")

    private fun fold(s: String): String = normalizeWs(stripDiacritics(s)).lowercase()

    /**
     * E2 grounding gate. Per source ref:
     *  - absent source text → warning (cannot verify);
     *  - span present → diacritic-EXACT confirm of the raw slice vs the quote; mismatch/oob → error;
     *  - span absent → diacritic-insensitive candidate-find: not found → error;
     *    found → error if the KC is grounding_tier=strict (span required), else warning;
     *  - empty source list → error;
     *  - grounding_tier=strict: every ref must be provenance=="vision-confirmed", else error.
     */
    fun checkVerbatimSources(
        sub: LoadedSubject,
        sourceText: (doc: String) -> String?,
    ): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()

        fun err(owner: String, id: String, msg: String) =
            issues.add(ValidationIssue("error", "verbatim_source", sub.subject, "$owner '$id': $msg"))
        fun warn(owner: String, id: String, msg: String) =
            issues.add(ValidationIssue("warning", "verbatim_source", sub.subject, "$owner '$id': $msg"))

        fun checkOne(owner: String, id: String, refs: List<SourceRef>, strict: Boolean) {
            if (refs.isEmpty()) { err(owner, id, "has no source attribution"); return }
            for (ref in refs) {
                val text = sourceText(ref.doc)
                if (text == null) { warn(owner, id, "source '${ref.doc}' has no extracted text on disk — quote unverifiable"); continue }
                val span = ref.span
                if (span != null) {
                    val slice = SourceOfRecord.slice(text, span)
                    if (slice == null) err(owner, id, "span ${span.start}..${span.end} out of bounds in '${ref.doc}'")
                    else if (slice != ref.quote) err(owner, id, "quote does not match raw span in '${ref.doc}' (diacritic-exact)")
                } else {
                    if (!fold(text).contains(fold(ref.quote))) err(owner, id, "quote not found in source '${ref.doc}'")
                    else if (strict) err(owner, id, "strict KC requires an anchored (page, span) — none on ref to '${ref.doc}'")
                    else warn(owner, id, "ungrounded span — quote found by fuzzy match only in '${ref.doc}'")
                }
                if (strict && ref.provenance != "vision-confirmed")
                    err(owner, id, "strict KC requires provenance=vision-confirmed on '${ref.doc}' (got '${ref.provenance}')")
            }
        }

        for (kc in sub.kcs) checkOne("KC", kc.id, kc.source, kc.grounding_tier == "strict")
        for (m in sub.misconceptions) checkOne("misconception", m.id, m.source, false)
        return issues
    }
```

- [ ] **Step 4: Run, verify pass**

Run: `/c/Tools/gradle-8.10/bin/gradle :test --tests "jarvis.content.ContentValidatorTest"`
Expected: PASS (all old + 5 new).

- [ ] **Step 5: Run full suite + validateContent**

Run: `/c/Tools/gradle-8.10/bin/gradle :test` then `/c/Tools/gradle-8.10/bin/gradle validateContent`
Expected: suite 0 FAIL. validateContent: OK — the existing PA KCs have span-less standard refs → WARNINGS, not errors, so the corpus stays green at this step. (Marking PA strict happens in 3b.)

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/jarvis/content/ContentValidator.kt src/test/kotlin/jarvis/content/ContentValidatorTest.kt
git commit -m "feat(e2): re-point validator to span-anchored diacritic-exact grounding + strict tier rule"
```

---

## Task 3b: PA re-grounding migration (authoring — Claude in-session)

**Not a TDD code task.** This regenerates the PA source-of-record from `pdftotext` and re-grounds the existing 6 KCs + 2 misconceptions against the RENDERED PDF (anti-laundering: a real vision read, not a stamp). Marking a KC `strict` + re-grounding it happens together so `gradle check` is not left red.

> **Honor-system caveat (round-4, Risk Analyst):** nothing in code can verify that `provenance: vision-confirmed` corresponds to an actual rendered-page read — it is an author-asserted string the CI gate cannot falsify (spec §4 names this the accepted frontier). The discipline is real but unenforceable; the executor (Claude) MUST actually Read the rendered page before stamping. Do not batch-stamp.
>
> **Offsets are LF-relative:** after Step 2 the file is committed as LF (Task 2 `.gitattributes`). Compute `span` offsets as character indices into the file exactly as written on disk (LF newlines, form-feed page breaks).

**Files:** `content/PA/_sources/pa-lecture-01.md` (regenerated), `content/PA/kcs/*.yaml`, `content/PA/misconceptions/*.yaml`.

- [ ] **Step 1: Locate the PA source PDF.** Per the spec, PA source PDFs live under `tmp-secondbrain-scrape/_fii/_gdrive/PA_Y1/...`; `pa-lecture-01.md` header names `PA_Y1/Curs/curs_2020-2021/Curs 1 PA.pdf`. Confirm the file exists; if not, search recursively for `Curs 1 PA.pdf`.

- [ ] **Step 2: Regenerate the source-of-record** (deterministic):

```bash
pdftotext -enc UTF-8 "<path>/Curs 1 PA.pdf" content/PA/_sources/pa-lecture-01.md
```

This OVERWRITES the hand-curated markdown with raw pdftotext (form-feed page breaks). Diff it: `git diff content/PA/_sources/pa-lecture-01.md` — expect the outline/attribution header to vanish and raw slide text to appear.

- [ ] **Step 3: For each of the 6 KCs + 2 misconceptions, re-ground each `source` ref:**
  1. Read the rendered PDF page in-session (Read tool on the PDF, the page the quote is on) — this is the free 20x vision read.
  2. Find the quote in the regenerated `pa-lecture-01.md`; compute its raw char offsets `(start, end)` and `page`. (Use Grep/Read to locate; offsets = character index in the file.)
  3. If the quote no longer appears verbatim (curated text was cleaned), either correct the `quote` to the actual rendered text or drop the ref.
  4. Set `page`, `span: {start, end}`. Set `provenance: vision-confirmed` ONLY if you actually read the rendered page and it matches (anti-laundering rule).
  5. For KCs whose content is a formula/algorithm/definition that must be exact (e.g. `pa-kc-001` "notion of algorithm" definitions), set `grounding_tier: strict`.

- [ ] **Step 4: Validate** — `/c/Tools/gradle-8.10/bin/gradle validateContent`. Expected: OK, 0 errors. Any strict KC still lacking a vision-confirmed span → fix or downgrade before proceeding. Then full suite `:test` → 0 FAIL (restores baseline).

- [ ] **Step 5: Commit**

```bash
git add content/PA/_sources/pa-lecture-01.md content/PA/kcs content/PA/misconceptions
git commit -m "chore(e2): re-ground PA corpus against rendered PDF (span+page+provenance), mark formula KCs strict"
```

---

## Task 4: Grade-route server-side wiring (wakes the mastery loop)

**Files:**
- Modify: `src/main/kotlin/jarvis/web/TutorRoutes.kt` (grade route, `:1758-1854`)
- Test: `src/test/kotlin/jarvis/tutor/DrillGradeServerSideTest.kt` (create — package `jarvis.tutor`)
- Migrate: `src/test/kotlin/jarvis/tutor/DrillGradeMasteryRouteTest.kt` (2 tests, Step 4)

Goal: mastery records on the **persisted** `Problem.kcIds` (looked up by `(taskId, problemId)`), and the canonical-answer exact-match uses the **persisted** `Problem.canonicalAnswer` — NOT client `req.conceptIds` / `req.canonicalAnswer`. (Round-4 correction: the real grade-route harness is `DrillGradeMasteryRouteTest.kt` in package `jarvis.tutor`; there is NO `withGradeTestApp`/`postGrade` helper — reuse its inline `testApplication`/`installFreshTutor`/`seedSession`/`FakeGraderLlm`/raw-JSON pattern verbatim.)

- [ ] **Step 1: Write the failing test** — create `src/test/kotlin/jarvis/tutor/DrillGradeServerSideTest.kt`. Copies the verified harness from `DrillGradeMasteryRouteTest.kt` (same package, so `installFreshTutor`/`seedSession`/`FakeGraderLlm` patterns apply; `TutorContext`, `TaskPrepRepo`, `KcMasteryRepo`, `Problem` are all in `jarvis.tutor`).

```kotlin
package jarvis.tutor

import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.cookie
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.testing.testApplication
import jarvis.ChatMessage
import jarvis.Llm
import jarvis.web.drillGraderLlmFactory
import jarvis.web.installTutorContext
import jarvis.web.installTutorRoutes
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DrillGradeServerSideTest {
    private class FakeGraderLlm(private val json: String) : Llm {
        override suspend fun complete(messages: List<ChatMessage>, maxTokens: Int, responseFormat: String?) =
            json to "fake-grader-model"
    }
    @AfterEach fun resetSeam() { drillGraderLlmFactory = { jarvis.OpenRouterChatLlm() } }

    private fun Application.installFreshTutor(tmp: Path) {
        install(ServerContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = true }) }
        installTutorContext(tmp.resolve("t.db").toString(), tmp)
        installTutorRoutes()
    }
    private fun seedSession(ctx: TutorContext): Pair<String, String> {
        val userId = TutorTypes.ulid()
        UserRepo(ctx.db).insert(User(userId, "v", UserScope.OWNER, Instant.now(), Instant.now()))
        val sid = SessionRepo(ctx.db).create(userId, ttlSeconds = 3600)
        AiLiteracyRepo(ctx.db).confirm(userId, AI_LITERACY_VERSION, "ro")
        return userId to sid
    }
    // Coherent grade → GradeScoring.isConfident == true.
    private val COHERENT = """{"correct":true,"rubric":{"numeric":true,"mechanism":true},""" +
        """"score":1.0,"misconception":null,"elaborated_feedback":"ok"}"""

    @Test
    fun `mastery records on persisted Problem kcIds, ignoring client conceptIds`(@TempDir tmp: Path) = testApplication {
        drillGraderLlmFactory = { FakeGraderLlm(COHERENT) }
        var ctx: TutorContext? = null
        application { installFreshTutor(tmp); ctx = attributes[TutorContextKey] }
        startApplication()
        val (userId, sid) = seedSession(ctx!!)
        // Authoritative server-side Problem: kcIds=["pa-kc-002"], canonical "42".
        TaskPrepRepo(ctx!!.db).upsert(TaskPrep(
            taskId = "task-1", generatedAt = Instant.now(), version = 1,
            problemsJson = Json.encodeToString(ListSerializer(Problem.serializer()),
                listOf(Problem(problemId = "d1", page = 1, statement = "6*7?",
                    kcIds = listOf("pa-kc-002"), canonicalAnswer = "42"))),
            drillsJson = "{}", railJson = "[]",
        ))
        val csrf = "test-csrf-12345"
        val client = createClient {
            install(HttpCookies); install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        // Client LIES: conceptIds=["WRONG-KC"], no client canonicalAnswer. Attempt "42".
        val resp = client.post("/api/v1/drill/grade") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody("""{"taskId":"task-1","problemId":"d1","userAttempt":"42","problemStatement":"6*7?","expectedAnswerHint":"42","conceptIds":["WRONG-KC"]}""")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        assertTrue(resp.bodyAsText().contains("\"answerMatch\":true"), "server canonical used; body=${resp.bodyAsText()}")
        assertNotNull(KcMasteryRepo(ctx!!.db).get(userId, "pa-kc-002"), "records on persisted Problem.kcIds")
        assertNull(KcMasteryRepo(ctx!!.db).get(userId, "WRONG-KC"), "client conceptIds NOT trusted")
    }

    @Test
    fun `confident grade with no persisted kcIds records nothing and recorded is false`(@TempDir tmp: Path) = testApplication {
        drillGraderLlmFactory = { FakeGraderLlm(COHERENT) }
        var ctx: TutorContext? = null
        application { installFreshTutor(tmp); ctx = attributes[TutorContextKey] }
        startApplication()
        val (userId, sid) = seedSession(ctx!!)
        // No TaskPrep persisted → no server-side kcIds.
        val csrf = "test-csrf-12345"
        val client = createClient {
            install(HttpCookies); install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val resp = client.post("/api/v1/drill/grade") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody("""{"taskId":"task-1","problemId":"d1","userAttempt":"x","problemStatement":"p","expectedAnswerHint":"h","conceptIds":["pa-kc-001"]}""")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        assertTrue(resp.bodyAsText().contains("\"recorded\":false"), "no server kcIds → not recorded; body=${resp.bodyAsText()}")
        assertNull(KcMasteryRepo(ctx!!.db).get(userId, "pa-kc-001"), "client conceptIds must not record")
    }
}
```

- [ ] **Step 2: Run, verify failure (RED)**

Run: `/c/Tools/gradle-8.10/bin/gradle :test --tests "jarvis.tutor.DrillGradeServerSideTest"`
Expected: FAIL. Correct RED mechanism (round-4 fix): the CURRENT code records mastery from client `req.conceptIds`, so test 1 records on `WRONG-KC` (→ `assertNull(WRONG-KC)` fails AND `assertNotNull(pa-kc-002)` fails; also no client canonical → no `"answerMatch":true`), and test 2 records on client `pa-kc-001` → `recorded:true` (→ `assertTrue("recorded":false)` fails).

- [ ] **Step 3: Implement the rewiring** — in `TutorRoutes.kt`, just after `req` is parsed (`:1772`, still inside `call.csrfProtect`), load the persisted problem:

```kotlin
                // E2: resolve the persisted Problem server-side — the client's
                // conceptIds / canonicalAnswer are NOT trusted for the recorded signal.
                val serverProblem: jarvis.tutor.Problem? = run {
                    val prep = jarvis.tutor.TaskPrepRepo(ctx.db).findByTaskId(req.taskId) ?: return@run null
                    val problems = try {
                        sensorJson.decodeFromString(
                            kotlinx.serialization.builtins.ListSerializer(jarvis.tutor.Problem.serializer()),
                            prep.problemsJson,
                        )
                    } catch (_: Exception) { emptyList() }
                    problems.firstOrNull { it.problemId == req.problemId }
                }
```

Then in the `else ->` branch (`:1840-1854`), replace the canonical-answer source and the mastery-recording block:

```kotlin
                        val g: GradeResult = attempt.parsed!!
                        // Canonical answer comes from the persisted Problem, not the client.
                        val canonical = serverProblem?.canonicalAnswer ?: req.canonicalAnswer
                        val answerMatch: Boolean? =
                            canonical?.let { GradeScoring.answerMatches(it, req.userAttempt) }
                        val rubricCorrect = GradeScoring.correctFromRubric(g.rubric)
                        val deterministicCorrect = answerMatch ?: rubricCorrect
                        val deterministicScore = GradeScoring.scoreFromRubric(g.rubric)
                        val coherent = GradeScoring.isConfident(g)
                        val answerAgrees = answerMatch == null || answerMatch == rubricCorrect
                        val confident = coherent && answerAgrees
                        var recorded = false
                        // Mastery credits the persisted Problem.kcIds (server-side). Client conceptIds ignored.
                        val masteryKcs = serverProblem?.kcIds ?: emptyList()
                        if (confident && masteryKcs.isNotEmpty()) {
                            val repo = jarvis.tutor.KcMasteryRepo(ctx.db)
                            masteryKcs.forEach { kcId -> repo.record(userId, kcId, deterministicScore) }
                            recorded = true
                        } else if (confident && masteryKcs.isEmpty()) {
                            // Round-4 gap #5: make the silent-skip observable. recorded stays false.
                            System.err.println("[drill-grade] confident grade for task=${req.taskId} problem=${req.problemId} but no server-side kcIds — mastery NOT recorded")
                        }
```

Leave `ApiDrillGradeReply(...)` unchanged (`recorded` already carries the false signal). Do NOT change the `DrillGrader.grade(...)` call in this task (keep `req.referenceSolution`/`req.rubricItems`) — moving those server-side is deferred to keep the existing grade tests stable.

- [ ] **Step 4: Migrate the 2 existing tests that now break** — in `DrillGradeMasteryRouteTest.kt`, the tests `confident grade records mastery` and `canonical answer match records and reports answerMatch true` post `conceptIds` but persist NO Problem, so under the new contract they record nothing and fail. Migrate each: right after `seedSession`, persist the authoritative Problem (the bodies use `taskId="task-1"`, `problemId="d1"`):

```kotlin
        TaskPrepRepo(ctx!!.db).upsert(TaskPrep(
            taskId = "task-1", generatedAt = Instant.now(), version = 1,
            problemsJson = Json.encodeToString(ListSerializer(Problem.serializer()),
                listOf(Problem(problemId = "d1", page = 1, statement = "p",
                    kcIds = listOf("pa-kc-001"),
                    canonicalAnswer = "o(n log n)."))),  // only needed for the canonical-match test
            drillsJson = "{}", railJson = "[]",
        ))
```

For `confident grade records mastery` omit `canonicalAnswer`. Add the imports `kotlinx.serialization.builtins.ListSerializer`. The `deferred…` and `disagreeing…` tests assert NO record and need no change (they still defer). NOTE: these two tests no longer prove "client conceptIds records" (that path is gone) — they now prove the server-side path; that is the intended contract change.

- [ ] **Step 5: Run new + migrated tests, verify pass**

Run: `/c/Tools/gradle-8.10/bin/gradle :test --tests "jarvis.tutor.DrillGradeServerSideTest"` then `--tests "jarvis.tutor.DrillGradeMasteryRouteTest"`
Expected: PASS.

- [ ] **Step 6: Run full suite**

Run: `/c/Tools/gradle-8.10/bin/gradle :test`
Expected: 0 FAIL. (Grep `src/test` for any OTHER test posting to `/api/v1/drill/grade` with `conceptIds` and no persisted Problem — migrate it the same way.)

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/jarvis/web/TutorRoutes.kt src/test/kotlin/jarvis/tutor/DrillGradeServerSideTest.kt src/test/kotlin/jarvis/tutor/DrillGradeMasteryRouteTest.kt
git commit -m "feat(e2): grade route records mastery on persisted Problem.kcIds + server canonical (no client trust); surface silent no-record"
```

---

## Task 4b: Production write path for kcId-bearing Problems (round-4 gap #1 — completes the loop)

**Files:**
- Modify: `src/main/kotlin/jarvis/web/TutorRoutes.kt` (add a route after `/api/v1/task/{id}/reprep`, `:1365`; add a request DTO near `ApiDrillGradeRequest`)
- Test: `src/test/kotlin/jarvis/tutor/PrepAuthoredRouteTest.kt` (create)

**Why:** Task 4 wired the loop's READ half, but no production code populates `Problem.kcIds`. The only `problemsJson` writer is `/reprep`, which builds `Problem(pid,page,stmt,eqs,givens)` with empty kcIds. This route lets the in-session `curate-tutor` flow (Task 5) PERSIST authored, kcId-bearing problems for a real task — the production write half. (Server-side authoritative: the route validates task ownership; the authored problems are produced by Claude's judgment, not client UI.)

- [ ] **Step 1: Write the failing test** — `PrepAuthoredRouteTest.kt`, package `jarvis.tutor`, reusing the same harness pattern. It must (a) seed a session, (b) create a Task owned by the user (mirror how `src/test/kotlin/jarvis/tutor/TutorRoutesTest.kt` builds a task via `TaskRepo(ctx.db).insert(...)` — read it for the exact `Task(...)`/`ContentRef(...)` constructor args), (c) POST authored problems, (d) assert `TaskPrepRepo.findByTaskId` round-trips `kcIds`.

```kotlin
        // ... after seedSession + creating a task with id `taskId` owned by userId ...
        val client = createClient {
            install(HttpCookies); install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val csrf = "test-csrf-12345"
        val resp = client.post("/api/v1/task/$taskId/prep-authored") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody("""{"problems":[{"problemId":"d1","page":1,"statement":"6*7?","kcIds":["pa-kc-002"],"canonicalAnswer":"42"}]}""")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val prep = TaskPrepRepo(ctx!!.db).findByTaskId(taskId)
        assertNotNull(prep)
        val problems = Json { ignoreUnknownKeys = true }
            .decodeFromString(ListSerializer(Problem.serializer()), prep.problemsJson)
        assertEquals(listOf("pa-kc-002"), problems.single { it.problemId == "d1" }.kcIds)
```

- [ ] **Step 2: Run, verify failure** — `/c/Tools/gradle-8.10/bin/gradle :test --tests "jarvis.tutor.PrepAuthoredRouteTest"` → FAIL (route 404 / not found).

- [ ] **Step 3: Add the request DTO** near `ApiDrillGradeRequest` (`TutorRoutes.kt:~2318`):

```kotlin
@Serializable
private data class ApiPrepAuthoredRequest(
    val problems: List<jarvis.tutor.Problem>,
    val version: Int = 1,
)
```

- [ ] **Step 4: Add the route** in `installTutorRoutes()`, immediately after the `/api/v1/task/{id}/reprep` block (`:1365`). Auth mirrors `/reprep` (session → userId → task ownership):

```kotlin
        post("/api/v1/task/{id}/prep-authored") {
            val ctx = application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@post }
            call.csrfProtect {
                val sid = call.request.cookies["jarvis_session"]
                val userId = sid?.let { SessionRepo(ctx.db).findUserId(it) }
                    ?: run { call.respond(HttpStatusCode.Unauthorized, "invalid session"); return@csrfProtect }
                val taskId = call.parameters["id"]?.takeIf { it.isNotBlank() }
                    ?: run { call.respond(HttpStatusCode.BadRequest, "id required"); return@csrfProtect }
                val task = jarvis.tutor.TaskRepo(ctx.db).findById(taskId)
                if (task == null || task.userId != userId) {
                    call.respond(HttpStatusCode.NotFound, "task not found"); return@csrfProtect
                }
                val body = try {
                    sensorJson.decodeFromString(ApiPrepAuthoredRequest.serializer(), call.receiveText())
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, "malformed: ${e.message?.take(160)}"); return@csrfProtect
                }
                val now = java.time.Instant.now()
                val problemsJson = jarvis.tutor.TutorTypes.tutorJson.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(jarvis.tutor.Problem.serializer()),
                    body.problems,
                )
                val existing = jarvis.tutor.TaskPrepRepo(ctx.db).findByTaskId(taskId)
                jarvis.tutor.TaskPrepRepo(ctx.db).upsert(jarvis.tutor.TaskPrep(
                    taskId = taskId, generatedAt = now, version = body.version,
                    problemsJson = problemsJson,
                    drillsJson = existing?.drillsJson ?: "{}",
                    railJson = existing?.railJson ?: "[]",
                ))
                call.respond(HttpStatusCode.OK, ApiTaskRepRepReply(taskId, body.problems.size, now.toString()))
            }
        }
```

- [ ] **Step 5: Run, verify pass** — `/c/Tools/gradle-8.10/bin/gradle :test --tests "jarvis.tutor.PrepAuthoredRouteTest"` → PASS. Then full suite `:test` → 0 FAIL.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/jarvis/web/TutorRoutes.kt src/test/kotlin/jarvis/tutor/PrepAuthoredRouteTest.kt
git commit -m "feat(e2): POST /task/{id}/prep-authored — persist authored kcId-bearing Problems (production write half)"
```

---

## Task 5: Harden the `curate-tutor` authoring flow (skill doc)

**Not a code task.** The `curate-tutor` skill is the in-session authoring procedure Claude follows to add a lecture/exam to the corpus. Extend its instructions to produce the new grounded shape.

**File:** the `curate-tutor` skill markdown (locate via the Skill registry; it lives under the user/plugin skills dir, not this repo).

- [ ] **Step 1: Read the current `curate-tutor` skill** to learn its existing KC-authoring steps.
- [ ] **Step 2: Add these procedure steps to the skill:**
  - Run `pdftotext -enc UTF-8 <pdf> content/<subj>/_sources/<doc>.md` to produce the source-of-record before authoring.
  - For each authored KC source ref: locate the quote in `_sources/<doc>.md`, record `page` + `span:{start,end}`; read the rendered PDF page in-session and set `provenance: vision-confirmed` when it matches; set `grounding_tier: strict` for formula/algorithm/definition KCs.
  - For exam ingest: extract problems, assign `kcIds` (match statement → subject KCs), copy the point-split rubric into `rubricItems`, set `canonicalAnswer` for computational problems and `referenceSolution`/`shape` as applicable; persist via the ingest path so they land in `task_prep.problemsJson`.
  - Anti-laundering reminder: never set `vision-confirmed` without actually reading the rendered page.
- [ ] **Step 3: Commit** (if the skill is in a git repo) or note the edit in the session handoff.

---

## Task 6: PA end-to-end run + feature-shipped proof (authoring)

**Not a code task.** Exercise the whole machine on PA and PROVE the loop fires on the live surface (CLAUDE.md feature-shipped rule — green tests are not enough).

- [ ] **Step 1: Pick a real PA past exam + answer-key** (per spec: Apr-2026 midterm in `Downloads/`, or a `PA_Y1` exam). Confirm the file.
- [ ] **Step 2: Ingest it via the hardened `curate-tutor` flow** (Task 5): create/identify a real task, extract problems, author each with `kcIds` (→ existing PA KCs) + `rubricItems` + `canonicalAnswer`/`referenceSolution` from the answer-key, and **persist via `POST /api/v1/task/{taskId}/prep-authored`** (Task 4b — the production write path). Confirm `task_prep.problemsJson` now carries non-empty `kcIds`. (Do NOT rely on `/reprep` — it writes problems with empty kcIds.)
- [ ] **Step 3: Validate** — `/c/Tools/gradle-8.10/bin/gradle validateContent` → OK; `:test` → 0 FAIL.
- [ ] **Step 4: Run the app and drill one problem** — `/c/Tools/gradle-8.10/bin/gradle runWeb`, log in, open the task, submit a correct attempt to one ingested problem. Confirm on the surface that `recorded: true` comes back AND mastery moved (query `kc_mastery` for the problem's `kcIds`, or the mastery surface if present). This is the gate: the dormant loop is now lit, observed live, not just in a test.
- [ ] **Step 5:** Record the proof (screenshot / DB row) in the session handoff. No commit unless content/code changed.

---

## Task 7: Run the same machine on PS / POO / SO / ALO (authoring)

**Not a code task — and NOT a single bite-sized task (round-4, Pragmatist).** This is FOUR independent multi-session authoring runs (one per subject), each repeating Tasks 5-6's flow. Treat each subject as its own task/session; do not attempt all four in one pass. Extraction flexes per the empirical PDF map: PA/POO/SO/ALO clean text-layer → `pdftotext`; PS = Type-3 fonts → rely on in-session vision reads for the affected pages; math-dense pages → vision regardless.

Per subject (PS, then POO, then SO, then ALO):
- [ ] Generate `_sources/*.md` via `pdftotext` (PS: vision for Type-3 pages); author grounded KCs (span/page/provenance, strict where formula/algorithm); build the prereq `edges.yaml`; `validateContent` green.
- [ ] Ingest that subject's past exams → author problems with `kcIds`/rubric/canonical → persist via `POST /api/v1/task/{taskId}/prep-authored` (Task 4b).
- [ ] Spot-drill one problem → confirm mastery records on the live surface (feature-shipped gate).
- [ ] Commit that subject's corpus: `git commit -m "content(e2): author <SUBJ> KC corpus + exam linkage"`.

---

## Self-Review

**1. Spec coverage:**
- §3 hybrid → Tasks 2 (Kotlin mechanical) + 5/6 (in-session judgment). ✓
- §4 grounding fix (source-of-record, span anchor, diacritic-exact, tier severity, anti-laundering) → Tasks 2, 3a, 3b. ✓
- §5 schema additive + destructive `_sources` migration → Tasks 1, 3b. ✓
- §6 shape on Problem/drill, not KC → Task 1 (`Problem.shape`; `KnowledgeConcept` gains only `grounding_tier`, not shape). ✓
- §7 / §7.1 exam ingest + grade-route rewiring → Tasks 4, 6. ✓
- §9 error rules (strict→error, span mismatch→error, kcId resolvability) → Task 3a (strict/span) ; kcId-resolves-to-real-KC enforcement: **GAP — add** (see below). 
- §10 green-baseline-breaks-then-restores → Task 3b ordering (validator change in 3a stays green via warnings; strict-marking+regrounding in 3b breaks+restores). ✓
- §11 effort honesty → reflected in task sizing (Task 2 flagged as largest mechanical). ✓
- §12 build order → Tasks 1-7 match. ✓

**Gap found & fixed inline:** §9 says "`Problem.kcIds` not resolving to a real KC → ERROR." No validator covers `Problem.kcIds` (problems aren't in the corpus validator). Resolution: this check belongs at INGEST time in the `curate-tutor` flow (Task 5) AND can be a lightweight server guard. **Add to Task 5 Step 2:** "reject/flag any `kcId` that does not resolve to an existing `content/<subj>/kcs/*.yaml` id." Documented here rather than forcing a new validator pass over transient problems. (Resolvable-but-wrong remains the accepted frontier per spec §9.)

**2. Placeholder scan:** No TBD/TODO. Authoring tasks (3b, 5, 6, 7) are inherently in-session judgment, not code — their steps are concrete actions with explicit acceptance (validateContent OK, mastery row present), not "implement later." Code tasks (1, 2, 3a, 4) carry complete code.

**3. Type consistency:** `Span(start,end)`, `SourceRef(doc,quote,page,span,provenance)`, `grounding_tier`, `Problem(...,kcIds,rubricItems,referenceSolution,canonicalAnswer,shape)` used identically across Tasks 1/3a/4. `SourceOfRecord.slice`/`pageOf`/`extract` signatures match between Task 2 def and Task 3a use. `KcMasteryRepo.record/get`, `TaskPrepRepo.findByTaskId/upsert`, `GradeScoring.*` match the read source.

**4. Build+mount pairing:** N/A — no new frontend components (E2 is backend; the user-facing proof is drilling an existing surface, Task 6 Step 4).

**5. Component-reuse contract:** N/A — no React component reuse.

**6. data-testid grep:** Spec has no `[data-testid]` Visual Acceptance list (backend slice); the visual gate is "mastery row moves on a live drill" (Task 6 Step 4).

---

## Revision 2 — plan-council (round 4) fixes folded in

1. **Loop WRITE half added (biggest):** new **Task 4b** `POST /api/v1/task/{id}/prep-authored` persists authored kcId-bearing Problems in production — previously no code populated `Problem.kcIds`, so the loop could never fire for a real task. Tasks 6/7 now persist via this route, not `/reprep`.
2. **Task 4 harness corrected:** real grade tests are `src/test/kotlin/jarvis/tutor/DrillGradeMasteryRouteTest.kt` (package `jarvis.tutor`) with inline `testApplication`/`installFreshTutor`/`seedSession`/`FakeGraderLlm` — the invented `withGradeTestApp`/`postGrade` helpers and the `jarvis/web/TutorRoutesTest.kt` path were fiction. New test rewritten to the real pattern.
3. **Task 4 migrates 2 breaking tests** (`confident grade records mastery`, `canonical answer match…`) to seed a kcId-bearing Problem (the no-client-trust contract change).
4. **Corrected RED rationale** (Task 4 Step 2): old code records on client conceptIds → WRONG-KC present / pa-kc-002 absent.
5. **Silent no-record surfaced:** grade route logs `confident && masteryKcs.isEmpty()`; `recorded:false` is the observable signal; Task 4 test 2 asserts it.
6. **CRLF/LF offset stability:** `SourceOfRecord.extract` normalizes to LF + `.gitattributes` pins `_sources` to LF (Task 2).
7. **Honor-system caveat** named at Task 3b (vision-confirmed is author-asserted, CI cannot verify).
8. **Task 7 split** into 4 per-subject multi-session runs (not one bite-sized task).
9. Task 3a test-edit count corrected to 1 (the `validate aggregates` test needs no change).

Validated by the council (round 4) and unchanged: diacritic offsets arithmetically correct; "stays green at 3a" correct (strict-marking deferred to 3b); kaml honors defaults; pdftotext/gradle/PA-PDF all present.
