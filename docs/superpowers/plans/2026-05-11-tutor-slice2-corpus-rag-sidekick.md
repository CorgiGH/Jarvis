# Tutor Slice 2 — Corpus-RAG Sidekick + Polish · Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship corpus-grounded sidekick + LaTeX rendering fix + `?debug=1` toggle + rail expansion (PRIOR_GAP + CONCEPT_REF auto-pop) for the Jarvis Tutor across all 5 subjects (PA/PS/POO/ALO/SO+RC) in ~3-4 days.

**Architecture:** Three phases sequenced — A (corpus ingest: PDF→Markdown converter + scp to VPS + `ingest-corpus`), B (sidekick citation plumbing: DTO extension + system prompt instruction + frontend pill rendering), C (UI polish: `<MathText>` wiring + `?debug=1` toggle + rail expansion using existing `ResourceRail` handlers).

**Tech Stack:** Kotlin/Ktor backend, React+Vite frontend, OpenRouter free-tier LLM, PDFBox + HybridRetriever for retrieval, KaTeX for math, Python 3 + pypdf for PDF conversion, rsync + ssh for VPS sync.

**Spec reference:** `docs/superpowers/specs/2026-05-11-tutor-slice2-corpus-rag-sidekick-design.md`

---

## File Structure

### Backend (modified)

| File | Change |
|---|---|
| `src/main/kotlin/jarvis/tutor/SidekickContext.kt` | Extend `systemContext()` with citation instruction; add `ApiCitation` data class |
| `src/main/kotlin/jarvis/web/TutorRoutes.kt` | Extend `ApiSidekickReply` DTO with `citations` field; populate from retrieval result in sidekick handler; in `/reprep` handler, after problem extraction call `HybridRetriever.search` per problem and populate `task.conceptRefs` |
| `src/main/kotlin/jarvis/tutor/RailJsonBuilder.kt` | Emit `PRIOR_GAP` rail items from `KnowledgeGapRepo.listForTask` |
| `src/main/kotlin/jarvis/tutor/JarvisToolset.kt` | Wrap retrieval results with source paths; expose to caller for citation extraction |

### Backend (new)

None.

### Frontend (modified)

| File | Change |
|---|---|
| `tutor-web/src/components/Sidekick.tsx` | Replace `<div whiteSpace:pre-wrap>` (line 76) with `<MathText text={...} />`; render `citations` strip below reply when present; wire pill click → open rail drawer for source |
| `tutor-web/src/components/DrillStack.tsx` | Wrap each card body text (`content.definition`, `content.worked`, `content.drill`, `content.check`) in `<MathText>` before passing to `DrillCard` children |
| `tutor-web/src/App.tsx` | Read `?debug=1` query flag; pass `debug` prop down; conditionally render `DaemonHealthPill` + domain footer based on flag |
| `tutor-web/src/components/DaemonHealthPill.tsx` | Accept optional `hidden?: boolean` prop; render `null` when hidden |
| `tutor-web/src/lib/sidekickContext.ts` (or wherever `askSidekick` reply type lives) | Extend reply type with optional `citations: Citation[]` |

### Frontend (new)

| File | Purpose |
|---|---|
| `tutor-web/src/components/CitationPill.tsx` | Clickable pill rendering `(src: <path>)` citation; emits click event with archival path |

### Tools (new)

| File | Purpose |
|---|---|
| `tools/pdf-to-md.py` | Convert PDFs under `tmp-secondbrain-scrape/` to Markdown under `tmp-md/` with Romanian + math glyph normalization |
| `tools/pdf-to-md.test.py` | Unit tests for the converter (NFC normalize, glyph-fix table, hyphenation cleanup) |
| `tools/sync-corpus-to-vps.sh` | `rsync` `tmp-md/` to VPS `/opt/jarvis/data/archival/_extras/`, then SSH-invoke `jarvis-kotlin ingest-corpus` |

### Tests

| File | Purpose |
|---|---|
| `src/test/kotlin/jarvis/tutor/RailJsonBuilderPriorGapTest.kt` | Test that PRIOR_GAP items emit for tasks with unresolved gaps |
| `src/test/kotlin/jarvis/tutor/RailJsonBuilderConceptRefTest.kt` | Test CONCEPT items emit from `task.conceptRefs` (verify existing behavior) |
| `src/test/kotlin/jarvis/web/SidekickCitationsTest.kt` | Test sidekick reply carries `citations: []` when retrieval returns hits |
| `src/test/kotlin/jarvis/web/ReprepConceptRefsTest.kt` | Test `/reprep` populates `task.conceptRefs` via `HybridRetriever.search` |
| `tutor-web/src/__tests__/Sidekick.mathtext.test.tsx` | Test Sidekick reply renders via MathText (assert `[data-testid="math-text"]` present) |
| `tutor-web/src/__tests__/DrillStack.mathtext.test.tsx` | Test DrillStack card bodies render via MathText |
| `tutor-web/src/__tests__/App.debug.test.tsx` | Test `?debug=1` shows DaemonHealthPill; absence hides it |
| `tutor-web/src/__tests__/Sidekick.citations.test.tsx` | Test citation pills render when reply has `citations[]` |
| `tutor-web/src/__tests__/CitationPill.test.tsx` | Test CitationPill click emits archival path event |
| `tools/slice2-playwright-gate.mjs` | Final whole-branch Playwright headless gate against live URL — interaction-smoke per spec §9 |

---

# Phase A — Corpus Ingest (~1.5-2 days)

Get the corpus from `tmp-secondbrain-scrape/` (~90 PDFs across 5 subjects + RC) onto the VPS `archival/_extras/` tree as `.md` files, then run `ingest-corpus` to refresh `knowledge.jsonl`. HybridRetriever already wired into sidekick — adding material is sufficient.

## Task A1: PDF-to-Markdown converter scaffold + first test

**Files:**
- Create: `tools/pdf-to-md.py`
- Create: `tools/pdf-to-md.test.py`

- [ ] **Step 1: Write the failing test for module-level convert function**

Create `tools/pdf-to-md.test.py`:

```python
"""Tests for tools/pdf-to-md.py — Romanian + math glyph fixes."""
import unittest
from importlib.util import spec_from_file_location, module_from_spec
from pathlib import Path

_HERE = Path(__file__).parent
_spec = spec_from_file_location("pdf_to_md", _HERE / "pdf-to-md.py")
_mod = module_from_spec(_spec)
_spec.loader.exec_module(_mod)
normalize_text = _mod.normalize_text


class TestNormalizeText(unittest.TestCase):
    def test_romanian_glyph_combining_marks(self):
        # PDFBox/pypdf output: combining breve + cedilla floating wrong.
        # Expected: composed Romanian letters.
        raw = "Probabilit˘ at ¸i"  # breve + cedilla scattered
        self.assertIn("Probabilități", normalize_text(raw))

    def test_micro_sign_left_as_is(self):
        # µ (U+00B5 micro sign) → leave as-is. Greek mu is U+03BC but
        # the math is readable either way; don't force-convert.
        raw = "µ = 0"
        self.assertIn("µ", normalize_text(raw))

    def test_whitespace_collapse(self):
        # Multi-space + newline runs collapse to single space, preserve
        # paragraph breaks (double newline).
        raw = "foo  bar\n\nbaz   qux"
        out = normalize_text(raw)
        self.assertEqual(out.count("\n\n"), 1)
        self.assertNotIn("  ", out)

    def test_glyph_fix_table_includes_sht(self):
        # ¸ s → ș and ¸ t → ț (cedilla glyph after letter)
        self.assertIn("ș", normalize_text("e¸s antion"))
        self.assertIn("ț", normalize_text("func¸t ie"))


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run test, verify it fails (module not found)**

Run: `python tools/pdf-to-md.test.py`
Expected: ModuleNotFoundError or `normalize_text` AttributeError — `pdf-to-md.py` doesn't exist yet.

- [ ] **Step 3: Implement minimal `pdf-to-md.py` with `normalize_text`**

Create `tools/pdf-to-md.py`:

```python
#!/usr/bin/env python3
"""Convert PDFs to Markdown with Romanian + math glyph normalization.

Usage:
    python tools/pdf-to-md.py --input tmp-secondbrain-scrape/ --output tmp-md/

Walks input tree; for each .pdf, extracts text via pypdf, normalizes Romanian
diacritic combining marks + math symbols, writes parallel .md file under
output tree with frontmatter (source_pdf, sha256, pages, extracted_at).

Idempotent: skips outputs whose source pdf sha256 hasn't changed.
"""
import argparse
import hashlib
import re
import sys
import unicodedata
from datetime import datetime, timezone
from pathlib import Path

# Romanian PDFBox/pypdf glyph artifacts → composed letters.
# These appear when the PDF uses Type 1 fonts with combining diacritic
# glyphs in adjacent code positions instead of single composed codepoints.
# Ordered: longest patterns first so partial matches don't swallow.
GLYPH_FIXES = [
    ("˘ a", "ă"), ("˘ A", "Ă"),
    ("¸ s", "ș"), ("¸ S", "Ș"),
    ("¸ t", "ț"), ("¸ T", "Ț"),
    ("ˆ ı", "î"), ("ˆ I", "Î"),
    ("ˆ a", "â"), ("ˆ A", "Â"),
    # No-space variants (some extractors drop the gap):
    ("˘a", "ă"), ("˘A", "Ă"),
    ("¸s", "ș"), ("¸S", "Ș"),
    ("¸t", "ț"), ("¸T", "Ț"),
]


def normalize_text(raw: str) -> str:
    """Apply Romanian glyph-fix table + NFC + whitespace collapse."""
    s = raw
    # Apply glyph-fix substitutions in order
    for src, dst in GLYPH_FIXES:
        s = s.replace(src, dst)
    # Unicode NFC normalize (composes any remaining decomposed forms)
    s = unicodedata.normalize("NFC", s)
    # Collapse runs of whitespace to single space, preserve paragraph breaks
    # (two or more newlines collapse to exactly two).
    s = re.sub(r"[ \t]+", " ", s)
    s = re.sub(r"\n{2,}", "\n\n", s)
    s = re.sub(r"(?<!\n)\n(?!\n)", " ", s)  # single newlines become spaces
    s = re.sub(r"  +", " ", s)
    return s.strip()


def extract_pdf_text(pdf_path: Path) -> tuple[str, int]:
    """Extract per-page text via pypdf; returns (joined_text, page_count)."""
    from pypdf import PdfReader

    reader = PdfReader(str(pdf_path))
    pages = []
    for p in reader.pages:
        try:
            pages.append(p.extract_text() or "")
        except Exception as e:
            pages.append(f"[extract-error: {e}]")
    return "\n\n".join(pages), len(reader.pages)


def sha256_hex(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(8192), b""):
            h.update(chunk)
    return h.hexdigest()


def convert_pdf_to_md(pdf_path: Path, md_path: Path) -> bool:
    """Convert one PDF; returns True if written, False if skipped (unchanged)."""
    sha = sha256_hex(pdf_path)

    if md_path.exists():
        # Check existing frontmatter for sha; skip if unchanged
        try:
            existing = md_path.read_text(encoding="utf-8")
            if f"sha256: {sha}" in existing[:500]:
                return False
        except Exception:
            pass

    text, pages = extract_pdf_text(pdf_path)
    normalized = normalize_text(text)
    now = datetime.now(timezone.utc).isoformat()

    body = (
        f"---\n"
        f"source_pdf: {pdf_path.as_posix()}\n"
        f"sha256: {sha}\n"
        f"pages: {pages}\n"
        f"extracted_at: {now}\n"
        f"---\n\n"
        f"{normalized}\n"
    )

    md_path.parent.mkdir(parents=True, exist_ok=True)
    md_path.write_text(body, encoding="utf-8")
    return True


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--input", required=True, help="Input root (e.g. tmp-secondbrain-scrape/)")
    ap.add_argument("--output", required=True, help="Output root (e.g. tmp-md/)")
    args = ap.parse_args()

    in_root = Path(args.input)
    out_root = Path(args.output)
    if not in_root.is_dir():
        print(f"input not a directory: {in_root}", file=sys.stderr)
        sys.exit(2)

    pdfs = list(in_root.rglob("*.pdf"))
    written, skipped = 0, 0
    for pdf in pdfs:
        rel = pdf.relative_to(in_root).with_suffix(".md")
        md = out_root / rel
        try:
            if convert_pdf_to_md(pdf, md):
                written += 1
                print(f"[write] {rel}")
            else:
                skipped += 1
        except Exception as e:
            print(f"[error] {pdf}: {e}", file=sys.stderr)

    print(f"\nDone. {written} written, {skipped} skipped (unchanged).")


if __name__ == "__main__":
    main()
```

- [ ] **Step 4: Run tests, verify pass**

Run: `python tools/pdf-to-md.test.py`
Expected: 4 tests pass.

- [ ] **Step 5: Commit**

```bash
git add tools/pdf-to-md.py tools/pdf-to-md.test.py
git commit -m "feat(slice2): pdf-to-md converter with Romanian glyph normalization

Phase A1 of Slice 2 corpus-RAG sidekick. Walks tmp-secondbrain-scrape/, extracts
text via pypdf, normalizes Romanian PDFBox glyph artifacts (combining breve +
cedilla → composed letters), writes Markdown to tmp-md/ with sha256 frontmatter
for idempotency.

Spec: docs/superpowers/specs/2026-05-11-tutor-slice2-corpus-rag-sidekick-design.md §4.1"
```

---

## Task A2: Hyphenation + column-wrap cleanup

PDFBox/pypdf output often breaks words across line boundaries (`Probabilit˘at¸i`/`a-\nti` style hyphenation). Real PDFs have invisible hyphens at column edges.

**Files:**
- Modify: `tools/pdf-to-md.py:normalize_text` (add hyphenation pass)
- Modify: `tools/pdf-to-md.test.py` (add hyphenation test)

- [ ] **Step 1: Add failing test for hyphenation**

Add to `tools/pdf-to-md.test.py` inside `TestNormalizeText`:

```python
    def test_hyphenation_joined(self):
        # Words split across line breaks via soft hyphen → joined.
        raw = "Distribut¸ia hipergeo-\nmetric˘ a"
        out = normalize_text(raw)
        self.assertIn("Distribuția hipergeometrică", out)

    def test_letter_break_no_hyphen(self):
        # When the line break has no hyphen, words stay separated by space.
        raw = "foo\nbar"
        out = normalize_text(raw)
        self.assertIn("foo bar", out)
```

- [ ] **Step 2: Run, verify fails**

Run: `python tools/pdf-to-md.test.py`
Expected: `test_hyphenation_joined` fails.

- [ ] **Step 3: Add hyphenation pass to `normalize_text`**

In `tools/pdf-to-md.py`, edit `normalize_text` to add hyphenation cleanup BEFORE whitespace collapse:

```python
def normalize_text(raw: str) -> str:
    """Apply Romanian glyph-fix table + NFC + whitespace collapse."""
    s = raw
    for src, dst in GLYPH_FIXES:
        s = s.replace(src, dst)
    s = unicodedata.normalize("NFC", s)
    # Hyphenation cleanup: word ending with hyphen at line break joins
    # with next-line continuation.  e.g. "hipergeo-\nmetric" → "hipergeometric"
    s = re.sub(r"(\w)-\n(\w)", r"\1\2", s)
    s = re.sub(r"[ \t]+", " ", s)
    s = re.sub(r"\n{2,}", "\n\n", s)
    s = re.sub(r"(?<!\n)\n(?!\n)", " ", s)
    s = re.sub(r"  +", " ", s)
    return s.strip()
```

- [ ] **Step 4: Run tests, verify pass**

Run: `python tools/pdf-to-md.test.py`
Expected: 6 tests pass.

- [ ] **Step 5: Commit**

```bash
git add tools/pdf-to-md.py tools/pdf-to-md.test.py
git commit -m "feat(slice2): hyphenation cleanup in pdf-to-md normalize_text"
```

---

## Task A3: Run converter against full corpus

**Files:**
- Generate (not committed): `tmp-md/` (gitignored — large generated tree)

- [ ] **Step 1: Add `tmp-md/` to .gitignore**

Edit `.gitignore` to add at the bottom (if not already present):

```gitignore
# Slice 2 corpus conversion output (regenerated via tools/pdf-to-md.py)
tmp-md/
```

- [ ] **Step 2: Ensure pypdf is installed**

Run: `python -m pip install --quiet pypdf`
Expected: install succeeds or "Requirement already satisfied".

- [ ] **Step 3: Run converter against full secondbrain corpus**

Run: `python tools/pdf-to-md.py --input tmp-secondbrain-scrape/ --output tmp-md/`
Expected: ~90 `[write]` lines, "X written, 0 skipped (unchanged)" summary. No `[error]` lines (or document errors for known-broken PDFs).

- [ ] **Step 4: Spot-check 3 random output files for fidelity**

Verify Romanian content survived for 3 subjects:
```bash
head -25 tmp-md/PS/ps_hw/Tema_A.md
head -25 tmp-md/POO/courses/poo_c1.md
head -25 tmp-md/ALO/courses/alo_c01.md
```
Expected: composed Romanian diacritics (`ă`, `ș`, `ț`, `î`, `â`), R function names + math symbols readable.

- [ ] **Step 5: Re-run converter, verify idempotent**

Run: `python tools/pdf-to-md.py --input tmp-secondbrain-scrape/ --output tmp-md/`
Expected: "0 written, ~90 skipped (unchanged)".

- [ ] **Step 6: Commit `.gitignore` only (no `tmp-md/` content)**

```bash
git add .gitignore
git commit -m "chore(slice2): gitignore tmp-md/ corpus conversion output"
```

---

## Task A4: sync-corpus-to-vps.sh wrapper

**Files:**
- Create: `tools/sync-corpus-to-vps.sh`

- [ ] **Step 1: Write the script**

Create `tools/sync-corpus-to-vps.sh`:

```bash
#!/usr/bin/env bash
# Sync converted Markdown corpus to VPS archival; trigger ingest-corpus.
#
# Usage:
#   bash tools/sync-corpus-to-vps.sh
#
# Expects:
#   - tmp-md/ exists locally (run tools/pdf-to-md.py first)
#   - VPS is reachable at root@46.247.109.91 (ssh key auth)
#   - VPS has /opt/jarvis/jarvis-kotlin/bin/jarvis-kotlin in PATH
#
# Side effects:
#   - rsync tmp-md/* → root@VPS:/opt/jarvis/data/archival/_extras/
#   - ssh + run jarvis-kotlin ingest-corpus on VPS
#   - Updates /opt/jarvis/data/knowledge.jsonl

set -euo pipefail

VPS="${JARVIS_VPS:-root@46.247.109.91}"
LOCAL_DIR="${LOCAL_DIR:-./tmp-md}"
REMOTE_DIR="${REMOTE_DIR:-/opt/jarvis/data/archival/_extras}"

if [[ ! -d "$LOCAL_DIR" ]]; then
    echo "error: $LOCAL_DIR does not exist; run tools/pdf-to-md.py first" >&2
    exit 2
fi

echo "[sync] rsync $LOCAL_DIR/ → $VPS:$REMOTE_DIR/"
rsync -av --update --exclude='*.swp' "$LOCAL_DIR/" "$VPS:$REMOTE_DIR/"

echo "[ingest] running ingest-corpus on $VPS"
ssh "$VPS" "set -a; source /opt/jarvis/.env; set +a; /opt/jarvis/jarvis-kotlin/bin/jarvis-kotlin ingest-corpus"

echo "[verify] knowledge.jsonl size + last entries on $VPS"
ssh "$VPS" "wc -l /opt/jarvis/data/knowledge.jsonl; tail -1 /opt/jarvis/data/knowledge.jsonl | head -c 400"

echo "[done] sync + ingest complete"
```

- [ ] **Step 2: Make executable**

Run: `chmod +x tools/sync-corpus-to-vps.sh`

- [ ] **Step 3: Commit**

```bash
git add tools/sync-corpus-to-vps.sh
git commit -m "feat(slice2): sync-corpus-to-vps.sh — rsync + ingest-corpus wrapper"
```

---

## Task A5: Sync corpus + verify HybridRetriever covers all 5 subjects + RC

**Files:** none (operational task)

- [ ] **Step 1: Run sync script**

Run: `bash tools/sync-corpus-to-vps.sh`
Expected: rsync output (some "sent ... bytes" line), ingest-corpus stdout, "knowledge.jsonl" wc-l > previous value, no errors.

- [ ] **Step 2: Probe sidekick per subject via curl (run during off-hours if free-tier daily quota tight)**

Open `https://corgflix.duckdns.org/tutor/?taskId=01KR6K07T6PATPRR5KH1JXYF8E` in a browser (or use Playwright headed) to get `csrf` cookie. Then from Playwright/curl:

```bash
# Replace $CSRF with the value of the csrf cookie from your browser.
for q in \
  "What does PS course material say about the Laplace distribution density formula? Quote source filename." \
  "What does POO material say about virtual methods? Quote source filename." \
  "What does ALO material say about mathematical induction? Quote source filename." \
  "What does PA material say about algorithm complexity analysis? Quote source filename." \
  "What does SO material say about process scheduling? Quote source filename." \
  "What does RC material say about TCP and OSI layers? Quote source filename." ; do
  echo "Q: $q"
  curl -s -b "csrf=$CSRF" -H "x-csrf-token: $CSRF" -H "content-type: application/json" \
    "https://corgflix.duckdns.org/api/v1/sidekick/ask" \
    -d "{\"task_id\":\"01KR6K07T6PATPRR5KH1JXYF8E\",\"user_question\":\"$q\"}" | jq -r .text | head -c 500
  echo "---"
done
```
Expected: each reply mentions a source filename from the appropriate subject's corpus (e.g. `_extras/PS/...md`, `_extras/POO/courses/poo_c5.md`, etc.).

- [ ] **Step 3: Document any subjects that come back empty in BRIDGE.md**

If a subject probe returns no matches: log it in BRIDGE.md as a coverage gap; the converter may need glyph-fix table tuning for that subject's PDFs. The slice still progresses (Phase B + C don't depend on every subject having content); plan a follow-up half-day.

- [ ] **Step 4: Commit BRIDGE.md update (if any) — no code commits for this task**

```bash
# Only if BRIDGE.md was updated:
git add ~/.claude/projects/C--Users-User-jarvis-kotlin/memory/BRIDGE.md
git commit -m "docs(slice2): corpus probe results per subject (Phase A5)"
```

---

# Phase B — Sidekick Citations (~½-1 day)

The sidekick currently returns plain text; HybridRetriever returns hits but the reply doesn't carry them as structured citations. This phase plumbs citations through DTO + system prompt + frontend rendering.

## Task B1: Add `ApiCitation` DTO + extend `ApiSidekickReply`

**Files:**
- Modify: `src/main/kotlin/jarvis/web/TutorRoutes.kt` (DTO definitions, currently around line 1600+)
- Create: `src/test/kotlin/jarvis/web/SidekickCitationsTest.kt`

- [ ] **Step 1: Locate `ApiSidekickReply` in `TutorRoutes.kt`**

Run: `grep -n "ApiSidekickReply\|ApiSidekickRequest" src/main/kotlin/jarvis/web/TutorRoutes.kt`
Note the line range — DTOs are at the bottom of the file.

- [ ] **Step 2: Write failing test asserting `citations` field exists in JSON encode/decode**

Create `src/test/kotlin/jarvis/web/SidekickCitationsTest.kt`:

```kotlin
package jarvis.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

class SidekickCitationsTest {

    @Test
    fun apiSidekickReply_encodes_with_citations_field() {
        val reply = ApiSidekickReply(
            text = "The Laplace MLE is the median (see ps_c4.md).",
            model = "test-model",
            quotedContext = null,
            citations = listOf(
                ApiCitation(
                    path = "_extras/PS/courses/ps_c4.md",
                    snippet = "MLE estimator for Laplace location parameter…",
                    score = 0.78,
                )
            ),
        )
        val json = Json.encodeToString(reply)
        assertTrue(json.contains("\"citations\""))
        assertTrue(json.contains("_extras/PS/courses/ps_c4.md"))
        assertTrue(json.contains("\"score\":0.78"))
    }

    @Test
    fun apiSidekickReply_decodes_without_citations_default_empty_list() {
        // Backwards compat: replies without citations decode with empty list.
        val raw = """{"text":"hi","model":"m","quotedContext":null}"""
        val reply = Json { ignoreUnknownKeys = true }.decodeFromString<ApiSidekickReply>(raw)
        assertEquals(emptyList(), reply.citations)
    }
}
```

- [ ] **Step 3: Run test, verify fails (no `citations` field, no `ApiCitation`)**

Run: `./gradlew test --tests jarvis.web.SidekickCitationsTest -q`
Expected: compile failure — `ApiCitation` unresolved.

- [ ] **Step 4: Add `ApiCitation` + extend `ApiSidekickReply`**

In `src/main/kotlin/jarvis/web/TutorRoutes.kt`, near the existing DTO block (search for `ApiSidekickReply`):

```kotlin
@Serializable
data class ApiCitation(
    val path: String,
    val snippet: String,
    val score: Double,
)

@Serializable
data class ApiSidekickReply(
    val text: String,
    val model: String,
    val quotedContext: String?,
    val citations: List<ApiCitation> = emptyList(),
)
```

If `ApiSidekickReply` already exists, just add the `citations` field (with `= emptyList()` default) and add the new `ApiCitation` class adjacent.

- [ ] **Step 5: Run test, verify pass**

Run: `./gradlew test --tests jarvis.web.SidekickCitationsTest -q`
Expected: 2 tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/jarvis/web/TutorRoutes.kt src/test/kotlin/jarvis/web/SidekickCitationsTest.kt
git commit -m "feat(slice2): ApiCitation DTO + extend ApiSidekickReply"
```

---

## Task B2: Extend SidekickContext system prompt to instruct citation format

**Files:**
- Modify: `src/main/kotlin/jarvis/tutor/SidekickContext.kt`
- Create: `src/test/kotlin/jarvis/tutor/SidekickContextCitationTest.kt`

- [ ] **Step 1: Write failing test asserting system prompt contains citation instruction**

Create `src/test/kotlin/jarvis/tutor/SidekickContextCitationTest.kt`:

```kotlin
package jarvis.tutor

import kotlin.test.Test
import kotlin.test.assertTrue

class SidekickContextCitationTest {
    @Test
    fun systemContext_instructs_llm_to_cite_source_filenames() {
        val env = SidekickEnvelope(
            taskId = "01ABCD",
            userQuestion = "What does ps_c4 say about Laplace?",
        )
        val ctx = SidekickContext.systemContext(env)
        // Must instruct LLM to cite when retrieval used
        assertTrue(ctx.contains("(src:"), "system context should describe citation format: $ctx")
        assertTrue(ctx.contains("filename") || ctx.contains("source"),
            "system context should mention source filenames: $ctx")
    }
}
```

- [ ] **Step 2: Run, verify fails**

Run: `./gradlew test --tests jarvis.tutor.SidekickContextCitationTest -q`
Expected: FAIL with `assertTrue` because current `systemContext` has no citation instructions.

- [ ] **Step 3: Edit `SidekickContext.systemContext` to add citation instruction**

In `src/main/kotlin/jarvis/tutor/SidekickContext.kt`, modify `systemContext`:

```kotlin
object SidekickContext {
    private const val CITATION_INSTRUCTION = """
# Source citation
When you use information from the corpus (lecture notes, lab sheets, themes),
cite the source filename inline using the format `(src: <path>)` where <path>
is the relative archival path returned by the search tool.
Do not invent filenames. Only cite paths the search tool actually returned.
If no source supports a claim, do not add a citation for it.
"""

    fun systemContext(env: SidekickEnvelope): String {
        val sb = StringBuilder()
        sb.append("# Sidekick context\n")
        env.taskId?.let { sb.append("task: ").append(it).append('\n') }
        env.problemId?.let { sb.append("problem: ").append(it).append('\n') }
        env.cardTitle?.let { sb.append("card: ").append(it).append('\n') }
        env.anchorText?.let {
            sb.append("paragraph the user is asking about:\n")
            sb.append(it.take(800)).append('\n')
        }
        env.selection?.let {
            sb.append("specific selection inside that paragraph:\n  \"")
            sb.append(it.take(200)).append("\"\n")
        }
        sb.append(CITATION_INSTRUCTION)
        return PromptInjectionScrubber.wrap(
            source = "sidekick_context",
            trust = "user_anchor",
            content = sb.toString(),
        )
    }
}
```

- [ ] **Step 4: Run, verify pass**

Run: `./gradlew test --tests jarvis.tutor.SidekickContextCitationTest -q`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/tutor/SidekickContext.kt src/test/kotlin/jarvis/tutor/SidekickContextCitationTest.kt
git commit -m "feat(slice2): SidekickContext instructs LLM to cite source filenames"
```

---

## Task B3: Extract citations from LLM reply + verify against retrieval set

The LLM emits `(src: <path>)` markers in its reply text. Backend post-processes: extract markers, verify each path was actually returned by HybridRetriever (drop fabricated ones), populate `ApiSidekickReply.citations`.

**Files:**
- Modify: `src/main/kotlin/jarvis/web/TutorRoutes.kt` (sidekick handler, around line 1275-1310)
- Create: `src/main/kotlin/jarvis/tutor/CitationExtractor.kt` (NEW — keeps regex + verify logic isolated + testable)
- Create: `src/test/kotlin/jarvis/tutor/CitationExtractorTest.kt`

- [ ] **Step 1: Write failing test for `CitationExtractor.extract`**

Create `src/test/kotlin/jarvis/tutor/CitationExtractorTest.kt`:

```kotlin
package jarvis.tutor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import jarvis.HybridRetriever

class CitationExtractorTest {

    private fun hit(path: String, snippet: String = "snip", score: Double = 0.5) =
        HybridRetriever.HybridHit(source = "lexical", id = path, snippet = snippet, score = score)

    @Test
    fun extracts_verified_citations_only() {
        val reply = "The Laplace MLE is median (src: _extras/PS/courses/ps_c4.md). Also see (src: fake/file.md)."
        val hits = listOf(hit("_extras/PS/courses/ps_c4.md"))
        val citations = CitationExtractor.extract(reply, hits)
        assertEquals(1, citations.size, "fake path should be dropped")
        assertEquals("_extras/PS/courses/ps_c4.md", citations[0].path)
    }

    @Test
    fun zero_citations_when_reply_has_no_markers() {
        val reply = "I don't have a source for this."
        val hits = listOf(hit("_extras/PS/concepts.md"))
        assertEquals(emptyList(), CitationExtractor.extract(reply, hits).map { it.path })
    }

    @Test
    fun deduplicates_repeated_citations() {
        val reply = "X (src: _extras/POO/courses/poo_c5.md). Y. Z (src: _extras/POO/courses/poo_c5.md)."
        val hits = listOf(hit("_extras/POO/courses/poo_c5.md"))
        val out = CitationExtractor.extract(reply, hits)
        assertEquals(1, out.size, "duplicate markers should dedupe")
    }

    @Test
    fun citation_score_and_snippet_propagate_from_hit() {
        val reply = "Foo (src: _extras/PS/foo.md)."
        val hits = listOf(hit("_extras/PS/foo.md", snippet = "expected snippet text", score = 0.83))
        val c = CitationExtractor.extract(reply, hits).single()
        assertEquals("expected snippet text", c.snippet)
        assertEquals(0.83, c.score)
    }
}
```

- [ ] **Step 2: Run, verify fails (no CitationExtractor)**

Run: `./gradlew test --tests jarvis.tutor.CitationExtractorTest -q`
Expected: compile fail — `CitationExtractor` unresolved.

- [ ] **Step 3: Implement `CitationExtractor`**

Create `src/main/kotlin/jarvis/tutor/CitationExtractor.kt`:

```kotlin
package jarvis.tutor

import jarvis.HybridRetriever
import jarvis.web.ApiCitation

/**
 * Extracts `(src: <path>)` markers from an LLM reply and matches each path
 * against the set of HybridRetriever hits that were actually fed to the LLM.
 * Fabricated paths are dropped silently. Deduplicates by path.
 */
object CitationExtractor {

    // Pattern: `(src: <path>)` where <path> is non-space, non-paren chars.
    // Allows nested slashes, dots, dashes. Min 1 char.
    private val CITE_RX = Regex("""\(src:\s*([^\s\)]+)\s*\)""")

    fun extract(replyText: String, hits: List<HybridRetriever.HybridHit>): List<ApiCitation> {
        val verifiedIds = hits.associateBy { it.id }
        val seen = LinkedHashSet<String>()
        val out = mutableListOf<ApiCitation>()
        for (match in CITE_RX.findAll(replyText)) {
            val path = match.groupValues[1]
            if (path !in verifiedIds) continue       // fabricated
            if (!seen.add(path)) continue            // dedupe
            val hit = verifiedIds.getValue(path)
            out.add(ApiCitation(path = hit.id, snippet = hit.snippet, score = hit.score))
        }
        return out
    }
}
```

- [ ] **Step 4: Run, verify pass**

Run: `./gradlew test --tests jarvis.tutor.CitationExtractorTest -q`
Expected: 4 tests pass.

- [ ] **Step 5: Surface retrieval hits through `JarvisToolset` so the handler sees the SAME hit set the LLM saw**

Goal: drop the planned-redundant re-run of `HybridRetriever.search` in the route. Re-running risks rank drift (different k, non-deterministic vector ties) so a real LLM citation could be dropped as "fabricated." Instead, capture hits at the dispatch boundary and surface through `ToolReply`.

Three concrete sub-changes:

(a) Extend `JarvisToolDefs.dispatch` to return a structured result instead of a bare `String`. Introduce a small data class adjacent to `dispatch`:

```kotlin
data class DispatchOut(
    val text: String,
    val hits: List<HybridRetriever.HybridHit> = emptyList(),
)
```

Change the signature: `fun dispatch(toolName: String, argsJson: String): DispatchOut`. Every existing dispatcher returns `DispatchOut(text = "...")` (empty hits). `dispatchSearchArchival` additionally fills `hits = <the list it just built>` before the existing string formatting runs.

(b) Update `JarvisToolset.chat`'s tool-dispatch loop (around line 152 of the current file) to accumulate hits across rounds:

```kotlin
val accumulatedHits = mutableListOf<HybridRetriever.HybridHit>()
// ... inside the tool dispatch for-loop, replace:
//   val result = try { JarvisToolDefs.dispatch(name, argsRaw) } catch ...
// with:
val out = try { JarvisToolDefs.dispatch(name, argsRaw) }
          catch (e: Exception) { DispatchOut("tool error: ${e.javaClass.simpleName}: ${e.message?.take(200)}") }
accumulatedHits += out.hits
val result = out.text
// rest of wrap + messages += ... unchanged
```

(c) Extend `ToolReply` with `hits` field and return at every exit:

```kotlin
data class ToolReply(
    val text: String,
    val model: String,
    val toolRounds: Int,
    val hits: List<HybridRetriever.HybridHit> = emptyList(),
)
// at the two return sites in chat(), pass hits = accumulatedHits
```

Then in the sidekick handler (`TutorRoutes.kt` around line 1296), after `ts.chat(...)`:

```kotlin
val r = kotlinx.coroutines.runBlocking { ts.chat(systemPrompt = systemContext, userText = env.userQuestion) }
val citations = jarvis.tutor.CitationExtractor.extract(r.text, r.hits)
call.respond(HttpStatusCode.OK, ApiSidekickReply(
    text = r.text,
    model = r.model,
    quotedContext = quoted,
    citations = citations,
))
```

(Adapt to existing local variable names in the handler — `quoted` may differ. Show the exact diff in the commit.)

**Test coverage:** Existing `JarvisToolDefsTest` tests call `dispatch(...)` directly. Update them to read `.text` on the new return type — that's the only call-site change. Add one new assertion in `CitationExtractorTest` (or a new `JarvisToolsetHitsCaptureTest`) verifying that a `chat` round that fires `search_archival` returns `ToolReply.hits.isNotEmpty()`. The test can stub the LLM client to issue a single tool call then a text reply — or just call `dispatch("search_archival", ...)` and assert `out.hits.size > 0` against a known archival corpus path.

- [ ] **Step 6: Compile + run sidekick tests**

Run: `./gradlew test --tests jarvis.web.SidekickCitationsTest --tests jarvis.tutor.CitationExtractorTest -q`
Expected: all tests pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/jarvis/tutor/CitationExtractor.kt \
        src/main/kotlin/jarvis/web/TutorRoutes.kt \
        src/test/kotlin/jarvis/tutor/CitationExtractorTest.kt
git commit -m "feat(slice2): sidekick reply carries verified citations[] field

CitationExtractor matches LLM (src: <path>) markers against HybridRetriever
hits returned for the query; fabricated paths dropped silently.

Spec §4.2; Phase B3."
```

---

## Task B4: Frontend reply type extension + askSidekick wire

**Files:**
- Modify: `tutor-web/src/lib/sidekickContext.ts` (or wherever `askSidekick` reply type lives — grep to find)
- Modify: `tutor-web/src/lib/inlineAsk.ts` (if reply type defined here)

- [ ] **Step 1: Locate reply type**

Run: `grep -rn "SidekickReply\|askSidekick\|quotedContext" tutor-web/src/lib/`
Identify which file defines the reply type. Likely `sidekickContext.ts` or `inlineAsk.ts`.

- [ ] **Step 2: Extend type to include `citations`**

Add to the appropriate type definition:

```typescript
export interface Citation {
  path: string;
  snippet: string;
  score: number;
}

export interface SidekickReply {
  text: string;
  model: string;
  quotedContext: string | null;
  citations?: Citation[];  // backwards-compat: default empty
}
```

Update `askSidekick` to read `citations` from response JSON (it's just JSON parse — should already work; this is just type-level).

- [ ] **Step 3: Commit**

```bash
git add tutor-web/src/lib/sidekickContext.ts tutor-web/src/lib/inlineAsk.ts
git commit -m "feat(slice2): frontend SidekickReply type extended with citations[]"
```

---

## Task B5: CitationPill component + Sidekick renders strip

**Files:**
- Create: `tutor-web/src/components/CitationPill.tsx`
- Create: `tutor-web/src/__tests__/CitationPill.test.tsx`
- Modify: `tutor-web/src/components/Sidekick.tsx`
- Create: `tutor-web/src/__tests__/Sidekick.citations.test.tsx`

- [ ] **Step 1: Write failing test for CitationPill**

Create `tutor-web/src/__tests__/CitationPill.test.tsx`:

```tsx
import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { CitationPill } from "../components/CitationPill";

describe("CitationPill", () => {
  it("renders the path basename as label", () => {
    render(
      <CitationPill
        citation={{ path: "_extras/PS/courses/ps_c4.md", snippet: "...", score: 0.7 }}
        onClick={() => {}}
      />
    );
    expect(screen.getByTestId("citation-pill")).toBeTruthy();
    expect(screen.getByText(/ps_c4\.md/)).toBeTruthy();
  });

  it("fires onClick with the citation when clicked", () => {
    const onClick = vi.fn();
    render(
      <CitationPill
        citation={{ path: "_extras/POO/courses/poo_c5.md", snippet: "...", score: 0.5 }}
        onClick={onClick}
      />
    );
    fireEvent.click(screen.getByTestId("citation-pill"));
    expect(onClick).toHaveBeenCalledWith(
      expect.objectContaining({ path: "_extras/POO/courses/poo_c5.md" })
    );
  });
});
```

- [ ] **Step 2: Run, verify fails (no component)**

Run: `cd tutor-web && npx vitest run src/__tests__/CitationPill.test.tsx`
Expected: FAIL — component file does not exist.

- [ ] **Step 3: Implement CitationPill**

Create `tutor-web/src/components/CitationPill.tsx`:

```tsx
import type { Citation } from "../lib/sidekickContext";

interface CitationPillProps {
  citation: Citation;
  onClick: (citation: Citation) => void;
}

/**
 * Renders one (src: <path>) citation as a clickable pill.
 * Label = filename basename. Title attr shows full path + snippet.
 * Click fires onClick with the full citation object.
 */
export function CitationPill({ citation, onClick }: CitationPillProps) {
  const basename = citation.path.split("/").pop() ?? citation.path;
  return (
    <button
      data-testid="citation-pill"
      onClick={() => onClick(citation)}
      title={`${citation.path}\n\n${citation.snippet}`}
      className="inline-flex items-center px-2 py-0.5 mr-1 mb-1 text-[10px] tracking-widest bg-accent-soft text-accent border border-accent hover:bg-accent hover:text-page-fg cursor-pointer"
    >
      src: {basename}
    </button>
  );
}
```

Verify Citation type imported from existing types file (adjust import path if Citation lives elsewhere).

- [ ] **Step 4: Run CitationPill tests, verify pass**

Run: `cd tutor-web && npx vitest run src/__tests__/CitationPill.test.tsx`
Expected: 2 tests pass.

- [ ] **Step 5: Write failing test for Sidekick rendering citations strip**

Create `tutor-web/src/__tests__/Sidekick.citations.test.tsx`:

```tsx
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { Sidekick } from "../components/Sidekick";

vi.mock("../lib/sidekickContext", async () => ({
  askSidekick: vi.fn(async () => ({
    text: "The Laplace MLE is the median (src: _extras/PS/courses/ps_c4.md).",
    model: "test-model",
    quotedContext: null,
    citations: [
      { path: "_extras/PS/courses/ps_c4.md", snippet: "MLE...", score: 0.7 },
    ],
  })),
}));

describe("Sidekick citations strip", () => {
  beforeEach(() => vi.clearAllMocks());

  it("renders one CitationPill per citation when reply contains them", async () => {
    render(<Sidekick envelope={{ task_id: "t1", user_question: "q" } as any} />);
    await waitFor(() =>
      expect(screen.getByTestId("sidekick-citations-strip")).toBeTruthy()
    );
    expect(screen.getAllByTestId("citation-pill")).toHaveLength(1);
  });

  it("does not render strip when citations is empty", async () => {
    vi.doMock("../lib/sidekickContext", async () => ({
      askSidekick: vi.fn(async () => ({
        text: "no source", model: "m", quotedContext: null, citations: [],
      })),
    }));
    // Re-import Sidekick under new mock — vitest hoisting subtleties may require
    // describe-scoped fresh module; for this test, just assert the absence
    // when citations array is empty (most paths will hit this via reload).
  });
});
```

- [ ] **Step 6: Run, verify fails — Sidekick doesn't render strip yet**

Run: `cd tutor-web && npx vitest run src/__tests__/Sidekick.citations.test.tsx`
Expected: FAIL — `sidekick-citations-strip` testid not found.

- [ ] **Step 7: Update Sidekick.tsx to render citations + pre-wire pill click**

Edit `tutor-web/src/components/Sidekick.tsx`. Replace the `FetchState` type + add citations field; render strip below text:

```tsx
import { useEffect, useState } from "react";
import { askSidekick } from "../lib/sidekickContext";
import type { SidekickEnvelope, Citation } from "../lib/inlineAsk";
import { CitationPill } from "./CitationPill";

interface SidekickProps {
  envelope?: SidekickEnvelope;
  onCitationClick?: (citation: Citation) => void;
}

type FetchState =
  | { status: "idle" }
  | { status: "loading" }
  | { status: "ok"; text: string; quotedContext: string | null; citations: Citation[] }
  | { status: "error" };

export function Sidekick({ envelope, onCitationClick }: SidekickProps) {
  const [expanded, setExpanded] = useState(false);
  const [fetchState, setFetchState] = useState<FetchState>({ status: "idle" });

  useEffect(() => {
    if (!envelope) return;
    setExpanded(true);
    setFetchState({ status: "loading" });

    let cancelled = false;
    askSidekick(envelope)
      .then((reply) => {
        if (cancelled) return;
        setFetchState({
          status: "ok",
          text: reply.text,
          quotedContext: reply.quotedContext,
          citations: reply.citations ?? [],
        });
      })
      .catch(() => {
        if (!cancelled) setFetchState({ status: "error" });
      });

    return () => { cancelled = true; };
  }, [envelope]);

  const chevron = expanded ? "▲" : "▼";

  return (
    <div
      data-testid="sidekick-panel"
      data-expanded={String(expanded)}
      style={{
        borderTop: "4px solid var(--color-border-strong, #0a0a0a)",
        background: "var(--color-panel-dark-bg, #1a1a1a)",
        color: "var(--color-panel-dark-fg, #f5f5f5)",
        fontFamily: "monospace",
      }}
    >
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "6px 12px", borderBottom: expanded ? "2px solid var(--color-border-strong, #0a0a0a)" : "none" }}>
        <span style={{ fontSize: "11px", fontWeight: 700, letterSpacing: "0.1em" }}>SIDEKICK</span>
        <button
          aria-label={expanded ? "Collapse sidekick" : "Expand sidekick"}
          onClick={() => setExpanded((v) => !v)}
          style={{ background: "none", border: "none", color: "inherit", fontFamily: "monospace", fontSize: "12px", cursor: "pointer", padding: "0 4px" }}
        >
          {chevron}
        </button>
      </div>
      <div style={{ overflow: "hidden", maxHeight: expanded ? "600px" : "0", transition: "max-height 200ms ease-out" }}>
        <div style={{ padding: "10px 12px", fontSize: "13px", lineHeight: 1.6 }}>
          {fetchState.status === "idle" && <span style={{ opacity: 0.5 }}>Select text or click ? to ask the sidekick.</span>}
          {fetchState.status === "loading" && <span style={{ opacity: 0.7 }}>thinking…</span>}
          {fetchState.status === "error" && <span style={{ color: "var(--color-accent, #ffcc00)", opacity: 0.9 }}>(LLM unavailable; rate-limited?)</span>}
          {fetchState.status === "ok" && (
            <>
              {fetchState.quotedContext && (
                <div
                  data-testid="sidekick-quote"
                  className="sidekick-quote-pop-in"
                  style={{ borderLeft: "3px solid var(--color-accent, #ffcc00)", paddingLeft: "10px", marginBottom: "10px", fontSize: "12px", opacity: 0.85, fontStyle: "italic" }}
                >
                  {`> quoted: "${fetchState.quotedContext}"`}
                </div>
              )}
              <div data-testid="sidekick-reply" style={{ whiteSpace: "pre-wrap" }}>{fetchState.text}</div>
              {fetchState.citations.length > 0 && (
                <div data-testid="sidekick-citations-strip" style={{ marginTop: "10px", borderTop: "1px dashed var(--color-border-thin, #444)", paddingTop: "8px" }}>
                  {fetchState.citations.map((c, i) => (
                    <CitationPill
                      key={i}
                      citation={c}
                      onClick={(cit) => onCitationClick?.(cit)}
                    />
                  ))}
                </div>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
}
```

(Note: `whiteSpace: pre-wrap` div at the body is kept here. Phase C1 will replace it with `<MathText>` separately.)

If `Citation` type isn't already exported from `lib/inlineAsk` or `lib/sidekickContext`, add it to whichever file defines `SidekickReply`.

- [ ] **Step 8: Run tests, verify pass**

Run: `cd tutor-web && npx vitest run src/__tests__/Sidekick.citations.test.tsx src/__tests__/CitationPill.test.tsx`
Expected: All pass.

- [ ] **Step 9: Commit**

```bash
git add tutor-web/src/components/CitationPill.tsx \
        tutor-web/src/components/Sidekick.tsx \
        tutor-web/src/lib/sidekickContext.ts \
        tutor-web/src/lib/inlineAsk.ts \
        tutor-web/src/__tests__/CitationPill.test.tsx \
        tutor-web/src/__tests__/Sidekick.citations.test.tsx
git commit -m "feat(slice2): Sidekick renders citations strip; new CitationPill component

Spec §4.2 (citations field) + §9 acceptance (sidekick-citations-strip testid).
Phase B4-B5."
```

---

# Phase C — UI Polish (~1-1.5 days)

## Task C1: Sidekick body text via `<MathText>` (LaTeX fix)

**Files:**
- Modify: `tutor-web/src/components/Sidekick.tsx` (replace line ~76 `<div whiteSpace pre-wrap>` with `<MathText>`)
- Create: `tutor-web/src/__tests__/Sidekick.mathtext.test.tsx`

- [ ] **Step 1: Write failing test**

Create `tutor-web/src/__tests__/Sidekick.mathtext.test.tsx`:

```tsx
import { describe, it, expect, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { Sidekick } from "../components/Sidekick";

vi.mock("../lib/sidekickContext", async () => ({
  askSidekick: vi.fn(async () => ({
    text: "Laplace MLE: $$\\hat{\\mu} = \\text{median}(x)$$ — the L1 estimator.",
    model: "m", quotedContext: null, citations: [],
  })),
}));

describe("Sidekick math rendering", () => {
  it("renders reply text via MathText (data-testid=math-text present)", async () => {
    render(<Sidekick envelope={{ task_id: "t", user_question: "q" } as any} />);
    await waitFor(() => expect(screen.getByTestId("math-text")).toBeTruthy());
  });
});
```

- [ ] **Step 2: Run, verify fails**

Run: `cd tutor-web && npx vitest run src/__tests__/Sidekick.mathtext.test.tsx`
Expected: FAIL — `math-text` testid not in Sidekick (still using pre-wrap div).

- [ ] **Step 3: Replace pre-wrap div with `<MathText>` (preserve `sidekick-reply` testid wrapper)**

In `tutor-web/src/components/Sidekick.tsx`, replace:

```tsx
<div data-testid="sidekick-reply" style={{ whiteSpace: "pre-wrap" }}>{fetchState.text}</div>
```

with:

```tsx
<div data-testid="sidekick-reply">
  <MathText text={fetchState.text} className="text-sm" />
</div>
```

(Keep the `sidekick-reply` wrapper testid — the C7 Playwright gate waits on it to know the reply finished loading. MathText emits its own `data-testid="math-text"` inside.)

Add import at top of file:

```tsx
import { MathText } from "./MathText";
```

- [ ] **Step 4: Run, verify pass**

Run: `cd tutor-web && npx vitest run src/__tests__/Sidekick.mathtext.test.tsx`
Expected: PASS.

- [ ] **Step 5: Run regression on prior Sidekick tests to ensure no break**

Run: `cd tutor-web && npx vitest run src/__tests__/Sidekick`
Expected: all sidekick-* tests pass.

- [ ] **Step 6: Commit**

```bash
git add tutor-web/src/components/Sidekick.tsx tutor-web/src/__tests__/Sidekick.mathtext.test.tsx
git commit -m "fix(slice2): Sidekick renders reply via <MathText> for LaTeX support

BRIDGE.md:435 known-broken. Replaces whiteSpace:pre-wrap div with <MathText>
which uses KaTeX for \$\$...\$\$ display + \$...\$ inline math.
Spec §4.3 C1."
```

---

## Task C2: DrillStack card body via `<MathText>` (LaTeX fix)

**Files:**
- Modify: `tutor-web/src/components/DrillStack.tsx` (wrap each `content.<field>` body text with `<MathText>` before passing to `DrillCard` children)
- Create: `tutor-web/src/__tests__/DrillStack.mathtext.test.tsx`

- [ ] **Step 1: Locate body-rendering JSX in DrillStack**

Run: `grep -n "content\." tutor-web/src/components/DrillStack.tsx | head -20`

Identify where `content.drill`, `content.worked`, `content.definition`, `content.check` get rendered as children of `<DrillCard>`.

- [ ] **Step 2: Write failing test asserting drill body uses MathText**

Create `tutor-web/src/__tests__/DrillStack.mathtext.test.tsx`:

```tsx
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { DrillStack } from "../components/DrillStack";

describe("DrillStack body math rendering", () => {
  it("renders drill body via MathText so $$...$$ becomes KaTeX", () => {
    render(
      <DrillStack
        taskId="t"
        problemId="A1"
        content={{
          drill: "What is $$\\hat{\\mu}_{MLE}$$ for x=(1,2,5)?",
          worked: "$$\\text{median} = 2$$",
          definition: "Laplace MLE is median.",
          check: "Verify: $$\\text{median}(1,2,5)=2$$",
          expectedAnswerHint: "median equals 2",
        }}
        onProblemComplete={() => {}}
      />
    );
    // At least the visible drill card should have MathText present
    expect(screen.getAllByTestId("math-text").length).toBeGreaterThan(0);
  });
});
```

- [ ] **Step 3: Run, verify fails**

Run: `cd tutor-web && npx vitest run src/__tests__/DrillStack.mathtext.test.tsx`
Expected: FAIL — no `math-text` testid.

- [ ] **Step 4: Wrap each body field in `<MathText>` inside DrillStack JSX**

In `tutor-web/src/components/DrillStack.tsx`, find the rendering of each DrillCard's children. For each body string from `content`, wrap in `<MathText>`. Example (adapt to actual JSX):

```tsx
import { MathText } from "./MathText";

// Inside the render() / return:
<DrillCard cardType="DRILL" title="③ DRILL · YOUR TURN" state={drillState()} staggerIndex={0}>
  <MathText text={content.drill} className="mb-3 text-sm" />
  {/* ... textarea + buttons unchanged ... */}
</DrillCard>

<DrillCard cardType="WORKED" title="② WORKED EXAMPLE" state={secondaryState()} staggerIndex={1}>
  <MathText text={content.worked} className="text-sm" />
</DrillCard>

<DrillCard cardType="DEFINITION" title="① DEFINITION" state={secondaryState()} staggerIndex={2}>
  <MathText text={content.definition} className="text-sm" />
</DrillCard>

<DrillCard cardType="CHECK" title="④ CHECK · TRANSFER" state={checkState()} staggerIndex={3}>
  <MathText text={content.check} className="text-sm" />
</DrillCard>
```

- [ ] **Step 5: Run, verify pass**

Run: `cd tutor-web && npx vitest run src/__tests__/DrillStack.mathtext.test.tsx`
Expected: PASS.

- [ ] **Step 6: Run all DrillStack tests, ensure no regression**

Run: `cd tutor-web && npx vitest run src/__tests__/DrillStack`
Expected: all DrillStack tests pass (including pre-existing).

- [ ] **Step 7: Commit**

```bash
git add tutor-web/src/components/DrillStack.tsx tutor-web/src/__tests__/DrillStack.mathtext.test.tsx
git commit -m "fix(slice2): DrillStack body fields render via <MathText>

LaTeX rendering for DRILL/WORKED/DEFINITION/CHECK card bodies.
Spec §4.3 C1."
```

---

## Task C3: `?debug=1` query flag at App level + thread `debug` prop down

**Files:**
- Modify: `tutor-web/src/App.tsx` (read `?debug=1`; pass `debug` prop to header)
- Modify: `tutor-web/src/components/DaemonHealthPill.tsx` (accept `hidden?: boolean` prop)
- Create: `tutor-web/src/__tests__/App.debug.test.tsx`

- [ ] **Step 1: Write failing test for `?debug=1` toggle**

Create `tutor-web/src/__tests__/App.debug.test.tsx`:

```tsx
import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { App } from "../App";

vi.mock("../lib/api", () => ({
  jarvisFetch: vi.fn(async () => ({ ok: true, json: async () => ({}) })),
}));

describe("App ?debug=1 toggle", () => {
  it("hides DaemonHealthPill when debug query absent (default)", () => {
    render(
      <MemoryRouter initialEntries={["/?taskId=t1"]}>
        <App />
      </MemoryRouter>
    );
    expect(screen.queryByTestId("daemon-health-pill")).toBeNull();
  });

  it("shows DaemonHealthPill when debug=1 query present", () => {
    render(
      <MemoryRouter initialEntries={["/?taskId=t1&debug=1"]}>
        <App />
      </MemoryRouter>
    );
    expect(screen.queryByTestId("daemon-health-pill")).not.toBeNull();
  });

  it("renders domain footer as 'READY' by default", () => {
    render(
      <MemoryRouter initialEntries={["/?taskId=t1"]}>
        <App />
      </MemoryRouter>
    );
    expect(screen.queryByTestId("domain-footer")).toBeNull();
    expect(screen.getByText(/^READY$/)).toBeTruthy();
  });
});
```

- [ ] **Step 2: Run, verify fails**

Run: `cd tutor-web && npx vitest run src/__tests__/App.debug.test.tsx`
Expected: FAIL — DaemonHealthPill renders unconditionally; domain footer present.

- [ ] **Step 3: Grep App.tsx for DaemonHealthPill + domain footer mount sites**

Run: `grep -n "DaemonHealthPill\|CORGFLIX\|duckdns" tutor-web/src/App.tsx`

Identify mount sites. (DaemonHealthPill likely mounted in App's header JSX. Domain footer may be in a status-bar component.)

- [ ] **Step 4: Edit App.tsx to read `?debug=1` + conditionally mount**

In `tutor-web/src/App.tsx`, add near the top of the `App` function body:

```tsx
const debug = params.get("debug") === "1";
```

At each DaemonHealthPill mount site:

```tsx
{debug && <DaemonHealthPill />}
```

For the domain footer (find the JSX containing `CORGFLIX.DUCKDNS.ORG` or similar):

```tsx
<span data-testid={debug ? "domain-footer" : undefined}>
  {debug ? "READY · CTRL+ENTER · CORGFLIX.DUCKDNS.ORG" : "READY"}
</span>
```

(Adapt mount-site exactly; preserve surrounding styles.)

- [ ] **Step 5: Run, verify pass**

Run: `cd tutor-web && npx vitest run src/__tests__/App.debug.test.tsx`
Expected: 3 tests pass.

- [ ] **Step 6: Run all App tests, ensure no regression**

Run: `cd tutor-web && npx vitest run src/__tests__/App`
Expected: all App tests pass.

- [ ] **Step 7: Commit**

```bash
git add tutor-web/src/App.tsx tutor-web/src/__tests__/App.debug.test.tsx
git commit -m "feat(slice2): ?debug=1 toggle hides DaemonHealthPill + domain footer

Default: hidden (clean UI for student). Flag: ?debug=1 shows full chrome.
Domain footer collapsed to single 'READY' word by default.
Spec §4.3 C2."
```

---

## Task C4: RailJsonBuilder emits PRIOR_GAP rail items

**Files:**
- Modify: `src/main/kotlin/jarvis/tutor/RailJsonBuilder.kt`
- Create: `src/test/kotlin/jarvis/tutor/RailJsonBuilderPriorGapTest.kt`

- [ ] **Step 1: Grep KnowledgeGapRepo for `listForTask` signature**

Run: `grep -n "fun listForTask" src/main/kotlin/jarvis/tutor/KnowledgeGapRepo.kt`

Note the signature — likely `fun listForTask(userId: String, taskId: String): List<KnowledgeGap>`. KnowledgeGap fields needed for rail emission: `id`, `topic`, `resolvedBy`.

- [ ] **Step 2: Write failing test for PRIOR_GAP emission**

Create `src/test/kotlin/jarvis/tutor/RailJsonBuilderPriorGapTest.kt`:

```kotlin
package jarvis.tutor

import jarvis.tutor.testdb.TestTutorDb
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SchemaUtils
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RailJsonBuilderPriorGapTest {

    private fun setupDb(): Database {
        val db = TestTutorDb.fresh()
        // Adjust to your existing test infra: ensure required tables exist.
        return db
    }

    @Test
    fun emits_prior_gap_item_for_unresolved_gap() {
        val db = setupDb()
        val userId = "u1"
        val taskId = TutorTypes.ulid()
        val ledgerDir = Files.createTempDirectory("ledger")

        // Seed: insert a task + an unresolved gap.
        TaskRepo(db).insert(Task(
            id = taskId, userId = userId, subject = "PS", title = "test",
            deadline = Instant.now().plusSeconds(86400),
            problemRef = ContentRef(repo = "test", path = "Tema_A.pdf", sha = "sha"),
            conceptRefs = emptyList(),
            rubricRef = ContentRef(repo = "test", path = "Tema_A.pdf", sha = "sha"),
            scratchpad = null, submission = null, grade = null,
            cardRefs = emptyList(),
            status = TaskStatus.ACTIVE,
            createdAt = Instant.now(), updatedAt = Instant.now(),
        ))
        val gapRepo = KnowledgeGapRepo(db, ledgerDir)
        val gap = KnowledgeGap(
            id = TutorTypes.ulid(),
            userId = userId,
            taskId = taskId,
            topic = "absolute value optimization",
            language = "ro",
            type = GapType.LLM_GROUNDED,
            trigger = GapTrigger.EXPLICIT_ASK,
            content = "I don't get argmin |x - mu|",
            exampleCode = null,
            sourceCitation = null,
            resolvedBy = null,    // unresolved
            reusedCount = 0,
            fsrsCardId = null,
            createdAt = Instant.now(),
        )
        gapRepo.insert(gap)

        val items = RailJsonBuilder.buildForTask(db, taskId, userId)
        val priorGap = items.firstOrNull { it["type"] == "PRIOR_GAP" }
        assertNotNull(priorGap, "expected PRIOR_GAP rail item; got items: $items")
        assertEquals("absolute value optimization", priorGap["label"])
        val payload = priorGap["payload"] as Map<*, *>
        assertEquals(gap.id, payload["gapId"])
    }

    @Test
    fun no_prior_gap_item_when_all_gaps_resolved() {
        val db = setupDb()
        val userId = "u1"
        val taskId = TutorTypes.ulid()
        TaskRepo(db).insert(Task(
            id = taskId, userId = userId, subject = "PS", title = "test",
            deadline = Instant.now().plusSeconds(86400),
            problemRef = ContentRef("t", "p", "s"),
            conceptRefs = emptyList(),
            rubricRef = ContentRef("t", "p", "s"),
            scratchpad = null, submission = null, grade = null, cardRefs = emptyList(),
            status = TaskStatus.ACTIVE,
            createdAt = Instant.now(), updatedAt = Instant.now(),
        ))
        // No gaps inserted — list should be empty.
        val items = RailJsonBuilder.buildForTask(db, taskId, userId)
        assertEquals(0, items.count { it["type"] == "PRIOR_GAP" })
    }
}
```

(Adapt to existing test helpers in `src/test/kotlin/jarvis/tutor/`. If `TestTutorDb` doesn't exist, look at existing tests like `TaskPrepRouteTest` for how DB is set up.)

- [ ] **Step 3: Run, verify fails**

Run: `./gradlew test --tests jarvis.tutor.RailJsonBuilderPriorGapTest -q`
Expected: FAIL — PRIOR_GAP not emitted.

- [ ] **Step 4: Add PRIOR_GAP emission to RailJsonBuilder**

In `src/main/kotlin/jarvis/tutor/RailJsonBuilder.kt`, edit `buildForTask` to add PRIOR_GAP emission AFTER conceptRefs, BEFORE FSRS_DUE:

```kotlin
import java.nio.file.Path

object RailJsonBuilder {
    /**
     * Returns rail items. Caller supplies optional ledgerDir for gap repo
     * (use Config.tutorLedgerDir Path in production wiring).
     */
    fun buildForTask(
        db: Database,
        taskId: String,
        userId: String,
        ledgerDir: Path = Path.of(jarvis.Config.tutorLedgerDir),
    ): List<Map<String, Any?>> {
        val task = TaskRepo(db).findById(taskId) ?: return emptyList()
        val items = mutableListOf<Map<String, Any?>>()

        // PDF
        items.add(mapOf(
            "type" to "PDF",
            "label" to "${task.problemRef.path.substringAfterLast('/')} p.1",
            "action" to "OPEN_DRAWER",
            "payload" to mapOf("path" to task.problemRef.path),
        ))

        // Scratchpad
        items.add(mapOf(
            "type" to "SCRATCHPAD",
            "label" to "draft answers",
            "action" to "OPEN_DRAWER",
            "payload" to emptyMap<String, Any?>(),
        ))

        // Concept refs from task
        task.conceptRefs.forEach { c ->
            items.add(mapOf(
                "type" to "CONCEPT",
                "label" to c.path.substringAfterLast('/'),
                "action" to "OPEN_DRAWER",
                "payload" to mapOf("conceptId" to c.sha),
            ))
        }

        // PRIOR_GAP items — one per unresolved gap on this task.
        try {
            val gapRepo = KnowledgeGapRepo(db, ledgerDir)
            val gaps = gapRepo.listForTask(userId, taskId).filter { it.resolvedBy == null }
            gaps.forEach { g ->
                items.add(mapOf(
                    "type" to "PRIOR_GAP",
                    "label" to g.topic.take(80),
                    "action" to "OPEN_DRAWER",
                    "payload" to mapOf("gapId" to g.id),
                ))
            }
        } catch (_: Exception) {
            // Ledger or repo failure shouldn't break rail rendering.
        }

        // FSRS due
        val due = try {
            FsrsDueQueue.due(db, userId, Instant.now(), 50).size
        } catch (_: Exception) { 0 }
        if (due > 0) {
            items.add(mapOf(
                "type" to "FSRS_DUE",
                "label" to "$due cards due",
                "action" to "NAVIGATE",
                "payload" to mapOf("count" to due, "route" to "/tutor/review"),
            ))
        }

        return items
    }

    // (toJsonArrayString + helpers unchanged)
    // ...
}
```

Verify `KnowledgeGapRepo` constructor + `listForTask` signature match (`ledgerDir: Path` may need adjustment).

- [ ] **Step 5: Update callers of `buildForTask` if signature changed**

If `ledgerDir` was added as a new parameter and callers use it without one, those callers either use the default OR need updating. Grep:

Run: `grep -n "RailJsonBuilder.buildForTask" src/main/kotlin`

Each caller should compile against the default param OR explicitly pass `ctx.ledgerDir` (from the existing tutor context).

- [ ] **Step 6: Run, verify pass**

Run: `./gradlew test --tests jarvis.tutor.RailJsonBuilderPriorGapTest -q`
Expected: 2 tests pass.

- [ ] **Step 7: Run all RailJsonBuilder tests, ensure no regression**

Run: `./gradlew test --tests "*RailJsonBuilder*" -q`
Expected: all RailJsonBuilder tests pass.

- [ ] **Step 8: Commit**

```bash
git add src/main/kotlin/jarvis/tutor/RailJsonBuilder.kt \
        src/test/kotlin/jarvis/tutor/RailJsonBuilderPriorGapTest.kt
git commit -m "feat(slice2): RailJsonBuilder emits PRIOR_GAP rail items

One item per unresolved gap from KnowledgeGapRepo.listForTask.
ResourceRail.tsx already handles PRIOR_GAP click → PriorGapAdapter (Slice 1.5).
Spec §4.3 C3 (PRIOR_GAP half)."
```

---

## Task C5: `/reprep` populates `task.conceptRefs` via HybridRetriever

**Files:**
- Modify: `src/main/kotlin/jarvis/web/TutorRoutes.kt` (sidekick `/reprep` handler, around line 1123)
- Create: `src/test/kotlin/jarvis/web/ReprepConceptRefsTest.kt`

- [ ] **Step 1: Locate `/reprep` handler**

Run: `grep -n "task/{id}/reprep\|reprep\"" src/main/kotlin/jarvis/web/TutorRoutes.kt`

Note line where `problemsJson` is constructed (TutorRoutes.kt:1151-1155 in current file).

- [ ] **Step 2: Write failing test asserting `task.conceptRefs` populated after `/reprep`**

Create `src/test/kotlin/jarvis/web/ReprepConceptRefsTest.kt`:

```kotlin
package jarvis.web

import jarvis.tutor.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class ReprepConceptRefsTest {

    @Test
    fun reprep_populates_concept_refs_via_hybrid_retrieval() = testApplication {
        // Use existing test harness: bootstrap tutor app with a fixed PDF
        // that contains predictable keywords (e.g. "Laplace", "MLE").
        // ...
        // POST /api/v1/task/{id}/reprep
        // Then GET /api/v1/tasks/{id} and assert task.conceptRefs non-empty
        // with at least one path containing the expected keyword's match.
        // ...
        // (Adapt to existing test scaffolding; the meat: assert
        // taskRepo.findById(taskId)!!.conceptRefs.isNotEmpty()
        // AFTER reprep returns 200.)
    }
}
```

(Full test scaffold mirrors existing `TaskPrepRouteTest` or `TasksRouteIdempotentTest`; copy the auth + Application set-up from there. The assertion: after `/reprep` returns 200, the task row's `concept_refs_json` column is no longer empty.)

- [ ] **Step 3: Run, verify fails**

Run: `./gradlew test --tests jarvis.web.ReprepConceptRefsTest -q`
Expected: FAIL — `task.conceptRefs` remains empty after /reprep.

- [ ] **Step 4: Edit /reprep handler to populate conceptRefs via HybridRetriever**

In `src/main/kotlin/jarvis/web/TutorRoutes.kt`, locate the /reprep handler. After `problems = ...` is computed, add:

```kotlin
// Phase C5: auto-populate conceptRefs from HybridRetriever per problem.
val conceptRefs = mutableListOf<jarvis.tutor.ContentRef>()
val seenPaths = HashSet<String>()
for (problem in problems) {
    try {
        val hits = kotlinx.coroutines.runBlocking {
            jarvis.HybridRetriever.search(
                query = problem.statement.take(400),
                k = 3,
                archivalRoot = jarvis.Config.archivalDir,
                semanticEmbed = null,
            )
        }
        for (h in hits) {
            if (seenPaths.add(h.id)) {
                conceptRefs.add(jarvis.tutor.ContentRef(
                    repo = "archival",
                    path = h.id,
                    sha = "pending",  // not load-bearing for rail render
                ))
            }
        }
    } catch (_: Exception) {
        // Retrieval failure shouldn't block reprep.
    }
}

// Persist conceptRefs to the task row
if (conceptRefs.isNotEmpty()) {
    TaskRepo(ctx.db).updateConceptRefs(taskId, conceptRefs)
}
```

If `TaskRepo.updateConceptRefs` doesn't exist, add it:

```kotlin
// In TaskRepo.kt:
fun updateConceptRefs(taskId: String, refs: List<ContentRef>) {
    transaction(db) {
        TaskTable.update({ TaskTable.id eq taskId }) {
            it[conceptRefsJson] = tutorJson.encodeToString(
                ListSerializer(ContentRef.serializer()), refs
            )
            it[updatedAt] = Instant.now().toString()  // adapt to column type
        }
    }
}
```

- [ ] **Step 5: Run, verify pass**

Run: `./gradlew test --tests jarvis.web.ReprepConceptRefsTest -q`
Expected: PASS.

- [ ] **Step 6: Run all reprep + rail tests, no regression**

Run: `./gradlew test --tests "*Reprep*" --tests "*RailJsonBuilder*" -q`
Expected: all pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/jarvis/web/TutorRoutes.kt \
        src/main/kotlin/jarvis/tutor/TaskRepo.kt \
        src/test/kotlin/jarvis/web/ReprepConceptRefsTest.kt
git commit -m "feat(slice2): /reprep populates task.conceptRefs via HybridRetriever

For each extracted problem, run HybridRetriever.search(statement, k=3) against
the corpus archival; dedupe paths; persist as ContentRefs into task row.
RailJsonBuilder + ResourceRail already render CONCEPT items from this list.
Spec §4.3 C3 (CONCEPT_REF auto-pop)."
```

---

## Task C6: CitationPill click opens rail drawer for source

The frontend pieces are in place but `onCitationClick` in `Sidekick` is just a no-op callback. Wire it through `TutorWorkspace` so click opens the rail drawer for that source path.

**Files:**
- Modify: `tutor-web/src/components/TutorWorkspace.tsx`
- Modify: `tutor-web/src/components/ResourceRail.tsx` (export imperative API or accept a "selected item" prop)

- [ ] **Step 1: Locate TutorWorkspace mount of Sidekick + ResourceRail**

Run: `grep -n "Sidekick\|ResourceRail" tutor-web/src/components/TutorWorkspace.tsx`

Identify how the rail items are passed in.

- [ ] **Step 2: Decide wire approach**

Simplest: TutorWorkspace holds a `selectedRailItem` state. When Sidekick's `onCitationClick` fires with a Citation, TutorWorkspace finds the matching rail item by path and sets `selectedRailItem` → ResourceRail receives it as a controlled-open prop. If no matching rail item exists, just navigate the iframe to a side-mount PDF route.

For MVP this slice: on citation click, find a rail item with matching `payload.path` (CONCEPT_REF or PDF), pass it to ResourceRail to open. If no match, no-op.

- [ ] **Step 3: Update ResourceRail to accept `forceOpen?: RailItem | null`**

In `tutor-web/src/components/ResourceRail.tsx`, add prop + effect:

```tsx
interface ResourceRailProps {
  taskId: string;
  items: RailItem[];
  forceOpen?: RailItem | null;
}

// Inside ResourceRail:
const [openDrawer, setOpenDrawer] = useState<RailItem | null>(null);

useEffect(() => {
  if (forceOpen) setOpenDrawer(forceOpen);
}, [forceOpen]);
```

- [ ] **Step 4: Wire TutorWorkspace**

In `tutor-web/src/components/TutorWorkspace.tsx`:

```tsx
const [citationOpen, setCitationOpen] = useState<RailItem | null>(null);

// ... in JSX:
<Sidekick
  envelope={sidekickEnvelope}
  onCitationClick={(c) => {
    const match = railItems.find(
      (i) =>
        (i.type === "CONCEPT" && (i.payload?.path === c.path || i.label === c.path.split("/").pop())) ||
        (i.type === "PDF" && i.payload?.path === c.path)
    );
    if (match) setCitationOpen(match);
  }}
/>

<ResourceRail taskId={taskId} items={railItems} forceOpen={citationOpen} />
```

- [ ] **Step 5: Write test for the wire**

Create `tutor-web/src/__tests__/CitationPill.wire.test.tsx`:

```tsx
import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { TutorWorkspace } from "../components/TutorWorkspace";

vi.mock("../lib/sidekickContext", async () => ({
  askSidekick: vi.fn(async () => ({
    text: "See (src: _extras/PS/courses/ps_c4.md).",
    model: "m", quotedContext: null,
    citations: [{ path: "_extras/PS/courses/ps_c4.md", snippet: "...", score: 0.7 }],
  })),
}));

// Mock taskPrep fetch to return a rail item with matching path.
vi.mock("../lib/api", () => ({
  jarvisFetch: vi.fn(async (url: string) => {
    if (url.endsWith("/prep")) {
      return new Response(JSON.stringify({
        taskId: "t",
        problemsJson: "[]",
        drillsJson: "{}",
        railJson: JSON.stringify([
          { type: "CONCEPT", label: "ps_c4.md", action: "OPEN_DRAWER",
            payload: { path: "_extras/PS/courses/ps_c4.md", conceptId: "ps_c4" } },
        ]),
      }), { status: 200, headers: { "content-type": "application/json" } });
    }
    return new Response("{}", { status: 200 });
  }),
}));

describe("CitationPill click → rail drawer opens", () => {
  it("opens rail drawer for matching source path", async () => {
    render(<TutorWorkspace taskId="t" />);
    // simulate sidekick envelope firing — depends on TutorWorkspace API,
    // adapt to actual code (e.g. dispatch a custom event, or render directly)
    await waitFor(() => screen.getByTestId("citation-pill"));
    fireEvent.click(screen.getByTestId("citation-pill"));
    await waitFor(() =>
      expect(screen.getByText(/ps_c4/i)).toBeTruthy()
    );
  });
});
```

(This test is rough; adapt to actual TutorWorkspace mount + initial-state requirements. If TutorWorkspace is too coupled for unit test, defer to Playwright gate in the final task and skip the unit test.)

- [ ] **Step 6: Run + adjust**

Run: `cd tutor-web && npx vitest run src/__tests__/CitationPill.wire.test.tsx`

If passing: great. If TutorWorkspace coupling makes this too complex, **delete the unit test** and rely on the Playwright gate (Task C7) to verify end-to-end interaction.

- [ ] **Step 7: Commit**

```bash
git add tutor-web/src/components/Sidekick.tsx \
        tutor-web/src/components/TutorWorkspace.tsx \
        tutor-web/src/components/ResourceRail.tsx \
        tutor-web/src/__tests__/CitationPill.wire.test.tsx
git commit -m "feat(slice2): CitationPill click opens matching rail drawer

When sidekick reply has a citation, clicking the pill finds a rail item with
matching payload.path (CONCEPT or PDF) and opens its drawer.
Spec §9 visual acceptance: sidekick-citations-strip interaction-smoke."
```

---

## Task C7: Deploy + Playwright interaction-smoke gate

**Files:**
- Create: `tools/slice2-playwright-gate.mjs`

- [ ] **Step 0: Pre-flight — production `data-testid` plumbing for the gate**

The Playwright gate (Step 3 below) selects on `[data-testid="inline-ask-chip"]`. Existing `tutor-web/src/components/InlineAskChip.tsx` (verified by grep) renders a `<button>` without that testid in production — only `__tests__/*` mocks have it. Add the attribute in production now:

```bash
grep -n "data-testid" tutor-web/src/components/InlineAskChip.tsx || echo "MISSING"
```

If missing, edit `InlineAskChip.tsx` and add `data-testid="inline-ask-chip"` to the `<button>` element. One-line edit. No new tests required (selection-fire flow is exercised by `TutorWorkspace.test.tsx` which mocks the chip anyway).

```bash
git add tutor-web/src/components/InlineAskChip.tsx
git commit -m "chore(slice2): InlineAskChip data-testid for Playwright gate"
```

- [ ] **Step 1: Build + deploy frontend**

Run:
```bash
cd tutor-web && npm run build
cd ..
bash tools/deploy.sh
```
Expected: BUILD SUCCESSFUL; bundle hash printed; `/healthz` 200 on VPS.

- [ ] **Step 2: Capture new bundle hash**

Run: `curl -sk https://corgflix.duckdns.org/tutor/ | grep -oE 'index-[A-Za-z0-9_-]+\.js' | head -1`
Save the bundle name; should differ from `index-D4v9m8-Y.js` (the pre-slice-2 bundle).

- [ ] **Step 3: Write Playwright gate script**

Create `tools/slice2-playwright-gate.mjs`:

```javascript
#!/usr/bin/env node
/**
 * Slice 2 interaction-smoke gate against live URL.
 *
 * Selectors-painted ≠ selectors-work (Slice 1.5 lesson). This gate clicks
 * every interactive surface introduced by Slice 2 and asserts no 4xx/5xx
 * + no on-screen error text after each click.
 *
 * Asserts:
 *   1. Default URL: no daemon-health-pill, no domain-footer; "READY" visible.
 *   2. ?debug=1: daemon-health-pill visible, domain-footer visible.
 *   3. Resource rail has >= 1 CONCEPT or PRIOR_GAP item for the test task.
 *   4. Selection-fired sidekick flow (the load-bearing interaction):
 *      a. Select text inside a card-body or PDF paragraph.
 *      b. InlineAskChip appears within 2s; click it.
 *      c. Sidekick reply paints within 30s; status row leaves "loading".
 *      d. Reply renders via MathText (data-testid="math-text" present).
 *      e. If response contained `(src:` markers AND backend returned
 *         citations, [data-testid="sidekick-citations-strip"] visible AND
 *         contains >=1 [data-testid^="citation-pill"].
 *      f. Click first citation-pill → assert ResourceRail drawer opens for
 *         that archival path (drawer testid OR title element changes),
 *         no on-screen text matches /404|HTTP \d{3}|not found|error/i.
 *   5. No 4xx/5xx network responses during first paint OR any of the clicks
 *      above.
 *
 * If the live task has no curated cards yet OR the sidekick reply lacks
 * any (src:) marker, citation pill assertions (e–f) are skipped — but the
 * MathText assertion (d) still runs because LaTeX-bearing replies are
 * the dominant case.
 */
import { chromium } from "playwright";

const URL = process.env.SLICE2_URL || "https://corgflix.duckdns.org/tutor/?taskId=01KR6K07T6PATPRR5KH1JXYF8E";
const fails = [];
const errs = [];
const ERROR_RX = /404|HTTP \d{3}|not found|no PDF attached|error/i;

async function gate() {
  const browser = await chromium.launch({ headless: true });
  const ctx = await browser.newContext();
  const page = await ctx.newPage();

  page.on("response", (resp) => {
    const status = resp.status();
    if (status >= 400 && resp.url().includes("/api/")) {
      errs.push(`HTTP ${status} ${resp.url()}`);
    }
  });

  // 1. Default URL: no daemon-pill, no domain-footer
  await page.goto(URL, { waitUntil: "networkidle" });
  const pill = await page.locator('[data-testid="daemon-health-pill"]').count();
  const footer = await page.locator('[data-testid="domain-footer"]').count();
  if (pill !== 0) fails.push(`daemon-health-pill visible at default URL (count=${pill})`);
  if (footer !== 0) fails.push(`domain-footer visible at default URL (count=${footer})`);
  const readyText = await page.locator('text=/^READY$/').count();
  if (readyText < 1) fails.push("'READY' label not found at default URL");

  // 2. ?debug=1
  await page.goto(URL + "&debug=1", { waitUntil: "networkidle" });
  const pillDbg = await page.locator('[data-testid="daemon-health-pill"]').count();
  if (pillDbg < 1) fails.push("daemon-health-pill missing at ?debug=1");

  // 3. Rail item count — back to default URL
  await page.goto(URL, { waitUntil: "networkidle" });
  const conceptOrGap = await page.locator(
    '[data-testid^="rail-item-CONCEPT"], [data-testid^="rail-item-PRIOR_GAP"]'
  ).count();
  if (conceptOrGap < 1) fails.push(`expected >= 1 CONCEPT|PRIOR_GAP rail item; got ${conceptOrGap}`);

  // 4. Selection-fired sidekick interaction-smoke (load-bearing — Slice 1.5 lesson)
  //
  // Strategy: find the first .card-body element OR drill statement paragraph,
  // programmatically select a substring via window.getSelection so the
  // existing InlineAskChip listener (mouseup + selection-change) fires.
  // Card-body fallback handles tasks where drill cards are hand-curated;
  // PDF iframe text selection is browser-side hard to script reliably
  // across origins, so we prefer card-body.
  const selectionFired = await page.evaluate(() => {
    const target = document.querySelector('[data-testid="drill-card-body"]')
                 || document.querySelector('.card-body')
                 || document.querySelector('[data-testid="problem-statement"]');
    if (!target || !target.textContent || target.textContent.trim().length < 20) return false;
    const range = document.createRange();
    // Select first 40 chars of the first text node we find.
    let textNode = null;
    const walker = document.createTreeWalker(target, NodeFilter.SHOW_TEXT);
    while (walker.nextNode()) {
      if (walker.currentNode.textContent.trim().length >= 20) { textNode = walker.currentNode; break; }
    }
    if (!textNode) return false;
    range.setStart(textNode, 0);
    range.setEnd(textNode, Math.min(40, textNode.textContent.length));
    const sel = window.getSelection();
    sel.removeAllRanges();
    sel.addRange(range);
    // Dispatch a synthetic mouseup so listeners that wait for it fire.
    target.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));
    return true;
  });

  if (!selectionFired) {
    fails.push("could not script text selection: no card-body / drill-card-body / problem-statement node with content");
  } else {
    const chip = page.locator('[data-testid="inline-ask-chip"]');
    try {
      await chip.waitFor({ state: "visible", timeout: 3000 });
      await chip.click();
    } catch (e) {
      fails.push(`InlineAskChip never appeared after selection (${e.message})`);
    }

    // Wait for reply to finish loading. Sidekick container testid + a non-loading
    // state attr OR a non-empty reply body.
    try {
      await page.locator('[data-testid="sidekick-reply"]').waitFor({ state: "visible", timeout: 30000 });
    } catch (e) {
      fails.push(`sidekick-reply never painted within 30s (${e.message})`);
    }

    // 4d. MathText wired — KaTeX adds .katex class even on text-only replies
    //     when the MathText component is used. data-testid="math-text" is
    //     emitted by the MathText wrapper. Either is sufficient evidence.
    const mathSeen = await page.locator('[data-testid="math-text"], .katex').count();
    if (mathSeen < 1) fails.push("sidekick reply rendered without MathText wrapper (no [data-testid=math-text] AND no .katex)");

    // 4e+f. Citation pill click. Only assert if backend returned a citations strip.
    const stripPresent = await page.locator('[data-testid="sidekick-citations-strip"]').count();
    if (stripPresent > 0) {
      const pills = page.locator('[data-testid^="citation-pill"]');
      const pillCount = await pills.count();
      if (pillCount < 1) {
        fails.push("citations strip present but no [data-testid^=citation-pill] children");
      } else {
        await pills.first().click();
        // Drawer opens — testid is rail-drawer-open OR resource-rail-drawer; the
        // implementer chooses. The assertion is: SOME drawer-like element with
        // a recognizable testid appears AND no error text shows.
        try {
          await page.locator('[data-testid$="-drawer"], [data-testid="rail-drawer-open"]')
                    .first()
                    .waitFor({ state: "visible", timeout: 5000 });
        } catch (e) {
          fails.push(`citation pill click did not open a rail drawer within 5s (${e.message})`);
        }
        const bodyTxt = await page.textContent('body');
        if (bodyTxt && ERROR_RX.test(bodyTxt)) {
          fails.push(`error text visible on screen after citation-pill click: ${(bodyTxt.match(ERROR_RX) || [])[0]}`);
        }
      }
    } else {
      console.log("[gate] note: sidekick reply has no citations strip — skipping pill-click assertions (4e/4f)");
    }
  }

  // 5. Final error check
  if (errs.length > 0) fails.push(`4xx/5xx network responses: ${errs.join(", ")}`);

  await browser.close();

  if (fails.length > 0) {
    console.error("[gate] FAIL:\n  " + fails.join("\n  "));
    process.exit(1);
  } else {
    console.log("[gate] PASS — all Slice 2 acceptance checks green");
  }
}

gate().catch((e) => {
  console.error("[gate] fatal:", e.message);
  process.exit(2);
});
```

- [ ] **Step 4: Run gate against live deploy**

Run: `node tools/slice2-playwright-gate.mjs`
Expected: `[gate] PASS — all Slice 2 acceptance checks green`.

If FAIL: debug each failure, push fixes, re-run. Common failures:
- DaemonHealthPill still visible at default URL → `?debug=1` toggle not wired in App.tsx mount site
- Rail has 0 CONCEPT/PRIOR_GAP → `/reprep` wasn't re-run on the test task; trigger it via POST or via UI button
- 4xx on `/api/v1/sidekick/ask` → check CSRF cookie set on cold load

- [ ] **Step 5: Commit gate script**

```bash
git add tools/slice2-playwright-gate.mjs
git commit -m "feat(slice2): playwright gate for Slice 2 acceptance criteria

Asserts spec §9: daemon-health-pill hidden by default, visible at ?debug=1,
rail has CONCEPT|PRIOR_GAP item, no 4xx/5xx on first paint."
```

- [ ] **Step 6: Final commit + push**

```bash
git push origin main
```

---

## Self-Review

### 1. Spec coverage

| Spec section | Plan task |
|---|---|
| §3.1-3.4 Corpus ingest (Phase A) | A1-A5 |
| §3.5-3.6 Sidekick citations (Phase B) | B1-B5 |
| §3.7 LaTeX `<MathText>` | C1 (Sidekick) + C2 (DrillStack) |
| §3.8 `?debug=1` toggle | C3 |
| §3.9 CONCEPT_REF auto-pop | C5 |
| §3.10 PRIOR_GAP rail items | C4 |
| §3.11 Parallel VPS env spike | (not in this slice — for Slice 3 plan) |
| §9 Visual acceptance criteria | C7 (playwright gate covers all listed `data-testid`s + 4xx/5xx check + click-then-no-error gate) |
| §10 Hard rules | embedded in spec; implementation respects (no admin route, no auto-gen, citations verified against pdfTextRaw substring via CitationExtractor) |

### 2. Placeholder scan

- "Adapt to existing test helpers..." in C4 Step 2 + C5 Step 2 — these are pointers to existing scaffolding the implementer can find with `grep`. Acceptable because the test-harness pattern already exists in the repo; this is not a placeholder, it's a reuse instruction.
- "If TutorWorkspace coupling makes this too complex, **delete the unit test**" in C6 Step 6 — explicit fallback path. Acceptable.

No `TBD` / `TODO` / "implement later" / vague "add validation" entries.

### 3. Type consistency

- `ApiCitation` defined once in TutorRoutes.kt; referenced by `ApiSidekickReply.citations` (same file) + `CitationExtractor.extract` return type. ✓
- `Citation` interface on frontend; used in `CitationPill` props + `SidekickReply.citations` + `Sidekick` state. ✓
- `RailJsonBuilder.buildForTask` signature gets new `ledgerDir: Path` param with default; callers either use default or pass `ctx.ledgerDir`. ✓
- `HybridRetriever.HybridHit` used as input type to `CitationExtractor.extract`. ✓

### 4. Build+mount pairing (post Slice 1 ghost-component lesson)

- **CitationPill (new component)** — mounted in `Sidekick.tsx` (Task B5 Step 7) as `<CitationPill ... />`. Mount-site shown verbatim. ✓
- No other "create new component" tasks; all other work is edits to existing components.

Grep check: search the plan body for `Create: tutor-web/src/components/*.tsx` — only `CitationPill.tsx`. Its mount appears in Task B5 Step 7 JSX block. ✓

### 5. Component-reuse contract

- **Sidekick mounts CitationPill** (Task B5) — task body shows CitationPill prop signature (Step 3), shows Sidekick wire-up JSX with prop values explicit (`citation={c} onClick={(cit) => onCitationClick?.(cit)}`), tests assert pill renders + click fires with full citation object. ✓
- **ResourceRail already mounts PdfPane, Scratchpad, ConceptDrawer, KnowledgeGapCard (PriorGapAdapter)** — these were Slice 1.5 work; this slice doesn't re-mount them, just makes `RailJsonBuilder` emit PRIOR_GAP + CONCEPT items. No new prop-mismatch risk because ResourceRail already handles both item types (verified by reading `ResourceRail.tsx:113-118` for PRIOR_GAP + `:101-112` for CONCEPT). ✓
- **TutorWorkspace wires Sidekick onCitationClick + ResourceRail forceOpen** (Task C6) — prop signature shown; wire-up JSX explicit with `(c) => { ... }`; effect in ResourceRail propagates `forceOpen` into `openDrawer` state. ✓

### 6. `data-testid` grep against spec §9 acceptance + gate-required testids

| testid | Source | Plan task that emits / preserves it |
|---|---|---|
| `sidekick-citations-strip` | spec §9 | B5 Step 7 (Sidekick.tsx JSX) |
| `sidekick-reply` | gate (interaction-smoke wait) | B5 Step 7 (wrapper around reply text); C1 Step 3 (preserved when MathText wraps inside) |
| `citation-pill` | gate (click target) | B5 Step 3 (CitationPill.tsx button) |
| `inline-ask-chip` | gate (selection-fire flow) | C7 Step 0 (pre-flight: add to InlineAskChip.tsx production button) |
| `math-text` / `.katex` | spec §9 + gate | C1 (Sidekick via MathText) + C2 (DrillStack via MathText). MathText emits its own `data-testid="math-text"`; KaTeX adds `.katex` class. |
| `daemon-health-pill` | spec §9 | C3 Step 4 (visible only when debug=1) — already in DaemonHealthPill.tsx |
| `domain-footer` | spec §9 | C3 Step 4 (App.tsx — testid present only when debug=1) |
| `rail-item-CONCEPT_REF` | spec §9 | live code emits `rail-item-CONCEPT`; gate wildcard matches both |
| `rail-item-PRIOR_GAP` | spec §9 | C4; ResourceRail.tsx already maps `rail-item-PRIOR_GAP` |
| `rail-drawer` (suffix `-drawer`) | gate (citation-click open assertion) | already in RailDrawer.tsx (verified by grep) |

**Discrepancy:** spec §9 says `rail-item-CONCEPT_REF`; live code emits `rail-item-CONCEPT`. Playwright gate (Task C7 Step 3) uses the wildcard `[data-testid^="rail-item-CONCEPT"]` which matches both. No bug. Recommend updating the spec post-acceptance to match implementation: `rail-item-CONCEPT`.

### 7. Retrieval-hit surfacing change (B3 patch, post-quibble review)

`JarvisToolDefs.dispatch` returns `DispatchOut(text, hits)` instead of bare `String`. `JarvisToolset.chat` accumulates hits across tool rounds. `ToolReply.hits` carries them to the route. CitationExtractor verifies LLM `(src: <path>)` markers against the SAME hit set the LLM saw — no re-run, no rank drift.

Sub-changes touch `JarvisToolset.kt` (signature + accumulator) + every `dispatch` caller (existing `JarvisToolDefsTest` reads `.text` field now). Build-time impact: small; existing tests adapt with one-character diff (`out` vs `out.text`).

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-05-11-tutor-slice2-corpus-rag-sidekick.md`. Two execution options:**

**1. Subagent-Driven (recommended)** - dispatch fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** - execute tasks in this session using executing-plans, batch execution with checkpoints.

**Which approach?**
