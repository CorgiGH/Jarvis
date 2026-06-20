# Material corpus reorganization — plan for council review

**Date:** 2026-06-19 (SESSION-83) · **Status:** proposed, pre-execution · **Author:** Claude (PM)
**Decision sought from council:** is this reorganization approach correct, flawed-but-fixable, or wrong? Surface failure modes that would lose or corrupt material before we touch 2.6 GB of un-backed-up files.

---

## 1. Goal

Turn the current sprawling, duplicated raw-material corpus into a clean, per-subject, year-tagged tree that the digestion pipeline (`content/<SUBJECT>/`) can ingest deterministically. This is the **source layer** only — no digestion into KCs in this job.

## 2. Verified current state (grounded this session, not from memory)

Total `material/` = **4690 files**, 2.6 GB, gitignored (untracked, **no git safety net**). Four parallel dumps:

| Dump | Size | What it actually is |
|---|---|---|
| `raw-corpus-secondbrain/` | 2.3 GB | Bulk scrape. Contains `_fii/_gdrive/<SUBJ>/<TYPE>/<thing>_<YEAR>/` — **already organized by subject + type + year** (e.g. `POO/Examen/examen_2023-2024/`), including **scanned exam/seminar pages as png/jpg**. Also `_fii/<SUBJ>/{files,site}` where `site/` = raw website scrape (html + image assets). |
| `raw-corpus-md/` | 57 MB | Markdown conversions — filenames mostly identical to secondbrain PDFs (e.g. `lecture11_en.md` ↔ `lecture11_en.pdf`). Mostly format-dupes. |
| `raw-corpus-study-guides/` | 86 MB | **Our OWN prior derived study-guide build** — React `.jsx` widgets + curated md under `study_guide/`. NOT raw course material. |
| `raw-corpus-sorc/` | 123 MB | SO + RC bulk dump (SORC = one subject, two halves). |

Plus **not yet in `material/`**: `~/Downloads/_material_survey/` = the **current-year (2026)** clean course material pulled SESSION-82 (PA/AD-2026 30 lectures + 48 seminars + exam zip; PS 97 files; POO 27 files). Inert, un-migrated.

Extension census: 1216 pdf · 1795 md · 105 txt · 175 cpp · 111 c · 81 h · 48 alk · 31 r · 9 csv · 4 rdata · 86 jsx · 299 html · 404 png · 98 jpg · 84 zip.

Tooling confirmed present: `pdftotext`, `pdfinfo`, `python`.

Digested `content/` today (the downstream target, for context): PA = 8 KCs · ALO = 1 · PS = 0 · POO = 0 · SO-RC = 0.

## 3. Hard rules (Alex, this session — non-negotiable)

1. **Current-year (2026) material kept SEPARATE and year-tagged** — never mixed into the old-scrape pile.
2. **Dedup by CONTENT, not filename** — two files are dupes iff their content matches, regardless of name or format.
3. **Reorganize the mega-scrape by CONTEXT** — using folder names + filenames **and the contents inside** each file.
4. **Format dupes: keep PDF only.** When a PDF and its `.md`/`.txt` conversion are the same content, keep the PDF (canonical; pipeline re-extracts text via `pdftotext`). An md/txt is kept only when it is the **sole** copy of that content (md-native, no PDF).

## 4. Proposed method (deterministic-first; dedup is a non-LLM oracle)

### Phase 1 — Manifest (deterministic script, NO LLM)
Walk every file. Record: path, ext, size, mtime, parent-folder-context, year-extracted-from-path (regex on `20\d\d[-_]20\d\d` / `20\d\d`), and a **content-hash**:
- **pdf** → `pdftotext` → normalize whitespace → sha256. **If extraction is empty/near-empty (scanned PDF) → fall back to byte-sha256**, never text-hash (else all scanned PDFs collide on the empty string).
- **md / txt / code / r / csv / html** → normalize whitespace → sha256.
- **png / jpg / zip / rdata / binary** → byte-sha256.

Group by content-hash → exact-content dupe clusters. Output a machine-readable manifest (JSON + CSV).

### Phase 2 — Classify (workflow; deterministic-from-context first)
Assign each **unique** document: subject ∈ {ALO, PA, POO, PS, SO, RC}; type ∈ {curs, seminar, laborator, examen, tema, test, carte, cod, alt}; year (or `nedatat`). Most are derivable deterministically from the `_gdrive` path. Only the ambiguous remainder gets an LLM agent that reads a content snippet. (LLM used for *classification taste*, never for dedup-truth.)

### Phase 3 — Plan target tree (deterministic) → DRY-RUN move-map, Alex eyeballs BEFORE any move
```
material/<SUBJ>/2026/{curs,seminar,laborator,examen,...}     ← current curriculum
material/<SUBJ>/arhiva/<an>/...                              ← year from folder context
material/<SUBJ>/nedatat/...                                  ← year not derivable
material/_derived/        ← study-guide jsx/site scrape (our prior output, excluded from ingest)
material/_dupes-removed.log   ← every dropped file + the canonical it matched
material/_manifest.json       ← full hash/classify manifest
```
**Canonical-keep policy for a content-dupe cluster:** PDF > md > txt > other; within a tie, richer folder-context (year-tagged `_gdrive` path) > loose path; drop `(1)`-suffixed re-download copies.

### Phase 4 — Execute (only after Alex approves the dry-run)
Move files into the new tree (move, not copy — 2.6 GB). Append every dropped dupe to `_dupes-removed.log` (path + matched canonical) **before** removal. Regenerate `material/_README.md`. Reversible via the logged move-map.

## 5. Known risks / open questions for council

1. **Scanned-PDF false-dedup** — `pdftotext` returns empty on image-only PDFs; without the byte-hash fallback they'd all collide. Mitigation stated in Phase 1; council confirm it is sufficient.
2. **Cross-year identical content** — a lecture reused verbatim across years would content-hash as one dupe, collapsing two year-distinct copies. Question: dedup *within* a year-bucket only, keeping cross-year copies (year = meaningful metadata)? Or global dedup (lose the duplicate-year copy but keep the year in the canonical's path)? Leaning: **dedup within (subject, year) scope, preserve one copy per year.**
3. **No git safety net** — corpus is gitignored, the off-box VPS backup is stale (2026-06-03), and this is a destructive in-place move. Mitigation: should we `tar` the whole corpus to an off-tree archive before Phase 4? Council weigh cost (2.6 GB) vs irreversibility.
4. **Subject cross-listing** — RC vs SO boundary; optional/elective courses (`Curs_Optional_OOB`) mis-binned. Classifier must handle.
5. **md-native loss** — rule 4 must NOT drop an md that has no PDF twin. The keep-policy ordering handles this (md kept when sole cluster member), council confirm.
6. **"Same content, different format, BUT the md has extra value"** — e.g. a hand-cleaned md vs a messy auto-extract PDF. PDF-only rule could drop the better artifact. Is that an acceptable loss, or should we detect md-with-no-pdf-twin-but-richer separately? Leaning: accept the loss — PDFs are the authoritative source; the cleaned md, if it exists, is in `_derived` (study-guides), not the raw dumps.
7. **PM-delegation:** the heavy lifting (hashing, classify fan-out, move) runs as scripts + a workflow, not hand-done. Confirm the split is right.

## 6. What is explicitly NOT in this job
- No digestion into `content/` KCs (separate, gated, downstream).
- No deploy, no live-DB mutation.
- No deletion of the `_derived` study-guide output — quarantined, not destroyed.

---

## 7. REVISION — post-council (`council-1781820575`), Alex-ratified 2026-06-19

Council verdict = **FLAWED-but-fixable, confidence 9** (2 REJECT + 3 CONDITIONAL, all grounded in real file inspection). The following supersedes §4 rule 4 and §4 Phase-1/3/4 where they conflict. **All changes are mandatory before execution.**

### 7.1 Rule 4 REVERSED — keep BOTH formats (Alex ratified)
- **Do NOT drop md conversions.** Reasons (verified): (a) 998/1176 md files carry a `source_pdf:`/`sha256:`/`extracted_at:` YAML frontmatter, so PDF↔md text never hash-equals — rule 4 as written deduped **zero** files; (b) spec §2.3 documents many PDFs as garbled (ALO c01/c02 Unicode-broken, PS watermark noise) and `curate-tutor/SKILL.md:24` explicitly prefers the cleaner md sibling — PDF-only would delete the only legible copy to save ~57 MB (2% of corpus).
- **PDF↔md linkage** is recorded as metadata (not a deletion): parse the md's embedded `source_pdf` + `sha256` (a non-LLM oracle already in the files); where absent, strip YAML frontmatter then hash. The manifest marks each md as `derived-of <pdf>` or `md-native`. **Both copies are kept** in the tree; the link is advisory for the pipeline.
- **True exact-dupes still collapse** — same content, same format, different path (e.g. `A.pdf` + `A (1).pdf`, the 186 `(1)`-suffix re-downloads): keep one canonical, log the rest.

### 7.2 NON-DESTRUCTIVE execution (replaces §4 Phase-4 "move in place")
- `tar` the entire 2.6 GB corpus to an off-tree archive **before any change** (175 G free on C: — cost trivial). Record the tar path + its sha256.
- **Copy** into the new tree → **verify** every target's sha256 against its source → only **then** remove originals. Never in-place move. ("Reversible via move-map" was FALSE for any overwrite — this replaces it with verify-before-delete.)

### 7.3 Injective move-map gate (new hard gate, Phase 3)
- The move-map MUST be an injective source→target mapping. **HARD-FAIL** the dry-run on any many-to-one target (750 colliding basenames measured; `P1/P2/P3 2024` are distinct POO exams differing only by parent folder, `problema.pdf` ×8 → 5 distinct contents).
- Collisions are disambiguated by appending a context segment from the source path (e.g. `.../examen/2024/P1/problema.pdf`), **never** overwritten.
- Same gate applies to the `_material_survey` → `material/<SUBJ>/2026/` migration (172/329 survey basenames collide with existing material).

### 7.4 Hashing robustness (Phase 1)
- `pdftotext` **nonzero exit code OR empty/near-empty output → byte-sha256** (not text-hash). One real errored PDF found (`SO/.../P7_flock_web-ro.pdf` nonzero exit); ~5/121 sampled extract empty (scanned exams, ~50/1216 extrapolated) — all byte-distinct, must not collide.
- **Near-dup pass = ADVISORY ONLY** (flag for human review, never auto-collapse): MinHash/SimHash over text shingles + pHash/dHash for scanned png/jpg, scoped **within (subject, year)** so cross-year revisions are surfaced, not merged. Cross-year copies are KEPT (year = meaningful metadata).
- **Null-delimited iteration everywhere** (`find -print0` / `xargs -0` / `-z`): 1156 paths (24.6%) contain spaces (`Programare Orientata Obiect`, `Un test`, `Specimen Examen 2022.pdf`).

### 7.5 Classification (Phase 2) — mostly deterministic
- 4355/4690 files (92.9%) carry a subject token in their path. Aliases: `PA_Y1`→PA, `SO_vidrascu`→SO, `Curs_Optional_OOB`→ (optional, flag). LLM fan-out is the small remainder; files that still don't classify → `material/_neclasificat/` for a later pass (not forced).
- The reorg uses path context as **advisory input** for human browsability; the digestion pipeline still runs its OWN spec'd dedup + classification at the `content/` layer (INV-2.1 — the pipeline does not trust these paths as truth).

### 7.6 Study-guide dump — spot-check before quarantine
- `raw-corpus-study-guides/` holds 151 PDFs + 26 js besides the jsx/curated-md. Spot-check those 151 PDFs — if any are raw source (not our derived output), route to the subject tree, not `_derived/`.

### 7.7 Dry-run deliverable = AGGREGATED report (not a 4690-row flat list)
- Per-subject × per-year file counts; the full dupe-drop log; the injective-collision check result; a flagged-anomalies subset (near-dup pairs, unclassified, errored extractions, study-guide PDFs). Alex reviews this BEFORE Phase 4.

### 7.8 Sequence
Phase 1 (manifest, script) → Phase 2 (classify) → Phase 3 (move-map + injective gate, dry-run aggregated report) → **Alex approves** → `tar` → Phase 4 (copy → verify → delete) → regenerate `material/_README.md`.
