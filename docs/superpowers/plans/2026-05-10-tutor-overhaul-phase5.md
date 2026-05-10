# Tutor Overhaul — Phase 5 (Corpus Expansion) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development.

**Goal:** Grow `_extras/<subject>/` corpus via re-runnable Playwright crawler against fiimaterials.web.app. Stub `CourseScraper` Kotlin interface so Phase 6 has the seam to plug per-faculty scrapers into. Re-ingest knowledge graph after crawl.

**Architecture:** Standalone Node script (`tools/extract-fiimaterials.mjs`) drives Playwright headless. SHA-256 dedup against existing `_extras/<subject>/extracted/` so re-runs only fetch new content. Kotlin-side `CourseScraper` interface + `ManualEntryScraper` placeholder lives in `jarvis.tutor.taskdetect` package (Phase 6 plugs subject-specific scrapers into the same interface). `.env.example` documents URL-slot env vars the user fills when ready.

**Tech Stack:** Node 20+ ESM, Playwright Chromium, sha256 (Node `crypto`). Kotlin interface only — no new runtime deps.

**Source spec:** `docs/superpowers/specs/2026-05-10-tutor-overhaul-design.md` § Phase 5 (lines 202-227).

**Scope discipline:**
- The actual full corpus fetch happens later (best-effort, user-driven). Phase 5 ships the scaffolding + verifies dry-run + verifies one happy-path fetch.
- Course-page URLs deferred per spec — user-provided.

---

## File Structure

**Created:**
- `tools/package.json` — Node deps for the crawler (playwright only).
- `tools/extract-fiimaterials.mjs` — the crawler.
- `tools/extract-fiimaterials.test.mjs` — minimal Node tests (URL classification + dedup).
- `src/main/kotlin/jarvis/tutor/taskdetect/CourseScraper.kt` — Kotlin interface + ManualEntryScraper stub.
- `src/test/kotlin/jarvis/tutor/taskdetect/CourseScraperTest.kt` — verifies the stub returns empty + the data classes round-trip.

**Modified:**
- `.env.example` — add 5 new env-var slots (one per subject).
- `tools/deploy.sh` (optional) — add a comment pointing at `gradle ingestCorpus` for post-crawl reingest. No code change required if just documenting in commit.

---

## Task 1: Phase 5 plan committed

- [ ] **Step 1: Commit**

```bash
git add docs/superpowers/plans/2026-05-10-tutor-overhaul-phase5.md
git commit -m "$(cat <<'EOF'
Phase 5 plan: Corpus expansion (fiimaterials crawler + CourseScraper stub)

Per spec § Phase 5. Phase 4 shipped + 4 gates passed at 60d4136.
Phase 5: Playwright crawler against fiimaterials.web.app with sha256
dedup; CourseScraper Kotlin interface stub for Phase 6 to plug per-
faculty scrapers into; .env.example URL slots for user-provided
sources.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: §5.1 — Playwright crawler

**Files:**
- Create: `tools/package.json`
- Create: `tools/extract-fiimaterials.mjs`
- Create: `tools/extract-fiimaterials.test.mjs`

The crawler exposes 3 modes:
- `node tools/extract-fiimaterials.mjs --dry-run` — print URLs that would be fetched without downloading.
- `node tools/extract-fiimaterials.mjs` — actually download new PDFs (skips already-downloaded by sha256).
- `node tools/extract-fiimaterials.mjs --root <path>` — override the destination directory (default `/opt/jarvis/data/archival/_extras` on VPS, `./archival/_extras` locally).

Subject classification: parse the URL path. Buckets `PA`, `PS`, `POO`, `ALO`, `SO`, `RC`, `OTHER`. Conservative — anything ambiguous lands in `OTHER`.

- [ ] **Step 1: `tools/package.json`**

Create:

```json
{
  "name": "jarvis-tools",
  "version": "0.0.0",
  "type": "module",
  "private": true,
  "scripts": {
    "crawl": "node extract-fiimaterials.mjs",
    "crawl:dry": "node extract-fiimaterials.mjs --dry-run",
    "test:tools": "node --test extract-fiimaterials.test.mjs"
  },
  "devDependencies": {
    "playwright": "^1.49.0"
  }
}
```

- [ ] **Step 2: Install + browser binaries (one-time setup)**

```bash
cd tools && npm install
# This pulls playwright. Chromium is bundled.
```

If `npm install` triggers a download of browsers (Playwright sometimes does post-install), let it run; ~150MB. If the install fails (firewall / restricted network), document the failure as a backlog item — the crawler can still be inspected without runtime deps.

- [ ] **Step 3: Write `extract-fiimaterials.mjs`**

```js
#!/usr/bin/env node
// fiimaterials.web.app crawler. Re-runnable via sha256 dedup.
//
// Usage:
//   node extract-fiimaterials.mjs              # download new PDFs
//   node extract-fiimaterials.mjs --dry-run    # list URLs only
//   node extract-fiimaterials.mjs --root <p>   # override dest dir
//
// Crawl strategy: depth-1 BFS from start URL; collect <a href*=".pdf">
// links; dedup by URL host+path; classify by URL path keywords; download
// + sha256 + write under root/<SUBJECT>/extracted/<sha8>/<filename>.pdf
// with sidecar meta.json.
//
// Conservative: doesn't follow redirects to non-fiimaterials hosts;
// access-controlled pages (login walls, 401/403) are logged + skipped.

import { chromium } from "playwright";
import { createHash } from "node:crypto";
import { mkdir, writeFile, readdir, stat } from "node:fs/promises";
import { existsSync } from "node:fs";
import path from "node:path";

const START_URL = "https://fiimaterials.web.app/";
const HOST_FILTER = /(^|\.)fiimaterials\.web\.app$/i;
const MAX_PAGES = 200;
const FETCH_TIMEOUT_MS = 30_000;

const args = process.argv.slice(2);
const DRY_RUN = args.includes("--dry-run");
const rootIdx = args.indexOf("--root");
const ROOT = rootIdx >= 0 ? args[rootIdx + 1] : (process.env.JARVIS_ARCHIVAL_ROOT ?? "./archival/_extras");

export function classifySubject(url) {
  const u = url.toLowerCase();
  if (/\bpa\b|programare-?avansata|study-?guide/.test(u)) return "PA";
  if (/\bps\b|probabilitati|statistica/.test(u)) return "PS";
  if (/\bpoo\b|programare-?orientata/.test(u)) return "POO";
  if (/\balo\b|algoritmica|optimizare/.test(u)) return "ALO";
  if (/\bso\b|sisteme-?de-?operare|linux/.test(u)) return "SO";
  if (/\brc\b|retele|networking/.test(u)) return "RC";
  return "OTHER";
}

export function classifyKind(url) {
  const u = url.toLowerCase();
  if (/curs|lecture|c\d/.test(u)) return "course";
  if (/seminar|sem\d/.test(u)) return "seminar";
  if (/lab|laborator/.test(u)) return "lab";
  if (/tema|hw|homework/.test(u)) return "hw";
  if (/exam|partial|final|colocviu/.test(u)) return "exam";
  return "other";
}

export function sha256Hex(buf) {
  return createHash("sha256").update(buf).digest("hex");
}

async function existingHashes(root) {
  // Walk root for any meta.json with a sha. Returns Set<sha>.
  const out = new Set();
  if (!existsSync(root)) return out;
  async function walk(dir) {
    let entries;
    try { entries = await readdir(dir, { withFileTypes: true }); } catch (_) { return; }
    for (const e of entries) {
      const p = path.join(dir, e.name);
      if (e.isDirectory()) await walk(p);
      else if (e.name === "meta.json") {
        try {
          const txt = await import("node:fs/promises").then(fs => fs.readFile(p, "utf8"));
          const j = JSON.parse(txt);
          if (j.sha256) out.add(j.sha256);
        } catch (_) {}
      }
    }
  }
  await walk(root);
  return out;
}

async function discoverPdfLinks(page, startUrl) {
  // Depth-1 BFS: load each page, collect anchors. Avoid mailto:/data: etc.
  const seen = new Set([startUrl]);
  const queue = [startUrl];
  const pdfs = new Set();
  let visited = 0;
  while (queue.length > 0 && visited < MAX_PAGES) {
    const url = queue.shift();
    try {
      await page.goto(url, { timeout: FETCH_TIMEOUT_MS, waitUntil: "domcontentloaded" });
    } catch (e) {
      console.error(`[crawl] skip (${url}): ${e.message}`);
      continue;
    }
    visited++;
    const hrefs = await page.$$eval("a[href]", els => els.map(a => a.getAttribute("href")));
    for (const h of hrefs) {
      if (!h) continue;
      let abs;
      try { abs = new URL(h, url).toString(); } catch (_) { continue; }
      if (!abs.startsWith("http")) continue;
      const u = new URL(abs);
      if (!HOST_FILTER.test(u.host)) continue;
      if (abs.toLowerCase().endsWith(".pdf")) {
        pdfs.add(abs);
      } else if (!seen.has(abs)) {
        seen.add(abs);
        queue.push(abs);
      }
    }
  }
  return [...pdfs];
}

async function downloadPdf(page, url) {
  const ctx = page.context();
  const resp = await ctx.request.get(url, { timeout: FETCH_TIMEOUT_MS });
  if (!resp.ok()) throw new Error(`HTTP ${resp.status()}`);
  return await resp.body();
}

async function run() {
  const browser = await chromium.launch();
  const ctx = await browser.newContext();
  const page = await ctx.newPage();
  const before = await existingHashes(ROOT);
  console.log(`[crawl] existing hashes: ${before.size}`);
  console.log(`[crawl] dry-run: ${DRY_RUN}`);
  console.log(`[crawl] root: ${ROOT}`);

  let pdfUrls;
  try {
    pdfUrls = await discoverPdfLinks(page, START_URL);
  } catch (e) {
    console.error(`[crawl] discovery failed: ${e.message}`);
    await browser.close();
    process.exit(1);
  }
  console.log(`[crawl] discovered ${pdfUrls.length} PDF link(s)`);

  let fetched = 0, skipped = 0, errored = 0;
  for (const url of pdfUrls) {
    if (DRY_RUN) {
      const subject = classifySubject(url);
      const kind = classifyKind(url);
      console.log(`[dry] ${subject}/${kind}  ${url}`);
      continue;
    }
    try {
      const body = await downloadPdf(page, url);
      const sha = sha256Hex(body);
      if (before.has(sha)) {
        skipped++;
        continue;
      }
      const subject = classifySubject(url);
      const kind = classifyKind(url);
      const filename = decodeURIComponent(url.split("/").pop() || "untitled.pdf").slice(-128);
      const sha8 = sha.slice(0, 8);
      const dir = path.join(ROOT, subject, "extracted", sha8);
      await mkdir(dir, { recursive: true });
      await writeFile(path.join(dir, filename), body);
      await writeFile(path.join(dir, "meta.json"), JSON.stringify({
        sourceUrl: url, fetchedAt: new Date().toISOString(),
        sha256: sha, subject, kind,
      }, null, 2));
      console.log(`[fetch] ${subject}/${kind}  ${filename}  (${(body.length / 1024).toFixed(1)} KB)`);
      fetched++;
    } catch (e) {
      console.error(`[error] ${url}: ${e.message}`);
      errored++;
    }
  }

  await browser.close();
  console.log(`[crawl] done. fetched=${fetched} skipped=${skipped} errored=${errored} dry=${DRY_RUN}`);
}

if (import.meta.url === `file://${process.argv[1].replace(/\\/g, "/")}`) {
  run().catch(e => {
    console.error(`[crawl] fatal: ${e.message}`);
    process.exit(1);
  });
}
```

- [ ] **Step 4: Write `extract-fiimaterials.test.mjs`**

```js
import { test } from "node:test";
import assert from "node:assert/strict";
import { classifySubject, classifyKind, sha256Hex } from "./extract-fiimaterials.mjs";

test("classifySubject buckets URLs into known faculty subjects", () => {
  assert.equal(classifySubject("https://fiimaterials.web.app/PA/curs1.pdf"), "PA");
  assert.equal(classifySubject("https://fiimaterials.web.app/programare-avansata/x.pdf"), "PA");
  assert.equal(classifySubject("https://fiimaterials.web.app/PS/sem.pdf"), "PS");
  assert.equal(classifySubject("https://fiimaterials.web.app/probabilitati/x.pdf"), "PS");
  assert.equal(classifySubject("https://fiimaterials.web.app/POO/lab1.pdf"), "POO");
  assert.equal(classifySubject("https://fiimaterials.web.app/ALO/exam.pdf"), "ALO");
  assert.equal(classifySubject("https://fiimaterials.web.app/SO/linux.pdf"), "SO");
  assert.equal(classifySubject("https://fiimaterials.web.app/RC/networking.pdf"), "RC");
  assert.equal(classifySubject("https://fiimaterials.web.app/random/x.pdf"), "OTHER");
});

test("classifyKind buckets by URL keyword", () => {
  assert.equal(classifyKind("https://x/curs1.pdf"), "course");
  assert.equal(classifyKind("https://x/lecture-3.pdf"), "course");
  assert.equal(classifyKind("https://x/seminar2.pdf"), "seminar");
  assert.equal(classifyKind("https://x/laborator-5.pdf"), "lab");
  assert.equal(classifyKind("https://x/tema-1.pdf"), "hw");
  assert.equal(classifyKind("https://x/partial-2021.pdf"), "exam");
  assert.equal(classifyKind("https://x/random.pdf"), "other");
});

test("sha256Hex matches known vector", () => {
  assert.equal(sha256Hex(Buffer.from("hello", "utf8")),
    "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
});
```

- [ ] **Step 5: Run tests**

```bash
cd tools
npm install   # if not already
node --test extract-fiimaterials.test.mjs
```
Expected: 3 tests PASS.

- [ ] **Step 6: Optional dry-run smoke test (skip if Playwright install failed)**

```bash
node extract-fiimaterials.mjs --dry-run
```
Expected: prints discovered PDF URLs with subject/kind classification. Logs `[crawl] done. fetched=0 ...`. The actual fetch path stays untouched.

If the network is restricted in this environment + the dry run fails to reach fiimaterials.web.app, document in the commit message that the crawler ships unfetched and recommend the user run on the VPS.

- [ ] **Step 7: Commit**

```bash
git add tools/package.json tools/package-lock.json tools/extract-fiimaterials.mjs tools/extract-fiimaterials.test.mjs
git commit -m "$(cat <<'EOF'
Phase 5.1: fiimaterials.web.app crawler (Playwright + sha256 dedup)

tools/extract-fiimaterials.mjs walks fiimaterials.web.app depth-1
from / via DOM <a href> scraping; downloads .pdf links; sha256-dedups
against existing _extras/<subject>/extracted/<sha8>/meta.json
sidecars; classifies by URL keyword into PA/PS/POO/ALO/SO/RC/OTHER
and course/seminar/lab/hw/exam/other.

--dry-run lists URLs without fetching. --root overrides destination.
JARVIS_ARCHIVAL_ROOT env also honored.

3 Node tests in extract-fiimaterials.test.mjs (subject classifier,
kind classifier, sha256 vector). Run via `node --test` from tools/.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: §5.3 — `CourseScraper` Kotlin interface + stub + .env slots

**Files:**
- Create: `src/main/kotlin/jarvis/tutor/taskdetect/CourseScraper.kt`.
- Create: `src/test/kotlin/jarvis/tutor/taskdetect/CourseScraperTest.kt`.
- Modify: `.env.example` — append URL slots.

The interface is the seam Phase 6 plugs into. Phase 5 only ships the contract + a no-op `ManualEntryScraper` so `JarvisToolset`-or-equivalent imports don't break when Phase 6 lands.

- [ ] **Step 1: Create the Kotlin file**

```kotlin
package jarvis.tutor.taskdetect

import java.time.Instant

/**
 * Phase 5.3 stub. Per-faculty / per-subject scrapers will implement
 * this; Phase 6 [TaskDetector] aggregates results across all
 * registered scrapers + dedups by externalId.
 *
 * Each scraper is responsible for one source (one course page, one
 * VLE site, one calendar). Returning an empty list is fine when
 * nothing is due.
 */
interface CourseScraper {
    /** Stable id for the scraper itself (e.g. "uaic-fii-pa-2025"). */
    val sourceId: String

    /**
     * Fetch the source + parse out current homework / lecture-due /
     * exam announcements. Idempotent. Best-effort: a single source
     * failing should not crash the crawl run; throw and the orchestrator
     * logs + skips.
     */
    suspend fun discover(): List<DetectedCourseItem>
}

/**
 * What a scraper returns. Phase 6 [TaskDetector] turns this into a
 * persisted Task row, dedupping by (sourceId, externalId).
 */
data class DetectedCourseItem(
    val sourceId: String,
    /** Stable id within source (URL, ICS UID, etc) for dedup. */
    val externalId: String,
    val subject: String,
    val title: String,
    val deadline: Instant,
    /** Optional raw URL or path the user can visit. */
    val sourceUrl: String? = null,
    /** Optional kind: "homework" | "exam" | "reading" | etc. Free-form. */
    val kind: String? = null,
)

/**
 * No-op placeholder so wiring code in Phase 6 + later phases can
 * reference [CourseScraper] without forcing a real scraper to exist
 * for every subject yet. Returns empty list always.
 *
 * Real scrapers (`UaicFiiPaScraper`, `UaicFiiPsScraper`, etc) land
 * in Phase 6 once the user provides the URLs.
 */
class ManualEntryScraper(override val sourceId: String = "manual") : CourseScraper {
    override suspend fun discover(): List<DetectedCourseItem> = emptyList()
}
```

- [ ] **Step 2: Create test**

```kotlin
package jarvis.tutor.taskdetect

import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CourseScraperTest {
    @Test
    fun `ManualEntryScraper discover returns empty list`() = runBlocking {
        val s = ManualEntryScraper()
        assertEquals("manual", s.sourceId)
        assertTrue(s.discover().isEmpty())
    }

    @Test
    fun `DetectedCourseItem holds the spec-required fields`() {
        val now = Instant.now()
        val item = DetectedCourseItem(
            sourceId = "uaic-fii-pa-2025",
            externalId = "tema-5",
            subject = "PA",
            title = "Tema 5",
            deadline = now.plusSeconds(86400),
            sourceUrl = "https://example.com/pa/tema-5",
            kind = "homework",
        )
        assertEquals("uaic-fii-pa-2025", item.sourceId)
        assertEquals("tema-5", item.externalId)
        assertEquals("PA", item.subject)
        assertEquals("Tema 5", item.title)
        assertEquals("homework", item.kind)
    }

    @Test
    fun `custom sourceId on ManualEntryScraper`() = runBlocking {
        val s = ManualEntryScraper(sourceId = "user-custom-source")
        assertEquals("user-custom-source", s.sourceId)
        assertTrue(s.discover().isEmpty())
    }
}
```

- [ ] **Step 3: Append URL slots to .env.example**

Find or open `.env.example`. Append at the bottom (above any final blank line):

```
# Phase 5.3: per-subject course-page scraper URLs. User fills these
# when known. Empty/unset → ManualEntryScraper takes over (returns
# nothing). Phase 6 wires real scrapers behind these env vars.
JARVIS_COURSE_PAGE_PA=
JARVIS_COURSE_PAGE_PS=
JARVIS_COURSE_PAGE_POO=
JARVIS_COURSE_PAGE_ALO=
JARVIS_COURSE_PAGE_SO=
JARVIS_COURSE_PAGE_RC=
```

- [ ] **Step 4: Run tests**

```bash
gradle :test --tests "jarvis.tutor.taskdetect.CourseScraperTest" -i
gradle :test
```
Expected: 3 new PASS. Backend total: 563 + 3 = 566.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/tutor/taskdetect/CourseScraper.kt src/test/kotlin/jarvis/tutor/taskdetect/CourseScraperTest.kt .env.example
git commit -m "$(cat <<'EOF'
Phase 5.3: CourseScraper interface stub + .env URL slots

Kotlin interface in jarvis.tutor.taskdetect package (Phase 6
TaskDetector aggregates these). DetectedCourseItem data class.
ManualEntryScraper no-op placeholder so Phase 6 wiring can reference
the contract before real scrapers exist.

.env.example adds JARVIS_COURSE_PAGE_{PA,PS,POO,ALO,SO,RC} slots —
empty/unset means ManualEntryScraper takes over.

3 backend tests. Backend tests 563 → 566.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: §5.2 — KnowledgeGraph reingest documentation

No code; reingest is operational. Document the runbook so future ops know what to do post-crawl.

- [ ] **Step 1: Append a short note**

Append to `tools/extract-fiimaterials.mjs` (top-of-file comment block) OR add a new section in `docs/superpowers/specs/2026-05-10-tutor-overhaul-backlog.md` § Phase 5:

> After running the crawler, rebuild the knowledge graph on the VPS so the new PDFs are indexed:
> ```
> ssh root@46.247.109.91 "set -a; source /opt/jarvis/.env; set +a; /opt/jarvis/jarvis-kotlin/bin/jarvis-kotlin ingest-corpus"
> ```
> The reingest is idempotent on existing nodes; new files become new nodes + edges.

- [ ] **Step 2: Commit**

```bash
git add docs/superpowers/specs/2026-05-10-tutor-overhaul-backlog.md
git commit -m "Phase 5.2: KnowledgeGraph reingest runbook in backlog"
```

---

## Task 5: Code Gate — push + CI green

```bash
git push origin main
```

Wait for CI completion. Note: CI doesn't run the Node tools tests by default unless the workflow is updated. If the existing CI workflow only covers backend + frontend + daemon, that's OK — the Node tests are run manually via `node --test`. Optionally add a `tools` job to `.github/workflows/test.yml` if it's a quick win; otherwise document in commit message.

---

## Task 6: Live Gate — no deploy needed

Phase 5 doesn't ship a frontend bundle change OR a backend route change. The CourseScraper interface is referenced by Phase 6 only. Skip rebuild + deploy — nothing visible changes for the user.

Still verify healthz to confirm we didn't break anything:

```bash
curl -sS https://corgflix.duckdns.org/healthz   # still ok
curl -sS https://corgflix.duckdns.org/tutor/ | grep -oE 'index-[A-Za-z0-9]+\.js' | head -1
```

The bundle hash should be the same as Phase 4's `index-D7M45CLu.js`.

---

## Task 7: Playwright Gate — corpus crawler dry-run

Run the crawler dry-run. The deliverable is "URLs discovered + subject classification works" — no actual fetch required.

```bash
cd tools
node extract-fiimaterials.mjs --dry-run 2>&1 | tee /tmp/phase5-crawl-dryrun.log
```

Expected: stdout shows `[crawl] discovered N PDF link(s)` (N likely > 0 if fiimaterials.web.app is reachable). For each PDF, `[dry] <SUBJECT>/<KIND>  <url>` line. Final summary `[crawl] done. fetched=0 skipped=0 errored=0 dry=true`.

If network is restricted: the script logs `[crawl] discovery failed: ...` and exits 1. Document this in the gate report — the script ships, full crawl deferred to user-with-network.

---

## Task 8: UX-Playbook Gate — n/a for Phase 5

Phase 5 has no user-visible surface change. No UX-Playbook agent dispatch needed; explicit `n/a`. Document in final review.

---

## Task 9: Final review

DoD:
- Backend tests 566 (563 + 3 CourseScraperTest).
- Frontend tests 106 (no new frontend tests).
- Daemon untouched (16).
- New Node tests 3 (extract-fiimaterials.test.mjs).
- CI green for the latest commit.
- Live healthz `ok` (no deploy needed; bundle unchanged).
- Crawler dry-run executable + tests pass.
- `CourseScraper` interface present + ManualEntryScraper stub returns empty.
- `.env.example` has the 6 new URL slots.

Spawn final reviewer subagent for the survey.

---

## Out of scope (do NOT do this in Phase 5)

- TaskDetector aggregation / DB-persisted detected tasks — Phase 6.
- ActiveTaskDashboard component — Phase 6.
- Real `UaicFiiPaScraper` / per-subject implementations — Phase 6 (or later, after user provides URLs).
- Inline reference popups, Knowledge Ledger, sympy — Phase 7.
- Cron-driven re-scrape (the actual cron schedule + invocation) — Phase 8.
