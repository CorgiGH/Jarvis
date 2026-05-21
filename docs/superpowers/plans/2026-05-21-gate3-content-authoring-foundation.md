# Gate 3 — Content Authoring Foundation — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the content authoring foundation — a git-tracked YAML content schema, a Kotlin DAG validator, read-only Ktor curator routes, and the `/curate-tutor` Claude skill — and prove it by authoring a validator-passing PA (Algorithm Design) lecture-1 knowledge-concept corpus.

**Architecture:** Content is git-tracked YAML under `content/{subject}/...`, the source of truth (spec §5's Postgres `kcs`/`kc_prerequisites` tables are stale — KCs load into a runtime DB only in later pedagogy/drill gates). A new `jarvis.content` Kotlin package defines `@Serializable` schema data classes (parsed by kaml), a `ContentValidator` doing structural + verbatim-source checks, and a `MermaidMirror` generator. New `/api/v1/curator/*` READ + VALIDATE routes live in a focused `CuratorRoutes.kt`, registered from `installTutorRoutes()`. The `/curate-tutor` Claude skill (markdown) is the offline authoring tool — Claude reads the source PDF/markdown and emits YAML. The Python DSPy/MinerU/PaperQA2 sidecar (Gate 5) is unrelated runtime infrastructure, NOT a Gate 3 dependency.

**Tech Stack:** Kotlin 2.0.21 / Ktor 3.0.1 / Exposed+SQLite (untouched by Gate 3) / kaml (new) / kotlinx.serialization / JUnit5 + kotlin.test / Gradle. PDFBox 2.0.30 (already a dependency) available for source-text extraction.

**Council amendments** (from `.claude/council-cache/council-1779311876.md`, verdict FLAWED → 3 fixes):
1. The validator includes a **verbatim-source-quote check** — each KC/misconception `source.quote` must be a verbatim substring of the committed extracted-source text — and the `ValidationReport` self-labels `"structural checks only — content not groundedness-verified"`.
2. Curator routes are **READ + VALIDATE only**. No write / `git commit` / version-lock path — that is deferred to Gate 4, built alongside the curator SPA that consumes it.
3. Gate 3 acceptance = **deterministic artifacts**: the schema, the validator (with tests), the read routes, and a committed PA-lecture-1 corpus that passes `gradle validateContent`. `/curate-tutor` is the tool that produced the corpus, not a test that must pass in CI.

---

## File Structure

**New — Kotlin (`src/main/kotlin/jarvis/content/`):**
- `ContentSchema.kt` — `@Serializable` data classes for the YAML schema.
- `ContentRepo.kt` — loads `content/` YAML files into schema objects; reads `_sources/` extracted text.
- `ContentValidator.kt` — cycle / orphan / exam-weight / bilingual / verbatim-source checks → `ValidationReport`.
- `MermaidMirror.kt` — generates `edges.mmd` from an `EdgesFile`.
- `ContentCli.kt` — `main()` for the `validateContent` Gradle task.

**New — Kotlin (`src/main/kotlin/jarvis/web/`):**
- `CuratorRoutes.kt` — `fun Route.installCuratorRoutes()` with the read+validate routes.

**New — tests (`src/test/kotlin/jarvis/`):**
- `content/ContentSchemaTest.kt`, `content/ContentRepoTest.kt`, `content/ContentValidatorTest.kt`, `content/MermaidMirrorTest.kt`
- `web/CuratorRoutesTest.kt`

**New — content store (repo root, git-tracked):**
- `content/README.md`, `content/subjects.yaml`
- `content/PA/kcs/*.yaml`, `content/PA/edges.yaml`, `content/PA/edges.mmd`, `content/PA/misconceptions/*.yaml`, `content/PA/_sources/*.md`
- `.gitkeep` files for the other 4 subject dirs.

**New — skill:**
- `.claude/skills/curate-tutor/SKILL.md`

**Modified:**
- `build.gradle.kts` — add kaml dependency + `validateContent` Gradle task + `check` dependsOn.
- `src/main/kotlin/jarvis/web/TutorRoutes.kt` — one line inside `installTutorRoutes()`'s `routing { }` block: `installCuratorRoutes()`.

**Schema field naming:** YAML keys use `snake_case` matching spec §5 (`name_ro`, `exam_weight`, `kc_id`). Kotlin properties use the same `snake_case` names so kotlinx.serialization maps them with zero `@SerialName` annotations. This is deliberate — it keeps YAML ↔ class mapping trivial and the spec-faithful.

---

## Task 1: Add kaml dependency + validateContent Gradle task

**Files:**
- Modify: `build.gradle.kts:60` (dependencies block) and append a task registration.

- [ ] **Step 1: Add the kaml dependency**

In `build.gradle.kts`, inside the `dependencies { }` block, after the `kotlinx-serialization-json` line (line 33), add:

```kotlin
    // Gate 3: YAML parsing for the content/ knowledge-concept corpus.
    // kaml binds to kotlinx.serialization @Serializable classes (same classes
    // serialize to JSON for the curator routes).
    implementation("com.charleskorn.kaml:kaml:0.65.0")
```

Note: `0.65.0` targets kotlinx-serialization 1.7.x (matches the pinned `kotlinx-serialization-json:1.7.3`). If `gradle build` reports a serialization-runtime version clash, pick the newest `com.charleskorn.kaml:kaml` release whose README lists kotlinx-serialization 1.7.x — verify on https://central.sonatype.com/artifact/com.charleskorn.kaml/kaml before changing the version.

- [ ] **Step 2: Register the validateContent task**

Append to `build.gradle.kts` (after the `migrateWiki` task, line 164):

```kotlin
tasks.register<JavaExec>("validateContent") {
    group = "verification"
    description = "Validate the content/ knowledge-concept corpus (DAG + structural checks)."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("jarvis.content.ContentCliKt")
    args = listOf("content")
    jvmArgs = listOf("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}
```

(`ContentCliKt` does not exist yet — it is created in Task 12. The build still configures fine; the task just cannot run until then.)

- [ ] **Step 3: Verify the build resolves**

Run: `gradle :compileKotlin -q`
Expected: BUILD SUCCESSFUL (kaml resolves from Maven Central).

- [ ] **Step 4: Commit**

```bash
git add build.gradle.kts
git commit -m "build(gate3): add kaml dependency + validateContent task"
```

---

## Task 2: Content schema data classes

**Files:**
- Create: `src/main/kotlin/jarvis/content/ContentSchema.kt`
- Test: `src/test/kotlin/jarvis/content/ContentSchemaTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/jarvis/content/ContentSchemaTest.kt`:

```kotlin
package jarvis.content

import com.charleskorn.kaml.Yaml
import kotlin.test.Test
import kotlin.test.assertEquals

class ContentSchemaTest {
    @Test
    fun `parses a subjects manifest`() {
        val yaml = """
            version: 1
            subjects:
              - id: PA
                name_ro: "Proiectarea Algoritmilor"
                name_en: "Algorithm Design"
        """.trimIndent()
        val m = Yaml.default.decodeFromString(SubjectsManifest.serializer(), yaml)
        assertEquals(1, m.version)
        assertEquals("PA", m.subjects.single().id)
        assertEquals("Algorithm Design", m.subjects.single().name_en)
    }

    @Test
    fun `parses a knowledge concept with source refs`() {
        val yaml = """
            id: pa-kc-001
            subject: PA
            name_ro: "Ce este un algoritm"
            name_en: "What is an algorithm"
            cluster: "foundations"
            bloom_level: "understand"
            difficulty: 1
            time_minutes: 10
            exam_weight: 0.05
            tier: 1
            version: 1
            source:
              - doc: pa-lecture-01
                quote: "An algorithm is a finite sequence of steps"
        """.trimIndent()
        val kc = Yaml.default.decodeFromString(KnowledgeConcept.serializer(), yaml)
        assertEquals("pa-kc-001", kc.id)
        assertEquals(1, kc.tier)
        assertEquals(0.05, kc.exam_weight)
        assertEquals("pa-lecture-01", kc.source.single().doc)
    }

    @Test
    fun `parses an edges file`() {
        val yaml = """
            subject: PA
            edges:
              - kc: pa-kc-002
                prereq: pa-kc-001
                rationale: "complexity needs the definition of an algorithm first"
        """.trimIndent()
        val e = Yaml.default.decodeFromString(EdgesFile.serializer(), yaml)
        assertEquals("pa-kc-002", e.edges.single().kc)
        assertEquals("pa-kc-001", e.edges.single().prereq)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle :test --tests 'jarvis.content.ContentSchemaTest' -q`
Expected: FAIL — `SubjectsManifest` / `KnowledgeConcept` / `EdgesFile` unresolved.

- [ ] **Step 3: Write the schema**

Create `src/main/kotlin/jarvis/content/ContentSchema.kt`:

```kotlin
package jarvis.content

import kotlinx.serialization.Serializable

/** A verbatim citation: [quote] must appear in the extracted text of source [doc]. */
@Serializable
data class SourceRef(
    val doc: String,
    val quote: String,
)

/** content/subjects.yaml — the top-level manifest. */
@Serializable
data class SubjectsManifest(
    val version: Int = 1,
    val subjects: List<SubjectEntry> = emptyList(),
)

@Serializable
data class SubjectEntry(
    val id: String,
    val name_ro: String,
    val name_en: String,
)

/** content/{subject}/kcs/{id}.yaml — one knowledge concept. */
@Serializable
data class KnowledgeConcept(
    val id: String,
    val subject: String,
    val name_ro: String,
    val name_en: String,
    val cluster: String,
    val bloom_level: String,
    val difficulty: Int,
    val time_minutes: Int,
    val exam_weight: Double,
    val tier: Int,
    val source: List<SourceRef> = emptyList(),
    val version: Int = 1,
)

/** A single prerequisite edge: [kc] depends on [prereq]. */
@Serializable
data class PrereqEdge(
    val kc: String,
    val prereq: String,
    val rationale: String,
)

/** content/{subject}/edges.yaml — the prerequisite DAG for one subject. */
@Serializable
data class EdgesFile(
    val subject: String,
    val edges: List<PrereqEdge> = emptyList(),
)

/** content/{subject}/misconceptions/{id}.yaml — one misconception. */
@Serializable
data class Misconception(
    val id: String,
    val kc_id: String,
    val label_ro: String,
    val label_en: String,
    val trigger: String,
    val refutation: String,
    val source: List<SourceRef> = emptyList(),
    val version: Int = 1,
)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle :test --tests 'jarvis.content.ContentSchemaTest' -q`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/content/ContentSchema.kt src/test/kotlin/jarvis/content/ContentSchemaTest.kt
git commit -m "feat(gate3): content YAML schema data classes"
```

---

## Task 3: Scaffold the content/ directory + subjects.yaml manifest

**Files:**
- Create: `content/README.md`, `content/subjects.yaml`
- Create: `content/{PA,PS,POO,ALO,SO-RC}/kcs/.gitkeep`, `.../misconceptions/.gitkeep`, `.../_sources/.gitkeep`

- [ ] **Step 1: Create the manifest**

Create `content/subjects.yaml`:

```yaml
version: 1
subjects:
  - id: PA
    name_ro: "Proiectarea Algoritmilor"
    name_en: "Algorithm Design"
  - id: PS
    name_ro: "Probabilități și Statistică"
    name_en: "Probability and Statistics"
  - id: POO
    name_ro: "Programare Orientată pe Obiecte"
    name_en: "Object-Oriented Programming"
  - id: ALO
    name_ro: "Algebră și Logică"
    name_en: "Algebra and Logic"
  - id: SO-RC
    name_ro: "Sisteme de Operare și Rețele de Calculatoare"
    name_en: "Operating Systems and Computer Networks"
```

(`name_ro` values are best-effort FII subject names — Task 18's curator may correct them; the schema only requires them non-empty.)

- [ ] **Step 2: Create the directory skeleton**

Create empty `.gitkeep` files at: `content/PA/kcs/.gitkeep`, `content/PA/misconceptions/.gitkeep`, `content/PA/_sources/.gitkeep`, and the same three sub-paths for `content/PS/`, `content/POO/`, `content/ALO/`, `content/SO-RC/`.

- [ ] **Step 3: Create content/README.md**

```markdown
# content/

Git-tracked knowledge-concept corpus — the source of truth for tutor content
(redesign spec §13). Authored by the `/curate-tutor` skill, validated by
`gradle validateContent`.

Layout: `content/subjects.yaml` (manifest) + `content/{subject}/` with
`kcs/*.yaml`, `edges.yaml`, `edges.mmd` (auto-generated), `misconceptions/*.yaml`,
`_sources/*.md` (extracted source text the validator checks `quote`s against).

Curator HTTP routes are read-only in Gate 3; the write path ships in Gate 4.
```

- [ ] **Step 4: Verify the manifest parses**

Run: `gradle :test --tests 'jarvis.content.ContentSchemaTest' -q` — still green (no regression). Manual: confirm `content/subjects.yaml` is valid YAML.

- [ ] **Step 5: Commit**

```bash
git add content/
git commit -m "feat(gate3): scaffold content/ store + subjects.yaml manifest"
```

---

## Task 4: ContentRepo — load YAML into schema objects

**Files:**
- Create: `src/main/kotlin/jarvis/content/ContentRepo.kt`
- Test: `src/test/kotlin/jarvis/content/ContentRepoTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/jarvis/content/ContentRepoTest.kt`:

```kotlin
package jarvis.content

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContentRepoTest {
    private fun seed(root: Path) {
        root.resolve("subjects.yaml").writeText(
            "version: 1\nsubjects:\n  - id: PA\n    name_ro: \"P\"\n    name_en: \"Algorithm Design\"\n"
        )
        val pa = root.resolve("PA"); pa.resolve("kcs").createDirectories()
        pa.resolve("_sources").createDirectories()
        pa.resolve("kcs/pa-kc-001.yaml").writeText(
            "id: pa-kc-001\nsubject: PA\nname_ro: \"A\"\nname_en: \"Algorithm\"\n" +
            "cluster: f\nbloom_level: understand\ndifficulty: 1\ntime_minutes: 10\n" +
            "exam_weight: 1.0\ntier: 1\nversion: 1\n"
        )
        pa.resolve("edges.yaml").writeText("subject: PA\nedges: []\n")
        pa.resolve("_sources/pa-lecture-01.md").writeText("An algorithm is a finite sequence of steps.")
    }

    @Test
    fun `loads manifest`(@TempDir tmp: Path) {
        seed(tmp)
        val m = ContentRepo(tmp).loadManifest()
        assertEquals("PA", m.subjects.single().id)
    }

    @Test
    fun `loads a subject with its kcs and edges`(@TempDir tmp: Path) {
        seed(tmp)
        val sc = ContentRepo(tmp).loadSubject("PA")
        assertEquals(1, sc.kcs.size)
        assertEquals("pa-kc-001", sc.kcs.single().id)
        assertTrue(sc.edges.isEmpty())
        assertTrue(sc.misconceptions.isEmpty())
    }

    @Test
    fun `reads extracted source text`(@TempDir tmp: Path) {
        seed(tmp)
        val txt = ContentRepo(tmp).sourceText("PA", "pa-lecture-01")
        assertTrue(txt!!.contains("finite sequence"))
        assertNull(ContentRepo(tmp).sourceText("PA", "missing-doc"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle :test --tests 'jarvis.content.ContentRepoTest' -q`
Expected: FAIL — `ContentRepo` unresolved.

- [ ] **Step 3: Write ContentRepo**

Create `src/main/kotlin/jarvis/content/ContentRepo.kt`:

```kotlin
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle :test --tests 'jarvis.content.ContentRepoTest' -q`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/content/ContentRepo.kt src/test/kotlin/jarvis/content/ContentRepoTest.kt
git commit -m "feat(gate3): ContentRepo loads content/ YAML into schema objects"
```

---

## Task 5: ContentValidator skeleton + cycle detection

**Files:**
- Create: `src/main/kotlin/jarvis/content/ContentValidator.kt`
- Test: `src/test/kotlin/jarvis/content/ContentValidatorTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/jarvis/content/ContentValidatorTest.kt`:

```kotlin
package jarvis.content

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
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle :test --tests 'jarvis.content.ContentValidatorTest' -q`
Expected: FAIL — `ContentValidator` unresolved.

- [ ] **Step 3: Write the validator skeleton + cycle detection**

Create `src/main/kotlin/jarvis/content/ContentValidator.kt`:

```kotlin
package jarvis.content

import kotlinx.serialization.Serializable

@Serializable
data class ValidationIssue(
    val severity: String,   // "error" | "warning"
    val rule: String,       // "cycle" | "orphan" | "exam_weight" | "bilingual" | "verbatim_source"
    val subject: String,
    val detail: String,
)

@Serializable
data class ValidationReport(
    val ok: Boolean,
    val disclaimer: String,
    val issues: List<ValidationIssue>,
)

/**
 * Structural validator for the content/ corpus. Per council 1779311876 amendment 1,
 * this validator proves the graph is well-FORMED (acyclic, connected, weights sum,
 * bilingual, attributed) — it does NOT prove the content is TRUE. Semantic
 * groundedness is the human curator's job (and the Gate 5 sidecar's).
 */
object ContentValidator {

    const val DISCLAIMER = "structural checks only — content not groundedness-verified"

    /** Three-color DFS cycle detection over the prerequisite graph (edge prereq -> kc). */
    fun detectCycles(sub: LoadedSubject): List<ValidationIssue> {
        val adj: Map<String, List<String>> = sub.edges.groupBy({ it.prereq }, { it.kc })
        val color = HashMap<String, Int>() // 0 = white, 1 = gray, 2 = black
        val nodes = (sub.kcs.map { it.id } + sub.edges.flatMap { listOf(it.kc, it.prereq) }).toSet()
        val issues = mutableListOf<ValidationIssue>()

        fun dfs(node: String): Boolean {
            color[node] = 1
            for (next in adj[node].orEmpty()) {
                val c = color[next] ?: 0
                if (c == 1) return true            // back-edge to a gray node = cycle
                if (c == 0 && dfs(next)) return true
            }
            color[node] = 2
            return false
        }

        for (n in nodes) {
            if ((color[n] ?: 0) == 0 && dfs(n)) {
                issues += ValidationIssue("error", "cycle", sub.subject,
                    "prerequisite graph contains a cycle reachable from KC '$n'")
                break // one cycle issue is enough — the curator fixes and re-runs
            }
        }
        return issues
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle :test --tests 'jarvis.content.ContentValidatorTest' -q`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/content/ContentValidator.kt src/test/kotlin/jarvis/content/ContentValidatorTest.kt
git commit -m "feat(gate3): content validator — cycle detection"
```

---

## Task 6: Validator — orphan detection

**Files:**
- Modify: `src/main/kotlin/jarvis/content/ContentValidator.kt`
- Modify: `src/test/kotlin/jarvis/content/ContentValidatorTest.kt`

- [ ] **Step 1: Write the failing test**

Append to `ContentValidatorTest` (re-uses the `kc(...)` helper):

```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle :test --tests 'jarvis.content.ContentValidatorTest' -q`
Expected: FAIL — `detectOrphans` unresolved.

- [ ] **Step 3: Add detectOrphans to ContentValidator**

Add this function inside `object ContentValidator`:

```kotlin
    /**
     * Orphan = a KC that is NOT a tier-1 root and has no prerequisite chain of
     * <= [MAX_HOPS] edges reaching a tier-1 KC. Walks edges kc -> prereq.
     */
    const val MAX_HOPS = 8

    fun detectOrphans(sub: LoadedSubject): List<ValidationIssue> {
        val tier1: Set<String> = sub.kcs.filter { it.tier == 1 }.map { it.id }.toSet()
        // prereqsOf[x] = the KCs that x directly depends on.
        val prereqsOf: Map<String, List<String>> = sub.edges.groupBy({ it.kc }, { it.prereq })
        val issues = mutableListOf<ValidationIssue>()

        for (kc in sub.kcs) {
            if (kc.id in tier1) continue
            // BFS up the prerequisite chain, bounded by MAX_HOPS.
            val seen = hashSetOf(kc.id)
            var frontier = listOf(kc.id)
            var reached = false
            var hop = 0
            while (frontier.isNotEmpty() && hop < MAX_HOPS && !reached) {
                val next = mutableListOf<String>()
                for (n in frontier) for (p in prereqsOf[n].orEmpty()) {
                    if (p in tier1) { reached = true }
                    if (seen.add(p)) next += p
                }
                frontier = next
                hop++
            }
            if (!reached) {
                issues += ValidationIssue("error", "orphan", sub.subject,
                    "KC '${kc.id}' has no prerequisite path (<= $MAX_HOPS hops) to a tier-1 root")
            }
        }
        return issues
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle :test --tests 'jarvis.content.ContentValidatorTest' -q`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/content/ContentValidator.kt src/test/kotlin/jarvis/content/ContentValidatorTest.kt
git commit -m "feat(gate3): content validator — orphan detection"
```

---

## Task 7: Validator — exam-weight sum check

**Files:**
- Modify: `src/main/kotlin/jarvis/content/ContentValidator.kt`
- Modify: `src/test/kotlin/jarvis/content/ContentValidatorTest.kt`

- [ ] **Step 1: Write the failing test**

Append to `ContentValidatorTest`:

```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle :test --tests 'jarvis.content.ContentValidatorTest' -q`
Expected: FAIL — `checkExamWeights` unresolved.

- [ ] **Step 3: Add checkExamWeights**

Add inside `object ContentValidator`:

```kotlin
    const val WEIGHT_TOLERANCE = 0.02

    /** Per spec §13: the exam_weight of a subject's KCs must sum to 1.0 +/- 0.02. */
    fun checkExamWeights(sub: LoadedSubject): List<ValidationIssue> {
        if (sub.kcs.isEmpty()) return emptyList()
        val sum = sub.kcs.sumOf { it.exam_weight }
        if (kotlin.math.abs(sum - 1.0) > WEIGHT_TOLERANCE) {
            return listOf(ValidationIssue("error", "exam_weight", sub.subject,
                "exam_weight sum is %.4f — must be 1.0 +/- %.2f".format(sum, WEIGHT_TOLERANCE)))
        }
        return emptyList()
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle :test --tests 'jarvis.content.ContentValidatorTest' -q`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/content/ContentValidator.kt src/test/kotlin/jarvis/content/ContentValidatorTest.kt
git commit -m "feat(gate3): content validator — exam-weight sum check"
```

---

## Task 8: Validator — bilingual completeness check

**Files:**
- Modify: `src/main/kotlin/jarvis/content/ContentValidator.kt`
- Modify: `src/test/kotlin/jarvis/content/ContentValidatorTest.kt`

- [ ] **Step 1: Write the failing test**

Append to `ContentValidatorTest`:

```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle :test --tests 'jarvis.content.ContentValidatorTest' -q`
Expected: FAIL — `checkBilingual` unresolved.

- [ ] **Step 3: Add checkBilingual**

Add inside `object ContentValidator`:

```kotlin
    /** Every KC and misconception must carry non-blank RO and EN text. */
    fun checkBilingual(sub: LoadedSubject): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        for (kc in sub.kcs) {
            if (kc.name_ro.isBlank() || kc.name_en.isBlank()) {
                issues += ValidationIssue("error", "bilingual", sub.subject,
                    "KC '${kc.id}' missing name_ro or name_en")
            }
        }
        for (m in sub.misconceptions) {
            if (m.label_ro.isBlank() || m.label_en.isBlank()) {
                issues += ValidationIssue("error", "bilingual", sub.subject,
                    "misconception '${m.id}' missing label_ro or label_en")
            }
        }
        return issues
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle :test --tests 'jarvis.content.ContentValidatorTest' -q`
Expected: PASS (9 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/content/ContentValidator.kt src/test/kotlin/jarvis/content/ContentValidatorTest.kt
git commit -m "feat(gate3): content validator — bilingual completeness check"
```

---

## Task 9: Validator — verbatim-source-quote check (council amendment 1)

**Files:**
- Modify: `src/main/kotlin/jarvis/content/ContentValidator.kt`
- Modify: `src/test/kotlin/jarvis/content/ContentValidatorTest.kt`

This is the council's core fix: an attribution is only valid if its `quote` is a real verbatim substring of the cited source's extracted text — not merely a non-empty field. Whitespace is normalized before comparison so PDF line-wrapping does not cause false failures.

- [ ] **Step 1: Write the failing test**

Append to `ContentValidatorTest`:

```kotlin
    private val srcLookup: (String) -> String? = { doc ->
        if (doc == "pa-lecture-01") "An algorithm is a finite\n  sequence of unambiguous steps." else null
    }

    @Test
    fun `quote that is a verbatim substring of the source passes`() {
        val withSrc = kc("a", weight = 1.0, tier = 1)
            .copy(source = listOf(SourceRef("pa-lecture-01", "a finite sequence of unambiguous steps")))
        val sub = LoadedSubject("PA", kcs = listOf(withSrc), edges = emptyList(), misconceptions = emptyList())
        assertTrue(ContentValidator.checkVerbatimSources(sub, srcLookup).isEmpty())
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle :test --tests 'jarvis.content.ContentValidatorTest' -q`
Expected: FAIL — `checkVerbatimSources` unresolved.

- [ ] **Step 3: Add checkVerbatimSources**

Add inside `object ContentValidator`:

```kotlin
    private fun normalizeWs(s: String): String = s.replace(Regex("\\s+"), " ").trim()

    /**
     * Council 1779311876 amendment 1. For every KC and misconception:
     *  - at least one SourceRef is required (empty source = error);
     *  - each SourceRef.quote, after whitespace normalization, must be a
     *    verbatim substring of [sourceText] of its doc;
     *  - if the doc's extracted text is absent, the check degrades to a
     *    warning (cannot verify) rather than an error.
     * [sourceText] maps a doc id to its extracted text, or null if absent.
     */
    fun checkVerbatimSources(
        sub: LoadedSubject,
        sourceText: (doc: String) -> String?,
    ): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()

        fun checkOne(ownerKind: String, ownerId: String, refs: List<SourceRef>) {
            if (refs.isEmpty()) {
                issues += ValidationIssue("error", "verbatim_source", sub.subject,
                    "$ownerKind '$ownerId' has no source attribution")
                return
            }
            for (ref in refs) {
                val text = sourceText(ref.doc)
                if (text == null) {
                    issues += ValidationIssue("warning", "verbatim_source", sub.subject,
                        "$ownerKind '$ownerId': source '${ref.doc}' has no extracted text on disk — quote unverifiable")
                    continue
                }
                if (!normalizeWs(text).contains(normalizeWs(ref.quote))) {
                    issues += ValidationIssue("error", "verbatim_source", sub.subject,
                        "$ownerKind '$ownerId': quote not found verbatim in source '${ref.doc}'")
                }
            }
        }

        for (kc in sub.kcs) checkOne("KC", kc.id, kc.source)
        for (m in sub.misconceptions) checkOne("misconception", m.id, m.source)
        return issues
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle :test --tests 'jarvis.content.ContentValidatorTest' -q`
Expected: PASS (13 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/content/ContentValidator.kt src/test/kotlin/jarvis/content/ContentValidatorTest.kt
git commit -m "feat(gate3): content validator — verbatim-source-quote check (council amendment 1)"
```

---

## Task 10: Validator — aggregate validate() entrypoint

**Files:**
- Modify: `src/main/kotlin/jarvis/content/ContentValidator.kt`
- Modify: `src/test/kotlin/jarvis/content/ContentValidatorTest.kt`

- [ ] **Step 1: Write the failing test**

Append to `ContentValidatorTest`:

```kotlin
    @Test
    fun `validate aggregates all checks and self-labels the disclaimer`() {
        val good = kc("a", weight = 1.0, tier = 1)
            .copy(source = listOf(SourceRef("pa-lecture-01", "a finite sequence of unambiguous steps")))
        val sub = LoadedSubject("PA", kcs = listOf(good), edges = emptyList(), misconceptions = emptyList())
        val report = ContentValidator.validate(listOf(sub), srcLookup)
        assertTrue(report.ok, report.issues.toString())
        assertEquals(ContentValidator.DISCLAIMER, report.disclaimer)
    }

    @Test
    fun `validate returns ok=false when any error issue is present`() {
        val sub = LoadedSubject("PA",
            kcs = listOf(kc("a", weight = 0.3, tier = 1)), // weight sum 0.3 -> error
            edges = emptyList(), misconceptions = emptyList())
        val report = ContentValidator.validate(listOf(sub), srcLookup)
        assertFalse(report.ok)
        assertTrue(report.issues.any { it.rule == "exam_weight" })
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle :test --tests 'jarvis.content.ContentValidatorTest' -q`
Expected: FAIL — `validate` unresolved.

- [ ] **Step 3: Add validate()**

Add inside `object ContentValidator`:

```kotlin
    /**
     * Runs every structural check across all [subjects]. [sourceText] resolves a
     * doc id to its extracted source text (see checkVerbatimSources).
     * ok = true iff there are no "error"-severity issues; warnings do not fail.
     */
    fun validate(
        subjects: List<LoadedSubject>,
        sourceText: (doc: String) -> String?,
    ): ValidationReport {
        val issues = mutableListOf<ValidationIssue>()
        for (sub in subjects) {
            issues += detectCycles(sub)
            issues += detectOrphans(sub)
            issues += checkExamWeights(sub)
            issues += checkBilingual(sub)
            issues += checkVerbatimSources(sub, sourceText)
        }
        val ok = issues.none { it.severity == "error" }
        return ValidationReport(ok = ok, disclaimer = DISCLAIMER, issues = issues)
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle :test --tests 'jarvis.content.ContentValidatorTest' -q`
Expected: PASS (15 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/content/ContentValidator.kt src/test/kotlin/jarvis/content/ContentValidatorTest.kt
git commit -m "feat(gate3): content validator — aggregate validate() entrypoint"
```

---

## Task 11: MermaidMirror — edges.yaml → edges.mmd generator

**Files:**
- Create: `src/main/kotlin/jarvis/content/MermaidMirror.kt`
- Test: `src/test/kotlin/jarvis/content/MermaidMirrorTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/jarvis/content/MermaidMirrorTest.kt`:

```kotlin
package jarvis.content

import kotlin.test.Test
import kotlin.test.assertTrue

class MermaidMirrorTest {
    @Test
    fun `renders a flowchart with one edge per prereq`() {
        val edges = EdgesFile("PA", listOf(
            PrereqEdge("pa-kc-002", "pa-kc-001", "r"),
            PrereqEdge("pa-kc-003", "pa-kc-001", "r"),
        ))
        val mmd = MermaidMirror.render(edges)
        assertTrue(mmd.startsWith("flowchart TD"), mmd)
        assertTrue(mmd.contains("pa-kc-001 --> pa-kc-002"))
        assertTrue(mmd.contains("pa-kc-001 --> pa-kc-003"))
    }

    @Test
    fun `empty edge list still renders a valid header`() {
        val mmd = MermaidMirror.render(EdgesFile("PA", emptyList()))
        assertTrue(mmd.startsWith("flowchart TD"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle :test --tests 'jarvis.content.MermaidMirrorTest' -q`
Expected: FAIL — `MermaidMirror` unresolved.

- [ ] **Step 3: Write MermaidMirror**

Create `src/main/kotlin/jarvis/content/MermaidMirror.kt`:

```kotlin
package jarvis.content

/**
 * Renders an [EdgesFile] as a Mermaid flowchart — the auto-generated `edges.mmd`
 * mirror of `edges.yaml` (spec §13). edges.mmd makes prerequisite-graph diffs
 * human-readable in git; it is generated, never hand-edited.
 */
object MermaidMirror {
    fun render(edges: EdgesFile): String = buildString {
        appendLine("flowchart TD")
        append("    %% AUTO-GENERATED from edges.yaml for subject ${edges.subject} — do not edit by hand")
        for (e in edges.edges.sortedWith(compareBy({ it.prereq }, { it.kc }))) {
            append("\n    ${e.prereq} --> ${e.kc}")
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle :test --tests 'jarvis.content.MermaidMirrorTest' -q`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/content/MermaidMirror.kt src/test/kotlin/jarvis/content/MermaidMirrorTest.kt
git commit -m "feat(gate3): MermaidMirror — edges.yaml to edges.mmd generator"
```

---

## Task 12: ContentCli — the validateContent entrypoint

**Files:**
- Create: `src/main/kotlin/jarvis/content/ContentCli.kt`
- Test: `src/test/kotlin/jarvis/content/ContentValidatorTest.kt` (add a CLI-helper test)

The Gradle `validateContent` task (Task 1) points at `jarvis.content.ContentCliKt`. The CLI loads the whole `content/` corpus, runs `ContentValidator.validate`, prints the report, regenerates each subject's `edges.mmd`, and exits non-zero on any error issue.

- [ ] **Step 1: Write the failing test**

Append to `ContentValidatorTest`:

```kotlin
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
```

Add the imports `import kotlin.io.path.createDirectories` and `import kotlin.io.path.writeText` to the test file if not already present.

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle :test --tests 'jarvis.content.ContentValidatorTest' -q`
Expected: FAIL — `ContentCli` unresolved.

- [ ] **Step 3: Write ContentCli**

Create `src/main/kotlin/jarvis/content/ContentCli.kt`:

```kotlin
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle :test --tests 'jarvis.content.ContentValidatorTest' -q`
Expected: PASS (16 tests).

- [ ] **Step 5: Verify the Gradle task runs**

Run: `gradle validateContent -q`
Expected: BUILD SUCCESSFUL — prints the disclaimer + `OK` (the `content/` corpus is manifest-only at this point; `validate` over zero KCs has no errors).

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/jarvis/content/ContentCli.kt src/test/kotlin/jarvis/content/ContentValidatorTest.kt
git commit -m "feat(gate3): ContentCli — validateContent entrypoint"
```

---

## Task 13: Wire validateContent into the build check

**Files:**
- Modify: `build.gradle.kts`

- [ ] **Step 1: Make `check` depend on `validateContent`**

Append to `build.gradle.kts`:

```kotlin
// Gate 3: content corpus validation runs as part of the standard verification
// lifecycle. `gradle check` (and `gradle build`) now fail on a malformed corpus.
tasks.named("check") { dependsOn("validateContent") }
```

- [ ] **Step 2: Verify**

Run: `gradle :check -q`
Expected: BUILD SUCCESSFUL — `validateContent` runs and passes alongside `test`. (Use `:check`, not bare `check` — this is a git worktree with no Android SDK config; bare `check` triggers `:android` and fails.)

- [ ] **Step 3: Commit**

```bash
git add build.gradle.kts
git commit -m "build(gate3): run validateContent as part of gradle check"
```

---

## Task 14: CuratorRoutes — list subjects + get KCs

**Files:**
- Create: `src/main/kotlin/jarvis/web/CuratorRoutes.kt`
- Test: `src/test/kotlin/jarvis/web/CuratorRoutesTest.kt`

Curator routes are auth-gated and require `OWNER` scope (Gate 2 ships only `OWNER`/`FRIEND`; a dedicated `curator` role is a Gate 7 RBAC concern). They read a content directory resolved from the `JARVIS_CONTENT_DIR` env, default `content` (CWD-relative). Per council amendment 2, Gate 3 ships **no write routes**.

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/jarvis/web/CuratorRoutesTest.kt`:

```kotlin
package jarvis.web

import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.cookie
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.testing.testApplication
import jarvis.tutor.SessionRepo
import jarvis.tutor.TutorContext
import jarvis.tutor.TutorContextKey
import jarvis.tutor.TutorTypes
import jarvis.tutor.User
import jarvis.tutor.UserRepo
import jarvis.tutor.UserScope
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CuratorRoutesTest {
    private fun seedContent(content: Path) {
        content.createDirectories()
        content.resolve("subjects.yaml").writeText(
            "version: 1\nsubjects:\n  - id: PA\n    name_ro: \"P\"\n    name_en: \"Algorithm Design\"\n")
        val pa = content.resolve("PA")
        pa.resolve("kcs").createDirectories()
        pa.resolve("_sources").createDirectories()
        pa.resolve("kcs/pa-kc-001.yaml").writeText(
            "id: pa-kc-001\nsubject: PA\nname_ro: \"A\"\nname_en: \"Algorithm\"\n" +
            "cluster: f\nbloom_level: understand\ndifficulty: 1\ntime_minutes: 10\n" +
            "exam_weight: 1.0\ntier: 1\nversion: 1\n")
        pa.resolve("edges.yaml").writeText("subject: PA\nedges: []\n")
    }

    private fun Application.installFresh(tmp: Path, content: Path) {
        install(ServerContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = true }) }
        installTutorContext(tmp.resolve("t.db").toString(), tmp)
        installTutorRoutes()
        // Curator routes read this dir; installTutorRoutes() already mounted them,
        // so override the resolved dir for the test via the system property.
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
    }

    private fun seedOwner(ctx: TutorContext): String {
        val uid = TutorTypes.ulid()
        UserRepo(ctx.db).insert(User(uid, "owner", UserScope.OWNER, Instant.now(), Instant.now()))
        return SessionRepo(ctx.db).create(uid, ttlSeconds = 3600)
    }

    private fun seedFriend(ctx: TutorContext): String {
        val uid = TutorTypes.ulid()
        UserRepo(ctx.db).insert(User(uid, "friend", UserScope.FRIEND, Instant.now(), Instant.now()))
        return SessionRepo(ctx.db).create(uid, ttlSeconds = 3600)
    }

    @Test
    fun `GET curator subjects returns the manifest for an OWNER`(@TempDir tmp: Path) = testApplication {
        val content = tmp.resolve("content")
        seedContent(content)
        var ctx: TutorContext? = null
        application { installFresh(tmp, content); ctx = attributes[TutorContextKey] }
        startApplication()
        val sid = seedOwner(ctx!!)
        val client = createClient {
            install(HttpCookies); install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val r = client.get("/api/v1/curator/subjects") { cookie("jarvis_session", sid) }
        assertEquals(HttpStatusCode.OK, r.status)
        assertTrue(r.bodyAsText().contains("Algorithm Design"))
    }

    @Test
    fun `GET curator subjects rejects an unauthenticated caller with 401`(@TempDir tmp: Path) = testApplication {
        val content = tmp.resolve("content"); seedContent(content)
        application { installFresh(tmp, content) }
        startApplication()
        val client = createClient { install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val r = client.get("/api/v1/curator/subjects")
        assertEquals(HttpStatusCode.Unauthorized, r.status)
    }

    @Test
    fun `GET curator subjects rejects a FRIEND-scope user with 403`(@TempDir tmp: Path) = testApplication {
        val content = tmp.resolve("content"); seedContent(content)
        var ctx: TutorContext? = null
        application { installFresh(tmp, content); ctx = attributes[TutorContextKey] }
        startApplication()
        val sid = seedFriend(ctx!!)
        val client = createClient {
            install(HttpCookies); install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val r = client.get("/api/v1/curator/subjects") { cookie("jarvis_session", sid) }
        assertEquals(HttpStatusCode.Forbidden, r.status)
    }

    @Test
    fun `GET curator kcs lists and fetches a single KC`(@TempDir tmp: Path) = testApplication {
        val content = tmp.resolve("content"); seedContent(content)
        var ctx: TutorContext? = null
        application { installFresh(tmp, content); ctx = attributes[TutorContextKey] }
        startApplication()
        val sid = seedOwner(ctx!!)
        val client = createClient {
            install(HttpCookies); install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val list = client.get("/api/v1/curator/subjects/PA/kcs") { cookie("jarvis_session", sid) }
        assertEquals(HttpStatusCode.OK, list.status)
        assertTrue(list.bodyAsText().contains("pa-kc-001"))
        val one = client.get("/api/v1/curator/subjects/PA/kcs/pa-kc-001") { cookie("jarvis_session", sid) }
        assertEquals(HttpStatusCode.OK, one.status)
        assertTrue(one.bodyAsText().contains("\"tier\":1"))
        val missing = client.get("/api/v1/curator/subjects/PA/kcs/pa-kc-999") { cookie("jarvis_session", sid) }
        assertEquals(HttpStatusCode.NotFound, missing.status)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle :test --tests 'jarvis.web.CuratorRoutesTest' -q`
Expected: FAIL — `installCuratorRoutes` unresolved / routes not mounted.

- [ ] **Step 3: Write CuratorRoutes.kt (subjects + kcs routes)**

Create `src/main/kotlin/jarvis/web/CuratorRoutes.kt`:

```kotlin
package jarvis.web

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.application
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import jarvis.content.ContentRepo
import jarvis.tutor.SessionRepo
import jarvis.tutor.TutorContextKey
import jarvis.tutor.UserRepo
import jarvis.tutor.UserScope
import kotlinx.serialization.json.Json
import java.nio.file.Path

private val curatorJson = Json { encodeDefaults = true; prettyPrint = false }

/** Resolves the content/ directory: JARVIS_CONTENT_DIR env/property, else "content" (CWD-relative). */
private fun contentDir(): Path =
    Path.of(System.getProperty("JARVIS_CONTENT_DIR")
        ?: System.getenv("JARVIS_CONTENT_DIR")
        ?: "content")

/**
 * Runs [block] with the authenticated OWNER-scope user id. Responds 401 if not
 * authenticated, 403 if authenticated but not OWNER. Gate 3 gates all curator
 * routes on OWNER; a dedicated `curator` role lands with Gate 7 RBAC.
 */
private suspend fun RoutingContext.requireOwner(block: suspend (userId: String) -> Unit) {
    val ctx = call.application.attributes.getOrNull(TutorContextKey)
        ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return }
    val sid = call.request.cookies["jarvis_session"]
    val userId = sid?.let { SessionRepo(ctx.db).findUserId(it) }
        ?: run { call.respond(HttpStatusCode.Unauthorized, """{"error":"not authenticated"}"""); return }
    val user = UserRepo(ctx.db).findById(userId)
    if (user?.scope != UserScope.OWNER) {
        call.respondText("""{"error":"curator access requires OWNER scope"}""",
            ContentType.Application.Json, HttpStatusCode.Forbidden)
        return
    }
    block(userId)
}

/**
 * Gate 3 curator routes — READ + VALIDATE only (council 1779311876 amendment 2:
 * the write/commit/version-lock path ships with the Gate 4 curator SPA).
 * Mounted from installTutorRoutes()'s routing { } block.
 */
fun Route.installCuratorRoutes() {

    get("/api/v1/curator/subjects") {
        requireOwner {
            val manifest = ContentRepo(contentDir()).loadManifest()
            call.respondText(
                curatorJson.encodeToString(jarvis.content.SubjectsManifest.serializer(), manifest),
                ContentType.Application.Json,
            )
        }
    }

    get("/api/v1/curator/subjects/{subject}/kcs") {
        requireOwner {
            val subject = call.parameters["subject"]
                ?: run { call.respond(HttpStatusCode.BadRequest, "subject required"); return@requireOwner }
            val kcs = ContentRepo(contentDir()).loadSubject(subject).kcs
            call.respondText(
                curatorJson.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(jarvis.content.KnowledgeConcept.serializer()),
                    kcs,
                ),
                ContentType.Application.Json,
            )
        }
    }

    get("/api/v1/curator/subjects/{subject}/kcs/{id}") {
        requireOwner {
            val subject = call.parameters["subject"]
                ?: run { call.respond(HttpStatusCode.BadRequest, "subject required"); return@requireOwner }
            val id = call.parameters["id"]
                ?: run { call.respond(HttpStatusCode.BadRequest, "id required"); return@requireOwner }
            val kc = ContentRepo(contentDir()).loadSubject(subject).kcs.firstOrNull { it.id == id }
                ?: run { call.respond(HttpStatusCode.NotFound, """{"error":"KC not found"}"""); return@requireOwner }
            call.respondText(
                curatorJson.encodeToString(jarvis.content.KnowledgeConcept.serializer(), kc),
                ContentType.Application.Json,
            )
        }
    }
}
```

- [ ] **Step 4: Mount the routes in TutorRoutes.kt**

In `src/main/kotlin/jarvis/web/TutorRoutes.kt`, inside `installTutorRoutes()`'s `routing { }` block, immediately after the `get("/api/v1/health") { ... }` route (around line 123), add:

```kotlin
        // Gate 3: content-authoring curator routes (read + validate only).
        installCuratorRoutes()
```

- [ ] **Step 5: Run test to verify it passes**

Run: `gradle :test --tests 'jarvis.web.CuratorRoutesTest' -q`
Expected: PASS (4 tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/jarvis/web/CuratorRoutes.kt src/main/kotlin/jarvis/web/TutorRoutes.kt src/test/kotlin/jarvis/web/CuratorRoutesTest.kt
git commit -m "feat(gate3): curator routes — list subjects + get KCs (OWNER-gated)"
```

---

## Task 15: CuratorRoutes — prerequisite graph endpoint

**Files:**
- Modify: `src/main/kotlin/jarvis/web/CuratorRoutes.kt`
- Modify: `src/test/kotlin/jarvis/web/CuratorRoutesTest.kt`

- [ ] **Step 1: Write the failing test**

Append to `CuratorRoutesTest` (re-uses `seedContent`, `installFresh`, `seedOwner`):

```kotlin
    @Test
    fun `GET curator graph returns nodes and edges`(@TempDir tmp: Path) = testApplication {
        val content = tmp.resolve("content")
        seedContent(content)
        // add a second KC + an edge
        val pa = content.resolve("PA")
        pa.resolve("kcs/pa-kc-002.yaml").writeText(
            "id: pa-kc-002\nsubject: PA\nname_ro: \"B\"\nname_en: \"Complexity\"\n" +
            "cluster: f\nbloom_level: understand\ndifficulty: 2\ntime_minutes: 15\n" +
            "exam_weight: 0.0\ntier: 2\nversion: 1\n")
        pa.resolve("edges.yaml").writeText(
            "subject: PA\nedges:\n  - kc: pa-kc-002\n    prereq: pa-kc-001\n    rationale: r\n")
        var ctx: TutorContext? = null
        application { installFresh(tmp, content); ctx = attributes[TutorContextKey] }
        startApplication()
        val sid = seedOwner(ctx!!)
        val client = createClient {
            install(HttpCookies); install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val r = client.get("/api/v1/curator/subjects/PA/graph") { cookie("jarvis_session", sid) }
        assertEquals(HttpStatusCode.OK, r.status)
        val body = r.bodyAsText()
        assertTrue(body.contains("pa-kc-001"))
        assertTrue(body.contains("pa-kc-002"))
        assertTrue(body.contains("\"prereq\":\"pa-kc-001\""))
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle :test --tests 'jarvis.web.CuratorRoutesTest' -q`
Expected: FAIL — 404 (no `/graph` route).

- [ ] **Step 3: Add a graph DTO + route**

In `CuratorRoutes.kt`, add this DTO near the top (after `curatorJson`):

```kotlin
@kotlinx.serialization.Serializable
private data class GraphNode(val id: String, val name_en: String, val tier: Int)

@kotlinx.serialization.Serializable
private data class GraphResponse(
    val subject: String,
    val nodes: List<GraphNode>,
    val edges: List<jarvis.content.PrereqEdge>,
)
```

And add this route inside `installCuratorRoutes()`:

```kotlin
    get("/api/v1/curator/subjects/{subject}/graph") {
        requireOwner {
            val subject = call.parameters["subject"]
                ?: run { call.respond(HttpStatusCode.BadRequest, "subject required"); return@requireOwner }
            val loaded = ContentRepo(contentDir()).loadSubject(subject)
            val resp = GraphResponse(
                subject = subject,
                nodes = loaded.kcs.map { GraphNode(it.id, it.name_en, it.tier) },
                edges = loaded.edges,
            )
            call.respondText(
                curatorJson.encodeToString(GraphResponse.serializer(), resp),
                ContentType.Application.Json,
            )
        }
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle :test --tests 'jarvis.web.CuratorRoutesTest' -q`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/web/CuratorRoutes.kt src/test/kotlin/jarvis/web/CuratorRoutesTest.kt
git commit -m "feat(gate3): curator route — prerequisite graph endpoint"
```

---

## Task 16: CuratorRoutes — validate endpoint

**Files:**
- Modify: `src/main/kotlin/jarvis/web/CuratorRoutes.kt`
- Modify: `src/test/kotlin/jarvis/web/CuratorRoutesTest.kt`

`GET /api/v1/curator/validate` runs `ContentCli.runValidation` over the live `content/` dir and returns the `ValidationReport` as JSON. It is a GET (idempotent, read-only — it regenerates `edges.mmd` as a side effect, which is deterministic) so no CSRF token is required.

- [ ] **Step 1: Write the failing test**

Append to `CuratorRoutesTest`:

```kotlin
    @Test
    fun `GET curator validate returns a structural report with the disclaimer`(@TempDir tmp: Path) = testApplication {
        val content = tmp.resolve("content")
        seedContent(content)
        var ctx: TutorContext? = null
        application { installFresh(tmp, content); ctx = attributes[TutorContextKey] }
        startApplication()
        val sid = seedOwner(ctx!!)
        val client = createClient {
            install(HttpCookies); install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val r = client.get("/api/v1/curator/validate") { cookie("jarvis_session", sid) }
        assertEquals(HttpStatusCode.OK, r.status)
        val body = r.bodyAsText()
        assertTrue(body.contains("structural checks only"))
        assertTrue(body.contains("\"ok\":true"))
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle :test --tests 'jarvis.web.CuratorRoutesTest' -q`
Expected: FAIL — 404 (no `/validate` route).

- [ ] **Step 3: Add the validate route**

Add inside `installCuratorRoutes()`:

```kotlin
    get("/api/v1/curator/validate") {
        requireOwner {
            val report = jarvis.content.ContentCli.runValidation(contentDir())
            call.respondText(
                curatorJson.encodeToString(jarvis.content.ValidationReport.serializer(), report),
                ContentType.Application.Json,
            )
        }
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle :test --tests 'jarvis.web.CuratorRoutesTest' -q`
Expected: PASS (6 tests).

- [ ] **Step 5: Run the whole content + web test set to confirm no regressions**

Run: `gradle :test --tests 'jarvis.content.*' --tests 'jarvis.web.CuratorRoutesTest' -q`
Expected: PASS (all content + curator tests green).

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/jarvis/web/CuratorRoutes.kt src/test/kotlin/jarvis/web/CuratorRoutesTest.kt
git commit -m "feat(gate3): curator route — validate endpoint"
```

---

## Task 17: The /curate-tutor skill

**Files:**
- Create: `.claude/skills/curate-tutor/SKILL.md`

The `/curate-tutor` skill is the offline authoring tool (council Q1). It is a markdown instruction file Claude executes — not Python. It reads a source lecture, drafts KCs / edges / misconceptions, writes them as schema-conforming YAML, extracts source text into `_sources/`, and runs `gradle validateContent`.

- [ ] **Step 1: Create the skill directory + SKILL.md**

Create `.claude/skills/curate-tutor/SKILL.md`:

```markdown
---
name: curate-tutor
description: Use when authoring tutor content from a university lecture (PDF or markdown) into the git-tracked content/ knowledge-concept corpus. Triggers on "curate", "author KCs", "add a lecture to content/", or /curate-tutor.
---

# curate-tutor

Offline content-authoring pipeline. Turns one university lecture into validated,
git-tracked YAML knowledge concepts under `content/{subject}/`. This is the
Gate 3 authoring tool — Claude performs each stage; there is NO Python sidecar
(DSPy/MinerU/PaperQA2 are Gate 5 runtime infrastructure, unrelated to authoring).

## Inputs

- `subject` — one of the ids in `content/subjects.yaml` (PA, PS, POO, ALO, SO-RC).
- `source` — path to the lecture PDF or pre-extracted markdown (e.g.
  `tmp-secondbrain-scrape/_fii/_gdrive/PA_Y1/Curs/curs_2020-2021/Curs 1 PA.pdf`).
- `doc_id` — a short slug for the source (e.g. `pa-lecture-01`).

## Stages

1. **Extract source text.** Read the `source`. If it is a PDF, extract its text
   (use the pre-extracted `.md` sibling when one exists — it is cleaner). Write
   the extracted plain text verbatim to `content/{subject}/_sources/{doc_id}.md`.
   This file is what the validator checks every `quote` against — it MUST be the
   real extracted text, never paraphrased.

2. **KC discovery.** Identify the distinct knowledge concepts the lecture
   teaches. One KC = one assessable idea. For each, draft a `content/{subject}/
   kcs/{kc_id}.yaml` file conforming to the `KnowledgeConcept` schema:
   `id` (`{subject-lower}-kc-NNN`), `subject`, `name_ro`, `name_en`, `cluster`,
   `bloom_level` (remember|understand|apply|analyze|evaluate|create),
   `difficulty` (1-5), `time_minutes`, `exam_weight`, `tier`, `version: 1`.

3. **Prerequisite edges.** Decide which KCs depend on which. Write
   `content/{subject}/edges.yaml` (`EdgesFile` schema): each edge is
   `{kc, prereq, rationale}`. The graph MUST stay acyclic. Every non-tier-1 KC
   MUST be within 8 prerequisite hops of a tier-1 KC.

4. **Misconception mining.** For KCs where the lecture or common student error
   suggests one, draft `content/{subject}/misconceptions/{id}.yaml`
   (`Misconception` schema): `id`, `kc_id`, `label_ro`, `label_en`, `trigger`,
   `refutation`, `version: 1`.

5. **Attribution.** Every KC and misconception MUST carry a non-empty `source:`
   list of `{doc, quote}` entries. `doc` = the `doc_id` from stage 1. `quote` =
   a VERBATIM substring copied from `content/{subject}/_sources/{doc_id}.md` —
   copy-paste it, do not retype or summarize. This is the groundedness anchor.

6. **Exam weight.** Set each KC's `exam_weight` so the subject's KCs sum to
   1.0 (+/- 0.02). If only part of the subject is authored so far, scale the
   weights of the authored KCs to sum to 1.0 and note that re-balancing is
   needed when more lectures are added.

7. **Verbatim self-check.** Before finishing, re-open each KC/misconception and
   confirm every `quote` is a character-for-character substring of the
   `_sources` file. Fix any that are not — a non-verbatim quote is a hallucination.

8. **Validate.** Run `gradle validateContent`. It MUST exit 0. Fix every `ERROR`
   issue (warnings about missing source files are acceptable only if `_sources`
   genuinely was not produced). Re-run until green.

## Output

A set of new/modified files under `content/{subject}/` and a green
`gradle validateContent`. The human curator then reviews the YAML via `git diff`
before committing — that review is Gate 3's groundedness gate (the Gate 4
curator SPA will replace it with a swipe queue).

## Guardrails

- Never invent a concept the lecture does not cover.
- Never write a `quote` that is not in the `_sources` file.
- Keep the graph acyclic; if a prerequisite feels circular, one of the two KCs
  is mis-scoped — split or merge instead of adding the back-edge.
```

- [ ] **Step 2: Verify the skill file is well-formed**

Manual: confirm the frontmatter has `name` + `description`, and the file is valid markdown.

- [ ] **Step 3: Commit**

```bash
git add .claude/skills/curate-tutor/SKILL.md
git commit -m "feat(gate3): /curate-tutor authoring skill"
```

---

## Task 18: Acceptance — author the PA lecture-1 corpus

**Files:**
- Create: `content/PA/_sources/pa-lecture-01.md`, `content/PA/kcs/pa-kc-*.yaml`, `content/PA/edges.yaml`, `content/PA/edges.mmd`, optionally `content/PA/misconceptions/*.yaml`

This task runs `/curate-tutor` against the real PA (Algorithm Design) lecture 1. Per council amendment 3, the deliverable is the **committed, validator-passing corpus** — `/curate-tutor` is the tool that produces it.

- [ ] **Step 1: Pick the source lecture**

Use `tmp-secondbrain-scrape/_fii/_gdrive/PA_Y1/Curs/curs_2020-2021/Curs 1 PA.pdf` (PA = Proiectarea Algoritmilor / Algorithm Design — confirmed via spec §7.6 and the Gate-1 viz roster). If that file is unreadable, fall back to `tmp-secondbrain-scrape/_fii/_gdrive/PA_Y1/Curs/curs_2021-2022/curs1/1 introd.pdf`. The source tree is git-ignored — only the `content/` output is committed.

- [ ] **Step 2: Run the /curate-tutor skill**

Invoke the `curate-tutor` skill with `subject=PA`, the source path from Step 1, and `doc_id=pa-lecture-01`. Follow all 8 stages. This produces:
- `content/PA/_sources/pa-lecture-01.md` — extracted lecture text.
- `content/PA/kcs/pa-kc-001.yaml` … (one file per concept the lecture teaches).
- `content/PA/edges.yaml` — the prerequisite edges among those KCs.
- `content/PA/misconceptions/*.yaml` — where the lecture warrants them.

- [ ] **Step 3: Validate**

Run: `gradle validateContent -q`
Expected: BUILD SUCCESSFUL — `OK`, 0 errors. `content/PA/edges.mmd` has been (re)generated. Fix every error and re-run until green.

- [ ] **Step 4: Human review**

Stop and have the curator (Alex) review `git diff content/PA/` — confirm the KCs and quotes faithfully reflect the lecture. This human review is Gate 3's semantic/groundedness gate.

- [ ] **Step 5: Commit**

```bash
git add content/PA/
git commit -m "feat(gate3): PA lecture-1 knowledge-concept corpus (curate-tutor acceptance run)"
```

---

## Self-Review (completed during plan authoring)

**Spec coverage** — §14 Gate 3 deliverables: `subjects.yaml` schema → Tasks 2-3; DAG validator → Tasks 5-13; Ktor curator routes → Tasks 14-16; `/curate-tutor` skill → Task 17; "stages 1-7 against PA chapter 1" → Tasks 17-18 (re-scoped per council: a Claude skill + an acceptance corpus, not a Python pipeline). §13 validation rules (cycle, orphan, exam-weight, bilingual, attribution) → Tasks 5-9. §13 Mermaid `edges.mmd` mirror → Task 11. §13 `version` field → in schema (Task 2); optimistic-lock writes are correctly NOT here (Gate 4, per council amendment 2).

**Council amendments** — amendment 1 (verbatim check + disclaimer) → Tasks 9-10; amendment 2 (no write path) → Tasks 14-16 are read+validate only; amendment 3 (deterministic acceptance) → Task 18 deliverable is the committed validator-passing corpus.

**Placeholder scan** — no TBDs; every code step has complete code; the only deliberate looseness is the kaml version (Task 1 names `0.65.0` + a verification instruction) and the PA lecture content (genuinely produced at authoring time in Task 18, which is correct — it is data, not code).

**Type consistency** — `LoadedSubject`, `KnowledgeConcept`, `PrereqEdge`, `EdgesFile`, `Misconception`, `SourceRef`, `ValidationIssue`, `ValidationReport` are defined once (Tasks 2, 4, 5) and used consistently. `ContentValidator` function names (`detectCycles`, `detectOrphans`, `checkExamWeights`, `checkBilingual`, `checkVerbatimSources`, `validate`) match between definition and the `validate()` aggregator and tests.

**Build+mount pairing** — this is a backend plan with no React components. The one mountable unit, `installCuratorRoutes()`, is created in Task 14 Step 3 and mounted in Task 14 Step 4 (exact site: `TutorRoutes.kt` `installTutorRoutes()` routing block, after `get("/api/v1/health")`). The `validateContent` task is registered in Task 1 and wired into `check` in Task 13.

**Post-Gate-3 follow-ups** (out of scope — do not build here):
- Curator WRITE path (upsert KC YAML + optimistic version lock + atomic git commit) — Gate 4, with the curator SPA. Council flagged git-from-JVM concurrency; Gate 4 must serialize writes.
- `JARVIS_CONTENT_DIR` on the VPS — the deployed service must be able to read `content/`. Either set `JARVIS_CONTENT_DIR` in `/opt/jarvis/.env` and `scp` the `content/` dir in `deploy.sh`, or relocate `content/` under `src/main/resources/`. Decide when Gate 4's write path makes runtime content access load-bearing.
- Template + glossary + past-paper artifact types (§13) — Gate 5/6 (pedagogy + drill engine) own these.
- Authoring the remaining PA lectures + the other 4 subjects — ongoing curation, not a gate task.
```
