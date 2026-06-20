# Material reorg — FINAL review before the destructive move

**Date:** 2026-06-19 (SESSION-83) · **Status:** dry-run complete, move NOT yet executed · **Author:** Claude (PM)
**Decision sought:** Go / No-Go on executing the irreversible move of a 2.6 GB un-git-tracked corpus. Surface any remaining data-loss vector or structural error BEFORE files are deleted. This is the last gate; after this the originals get removed.

This supersedes the first review (`council-1781820575`) — that plan evolved substantially after Alex corrected several wrong assumptions. Read this whole doc, then verify against the real artifacts (paths in §6).

---

## 1. What this job is now (evolved from the first council)

Organize the raw material corpus into TWO separated top-level layers (Alex's hard rule), non-destructively, then move:

```
material/
  current/   ← UP-TO-DATE material freshly pulled from the official subject sites. DONE + verified.
  archive/   ← the old CS (BScINFO) scrape, deduped + organized by subject/year/type. PLANNED (move pending).
```

## 2. Corrections applied since the first council (all from Alex)

1. **Keep BOTH formats** (PDF + md). The first plan's "PDF-only" was reversed — md is often the cleaner/only-legible copy. md↔pdf linkage is metadata only; both kept.
2. **Year comes from PARENT DIRECTORIES only**, never the basename (filename digit-runs like `62054912.jpg` produced false years e.g. "2054"). No year-bearing parent dir → `nedatat`. Digit-bounded year regex.
3. **No "current-year"/"2026"/download-date concept.** It is all an archive sorted by folder-year. (The only `2026` is the literal folder `PA_AD_2026`.) Download date / file age is irrelevant — Alex did not gather these; they are other people's archive.
4. **The archive is BScINFO (CS) material; Alex's program is BScIA (AI).** They are DIFFERENT programs. Grounded from both official fișe + the live course portal `edu.info.uaic.ro`:
   - Only **PA, PS, POO** are genuinely shared (same subject in both).
   - **SORC** (AI1202, "Sisteme de operare ȘI rețele") is ONE new IA subject — first year the IA bachelor exists, so NO archive for it.
   - **ALO** (AI1203, Linear Algebra & Optimisation) ≠ CS's "Fundamente Algebrice" — different subject.
5. **Decision A: the archive stays faithful to its CS source** — SO and RC are kept as SEPARATE subjects (`archive/SO/`, `archive/RC/`), because in BScINFO they ARE separate (SO=Y1, RC=Y2). Alex's combined SORC lives ONLY in `current/`. A tiny `archive/SORC/` bucket holds only material explicitly labelled combined ("SO&RC").
6. **`current/` was pulled this session** from the official sites (PA Google-Sites/Drive, PS+ALO+SORC edu.info, POO github) into `material/current/<SUBJECT>/<type>/`. Verified by magic bytes (not the curl log): **155/155 real files, 96 MB** — PA 34, PS 57, ALO 25, SORC 8, POO 31. Known site-side holes (not failures): SORC's SO-half is login-walled (401); POO/SORC fișe + RC labs are dead links (404).

## 3. Dry-run result (the move-map, NOT yet executed)

Tool: `tools/material-reorg/manifest.py` (non-destructive — writes manifest/move-map/report only). Latest run over the archive (excludes `current/`):
- **processed 5038** (4690 raw dumps + 348 `~/Downloads/_material_survey`), **kept 4345**, **exact-dupes dropped 693**.
- hash methods: text 4172, byte 788, scanned-PDF byte-fallback 78. `_neclasificat` 11 (README/logs/curriculum/scrape-index junk).
- **Injective move-map gate: PASS** — every kept source maps to a unique target.
- **4024 clean targets**; **321 (7%)** got a `__`-joined context-dir suffix because they are same-basename-but-different-content files (disambiguation, not overwrite).
- Target shape: `material/archive/<SUBJECT>/<year|nedatat>/<type>/<context…>/<basename>`. 186 subject/year/type folders.
- Subjects (kept): SO 1395, POO 997, PA 632, ALO 558, PS 474, RC 277, SORC 1, _neclasificat 11.

## 4. Dedup correctness (the council's prior central concern)

- Exact-dupe cluster key = (content_hash, format_class, subject, year). NEVER collapses across format (pdf vs md both kept), NEVER across (subject, year).
- Canonical-keep policy: richer folder-context (year token in path) > loose; non-`(1)`-suffix > `(1)` re-download; shorter path tiebreak.
- pdftotext: nonzero-exit OR empty/near-empty (<20 chars) → byte-sha256 (scanned PDFs never text-collide on empty).
- md frontmatter (`source_pdf`/`sha256`) stripped before hashing; near-dup pass (token-shingle Jaccard within subject,year) is ADVISORY ONLY (113 pairs flagged, never auto-collapsed).
- Paths handled via python os.walk / subprocess-list-argv (1156 space-paths safe; no shell globbing).

## 5. The PENDING destructive move (what needs Go/No-Go)

Not yet built. Planned mover, council-mandated safety order:
1. **`tar` the entire `material/` raw corpus + `~/Downloads/_material_survey` → an off-tree archive** (e.g. `~/material-backup-<ts>.tar`), record its sha256. (175 GB free on C:; corpus 2.6 GB.)
2. **Copy** each source → its `material/archive/…` target (per move-map.csv).
3. **Verify** each copied file's sha256 == source sha256. Abort on any mismatch.
4. Only after全 verify: **delete** the old dumps (`material/raw-corpus-*`) and `~/Downloads/_material_survey`.
5. Regenerate `material/_README.md`.

## 6. Artifacts to verify (open these — do not trust this prose)
- `tools/material-reorg/manifest.py` — the tool (read the dedup, injective-gate, year, classify logic).
- `tools/material-reorg/out/move-map.csv` — 4345 rows (source,target,action,reason).
- `tools/material-reorg/out/manifest.json` — full per-file records.
- `tools/material-reorg/out/dry-run-report.md` — aggregated report.
- `tools/material-reorg/out/dupe-log.csv` — 693 dropped dupes + canonicals.
- `material/current/` — the 155 pulled files (verify they're excluded from the move-map).
- `material/raw-corpus-*` + `~/Downloads/_material_survey` — the move sources.

## 7. Open questions / attack surface for the council
1. **Is the tar-then-copy-verify-delete order actually safe**, or is there a failure window? Should the delete be deferred entirely (keep originals until Alex confirms the new tree, delete next session)?
2. **Does the move-map truly exclude `material/current/`?** Confirm no current/ file appears as a source (would double-handle freshly-pulled material).
3. **The 321 `__`-disambiguated targets** — inspect a few; are they genuinely distinct content (correct) or a classification artifact bloating the tree?
4. **`_material_survey` deletion from `~/Downloads`** — it's outside the repo. Safe to delete after copy+verify (it's covered by the tar)? Or leave it?
5. **Cross-layer redundancy**: `current/PA` (official pull) and `archive/PA/2026` (the `_material_survey` PA_AD_2026 scrape) are ~the same content in two layers. Intended (layer separation) or wasteful?
6. **Any remaining silent-data-loss vector** in the move (filename collision after disambiguation, symlink, zero-byte, path-length limits on Windows, the 84 zip archives, the 11 `_neclasificat` junk files getting moved vs dropped)?
7. **Is keeping `current/` and `archive/` under the SAME gitignored `material/` correct**, given the tutor pipeline ingests from here later?
