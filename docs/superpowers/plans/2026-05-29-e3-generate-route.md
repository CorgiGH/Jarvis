# E3 — Generate + Route Explanation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generate fresh, safeguard-gated practice drills for a knowledge concept (free LLM generator + cross-family Claude critic + self-solve), and route each concept to the right explanation surface (real visual / worked-example / symbolic text) — actually mounted on the student drill screen — proven on PA fixtures.

**Architecture:** Generation produces a *drill bundle* = a gradable `Problem` (→ `problemsJson`, the grade store) AND a renderable `DrillContent` (→ `drillsJson`, the render store), persisted by a read-merge-write route so nothing clobbers existing drills; the existing E1 grade route records mastery on the server-canonical `Problem.kcIds`. Routing adds a `viz_id`/`requires_visual` field on `KnowledgeConcept`, a shared `content/viz-ids.yaml` both Kotlin and TS read, a validator that fails closed on a missing viz, and a `RoutedViz` panel mounted inside `DrillStack`. Spec: `docs/superpowers/specs/2026-05-29-e3-generate-route-design.md` (rev 2.1).

**Tech Stack:** Kotlin/Ktor + Exposed (SQLite), kotlinx.serialization + `com.charleskorn.kaml:kaml:0.65.0` (YAML), free LLMs (OpenRouter `meta-llama/llama-3.3-70b-instruct:free` generator, `RelayLlm` Claude critic); React 19 + TypeScript + Vitest/@testing-library + a new Playwright harness.

---

## File Structure

**Kotlin (backend)**
- `src/main/kotlin/jarvis/content/ContentSchema.kt` — *modify*: add `viz_id`/`requires_visual` to `KnowledgeConcept`; add `VizIdsFile`.
- `src/main/kotlin/jarvis/content/ContentRepo.kt` — *modify*: add `loadVizIds()`.
- `src/main/kotlin/jarvis/content/ContentValidator.kt` — *modify*: add `checkVizReferences` + wire into `validate(...)`.
- `src/main/kotlin/jarvis/content/ContentCli.kt` — *modify*: pass loaded viz-ids into `validate`.
- `src/main/kotlin/jarvis/tutor/DrillContentDto.kt` — *create*: Kotlin mirror of the TS `DrillContent` (+ `vizId`) for writing `drillsJson`.
- `src/main/kotlin/jarvis/tutor/DrillGenerator.kt` — *create*: generator + safeguards + the new output parser.
- `src/main/kotlin/jarvis/web/TutorRoutes.kt` — *modify*: add `drillGeneratorLlmFactory`/`drillCriticLlmFactory`/`drillKcLookup` seams, the `POST /api/v1/task/{id}/generate-drills` route, and the `/reprep` kcId-preserving guard.
- `content/viz-ids.yaml` — *create*: the shared viz-id source of truth.
- `content/PA/kcs/pa-kc-fixture-recursion.yaml` + `pa-kc-fixture-compute.yaml` — *create*: required fixtures.
- `content/PA/edges.yaml` — *modify*: prereq edges for the fixtures.

**TypeScript (frontend)**
- `tutor-web/src/components/TutorWorkspace.tsx` — *modify*: extend the `Problem` interface (`shape?`, `vizId?`).
- `tutor-web/src/components/DrillStack.tsx` — *modify*: extend `DrillContent` (`vizId?`); mount `RoutedViz`.
- `tutor-web/src/components/viz/vizRegistry.ts` — *create*: `vizId → component` registry.
- `tutor-web/src/components/viz/vizRegistry.test.ts` — *create*: parity test vs `content/viz-ids.yaml`.
- `tutor-web/src/components/RoutedViz.tsx` — *create*: resolves a `vizId` to a component, stamps `data-testid="routed-viz-<id>"`.
- `tutor-web/src/components/DrillStack.test.tsx` — *create*: anti-ghost render test.
- `tutor-web/e2e/` + `tutor-web/playwright.config.ts` — *create*: Playwright harness + paint smoke.

---

## Task 0: Verify-and-pin the LLMs (prerequisite gate P1)

**Not a TDD code task — a verification gate. Block all generation code (Tasks 7-14) until this passes.**

- [ ] **Step 1: Confirm the generator model answers (free).**

Run (PowerShell, with `OPENROUTER_API_KEY` set in env):
```bash
/c/Tools/gradle-8.10/bin/gradle :run --args="llm-ping meta-llama/llama-3.3-70b-instruct:free" 2>NUL || echo "no llm-ping task — use the curl below"
curl -s https://openrouter.ai/api/v1/chat/completions -H "Authorization: Bearer $OPENROUTER_API_KEY" -H "Content-Type: application/json" -d '{"model":"meta-llama/llama-3.3-70b-instruct:free","messages":[{"role":"user","content":"reply OK"}],"max_tokens":5}'
```
Expected: HTTP 200 with a non-empty assistant message. If 404/400 (model gone), pick another free generator and update `OpenRouterChatLlm.kt:73` + this plan.

- [ ] **Step 2: Confirm the relay (Claude critic) answers.** Confirm `JARVIS_RELAY_URL` + `JARVIS_RELAY_TOKEN` are set and the home PC relay server is up:
```bash
curl -s -X POST "$JARVIS_RELAY_URL/complete" -H "Authorization: Bearer $JARVIS_RELAY_TOKEN" -H "Content-Type: application/json" -d '{"messages":[{"role":"user","content":"reply OK"}],"max_tokens":5}'
```
Expected: 200 with `{"reply": "...", "model": "..."}`. If connection refused → the relay PC is asleep; per DEC-1 (relay-only) generation will error until it's on — that is acceptable, but do not proceed to build against a dead relay.

- [ ] **Step 3: Pin the confirmed ids in the spec + commit a note.** Record in `docs/superpowers/specs/2026-05-29-e3-generate-route-design.md` §6 the exact model ids that answered. No code commit.

---

## Task 1: Add `viz_id` + `requires_visual` to `KnowledgeConcept`

**Files:**
- Modify: `src/main/kotlin/jarvis/content/ContentSchema.kt:36-54`
- Test: `src/test/kotlin/jarvis/content/ContentSchemaVizTest.kt` (create)

- [ ] **Step 1: Write the failing test**

```kotlin
package jarvis.content

import com.charleskorn.kaml.Yaml
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ContentSchemaVizTest {
    @Test fun `kc without viz fields decodes with defaults`() {
        val yaml = """
            id: pa-kc-001
            subject: PA
            name_ro: a
            name_en: a
            cluster: c
            bloom_level: understand
            difficulty: 1
            time_minutes: 25
            exam_weight: 1.0
            tier: 1
        """.trimIndent()
        val kc = Yaml.default.decodeFromString(KnowledgeConcept.serializer(), yaml)
        assertNull(kc.viz_id)
        assertFalse(kc.requires_visual)
    }

    @Test fun `kc with viz fields decodes`() {
        val yaml = """
            id: x
            subject: PA
            name_ro: a
            name_en: a
            cluster: c
            bloom_level: understand
            difficulty: 1
            time_minutes: 25
            exam_weight: 0.0
            tier: 2
            viz_id: recursion-tree
            requires_visual: true
        """.trimIndent()
        val kc = Yaml.default.decodeFromString(KnowledgeConcept.serializer(), yaml)
        assertEquals("recursion-tree", kc.viz_id)
        assertEquals(true, kc.requires_visual)
    }
}
```

- [ ] **Step 2: Run it, verify it fails**

Run: `/c/Tools/gradle-8.10/bin/gradle :test --tests "jarvis.content.ContentSchemaVizTest"`
Expected: FAIL — `KnowledgeConcept` has no `viz_id`/`requires_visual`.

- [ ] **Step 3: Add the fields**

In `ContentSchema.kt`, inside `data class KnowledgeConcept`, after `val source: List<SourceRef> = emptyList(),`:
```kotlin
    val source: List<SourceRef> = emptyList(),
    /** E3 routing: id of the visualization component (must appear in content/viz-ids.yaml). */
    val viz_id: String? = null,
    /** E3 routing: when true, the validator ERRORs if viz_id is null or unresolvable. */
    val requires_visual: Boolean = false,
    val version: Int = 1,
```

- [ ] **Step 4: Run the test, verify it passes**

Run: `/c/Tools/gradle-8.10/bin/gradle :test --tests "jarvis.content.ContentSchemaVizTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/content/ContentSchema.kt src/test/kotlin/jarvis/content/ContentSchemaVizTest.kt
git commit -m "feat(e3): add viz_id + requires_visual to KnowledgeConcept (additive, defaulted)"
```

---

## Task 2: `content/viz-ids.yaml` + `ContentRepo.loadVizIds()`

**Files:**
- Create: `content/viz-ids.yaml`
- Modify: `src/main/kotlin/jarvis/content/ContentSchema.kt` (add `VizIdsFile`), `src/main/kotlin/jarvis/content/ContentRepo.kt`
- Test: `src/test/kotlin/jarvis/content/VizIdsLoadTest.kt` (create)

- [ ] **Step 1: Create the source-of-truth file** `content/viz-ids.yaml`:
```yaml
# Canonical list of valid viz ids. The Kotlin validator (checkVizReferences) reads
# this; tutor-web/src/components/viz/vizRegistry.ts MUST cover exactly this set
# (enforced by vizRegistry.test.ts). Add an id here only when a component is
# registered in vizRegistry.ts.
viz_ids:
  - recursion-tree
```

- [ ] **Step 2: Write the failing test**

```kotlin
package jarvis.content

import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VizIdsLoadTest {
    @Test fun `loadVizIds reads the id set`() {
        val dir = Files.createTempDirectory("content")
        dir.resolve("viz-ids.yaml").writeText("viz_ids:\n  - recursion-tree\n  - bayes-tree\n")
        val ids = ContentRepo(dir).loadVizIds()
        assertEquals(setOf("recursion-tree", "bayes-tree"), ids)
    }

    @Test fun `loadVizIds returns empty when file absent`() {
        val dir = Files.createTempDirectory("content")
        assertTrue(ContentRepo(dir).loadVizIds().isEmpty())
    }
}
```

- [ ] **Step 3: Run it, verify it fails**

Run: `/c/Tools/gradle-8.10/bin/gradle :test --tests "jarvis.content.VizIdsLoadTest"`
Expected: FAIL — `VizIdsFile` / `loadVizIds` undefined.

- [ ] **Step 4: Add `VizIdsFile`** to `ContentSchema.kt` (near the other `@Serializable` data classes):
```kotlin
/** content/viz-ids.yaml — the canonical set of valid viz ids (Kotlin↔TS source of truth). */
@Serializable
data class VizIdsFile(val viz_ids: List<String> = emptyList())
```

- [ ] **Step 5: Add `loadVizIds()`** to `ContentRepo.kt` (mirror `loadSubject`'s yaml pattern; `root` is the constructor's content `Path`):
```kotlin
    /** E3: load content/viz-ids.yaml into a set. Empty if the file is absent. */
    fun loadVizIds(): Set<String> {
        val f = root.resolve("viz-ids.yaml")
        if (!f.exists()) return emptySet()
        return Yaml.default.decodeFromString(VizIdsFile.serializer(), f.readText()).viz_ids.toSet()
    }
```

- [ ] **Step 6: Run the test, verify it passes**

Run: `/c/Tools/gradle-8.10/bin/gradle :test --tests "jarvis.content.VizIdsLoadTest"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add content/viz-ids.yaml src/main/kotlin/jarvis/content/ContentSchema.kt src/main/kotlin/jarvis/content/ContentRepo.kt src/test/kotlin/jarvis/content/VizIdsLoadTest.kt
git commit -m "feat(e3): content/viz-ids.yaml + ContentRepo.loadVizIds (Kotlin side of the shared viz-id source of truth)"
```

---

## Task 3: `ContentValidator.checkVizReferences` (fail-closed gate)

**Files:**
- Modify: `src/main/kotlin/jarvis/content/ContentValidator.kt` (add check + wire into `validate`), `src/main/kotlin/jarvis/content/ContentCli.kt:10-20` (pass viz-ids)
- Test: `src/test/kotlin/jarvis/content/ContentValidatorVizTest.kt` (create)

- [ ] **Step 1: Write the failing test**

```kotlin
package jarvis.content

import kotlin.test.Test
import kotlin.test.assertTrue

class ContentValidatorVizTest {
    private fun kc(id: String, viz: String?, requires: Boolean) = KnowledgeConcept(
        id = id, subject = "PA", name_ro = "a", name_en = "a", cluster = "c",
        bloom_level = "understand", difficulty = 1, time_minutes = 1, exam_weight = 0.0,
        tier = 1, viz_id = viz, requires_visual = requires,
    )
    private fun sub(vararg kcs: KnowledgeConcept) = LoadedSubject("PA", kcs.toList(), emptyList(), emptyList())

    @Test fun `requires_visual with unresolvable viz_id is an error`() {
        val issues = ContentValidator.checkVizReferences(sub(kc("k1", "ghost-viz", true)), setOf("recursion-tree"))
        assertTrue(issues.any { it.severity == "error" && it.rule == "viz_reference" })
    }

    @Test fun `requires_visual with null viz_id is an error`() {
        val issues = ContentValidator.checkVizReferences(sub(kc("k1", null, true)), setOf("recursion-tree"))
        assertTrue(issues.any { it.severity == "error" && it.rule == "viz_reference" })
    }

    @Test fun `requires_visual with resolvable viz_id passes`() {
        val issues = ContentValidator.checkVizReferences(sub(kc("k1", "recursion-tree", true)), setOf("recursion-tree"))
        assertTrue(issues.isEmpty())
    }

    @Test fun `non-visual kc with null viz_id passes`() {
        val issues = ContentValidator.checkVizReferences(sub(kc("k1", null, false)), setOf("recursion-tree"))
        assertTrue(issues.isEmpty())
    }
}
```

- [ ] **Step 2: Run it, verify it fails**

Run: `/c/Tools/gradle-8.10/bin/gradle :test --tests "jarvis.content.ContentValidatorVizTest"`
Expected: FAIL — `checkVizReferences` undefined.

- [ ] **Step 3: Add the check** to `ContentValidator.kt` (mirror `checkExamWeights`'s structure):
```kotlin
    /** E3: a requires_visual KC must name a viz_id present in content/viz-ids.yaml. */
    fun checkVizReferences(sub: LoadedSubject, validVizIds: Set<String>): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        for (kc in sub.kcs) {
            if (!kc.requires_visual) continue
            val vid = kc.viz_id
            when {
                vid == null -> issues += ValidationIssue("error", "viz_reference", sub.subject,
                    "KC '${kc.id}' is requires_visual but has no viz_id")
                vid !in validVizIds -> issues += ValidationIssue("error", "viz_reference", sub.subject,
                    "KC '${kc.id}' viz_id '$vid' is not in content/viz-ids.yaml")
            }
        }
        return issues
    }
```

- [ ] **Step 4: Wire it into `validate(...)`.** Change the signature (add `validVizIds` with a default BEFORE the trailing `sourceText` lambda — Kotlin binds the trailing lambda to the last param, so existing `validate(subjects) { ... }` callers still compile) and add the call in the loop:
```kotlin
    fun validate(
        subjects: List<LoadedSubject>,
        validVizIds: Set<String> = emptySet(),
        sourceText: (doc: String) -> String?,
    ): ValidationReport {
        val issues = mutableListOf<ValidationIssue>()
        for (sub in subjects) {
            issues += detectCycles(sub)
            issues += detectOrphans(sub)
            issues += checkExamWeights(sub)
            issues += checkBilingual(sub)
            issues += checkVerbatimSources(sub, sourceText)
            issues += checkVizReferences(sub, validVizIds)
        }
        val ok = issues.none { it.severity == "error" }
        return ValidationReport(ok = ok, disclaimer = DISCLAIMER, issues = issues)
    }
```
Also update the rule-list comment on `ValidationIssue` (line ~7) to include `"viz_reference"`.

- [ ] **Step 5: Pass the live viz-ids in `ContentCli.validateOnly`** (`ContentCli.kt:10-20`):
```kotlin
    fun validateOnly(contentDir: Path): ValidationReport {
        val repo = ContentRepo(contentDir)
        val manifest = repo.loadManifest()
        val subjects = manifest.subjects.map { repo.loadSubject(it.id) }
        val vizIds = repo.loadVizIds()
        return ContentValidator.validate(subjects, vizIds) { doc ->
            manifest.subjects.firstNotNullOfOrNull { repo.sourceText(it.id, doc) }
        }
    }
```

- [ ] **Step 6: Run the new test + the full content-validator suite + the live corpus**

Run:
```bash
/c/Tools/gradle-8.10/bin/gradle :test --tests "jarvis.content.ContentValidatorVizTest" --tests "jarvis.content.ContentValidatorTest"
/c/Tools/gradle-8.10/bin/gradle validateContent
```
Expected: PASS; `validateContent` prints `OK` (no KC sets `requires_visual` yet → the live corpus stays green).

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/jarvis/content/ContentValidator.kt src/main/kotlin/jarvis/content/ContentCli.kt src/test/kotlin/jarvis/content/ContentValidatorVizTest.kt
git commit -m "feat(e3): ContentValidator.checkVizReferences — fail-closed on requires_visual KCs with missing/unresolvable viz_id"
```

---

## Task 4: Frontend — extend the `Problem` interface (carry `shape` + `vizId`)

**Files:**
- Modify: `tutor-web/src/components/TutorWorkspace.tsx:18-24`

- [ ] **Step 1: Extend the interface**
```typescript
interface Problem {
  problemId: string;  // camelCase matches the JSON produced by the Kotlin serializer
  page: number;
  statement: string;
  equationRefs?: string[];
  dataGivens?: string[];
  shape?: string;   // E3: computational|proof-derivation|design-implement|analysis-trace|fact-conceptual
  vizId?: string;   // E3: resolved viz id for routing (mirrors the KC's viz_id)
}
```

- [ ] **Step 2: Typecheck**

Run: `cd tutor-web && npx tsc --noEmit`
Expected: no new errors (fields optional; existing code unaffected).

- [ ] **Step 3: Commit**

```bash
git add tutor-web/src/components/TutorWorkspace.tsx
git commit -m "feat(e3): carry shape + vizId on the frontend Problem type"
```

---

## Task 5: `vizRegistry.ts` + parity test

**Files:**
- Create: `tutor-web/src/components/viz/vizRegistry.ts`, `tutor-web/src/components/viz/vizRegistry.test.ts`

- [ ] **Step 1: Write the failing parity test** (reads `content/viz-ids.yaml` as text — no yaml dep needed):
```typescript
import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { vizRegistry } from "./vizRegistry";

describe("vizRegistry", () => {
  it("covers exactly the ids in content/viz-ids.yaml", () => {
    const yaml = readFileSync(resolve(__dirname, "../../../../content/viz-ids.yaml"), "utf8");
    const ids = yaml
      .split("\n")
      .map((l) => l.match(/^\s*-\s*(\S+)\s*$/)?.[1])
      .filter((x): x is string => Boolean(x));
    expect(Object.keys(vizRegistry).sort()).toEqual([...ids].sort());
  });
});
```

- [ ] **Step 2: Run it, verify it fails**

Run: `cd tutor-web && npx vitest run src/components/viz/vizRegistry.test.ts`
Expected: FAIL — `vizRegistry` module does not exist.

- [ ] **Step 3: Create the registry**
```typescript
import type { ReactNode } from "react";
import { RecursionTree } from "./RecursionTree";

/**
 * E3 routing: viz id → component. The key set MUST equal content/viz-ids.yaml
 * (enforced by vizRegistry.test.ts). Add an entry here AND in content/viz-ids.yaml
 * together. Most components are zero-prop concept-level illustrations.
 */
export const vizRegistry: Record<string, () => ReactNode> = {
  "recursion-tree": RecursionTree,
};
```

- [ ] **Step 4: Run the test, verify it passes**

Run: `cd tutor-web && npx vitest run src/components/viz/vizRegistry.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add tutor-web/src/components/viz/vizRegistry.ts tutor-web/src/components/viz/vizRegistry.test.ts
git commit -m "feat(e3): vizRegistry.ts + parity test vs content/viz-ids.yaml (TS side of the source of truth)"
```

---

## Task 6: `RoutedViz` + mount in `DrillStack` (the anti-ghost mount)

**Files:**
- Create: `tutor-web/src/components/RoutedViz.tsx`, `tutor-web/src/components/DrillStack.test.tsx`
- Modify: `tutor-web/src/components/DrillStack.tsx` (extend `DrillContent`, mount `RoutedViz`)

- [ ] **Step 1: Create `RoutedViz`** (resolves the id, stamps the routing testid; renders nothing if unresolved — fallback-to-text, "never fake fancy boxes"):
```typescript
import { vizRegistry } from "./viz/vizRegistry";

/** E3 routing: mount the registry component for `vizId`, wrapped in a testid'd panel.
 *  Renders null when vizId is absent/unknown (text-only fallback — never a fake box). */
export function RoutedViz({ vizId }: { vizId?: string }) {
  if (!vizId) return null;
  const Component = vizRegistry[vizId];
  if (!Component) return null;
  return (
    <div data-testid={`routed-viz-${vizId}`} className="border-2 border-border-strong p-2">
      <Component />
    </div>
  );
}
```

- [ ] **Step 2: Write the failing render test** (mounts the REAL `DrillStack` with a `vizId` drill — catches the ghost):
```typescript
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { DrillStack } from "./DrillStack";
import type { DrillContent } from "./DrillStack";

const content: DrillContent = {
  drill: "What does fib(5) expand to?",
  worked: "fib(5) = fib(4) + fib(3) ...",
  definition: "Recursion: a function defined in terms of itself.",
  check: "Trace fib(4).",
  expectedAnswerHint: "5",
  vizId: "recursion-tree",
};

describe("DrillStack routing", () => {
  it("mounts the routed viz and the drill card", () => {
    render(<DrillStack taskId="t1" problemId="p1" content={content} onProblemComplete={() => {}} />);
    expect(screen.getAllByTestId("drill-card").length).toBeGreaterThan(0);
    expect(screen.getByTestId("routed-viz-recursion-tree")).toBeInTheDocument();
    expect(screen.getByTestId("recursion-tree-root")).toBeInTheDocument();
  });

  it("renders no routed viz when vizId is absent", () => {
    render(<DrillStack taskId="t1" problemId="p1" content={{ ...content, vizId: undefined }} onProblemComplete={() => {}} />);
    expect(screen.queryByTestId("routed-viz-recursion-tree")).not.toBeInTheDocument();
  });
});
```

- [ ] **Step 3: Run it, verify it fails**

Run: `cd tutor-web && npx vitest run src/components/DrillStack.test.tsx`
Expected: FAIL — `DrillContent` has no `vizId` / no `routed-viz` element.

- [ ] **Step 4: Extend `DrillContent`** (`DrillStack.tsx:9-24`), adding after `rubricItems?: string[];`:
```typescript
  rubricItems?: string[];
  /** E3 routing: when set, DrillStack mounts the registry component for this id. */
  vizId?: string;
```

- [ ] **Step 5: Mount `RoutedViz` in `DrillStack`.** Add the import at the top:
```typescript
import { RoutedViz } from "./RoutedViz";
```
Then in the returned JSX (`DrillStack.tsx:~143`), immediately inside `<div data-testid="drill-stack" ...>`, before the DRILL card:
```tsx
    <div data-testid="drill-stack" className="flex flex-col gap-4 p-4">
      <RoutedViz vizId={content.vizId} />
      {/* Card order: DRILL → WORKED → DEFINITION → CHECK */}
```

- [ ] **Step 6: Run the test, verify it passes; typecheck**

Run:
```bash
cd tutor-web && npx vitest run src/components/DrillStack.test.tsx && npx tsc --noEmit
```
Expected: PASS, no type errors.

- [ ] **Step 7: Commit**

```bash
git add tutor-web/src/components/RoutedViz.tsx tutor-web/src/components/DrillStack.tsx tutor-web/src/components/DrillStack.test.tsx
git commit -m "feat(e3): RoutedViz + mount in DrillStack — viz paints on the real drill surface (anti-ghost)"
```

---

## Task 7: `DrillContentDto` (Kotlin mirror of `DrillContent`)

**Files:**
- Create: `src/main/kotlin/jarvis/tutor/DrillContentDto.kt`
- Test: `src/test/kotlin/jarvis/tutor/DrillContentDtoTest.kt` (create)

- [ ] **Step 1: Write the failing test** (round-trips through the same `tutorJson` used for prep):
```kotlin
package jarvis.tutor

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlin.test.Test
import kotlin.test.assertEquals

class DrillContentDtoTest {
    @Test fun `drillsJson encodes a problemId-keyed map with vizId`() {
        val map = mapOf("p1" to DrillContentDto(
            drill = "d", worked = "w", definition = "def", check = "c",
            expectedAnswerHint = "5", vizId = "recursion-tree",
        ))
        val s = TutorTypes.tutorJson.encodeToString(
            MapSerializer(String.serializer(), DrillContentDto.serializer()), map)
        val back = TutorTypes.tutorJson.decodeFromString(
            MapSerializer(String.serializer(), DrillContentDto.serializer()), s)
        assertEquals("recursion-tree", back["p1"]!!.vizId)
        assertEquals("5", back["p1"]!!.expectedAnswerHint)
    }
}
```

- [ ] **Step 2: Run it, verify it fails**

Run: `/c/Tools/gradle-8.10/bin/gradle :test --tests "jarvis.tutor.DrillContentDtoTest"`
Expected: FAIL — `DrillContentDto` undefined.

- [ ] **Step 3: Create the DTO** (field names match the TS `DrillContent` exactly so the frontend parses it):
```kotlin
package jarvis.tutor

import kotlinx.serialization.Serializable

/** E3 render store: Kotlin mirror of tutor-web's DrillContent (DrillStack.tsx).
 *  drillsJson is a JSON object problemId -> DrillContentDto. */
@Serializable
data class DrillContentDto(
    val drill: String,
    val worked: String,
    val definition: String,
    val check: String,
    val expectedAnswerHint: String,
    val language: String? = null,
    val referenceSolution: String? = null,
    val rubricItems: List<String>? = null,
    val vizId: String? = null,
)
```

- [ ] **Step 4: Run the test, verify it passes**

Run: `/c/Tools/gradle-8.10/bin/gradle :test --tests "jarvis.tutor.DrillContentDtoTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/tutor/DrillContentDto.kt src/test/kotlin/jarvis/tutor/DrillContentDtoTest.kt
git commit -m "feat(e3): DrillContentDto — Kotlin render-store mirror of the TS DrillContent (+vizId)"
```

---

## Task 8: Generator output parser (NOT `parseLlmJson`)

**Files:**
- Create: `src/main/kotlin/jarvis/tutor/DrillGenParser.kt`
- Test: `src/test/kotlin/jarvis/tutor/DrillGenParserTest.kt` (create)

`parseLlmJson` (PdfProblemExtractor.kt:33-51) DROPS kcIds/shape/canonicalAnswer/rubricItems — reusing it = ungradable drills. This parser keeps every field, and mirrors DrillGrader's brace-balance resilience.

- [ ] **Step 1: Write the failing test**
```kotlin
package jarvis.tutor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DrillGenParserTest {
    @Test fun `parses a full generated drill incl grading + render fields`() {
        val raw = """
          Here is the drill:
          {"statement":"Compute 6*7.","canonical_answer":"42","rubric_items":["arithmetic correct"],
           "reference_solution":null,"worked":"6*7=42","definition":"Multiplication.",
           "check":"Compute 7*8.","expected_answer_hint":"42"}
        """.trimIndent()
        val d = DrillGenParser.parse(raw)!!
        assertEquals("Compute 6*7.", d.statement)
        assertEquals("42", d.canonicalAnswer)
        assertEquals(listOf("arithmetic correct"), d.rubricItems)
        assertEquals("6*7=42", d.worked)
        assertEquals("42", d.expectedAnswerHint)
    }

    @Test fun `returns null on garbage`() {
        assertNull(DrillGenParser.parse("no json here"))
    }
}
```

- [ ] **Step 2: Run it, verify it fails**

Run: `/c/Tools/gradle-8.10/bin/gradle :test --tests "jarvis.tutor.DrillGenParserTest"`
Expected: FAIL — `DrillGenParser` undefined.

- [ ] **Step 3: Implement the parser**
```kotlin
package jarvis.tutor

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Parsed generator output — carries BOTH grading fields and render fields. */
data class GeneratedDrill(
    val statement: String,
    val canonicalAnswer: String?,
    val rubricItems: List<String>,
    val referenceSolution: String?,
    val worked: String,
    val definition: String,
    val check: String,
    val expectedAnswerHint: String,
)

object DrillGenParser {
    private val json = Json { ignoreUnknownKeys = true }

    /** Extract the first balanced {...} block (CLI/relay providers wrap JSON in prose). */
    private fun firstBalancedBraceBlock(s: String): String? {
        val start = s.indexOf('{'); if (start < 0) return null
        var depth = 0; var inString = false; var escape = false
        for (i in start until s.length) {
            val c = s[i]
            if (escape) { escape = false; continue }
            if (c == '\\' && inString) { escape = true; continue }
            if (c == '"') { inString = !inString; continue }
            if (inString) continue
            when (c) { '{' -> depth++; '}' -> { depth--; if (depth == 0) return s.substring(start, i + 1) } }
        }
        return null
    }

    private fun str(o: JsonObject, k: String): String? = (o[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
    private fun arr(o: JsonObject, k: String): List<String> =
        (o[k] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()

    fun parse(raw: String): GeneratedDrill? {
        val block = firstBalancedBraceBlock(raw) ?: return null
        val obj = try { json.parseToJsonElement(block) as? JsonObject } catch (_: Exception) { null } ?: return null
        val statement = str(obj, "statement") ?: return null
        val worked = str(obj, "worked") ?: return null
        val definition = str(obj, "definition") ?: return null
        val check = str(obj, "check") ?: return null
        val hint = str(obj, "expected_answer_hint") ?: ""
        return GeneratedDrill(
            statement = statement,
            canonicalAnswer = str(obj, "canonical_answer"),
            rubricItems = arr(obj, "rubric_items"),
            referenceSolution = str(obj, "reference_solution"),
            worked = worked, definition = definition, check = check, expectedAnswerHint = hint,
        )
    }
}
```

- [ ] **Step 4: Run the test, verify it passes**

Run: `/c/Tools/gradle-8.10/bin/gradle :test --tests "jarvis.tutor.DrillGenParserTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/tutor/DrillGenParser.kt src/test/kotlin/jarvis/tutor/DrillGenParserTest.kt
git commit -m "feat(e3): DrillGenParser — keeps grading + render fields the LLM emits (not parseLlmJson)"
```

---

## Task 9: `DrillGenerator` — generate + safeguards (leak / self-solve / cross-family critic / reject-don't-ship)

**Files:**
- Create: `src/main/kotlin/jarvis/tutor/DrillGenerator.kt`
- Test: `src/test/kotlin/jarvis/tutor/DrillGeneratorTest.kt` (create)

- [ ] **Step 1: Write the failing test** (fake generator + fake critic Llms):
```kotlin
package jarvis.tutor

import jarvis.ChatMessage
import jarvis.Llm
import jarvis.content.KnowledgeConcept
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DrillGeneratorTest {
    private class Fake(private val reply: String) : Llm {
        override suspend fun complete(messages: List<ChatMessage>, maxTokens: Int, responseFormat: String?) = reply to "fake"
    }
    private val kc = KnowledgeConcept("pa-kc-x", "PA", "a", "a", "c", "understand", 1, 1, 0.0, 1)
    private val goodDrill = """{"statement":"Compute 6*7.","canonical_answer":"42","rubric_items":["ok"],"worked":"42","definition":"mult","check":"7*8?","expected_answer_hint":"42"}"""
    private val goodCritic = """{"confidence":0.9,"grounded":true,"leak":false,"solvable":true}"""

    @Test fun `accepts a self-consistent computational drill the critic approves`() = runBlocking {
        // generator: 1st call = drill, 2nd call = self-solve answer "42"
        val gen = object : Llm {
            var n = 0
            override suspend fun complete(m: List<ChatMessage>, t: Int, r: String?) =
                (if (n++ == 0) goodDrill else "42") to "fake-gen"
        }
        val res = DrillGenerator.generate(kc, listOf("mult quote"), "computational", 1, gen, Fake(goodCritic))
        assertEquals(1, res.bundles.size)
        assertEquals(listOf("pa-kc-x"), res.bundles[0].problem.kcIds)
        assertEquals("42", res.bundles[0].problem.canonicalAnswer)
        assertEquals("Compute 6*7.", res.bundles[0].content.drill)
        assertNull(res.bundles[0].content.vizId) // kc.viz_id is null here; the route sets it (see GenerateDrillsRouteTest)
    }

    @Test fun `rejects when self-solve disagrees with canonical answer`() = runBlocking {
        val gen = object : Llm {
            var n = 0
            override suspend fun complete(m: List<ChatMessage>, t: Int, r: String?) =
                (if (n++ == 0) goodDrill else "99") to "fake-gen"  // self-solve says 99 != 42
        }
        val res = DrillGenerator.generate(kc, listOf("q"), "computational", 1, gen, Fake(goodCritic))
        assertEquals(0, res.bundles.size)
        assertTrue(res.rejectReasons.any { it.contains("self-solve") })
    }

    @Test fun `rejects when critic is not confident`() = runBlocking {
        val gen = object : Llm { var n = 0
            override suspend fun complete(m: List<ChatMessage>, t: Int, r: String?) = (if (n++ == 0) goodDrill else "42") to "g" }
        val lowCritic = """{"confidence":0.2,"grounded":true,"leak":false,"solvable":true}"""
        val res = DrillGenerator.generate(kc, listOf("q"), "computational", 1, gen, Fake(lowCritic))
        assertEquals(0, res.bundles.size)
        assertTrue(res.rejectReasons.any { it.contains("critic") })
    }

    @Test fun `rejects when the stem leaks its own answer`() = runBlocking {
        val leaky = """{"statement":"Compute 6*7. The answer is 42.","canonical_answer":"42","rubric_items":["ok"],"worked":"42","definition":"m","check":"c","expected_answer_hint":"42"}"""
        val gen = object : Llm { var n = 0
            override suspend fun complete(m: List<ChatMessage>, t: Int, r: String?) = (if (n++ == 0) leaky else "42") to "g" }
        val res = DrillGenerator.generate(kc, listOf("q"), "computational", 1, gen, Fake(goodCritic))
        assertEquals(0, res.bundles.size)
        assertTrue(res.rejectReasons.any { it.contains("leak") })
    }
}
```

- [ ] **Step 2: Run it, verify it fails**

Run: `/c/Tools/gradle-8.10/bin/gradle :test --tests "jarvis.tutor.DrillGeneratorTest"`
Expected: FAIL — `DrillGenerator` undefined.

- [ ] **Step 3: Implement `DrillGenerator`**
```kotlin
package jarvis.tutor

import jarvis.ChatMessage
import jarvis.Llm
import jarvis.content.KnowledgeConcept
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

object DrillGenerator {
    const val CONFIDENCE_THRESHOLD = 0.7
    private val json = Json { ignoreUnknownKeys = true }

    data class Bundle(val problem: Problem, val content: DrillContentDto)
    data class GenerateResult(val bundles: List<Bundle>, val rejectReasons: List<String>)
    private data class CriticVerdict(val confidence: Double, val grounded: Boolean, val leak: Boolean, val solvable: Boolean)

    private fun shapePrompt(shape: String, kc: KnowledgeConcept, sources: List<String>): String = """
        You are authoring ONE fresh practice drill for the concept "${kc.name_en}" (${kc.name_ro}), bloom level ${kc.bloom_level}, difficulty ${kc.difficulty}.
        Shape: $shape.
        Ground it ONLY in this source material (do not invent facts beyond it):
        ${sources.joinToString("\n") { "- $it" }}
        Output ONE JSON object, no prose, with keys:
        statement (the problem; do NOT state the answer in it),
        ${if (shape == "computational") "canonical_answer (the exact numeric/string answer)," else "rubric_items (array of point-split grading criteria),"}
        reference_solution (or null), worked (a worked solution), definition (a one-sentence concept definition),
        check (a short transfer question), expected_answer_hint (a brief hint).
    """.trimIndent()

    private fun parseCritic(raw: String): CriticVerdict? {
        val start = raw.indexOf('{'); val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val obj = try { json.parseToJsonElement(raw.substring(start, end + 1)) as? JsonObject } catch (_: Exception) { null } ?: return null
        val conf = (obj["confidence"] as? JsonPrimitive)?.doubleOrNull ?: return null
        fun b(k: String) = (obj[k] as? JsonPrimitive)?.booleanOrNull ?: false
        return CriticVerdict(conf, b("grounded"), b("leak"), b("solvable"))
    }

    /** Generate up to [count] drills for [kc] of [shape]; each must pass leak + self-solve + cross-family critic. */
    suspend fun generate(
        kc: KnowledgeConcept,
        sources: List<String>,
        shape: String,
        count: Int,
        generator: Llm,
        critic: Llm,
    ): GenerateResult {
        val bundles = mutableListOf<Bundle>()
        val rejects = mutableListOf<String>()
        for (i in 0 until count) {
            val (raw, _) = generator.complete(listOf(ChatMessage("user", shapePrompt(shape, kc, sources))), maxTokens = 1200, responseFormat = "json_object")
            val d = DrillGenParser.parse(raw)
            if (d == null) { rejects += "parse failure"; continue }
            // (1) answer-leak: the stem must not contain its own canonical answer.
            if (d.canonicalAnswer != null && d.statement.contains(d.canonicalAnswer, ignoreCase = true)) { rejects += "leak: stem contains canonical answer"; continue }
            // (2) self-solve reconcile (computational only).
            if (d.canonicalAnswer != null) {
                val (solvedRaw, _) = generator.complete(listOf(ChatMessage("user", "Solve and reply with ONLY the final answer:\n${d.statement}")), maxTokens = 200)
                if (!GradeScoring.answerMatches(d.canonicalAnswer, solvedRaw.trim())) { rejects += "self-solve mismatch (${d.canonicalAnswer} vs ${solvedRaw.trim().take(40)})"; continue }
            }
            // (3) cross-family critic.
            val (criticRaw, criticModel) = critic.complete(listOf(ChatMessage("user",
                "Review this drill. Reply ONLY JSON {confidence:0..1, grounded:bool, leak:bool, solvable:bool}.\nSOURCES:\n${sources.joinToString("\n")}\nDRILL:\n${d.statement}\nANSWER: ${d.canonicalAnswer ?: d.rubricItems.joinToString("; ")}")), maxTokens = 200)
            val v = parseCritic(criticRaw)
            if (v == null) { rejects += "critic parse failure ($criticModel)"; continue }
            if (v.confidence < CONFIDENCE_THRESHOLD || !v.grounded || v.leak || !v.solvable) { rejects += "critic rejected (conf=${v.confidence}, grounded=${v.grounded}, leak=${v.leak}, solvable=${v.solvable})"; continue }
            // accept.
            val pid = "gen-${kc.id}-$i"
            bundles += Bundle(
                problem = Problem(problemId = pid, page = 0, statement = d.statement,
                    kcIds = listOf(kc.id), rubricItems = d.rubricItems, referenceSolution = d.referenceSolution,
                    canonicalAnswer = d.canonicalAnswer, shape = shape),
                content = DrillContentDto(drill = d.statement, worked = d.worked, definition = d.definition,
                    check = d.check, expectedAnswerHint = d.expectedAnswerHint,
                    referenceSolution = d.referenceSolution,
                    rubricItems = d.rubricItems.ifEmpty { null }, vizId = kc.viz_id),
            )
        }
        return GenerateResult(bundles, rejects)
    }
}
```
_(Note: in the test, `kc.viz_id` is null so `content.vizId` is null — the test's `?: "recursion-tree"` tolerates that. The route sets the real `viz_id` from the KC.)_

- [ ] **Step 4: Run the test, verify it passes**

Run: `/c/Tools/gradle-8.10/bin/gradle :test --tests "jarvis.tutor.DrillGeneratorTest"`
Expected: PASS (all 4 cases).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/tutor/DrillGenerator.kt src/test/kotlin/jarvis/tutor/DrillGeneratorTest.kt
git commit -m "feat(e3): DrillGenerator — leak/self-solve/cross-family-critic safeguards + reject-don't-ship"
```

---

## Task 10: LLM + KC-lookup seams

**Files:**
- Modify: `src/main/kotlin/jarvis/web/TutorRoutes.kt` (near line 106, beside `drillGraderLlmFactory`)

- [ ] **Step 1: Add the seams** (same `internal var` pattern; critic defaults to the relay per DEC-1; `drillKcLookup` makes the generate route testable):
```kotlin
/** E3 test seams. Production: generator = free OpenRouter Llama; critic = Claude via relay (DEC-1 relay-only). */
internal var drillGeneratorLlmFactory: () -> jarvis.Llm = { jarvis.OpenRouterChatLlm() }
internal var drillCriticLlmFactory: () -> jarvis.Llm = { jarvis.RelayLlm() }
/** E3: resolve a KC for generation grounding. Default loads from the content corpus. Overridden in tests. */
internal var drillKcLookup: (subject: String, kcId: String) -> jarvis.content.KnowledgeConcept? = { subject, kcId ->
    try {
        jarvis.content.ContentRepo(jarvis.Config.contentDir).loadSubject(subject).kcs.firstOrNull { it.id == kcId }
    } catch (_: Exception) { null }
}
```

- [ ] **Step 2: Confirm `jarvis.Config.contentDir` exists; if not, mirror CuratorRoutes.**

Run: `rg -n "contentDir|ContentRepo\(" src/main/kotlin/jarvis`
Expected: a `Config.contentDir` (or equivalent) accessor used by `CuratorRoutes`. If the symbol differs, replace `jarvis.Config.contentDir` above with the exact accessor CuratorRoutes uses. (This is a follow-existing-pattern step, not a new design.)

- [ ] **Step 3: Compile**

Run: `/c/Tools/gradle-8.10/bin/gradle :compileKotlin`
Expected: success.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/jarvis/web/TutorRoutes.kt
git commit -m "feat(e3): drillGenerator/critic Llm factories + drillKcLookup seam (critic=relay per DEC-1)"
```

---

## Task 11: `POST /api/v1/task/{id}/generate-drills` — read-merge-write both stores

**Files:**
- Modify: `src/main/kotlin/jarvis/web/TutorRoutes.kt` (add the route inside `installTutorRoutes`'s `routing { }`; add request/reply DTOs near the other DTOs ~2374)
- Test: `src/test/kotlin/jarvis/tutor/GenerateDrillsRouteTest.kt` (create)

- [ ] **Step 1: Write the failing route test** (overrides all three seams; asserts both stores get the drill, keyed by problemId, and existing drills survive):
```kotlin
package jarvis.tutor

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import jarvis.ChatMessage
import jarvis.Llm
import jarvis.content.KnowledgeConcept
import jarvis.web.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.Instant
import kotlin.test.*

class GenerateDrillsRouteTest {
    private val goodDrill = """{"statement":"Compute 6*7.","canonical_answer":"42","rubric_items":["ok"],"worked":"42","definition":"mult","check":"7*8?","expected_answer_hint":"42"}"""
    private val goodCritic = """{"confidence":0.9,"grounded":true,"leak":false,"solvable":true}"""

    @AfterEach fun reset() {
        drillGeneratorLlmFactory = { jarvis.OpenRouterChatLlm() }
        drillCriticLlmFactory = { jarvis.RelayLlm() }
        drillKcLookup = { _, _ -> null }
    }

    @Test fun `generate-drills persists a gradable Problem + a renderable DrillContent and preserves existing`() = testApplication {
        // installFreshTutor = local helper (see harness note below); seedSession/seedTask copied from E2LoopSmokeTest.kt.
        val ctx = installFreshTutor(this)
        val (userId, sid) = seedSession(ctx)
        seedTask(ctx, userId, "task-1")
        // pre-existing authored drill must survive the merge:
        TaskPrepRepo(ctx.db).upsert(TaskPrep("task-1", Instant.now(), 1,
            problemsJson = Json.encodeToString(ListSerializer(Problem.serializer()),
                listOf(Problem("authored-1", 1, "old", kcIds = listOf("pa-kc-001")))),
            drillsJson = "{}", railJson = "[]"))

        drillKcLookup = { _, _ -> KnowledgeConcept("pa-kc-001", "PA", "a", "a", "c", "understand", 1, 1, 0.0, 1, viz_id = "recursion-tree") }
        val gen = object : Llm { var n = 0
            override suspend fun complete(m: List<ChatMessage>, t: Int, r: String?) = (if (n++ == 0) goodDrill else "42") to "g" }
        drillGeneratorLlmFactory = { gen }
        drillCriticLlmFactory = { object : Llm { override suspend fun complete(m: List<ChatMessage>, t: Int, r: String?) = goodCritic to "claude" } }

        val resp = client.post("/api/v1/task/task-1/generate-drills") {
            header(HttpHeaders.Cookie, "jarvis_session=$sid")
            header("X-CSRF", "1")  // match the csrfProtect token mechanism used by other tests
            contentType(ContentType.Application.Json)
            setBody("""{"kcId":"pa-kc-001","shape":"computational","count":1}""")
        }
        assertEquals(HttpStatusCode.OK, resp.status)

        val prep = TaskPrepRepo(ctx.db).findByTaskId("task-1")!!
        val problems = Json.decodeFromString(ListSerializer(Problem.serializer()), prep.problemsJson)
        assertTrue(problems.any { it.problemId == "authored-1" }, "existing authored problem must survive")
        val gen1 = problems.first { it.problemId == "gen-pa-kc-001-0" }
        assertEquals(listOf("pa-kc-001"), gen1.kcIds)
        assertTrue(prep.drillsJson.contains("gen-pa-kc-001-0") && prep.drillsJson.contains("recursion-tree"))
    }
}
```
_(Harness note: there is no shared `installFreshTutor` helper — define it locally in this test file, mirroring `DrillGradeServerSideTest.kt`'s app setup (which imports `jarvis.web.installTutorContext` + `jarvis.web.installTutorRoutes` and builds a temp-DB `TutorContext`). `seedSession`/`seedTask` are `private` in `E2LoopSmokeTest.kt` — copy them into this file. Use that test's exact CSRF approach for the `X-CSRF` header / `csrfProtect` token. All in package `jarvis.tutor`.)_

- [ ] **Step 2: Run it, verify it fails**

Run: `/c/Tools/gradle-8.10/bin/gradle :test --tests "jarvis.tutor.GenerateDrillsRouteTest"`
Expected: FAIL — route 404 / DTO undefined.

- [ ] **Step 3: Add the DTOs** near `ApiPrepAuthoredRequest` (~TutorRoutes.kt:2374):
```kotlin
@Serializable
private data class ApiGenerateDrillsRequest(val kcId: String, val shape: String? = null, val count: Int = 1)

@Serializable
private data class ApiGenerateDrillsReply(
    val taskId: String,
    val accepted: List<AcceptedDrill>,
    val rejectedCount: Int,
    val rejectReasons: List<String>,
    val criticUsed: String,
    val generatedAt: String,
)
@Serializable
private data class AcceptedDrill(val problemId: String, val shape: String)
```

- [ ] **Step 4: Add the route** inside `routing { }` in `installTutorRoutes` (model the auth/ownership block on `prep-authored`, TutorRoutes.kt:1370):
```kotlin
        post("/api/v1/task/{id}/generate-drills") {
            val ctx = application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@post }
            call.csrfProtect {
                val sid = call.request.cookies["jarvis_session"]
                val userId = sid?.let { SessionRepo(ctx.db).findUserId(it) }
                    ?: run { call.respond(HttpStatusCode.Unauthorized, "invalid session"); return@csrfProtect }
                val taskId = call.parameters["id"]?.takeIf { it.isNotBlank() }
                    ?: run { call.respond(HttpStatusCode.BadRequest, "id required"); return@csrfProtect }
                val task = TaskRepo(ctx.db).findById(taskId)
                if (task == null || task.userId != userId) { call.respond(HttpStatusCode.NotFound, "task not found"); return@csrfProtect }
                val body = try { sensorJson.decodeFromString(ApiGenerateDrillsRequest.serializer(), call.receiveText()) }
                    catch (e: Exception) { call.respond(HttpStatusCode.BadRequest, "malformed: ${e.message?.take(160)}"); return@csrfProtect }

                val kc = drillKcLookup(task.subject, body.kcId)
                    ?: run { call.respond(HttpStatusCode.NotFound, "kc not found: ${body.kcId}"); return@csrfProtect }
                val shape = body.shape ?: kc.bloom_level.let { "fact-conceptual" }  // default; per-KC shape table refined later
                val sources = kc.source.map { it.quote }

                val result = try {
                    drillGeneratorLlmFactory().use { gen ->
                        drillCriticLlmFactory().use { critic ->
                            kotlinx.coroutines.runBlocking {
                                jarvis.tutor.DrillGenerator.generate(kc, sources, shape, body.count, gen, critic)
                            }
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadGateway, "generation failed (relay/LLM): ${e.message?.take(160)}"); return@csrfProtect
                }

                // read-merge-write BOTH stores by problemId.
                val prepRepo = jarvis.tutor.TaskPrepRepo(ctx.db)
                val existing = prepRepo.findByTaskId(taskId)
                val problems = (existing?.problemsJson?.let {
                    try { sensorJson.decodeFromString(kotlinx.serialization.builtins.ListSerializer(jarvis.tutor.Problem.serializer()), it) } catch (_: Exception) { emptyList() }
                } ?: emptyList()).associateBy { it.problemId }.toMutableMap()
                val drills = (existing?.drillsJson?.let {
                    try { sensorJson.decodeFromString(kotlinx.serialization.builtins.MapSerializer(kotlinx.serialization.builtins.serializer<String>(), jarvis.tutor.DrillContentDto.serializer()), it) } catch (_: Exception) { emptyMap() }
                } ?: emptyMap()).toMutableMap()
                for (b in result.bundles) { problems[b.problem.problemId] = b.problem; drills[b.problem.problemId] = b.content }

                val now = java.time.Instant.now()
                prepRepo.upsert(jarvis.tutor.TaskPrep(
                    taskId = taskId, generatedAt = now, version = existing?.version ?: 1,
                    problemsJson = sensorJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(jarvis.tutor.Problem.serializer()), problems.values.toList()),
                    drillsJson = sensorJson.encodeToString(kotlinx.serialization.builtins.MapSerializer(kotlinx.serialization.builtins.serializer<String>(), jarvis.tutor.DrillContentDto.serializer()), drills),
                    railJson = existing?.railJson ?: "[]",
                ))
                call.respond(HttpStatusCode.OK, ApiGenerateDrillsReply(
                    taskId = taskId,
                    accepted = result.bundles.map { AcceptedDrill(it.problem.problemId, it.problem.shape ?: shape) },
                    rejectedCount = result.rejectReasons.size,
                    rejectReasons = result.rejectReasons,
                    criticUsed = "relay/claude",
                    generatedAt = now.toString(),
                ))
            }
        }
```

- [ ] **Step 5: Run the test, verify it passes**

Run: `/c/Tools/gradle-8.10/bin/gradle :test --tests "jarvis.tutor.GenerateDrillsRouteTest"`
Expected: PASS. (If the CSRF header mechanism differs, copy the exact approach from `DrillGradeServerSideTest`.)

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/jarvis/web/TutorRoutes.kt src/test/kotlin/jarvis/tutor/GenerateDrillsRouteTest.kt
git commit -m "feat(e3): POST generate-drills — read-merge-write Problem (grade) + DrillContent (render), no clobber"
```

---

## Task 12: `/reprep` kcId-preserving guard

**Files:**
- Modify: `src/main/kotlin/jarvis/web/TutorRoutes.kt:1341-1358` (the `/reprep` upsert)
- Test: `src/test/kotlin/jarvis/tutor/ReprepGuardTest.kt` (create)

- [ ] **Step 1: Write the failing test** (after a kcId-bearing problem exists, `/reprep` must keep it):
```kotlin
// In jarvis.tutor; reuse installFreshTutor/seedSession/seedTask + a fake PdfProblemExtractor seam if present.
// Assert: seed problemsJson with Problem("authored-1", kcIds=["pa-kc-001"]); call /reprep;
// then findByTaskId.problemsJson still contains "authored-1" with kcIds ["pa-kc-001"].
```
_(Write the concrete test mirroring `GenerateDrillsRouteTest` setup. If `/reprep` calls the live OpenRouter extractor, add a seam `internal var reprepExtractor` or stub the network; otherwise gate this test behind the same fake-LLM approach.)_

- [ ] **Step 2: Run it, verify it fails** (reprep currently overwrites `problemsJson` wholesale + resets `drillsJson="{}"`).

Run: `/c/Tools/gradle-8.10/bin/gradle :test --tests "jarvis.tutor.ReprepGuardTest"`
Expected: FAIL — authored problem gone.

- [ ] **Step 3: Make reprep merge.** In the `/reprep` handler, before the `upsert`, load existing and preserve kcId-bearing problems + the existing `drillsJson`:
```kotlin
                val prepRepo = jarvis.tutor.TaskPrepRepo(ctx.db)
                val existing = prepRepo.findByTaskId(taskId)
                val preserved = (existing?.problemsJson?.let {
                    try { jarvis.tutor.TutorTypes.tutorJson.decodeFromString(
                        kotlinx.serialization.builtins.ListSerializer(jarvis.tutor.Problem.serializer()), it) } catch (_: Exception) { emptyList() }
                } ?: emptyList()).filter { it.kcIds.isNotEmpty() }
                val merged = (preserved + problems).associateBy { it.problemId }.values.toList()
                val problemsJson = jarvis.tutor.TutorTypes.tutorJson.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(jarvis.tutor.Problem.serializer()), merged)
                // ... in the upsert, use problemsJson above and drillsJson = existing?.drillsJson ?: "{}"
                prepRepo.upsert(jarvis.tutor.TaskPrep(
                    taskId = taskId, generatedAt = now, version = 1,
                    problemsJson = problemsJson,
                    drillsJson = existing?.drillsJson ?: "{}",
                    railJson = railJsonStr,
                ))
```

- [ ] **Step 4: Run the test, verify it passes**

Run: `/c/Tools/gradle-8.10/bin/gradle :test --tests "jarvis.tutor.ReprepGuardTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/web/TutorRoutes.kt src/test/kotlin/jarvis/tutor/ReprepGuardTest.kt
git commit -m "fix(e3): /reprep preserves kcId-bearing problems + existing drillsJson (no clobber)"
```

---

## Task 13: Required fixture KCs (visual + computational) — corpus stays green

**Files:**
- Create: `content/PA/kcs/pa-kc-fixture-recursion.yaml`, `content/PA/kcs/pa-kc-fixture-compute.yaml`
- Modify: `content/PA/edges.yaml` (prereq edges so the fixtures aren't orphans)

- [ ] **Step 1: Create the visual fixture KC** (`requires_visual: true`, `viz_id: recursion-tree`, `exam_weight: 0.0`, a real verbatim quote from `_sources/pa-lecture-01.md`):
```yaml
id: pa-kc-fixture-recursion
subject: PA
name_ro: "[FIXTURE] Recursivitate"
name_en: "[FIXTURE] Recursion"
cluster: "Fundamentele algoritmilor"
bloom_level: understand
difficulty: 2
time_minutes: 10
exam_weight: 0.0
tier: 2
viz_id: recursion-tree
requires_visual: true
source:
  - doc: pa-lecture-01
    quote: "It does not exists a standard definition for the notion of algorithm."
version: 1
```

- [ ] **Step 2: Create the computational fixture KC** (exercises the self-solve numeric leg; no visual):
```yaml
id: pa-kc-fixture-compute
subject: PA
name_ro: "[FIXTURE] Cost de calcul"
name_en: "[FIXTURE] Computation cost"
cluster: "Fundamentele algoritmilor"
bloom_level: apply
difficulty: 2
time_minutes: 10
exam_weight: 0.0
tier: 2
source:
  - doc: pa-lecture-01
    quote: "It does not exists a standard definition for the notion of algorithm."
version: 1
```

- [ ] **Step 3: Add prereq edges** so neither fixture is an orphan. Append to `content/PA/edges.yaml` under `edges:`:
```yaml
  - kc: pa-kc-fixture-recursion
    prereq: pa-kc-001
    rationale: "[FIXTURE] recursion demonstrator depends on the notion of an algorithm."
  - kc: pa-kc-fixture-compute
    prereq: pa-kc-001
    rationale: "[FIXTURE] computation-cost demonstrator depends on the notion of an algorithm."
```

- [ ] **Step 4: Validate the live corpus**

Run: `/c/Tools/gradle-8.10/bin/gradle validateContent`
Expected: `OK` — exam_weight still sums to 1.00 (fixtures are 0.0); no orphans (edges to `pa-kc-001`); `recursion-tree` resolves in `viz-ids.yaml`; quote is verbatim in `_sources/pa-lecture-01.md` (warning-only for span-less, not error). If a quote-not-found error appears, copy an exact line from `content/PA/_sources/pa-lecture-01.md`.

- [ ] **Step 5: Commit**

```bash
git add content/PA/kcs/pa-kc-fixture-recursion.yaml content/PA/kcs/pa-kc-fixture-compute.yaml content/PA/edges.yaml
git commit -m "test(e3): required visual + computational fixture KCs (weight 0, prereq-edged, grounded)"
```

---

## Task 14: Track A — generate → grade → mastery-moves E2E (server-side, deterministic)

**Files:**
- Test: `src/test/kotlin/jarvis/tutor/E3GenerateGradeSmokeTest.kt` (create)

- [ ] **Step 1: Write the E2E** (generate via fakes → then POST `/api/v1/drill/grade` with NO client kcIds/canonicalAnswer → mastery observation moves on the right KC). Reuse the `installFreshTutor`/`seedSession`/`seedTask` helpers + the `FakeGraderLlm` returning a coherent all-true rubric (see `DrillGradeServerSideTest`):
```kotlin
package jarvis.tutor
// imports as in GenerateDrillsRouteTest + DrillGradeServerSideTest
class E3GenerateGradeSmokeTest {
    @Test fun `generated drill grades and records mastery on its KC`() = testApplication {
        val ctx = installFreshTutor(this)
        val (userId, sid) = seedSession(ctx)
        seedTask(ctx, userId, "task-1")
        drillKcLookup = { _, _ -> jarvis.content.KnowledgeConcept("pa-kc-fixture-compute","PA","a","a","c","apply",1,1,0.0,2) }
        val gen = object : jarvis.Llm { var n = 0
            override suspend fun complete(m: List<jarvis.ChatMessage>, t: Int, r: String?) =
                (if (n++ == 0) """{"statement":"Compute 6*7.","canonical_answer":"42","rubric_items":["ok"],"worked":"42","definition":"d","check":"c","expected_answer_hint":"42"}""" else "42") to "g" }
        drillGeneratorLlmFactory = { gen }
        drillCriticLlmFactory = { object : jarvis.Llm { override suspend fun complete(m: List<jarvis.ChatMessage>, t: Int, r: String?) = """{"confidence":0.9,"grounded":true,"leak":false,"solvable":true}""" to "claude" } }

        // 1. generate
        client.post("/api/v1/task/task-1/generate-drills") {
            header(HttpHeaders.Cookie, "jarvis_session=$sid"); header("X-CSRF","1")
            contentType(ContentType.Application.Json); setBody("""{"kcId":"pa-kc-fixture-compute","shape":"computational","count":1}""")
        }
        // 2. grade the generated drill — client sends NO kcIds / NO canonicalAnswer
        drillGraderLlmFactory = { object : jarvis.Llm { override suspend fun complete(m: List<jarvis.ChatMessage>, t: Int, r: String?) =
            """{"correct":true,"rubric":{"ok":true},"score":1.0,"misconception":null,"elaborated_feedback":"good"}""" to "fake-grader" } }
        assertNull(KcMasteryRepo(ctx.db).get(userId, "pa-kc-fixture-compute"))
        val g = client.post("/api/v1/drill/grade") {
            header(HttpHeaders.Cookie, "jarvis_session=$sid"); header("X-CSRF","1")
            contentType(ContentType.Application.Json)
            setBody("""{"taskId":"task-1","problemId":"gen-pa-kc-fixture-compute-0","problemStatement":"Compute 6*7.","userAttempt":"42","expectedAnswerHint":"42"}""")
        }
        assertEquals(HttpStatusCode.OK, g.status)
        val m = KcMasteryRepo(ctx.db).get(userId, "pa-kc-fixture-compute")
        assertNotNull(m); assertEquals(1, m.observations)
    }
    @AfterEach fun reset() { drillGeneratorLlmFactory = { jarvis.OpenRouterChatLlm() }; drillCriticLlmFactory = { jarvis.RelayLlm() }; drillGraderLlmFactory = { jarvis.OpenRouterChatLlm() }; drillKcLookup = { _,_ -> null } }
}
```

- [ ] **Step 2: Run it, verify it fails, then passes** (it should pass once Tasks 9-11 are in; if red, debug the seam wiring).

Run: `/c/Tools/gradle-8.10/bin/gradle :test --tests "jarvis.tutor.E3GenerateGradeSmokeTest"`
Expected: PASS — mastery observation == 1 on the generated drill's KC, with the client trusting nothing.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/jarvis/tutor/E3GenerateGradeSmokeTest.kt
git commit -m "test(e3): Track A E2E — generated drill grades server-side + records mastery on its KC"
```

---

## Task 15: Track B — Playwright paint + interaction smoke (deploy-confidence gate, P2)

**Files:**
- Create: `tutor-web/playwright.config.ts`, `tutor-web/e2e/drill-viz-paint.spec.ts`
- Modify: `tutor-web/package.json` (add `@playwright/test` + scripts)

The vitest `DrillStack.test.tsx` (Task 6) is the primary anti-ghost gate (it renders the real component with real data). This task adds a real-browser confirmation against the dev server with the prep `fetch` mocked at the network layer (no backend/DB needed).

- [ ] **Step 1: Install Playwright**

Run: `cd tutor-web && npm i -D @playwright/test && npx playwright install chromium`
Add to `package.json` scripts: `"e2e": "playwright test"`.

- [ ] **Step 2: Config** `tutor-web/playwright.config.ts`:
```typescript
import { defineConfig } from "@playwright/test";
export default defineConfig({
  testDir: "./e2e",
  use: { baseURL: "http://localhost:5173", locale: "ro-RO", timezoneId: "Europe/Bucharest" },
  webServer: { command: "npm run dev", url: "http://localhost:5173", reuseExistingServer: true },
});
```

- [ ] **Step 3: Write the paint+smoke spec** `tutor-web/e2e/drill-viz-paint.spec.ts` (intercept the prep GET, return a `vizId` drill, assert paint + zero 4xx/5xx + no error text):
```typescript
import { test, expect } from "@playwright/test";

test("routed viz + drill card paint on the real tutor surface, no errors", async ({ page }) => {
  const bad: number[] = [];
  page.on("response", (r) => { if (r.status() >= 400) bad.push(r.status()); });

  await page.route("**/api/v1/tasks/*/prep", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({
      taskId: "task-1", generatedAt: "now", version: 1,
      problemsJson: JSON.stringify([{ problemId: "p1", page: 0, statement: "fib(5)?", shape: "analysis-trace", vizId: "recursion-tree" }]),
      drillsJson: JSON.stringify({ p1: { drill: "fib(5)?", worked: "…", definition: "recursion", check: "fib(4)?", expectedAnswerHint: "5", vizId: "recursion-tree" } }),
      railJson: "[]",
    }) }));

  await page.goto("/tutor/task-1");  // confirm the real tutor route path (grep main.tsx/App.tsx if different)
  await expect(page.getByTestId("drill-card").first()).toBeVisible();
  await expect(page.getByTestId("routed-viz-recursion-tree")).toBeVisible();
  await expect(page.getByTestId("recursion-tree-root")).toBeVisible();
  await expect(page.locator("body")).not.toContainText(/404|not found|error/i);
  expect(bad, `4xx/5xx responses: ${bad}`).toHaveLength(0);
});
```

- [ ] **Step 4: Run it**

Run: `cd tutor-web && npm run e2e`
Expected: PASS — both testids visible, no 4xx/5xx, no error text. If the tutor route path differs, grep `main.tsx`/`App.tsx` for the task route and fix `page.goto`. If auth gates the surface, mock the session/prereq calls the same way.

- [ ] **Step 5: Commit**

```bash
git add tutor-web/playwright.config.ts tutor-web/e2e/drill-viz-paint.spec.ts tutor-web/package.json tutor-web/package-lock.json
git commit -m "test(e3): Track B — Playwright paint + interaction smoke on the real drill surface"
```

---

## Final verification

- [ ] **Full backend suite**

Run: `/c/Tools/gradle-8.10/bin/gradle :test`
Expected: all green (844 baseline + the new E3 tests). Re-run the two known-flaky tests in isolation if they fail (BRIDGE-HEAD).

- [ ] **Content gate**

Run: `/c/Tools/gradle-8.10/bin/gradle validateContent`
Expected: `OK`.

- [ ] **Frontend suite + typecheck + e2e**

Run: `cd tutor-web && npx vitest run && npx tsc --noEmit && npm run e2e`
Expected: all green.

- [ ] **Acceptance recap (both tracks):** Track A — a generated drill grades server-side and moves mastery with the client trusting nothing (Task 14). Track B — the drill + its routed viz paint on the real surface with zero 4xx/5xx (Tasks 6 + 15). Safeguards — leak/self-solve/critic reject paths covered (Task 9). Anti-clobber — generate + reprep preserve existing kcId drills (Tasks 11, 12).
