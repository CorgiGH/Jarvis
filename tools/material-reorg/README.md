# material-reorg

**What:** NON-DESTRUCTIVE dry-run tool that produces a manifest + planned move-map +
aggregated review report for reorganizing the raw material corpus. · **When:** 2026-06-19
(SESSION-83) · **Status:** active

It **MOVES NOTHING and DELETES NOTHING.** It only writes JSON/CSV/markdown into `out/`.
A human reviews the report; the actual copy→verify→delete is a separate later step (not
this tool). Binding spec: `build-review/2026-06-19-material-reorg-plan-for-council.md` —
**§7 (post-council revision) is law.**

## Run

```bash
# fast self-test (first 80 files)
python tools/material-reorg/manifest.py --sample 80

# small real subject (RC — has scanned PDFs + md-with-frontmatter)
python tools/material-reorg/manifest.py --subject RC

# FULL pass (PM runs this after reviewing the code; ~1216 PDFs, slow first run)
python tools/material-reorg/manifest.py
```

Flags: `--sample N` (first N files) · `--subject X` · `--jobs N` (worker pool, default
cpu_count) · `--no-cache` (ignore hash cache).

## What it does

1. **Walk** `material/` (4 dumps) + `~/Downloads/_material_survey/` (current-year 2026)
   via `os.walk` (space/unicode safe).
2. **Content-hash** every file (parallel `multiprocessing.Pool`, cached by
   `(path,size,mtime)` in `out/.hashcache.json` so re-runs skip unchanged files):
   - **pdf** → `pdftotext -q -enc UTF-8 <f> -`; nonzero exit OR empty/near-empty (<20
     chars) → **byte-sha256** (method `scanned`); else normalize whitespace → sha256
     (method `text`).
   - **md/txt/code/r/csv/html** → strip leading YAML frontmatter, normalize, sha256
     (method `text`); also capture frontmatter `source_pdf:` + `sha256:`.
   - **png/jpg/zip/rdata/binary** → byte-sha256 (method `byte`).
3. **Link md↔pdf** (metadata only — both kept): frontmatter `sha256` matched against pdf
   byte-hashes, else `source_pdf` basename → `derived-of:<pdf>` or `md-native`.
4. **Collapse true exact-dupes** (same content-hash AND same format AND same
   subject+year): keep one canonical (year-context > loose; non-`(1)` > `(1)`; shorter
   path), the rest become `drop` in `dupe-log.csv`. Never across formats or
   (subject,year).
5. **Classify** deterministically from path: subject {ALO,PA,POO,PS,SO,RC} (+aliases
   PA_Y1→PA, SO_vidrascu→SO, Proiectarea Algoritmilor→PA, Programare Orientata
   Obiect→POO; Curs_Optional flagged); type {curs,seminar,laborator,examen,tema,test,
   carte,cod,alt}; year (`20\d\d[-_]?20?\d\d` / single `20\d\d` / `nedatat`). No subject
   token → `_neclasificat`.
6. **Plan target**:
   `material/<SUBJ>/<2026|arhiva/<an>|nedatat>/<type>/<context…>/<basename>`.
   Survey + 2025/2026-tagged → `2026/`. Study-guide jsx/js/html → `material/_derived/`
   (study-guide **PDFs are NOT auto-quarantined** — surfaced as an anomaly).
7. **Injective gate (HARD FAIL)**: assert source→target is injective; disambiguate by
   appending source-path context; re-check; report PASS/FAIL + any collisions.
8. **Near-dup ADVISORY pass**: token-3-shingle Jaccard within (subject,year) over
   text-extracted files; flagged in `near-dups.csv` — never auto-collapsed. (pHash for
   images is optional and currently skipped; see Deviations.)

## Outputs (`out/`, gitignored)

| file | contents |
|---|---|
| `manifest.json` | one record per file: path, ext, size, content_hash, hash_method, frontmatter_source_pdf, frontmatter_sha256, md_link, subject, type, year, target, dupe_of, error |
| `move-map.csv` | `source,target,action[keep\|drop\|derived],reason` |
| `dupe-log.csv` | every dropped exact-dupe + the canonical it matched |
| `near-dups.csv` | advisory near-dup pairs (subject,year,jaccard) |
| `dry-run-report.md` | aggregated: subject×year matrix, totals, dupe log, injective gate result, anomalies |
| `.hashcache.json` | resume cache (not for review) |

## Deviations from spec

- **Image pHash skipped.** Spec marks it OPTIONAL ("if PIL/imagehash unavailable,
  skip and note it"). Not implemented to avoid a new dependency; text near-dup
  (Jaccard) is implemented. Noted here per spec.
- **Near-dup pass shingles md/txt/code/html only**, not PDFs. Re-running `pdftotext`
  for every pairwise comparison is expensive; the advisory pass stays cheap by skipping
  PDF re-extraction. PDF exact-dupes are still fully caught in step 4 (text-hash).
  Buckets > 400 files are skipped for the O(n²) Jaccard (rare).
