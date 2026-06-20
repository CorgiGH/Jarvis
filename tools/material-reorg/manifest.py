#!/usr/bin/env python3
"""
material-reorg manifest tool — NON-DESTRUCTIVE.

Produces, for the raw material corpus, three review artifacts and NOTHING ELSE:
  out/manifest.json    one record per file (hash, classification, planned target, ...)
  out/move-map.csv     source,target,action[keep|drop|derived],reason
  out/dry-run-report.md aggregated human-review report (subject x year matrix, dupes,
                        injective-gate result, anomalies)
plus supporting machine logs:
  out/dupe-log.csv     every dropped exact-dupe + the canonical it matched
  out/near-dups.csv    ADVISORY near-dup pairs (within subject,year) — NEVER auto-collapsed
  out/.hashcache.json  (path,size,mtime)->hash cache for idempotent/resumable re-runs

It MOVES NOTHING and DELETES NOTHING. A human reviews the report before any move
(a separate step). See build-review/2026-06-19-material-reorg-plan-for-council.md §7.

Usage:
  python tools/material-reorg/manifest.py                  # full pass
  python tools/material-reorg/manifest.py --sample 80      # first 80 files only
  python tools/material-reorg/manifest.py --subject RC     # only files classified RC
  python tools/material-reorg/manifest.py --no-cache       # ignore the hash cache
  python tools/material-reorg/manifest.py --jobs 8         # worker pool size
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
import re
import subprocess
import sys
from collections import defaultdict
from multiprocessing import Pool, cpu_count

# --------------------------------------------------------------------------- #
# Roots — resolved absolute so the tool is cwd-independent.
# --------------------------------------------------------------------------- #
HERE = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.abspath(os.path.join(HERE, os.pardir, os.pardir))
OUT_DIR = os.path.join(HERE, "out")

# The corpus dumps and the un-migrated current-year survey.
MATERIAL_ROOT = os.path.join(REPO_ROOT, "material")
SURVEY_ROOT = os.path.expanduser(os.path.join("~", "Downloads", "_material_survey"))

# Target tree prefix for the planned move-map. The reorg ONLY touches the archive layer;
# material/current/ (the freshly-pulled up-to-date material) is kept separate and is
# excluded from the walk (see walk_corpus).
TARGET_PREFIX = "material/archive"

# --------------------------------------------------------------------------- #
# Extension buckets.
# --------------------------------------------------------------------------- #
PDF_EXTS = {".pdf"}
TEXT_EXTS = {".md", ".txt", ".csv", ".html", ".htm",
             ".cpp", ".c", ".h", ".hpp", ".alk", ".r", ".js", ".jsx",
             ".ts", ".tsx", ".py", ".java", ".json", ".rtf"}
BINARY_EXTS = {".png", ".jpg", ".jpeg", ".gif", ".zip", ".rdata", ".rda",
               ".mp3", ".mp4", ".docx", ".pptx", ".xlsx", ".doc", ".ppt", ".xls"}

CODE_EXTS = {".cpp", ".c", ".h", ".hpp", ".alk", ".r", ".java", ".py"}

# --------------------------------------------------------------------------- #
# Regexes.
# --------------------------------------------------------------------------- #
# Year tokens — digit-bounded so they don't match inside longer digit runs
# (e.g. the '2054' inside a facebook image name '62054912_...jpg').
YEAR_RANGE_RE = re.compile(r"(?<!\d)20\d\d[-_]20\d\d(?!\d)")   # 2020-2021 / 2020_2021
YEAR_SINGLE_RE = re.compile(r"(?<!\d)20\d\d(?!\d)")
PAREN_DUP_RE = re.compile(r"\(\d+\)")               # "foo (1).pdf" re-download suffix
WS_RE = re.compile(r"\s+")
FRONTMATTER_RE = re.compile(r"^---\s*\n(.*?)\n---\s*\n", re.DOTALL)
FM_SOURCE_PDF_RE = re.compile(r"^source_pdf:\s*(.+?)\s*$", re.MULTILINE)
FM_SHA256_RE = re.compile(r"^sha256:\s*([0-9a-fA-F]{64})\s*$", re.MULTILINE)

NEAR_EMPTY_CHARS = 20  # pdftotext output shorter than this -> byte fallback (scanned)

# --------------------------------------------------------------------------- #
# Classification tables.
# --------------------------------------------------------------------------- #
# The ARCHIVE is BScINFO (CS) material. Decision A: keep it FAITHFUL to its CS source —
# SO and RC are TWO separate CS subjects, kept separate here. (Alex's combined IA subject
# SORC=AI1202 lives only in material/current/, not in the archive.) A small SORC bucket
# exists only for material explicitly labelled combined "SO&RC"/"SO-RC".
SUBJECTS = ["ALO", "PA", "POO", "PS", "SO", "RC", "SORC"]

# Subject token -> canonical subject. Order matters: longest/most-specific first.
# Tokens matched as path segments (case-insensitive) or substrings of segments.
SUBJECT_ALIASES = [
    ("pa_ad_2026", "PA"),
    ("proiectarea algoritmilor", "PA"),
    ("proiectarea-algoritmilor", "PA"),
    ("pa_y1", "PA"),
    ("so_vidrascu", "SO"),
    ("programare orientata obiect", "POO"),
    ("programare-orientata-obiect", "POO"),
    ("sisteme de operare", "SO"),
    ("retele", "RC"),
    ("rețele", "RC"),
    ("algebra-liniara", "ALO"),
    ("so-rc", "SORC"),
    ("so&rc", "SORC"),
    ("sorc", "SORC"),
    ("alo", "ALO"),
    ("poo", "POO"),
    ("pa", "PA"),
    ("ps", "PS"),
    ("so", "SO"),
    ("rc", "RC"),
]

# Optional/elective marker — flagged, subject still derived from rest of path.
OPTIONAL_MARKERS = ["curs_optional_oob", "optional"]

# type keyword -> canonical type. Checked in order; first hit wins.
TYPE_KEYWORDS = [
    ("seminar", "seminar"),
    ("laborator", "laborator"),
    ("labs", "laborator"),
    ("lab_", "laborator"),
    ("/lab", "laborator"),
    ("examen", "examen"),
    ("exam", "examen"),
    ("partial", "examen"),
    ("specimen", "examen"),
    ("tema", "tema"),
    ("teme", "tema"),
    ("hw", "tema"),
    ("teste", "test"),
    ("test", "test"),
    ("curs", "curs"),
    ("course", "curs"),
    ("lecture", "curs"),
    ("carte", "carte"),
    ("books", "carte"),
    ("book", "carte"),
]


# --------------------------------------------------------------------------- #
# Helpers.
# --------------------------------------------------------------------------- #
def posix(p: str) -> str:
    return p.replace("\\", "/")


def rel_to_repo(abspath: str) -> str:
    """Relative POSIX path from the repo root; survey files keep an absolute-ish tag."""
    try:
        return posix(os.path.relpath(abspath, REPO_ROOT))
    except ValueError:
        return posix(abspath)


def normalize_ws(text: str) -> str:
    return WS_RE.sub(" ", text).strip()


def strip_frontmatter(text: str):
    """Return (body_without_frontmatter, source_pdf_or_None, sha256_or_None)."""
    m = FRONTMATTER_RE.match(text)
    if not m:
        return text, None, None
    block = m.group(1)
    src = FM_SOURCE_PDF_RE.search(block)
    sha = FM_SHA256_RE.search(block)
    body = text[m.end():]
    return body, (src.group(1).strip() if src else None), \
        (sha.group(1).lower() if sha else None)


def byte_sha256(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def text_sha256(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8", "replace")).hexdigest()


def pdf_to_text(path: str):
    """Return (text, returncode). Never raises; argv as list (paths w/ spaces safe)."""
    try:
        r = subprocess.run(
            ["pdftotext", "-q", "-enc", "UTF-8", path, "-"],
            capture_output=True, timeout=120,
        )
        return r.stdout.decode("utf-8", "replace"), r.returncode
    except Exception:
        return "", 1


# --------------------------------------------------------------------------- #
# Per-file hashing — runs in worker processes.
# --------------------------------------------------------------------------- #
def hash_file(path: str):
    """
    Returns dict: content_hash, hash_method[text|byte|scanned],
    frontmatter_source_pdf, frontmatter_sha256, error.
    """
    ext = os.path.splitext(path)[1].lower()
    res = {
        "content_hash": None,
        "hash_method": None,
        "frontmatter_source_pdf": None,
        "frontmatter_sha256": None,
        "error": None,
    }
    try:
        if ext in PDF_EXTS:
            text, rc = pdf_to_text(path)
            stripped = text.strip()
            if rc != 0 or len(stripped) < NEAR_EMPTY_CHARS:
                # nonzero exit OR empty/near-empty -> byte fallback.
                res["content_hash"] = byte_sha256(path)
                res["hash_method"] = "scanned"  # = byte-hashed because unextractable
            else:
                res["content_hash"] = text_sha256(normalize_ws(text))
                res["hash_method"] = "text"
        elif ext in TEXT_EXTS:
            with open(path, "rb") as f:
                raw = f.read()
            text = raw.decode("utf-8", "replace")
            body, src_pdf, fm_sha = strip_frontmatter(text)
            res["frontmatter_source_pdf"] = src_pdf
            res["frontmatter_sha256"] = fm_sha
            res["content_hash"] = text_sha256(normalize_ws(body))
            res["hash_method"] = "text"
        else:
            # png/jpg/zip/rdata/other binary (and unknown exts) -> byte.
            res["content_hash"] = byte_sha256(path)
            res["hash_method"] = "byte"
    except Exception as e:  # corrupt/locked file — record, never crash the pass.
        res["error"] = "%s: %s" % (type(e).__name__, e)
        try:
            res["content_hash"] = byte_sha256(path)
            res["hash_method"] = "byte"
        except Exception:
            res["content_hash"] = "ERROR-" + text_sha256(path)
            res["hash_method"] = "byte"
    return res


def hash_worker(args):
    path, size, mtime = args
    out = hash_file(path)
    out["_path"] = path
    out["_size"] = size
    out["_mtime"] = mtime
    return out


# --------------------------------------------------------------------------- #
# Classification (deterministic-from-path).
# --------------------------------------------------------------------------- #
def classify_subject(relpath_lower: str):
    """Return (subject, optional_flag)."""
    optional = any(m in relpath_lower for m in OPTIONAL_MARKERS)
    segs = relpath_lower.split("/")
    seg_set = set(segs)
    # First try exact path-segment match (most reliable), then substring.
    for token, subj in SUBJECT_ALIASES:
        if token in seg_set:
            return subj, optional
    for token, subj in SUBJECT_ALIASES:
        # Match token as a path segment substring (handles "so-rc", "pa_ad_2026_lectures").
        for s in segs:
            if token in s and _token_is_subjecty(token, s):
                return subj, optional
    return "_neclasificat", optional


def _token_is_subjecty(token: str, seg: str) -> bool:
    """
    Guard short 2-char tokens (pa/ps/so/rc/po) against false hits inside words
    like 'specimen' (contains nothing) or 'support'. Require the token to sit at
    a word boundary within the segment.
    """
    if len(token) > 3:
        return True
    return bool(re.search(r"(?:^|[ _\-./])" + re.escape(token) + r"(?:$|[ _\-./0-9])", seg))


def classify_type(relpath_lower: str, ext: str) -> str:
    if ext in CODE_EXTS:
        return "cod"
    for token, typ in TYPE_KEYWORDS:
        if token in relpath_lower:
            return typ
    return "alt"


def extract_year(relpath: str) -> str:
    """
    Year comes from PARENT DIRECTORIES only — never the basename. Course material is
    sorted into year-named folders (curs_2020-2021/, examen_2023-2024/); filenames
    carry digit-runs that produce false years. No year-bearing parent dir -> undated.
    """
    dirpart = posix(os.path.dirname(relpath))
    m = YEAR_RANGE_RE.search(dirpart)
    if m:
        return m.group(0).replace("_", "-")
    m = YEAR_SINGLE_RE.search(dirpart)
    if m:
        return m.group(0)
    return "nedatat"


def is_study_guide(relpath_lower: str) -> bool:
    return "raw-corpus-study-guides" in relpath_lower


# --------------------------------------------------------------------------- #
# Target-path planning.
# --------------------------------------------------------------------------- #
SAFE_SEG_RE = re.compile(r"[^A-Za-z0-9._\- ]+")


def context_segments(relpath: str, subject: str):
    """
    Path segments between the dump root and the basename, used as disambiguating
    context in the target path (and for injectivity tie-breaks). Drops the dump-root
    + subject + type marker noise; keeps year/model/P1 style folders.
    """
    segs = posix(relpath).split("/")
    # Drop leading material/<dump>/ or the survey root tag.
    # Keep everything that isn't pure structural noise.
    drop = {"material", "raw-corpus-secondbrain", "raw-corpus-md",
            "raw-corpus-sorc", "raw-corpus-study-guides", "_fii", "_gdrive",
            "files", "site", "study_guide", "courses", "labs", "courses_local",
            "labs_local", "local_lectures", "local_extras", "external",
            "_material_survey", "downloads",
            # subject + type marker segments — already encoded as named path parts,
            # so dropping them from the context avoids redundant doubling
            # (e.g. material/RC/.../curs/RC/Curs/...).
            "alo", "pa", "poo", "ps", "so", "rc", "sorc",
            "pa_y1", "so_vidrascu", "pa_ad_2026_lectures",
            "pa_ad_2026_seminars", "proiectarea algoritmilor",
            "programare orientata obiect",
            "curs", "seminar", "examen", "tema", "teme", "teste"}
    keep = []
    for s in segs[:-1]:  # exclude basename
        sl = s.lower()
        if sl in drop:
            continue
        keep.append(s)
    return keep


def plan_target(rec: dict) -> str:
    """
    Planned target path (string, not created):
      material/<SUBJ>/<year|nedatat>/<type>/<context.../><basename>
    Study-guide jsx/js/site-scrape -> material/_derived/...
    """
    subject = rec["subject"]
    typ = rec["type"]
    year = rec["year"]
    basename = rec["basename"]
    relpath = rec["relpath"]
    ext = rec["ext"]

    # _derived routing: ONLY jsx/js/site-scrape assets from study-guides auto-route.
    # Study-guide PDFs are NOT auto-quarantined (req 7) — they classify normally and
    # are surfaced as an anomaly for human decision.
    if rec["study_guide"] and ext in {".jsx", ".js", ".html", ".htm"}:
        ctx = context_segments(relpath, subject)
        parts = [TARGET_PREFIX, "_derived"] + ctx + [basename]
        return posix("/".join(parts))

    if subject == "_neclasificat":
        ctx = context_segments(relpath, subject)
        parts = [TARGET_PREFIX, "_neclasificat"] + ctx + [basename]
        return posix("/".join(parts))

    # It's all one archive sorted by folder-year. No "current year" concept:
    # year-named folder -> that year; no year in parent dirs -> undated.
    year_seg = year  # "2020-2021" / "2023" / "nedatat"

    ctx = context_segments(relpath, subject)
    parts = [TARGET_PREFIX, subject, year_seg, typ] + ctx + [basename]
    return posix("/".join(parts))


# --------------------------------------------------------------------------- #
# Walk corpus.
# --------------------------------------------------------------------------- #
def walk_corpus():
    """
    Yield (abspath, is_survey) for every ARCHIVE file. Prunes material/current/ and
    material/archive/ (the already-pulled current layer + any prior move output) so the
    reorg only ever processes the raw archive dumps.
    """
    for root, is_survey in ((MATERIAL_ROOT, False), (SURVEY_ROOT, True)):
        if not os.path.isdir(root):
            continue
        for dp, dirs, files in os.walk(root):
            if os.path.abspath(dp) == os.path.abspath(MATERIAL_ROOT):
                dirs[:] = [d for d in dirs if d not in ("current", "archive")]
            for fn in files:
                yield os.path.join(dp, fn), is_survey


# --------------------------------------------------------------------------- #
# Exact-dupe collapse — same content_hash AND same format(ext-class),
# never across (subject, year). Canonical-keep policy per spec.
# --------------------------------------------------------------------------- #
def format_class(ext: str) -> str:
    if ext in PDF_EXTS:
        return "pdf"
    if ext in {".md"}:
        return "md"
    if ext in {".txt"}:
        return "txt"
    return ext.lstrip(".") or "noext"


def canonical_rank(rec: dict):
    """
    Sort key — SMALLER is MORE canonical (kept). Policy:
      1. richer folder-context (path has a year token) > loose path
      2. non-(1)-suffixed > (1)-suffixed re-download
      3. shorter path (final tiebreak)
    """
    has_year = 0 if YEAR_RANGE_RE.search(rec["relpath"]) or \
        YEAR_SINGLE_RE.search(rec["relpath"]) else 1
    is_paren = 1 if PAREN_DUP_RE.search(rec["basename"]) else 0
    return (has_year, is_paren, len(rec["relpath"]), rec["relpath"])


def collapse_exact_dupes(records):
    """
    Mutates records: sets action/dupe_of/reason for exact dupes.
    Cluster key = (content_hash, format_class, subject, year). Within a cluster
    keep the canonical; the rest become action='drop'.
    Returns the dupe-drop log rows.
    """
    clusters = defaultdict(list)
    for r in records:
        key = (r["content_hash"], format_class(r["ext"]), r["subject"], r["year"])
        clusters[key].append(r)

    dupe_log = []
    for key, group in clusters.items():
        if len(group) < 2:
            continue
        group_sorted = sorted(group, key=canonical_rank)
        canonical = group_sorted[0]
        for dropped in group_sorted[1:]:
            dropped["action"] = "drop"
            dropped["dupe_of"] = canonical["relpath"]
            dropped["reason"] = "exact-dupe (same content+format+subject+year) of %s" \
                % canonical["relpath"]
            dupe_log.append({
                "dropped": dropped["relpath"],
                "canonical": canonical["relpath"],
                "content_hash": canonical["content_hash"],
                "format": format_class(canonical["ext"]),
                "subject": canonical["subject"],
                "year": canonical["year"],
            })
    return dupe_log


# --------------------------------------------------------------------------- #
# md <-> pdf linkage (metadata only; both kept).
# --------------------------------------------------------------------------- #
def link_md_to_pdf(records):
    """
    For each .md, set md_link = 'derived-of:<pdf relpath>' or 'md-native'.
    Linkage oracle (in priority order):
      1. frontmatter sha256 matches a pdf's byte/scanned content_hash in the manifest
         (the source_pdf byte-sha is stored in frontmatter sha256, verified grounded).
      2. frontmatter source_pdf basename matches a pdf basename.
    Non-md files get md_link=None.
    """
    # Index pdfs by their byte/scanned content_hash and by basename.
    pdf_by_hash = defaultdict(list)
    pdf_by_basename = defaultdict(list)
    for r in records:
        if r["ext"] in PDF_EXTS:
            pdf_by_hash[r["content_hash"]].append(r)
            pdf_by_basename[r["basename"].lower()].append(r)

    for r in records:
        if r["ext"] != ".md":
            continue
        fm_sha = r.get("frontmatter_sha256")
        fm_src = r.get("frontmatter_source_pdf")
        link = None
        # 1. frontmatter sha256 -> pdf whose content_hash (byte or scanned) equals it.
        if fm_sha and fm_sha in pdf_by_hash:
            link = pdf_by_hash[fm_sha][0]["relpath"]
        # 2. fall back to source_pdf basename match.
        if link is None and fm_src:
            base = posix(fm_src).split("/")[-1].lower()
            if base in pdf_by_basename:
                link = pdf_by_basename[base][0]["relpath"]
        r["md_link"] = ("derived-of:" + link) if link else "md-native"


# --------------------------------------------------------------------------- #
# Injective move-map gate (hard fail). Disambiguate by appending context.
# --------------------------------------------------------------------------- #
def enforce_injective(kept):
    """
    kept = list of kept records (action in {keep, derived}). Each has rec['target'].
    Returns (status, collisions) where status in {'PASS','FAIL'} and collisions is a
    list of {target, sources:[...]} for any target still colliding after disambiguation.
    Mutates rec['target'] when disambiguation is applied.
    """
    def build_index(recs):
        idx = defaultdict(list)
        for r in recs:
            idx[r["target"]].append(r)
        return idx

    idx = build_index(kept)
    colliding = {t: rs for t, rs in idx.items() if len(rs) > 1}

    # Disambiguate: for each colliding target, append source context segments until
    # distinct. We append progressively deeper parent segments of the source path.
    for target, recs in list(colliding.items()):
        for r in recs:
            segs = posix(r["relpath"]).split("/")
            # parent chain excluding basename, deepest-first
            parents = segs[:-1]
            basename = segs[-1]
            # find smallest suffix of parents that disambiguates within the group
            chosen = None
            for depth in range(1, len(parents) + 1):
                ctx = parents[-depth:]
                candidate = posix("/".join(
                    [os.path.dirname(target).replace("\\", "/")]
                    + ctx + [basename]
                ))
                chosen = candidate
                # uniqueness checked globally after loop; here just take deepest needed
            # Use full parent chain context appended (guaranteed maximal disambiguation
            # since source paths are unique). Insert context dir before basename.
            ctx_dir = "__".join(SAFE_SEG_RE.sub("_", p) for p in parents[-3:]) or "ctx"
            base_dir = os.path.dirname(target).replace("\\", "/")
            r["target"] = posix("/".join([base_dir, ctx_dir, basename]))

    # Re-check globally after disambiguation.
    idx2 = build_index(kept)
    collisions = []
    for t, rs in idx2.items():
        if len(rs) > 1:
            collisions.append({"target": t, "sources": [r["relpath"] for r in rs]})

    status = "PASS" if not collisions else "FAIL"
    return status, collisions


# --------------------------------------------------------------------------- #
# Near-dup ADVISORY pass — token-shingle Jaccard within (subject, year).
# --------------------------------------------------------------------------- #
def near_dup_pass(records, threshold=0.82):
    """
    Cheap MinHash-free Jaccard over word-3-shingles of normalized text, scoped within
    (subject, year), text-hashed files only. ADVISORY: returns flagged pairs, never
    mutates action. Skips files we couldn't text-extract (byte/scanned).
    Caps comparisons per bucket to stay cheap.
    """
    flagged = []
    by_bucket = defaultdict(list)
    for r in records:
        if r["hash_method"] != "text":
            continue
        if r.get("_shingles") is None:
            continue
        by_bucket[(r["subject"], r["year"])].append(r)

    for bucket, recs in by_bucket.items():
        if len(recs) < 2:
            continue
        # O(n^2) within bucket; cap to avoid pathological buckets.
        n = len(recs)
        if n > 400:
            continue  # too large to compare cheaply; skip (rare)
        for i in range(n):
            si = recs[i]["_shingles"]
            if not si:
                continue
            for j in range(i + 1, n):
                # exact dupes already collapsed; skip identical-hash pairs
                if recs[i]["content_hash"] == recs[j]["content_hash"]:
                    continue
                sj = recs[j]["_shingles"]
                if not sj:
                    continue
                inter = len(si & sj)
                if inter == 0:
                    continue
                union = len(si | sj)
                jac = inter / union if union else 0.0
                if jac >= threshold:
                    flagged.append({
                        "a": recs[i]["relpath"],
                        "b": recs[j]["relpath"],
                        "subject": bucket[0],
                        "year": bucket[1],
                        "jaccard": round(jac, 3),
                    })
    return flagged


def shingles_of(text: str, k=3):
    toks = normalize_ws(text).lower().split()
    if len(toks) < k:
        return frozenset(toks)
    return frozenset(" ".join(toks[i:i + k]) for i in range(len(toks) - k + 1))


# --------------------------------------------------------------------------- #
# Hash cache.
# --------------------------------------------------------------------------- #
def load_cache(use_cache: bool):
    if not use_cache:
        return {}
    path = os.path.join(OUT_DIR, ".hashcache.json")
    if os.path.isfile(path):
        try:
            with open(path, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            return {}
    return {}


def save_cache(cache: dict):
    path = os.path.join(OUT_DIR, ".hashcache.json")
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(cache, f)
    os.replace(tmp, path)


def cache_key(path, size, mtime):
    return "%s|%d|%d" % (posix(path), size, int(mtime))


# --------------------------------------------------------------------------- #
# Report writer.
# --------------------------------------------------------------------------- #
def write_report(records, dupe_log, inj_status, collisions, near_dups,
                 anomalies, hash_breakdown, args):
    kept = [r for r in records if r["action"] in ("keep", "derived")]
    dropped = [r for r in records if r["action"] == "drop"]

    # subject x year matrix over KEPT files.
    matrix = defaultdict(lambda: defaultdict(int))
    years = set()
    subs = set()
    for r in kept:
        y = r["year"]
        matrix[r["subject"]][y] += 1
        years.add(y)
        subs.add(r["subject"])
    year_cols = sorted(years, key=lambda x: (x == "nedatat", x))
    sub_rows = sorted(subs)

    lines = []
    lines.append("# Material reorg — DRY-RUN report (NON-DESTRUCTIVE)")
    lines.append("")
    lines.append("**What:** planned manifest + move-map + dupe/anomaly review for the "
                 "raw material corpus. Nothing was moved or deleted. · "
                 "**When:** generated by `tools/material-reorg/manifest.py` · "
                 "**Status:** review-before-move")
    lines.append("")
    scope = []
    if args.sample:
        scope.append("--sample %d" % args.sample)
    if args.subject:
        scope.append("--subject %s" % args.subject)
    lines.append("**Scope:** %s" % (" ".join(scope) if scope else "FULL corpus"))
    lines.append("")
    lines.append("## Totals")
    lines.append("")
    lines.append("| metric | count |")
    lines.append("|---|---|")
    lines.append("| files processed | %d |" % len(records))
    lines.append("| kept (keep+derived) | %d |" % len(kept))
    lines.append("| exact-dupes dropped | %d |" % len(dropped))
    lines.append("| hash method: text | %d |" % hash_breakdown.get("text", 0))
    lines.append("| hash method: byte | %d |" % hash_breakdown.get("byte", 0))
    lines.append("| hash method: scanned (byte-fallback) | %d |"
                 % hash_breakdown.get("scanned", 0))
    lines.append("| _neclasificat | %d |" % anomalies["neclasificat_count"])
    lines.append("| md derived-of pdf | %d |" % anomalies["md_derived_count"])
    lines.append("| md-native | %d |" % anomalies["md_native_count"])
    lines.append("")

    lines.append("## Injective move-map gate")
    lines.append("")
    lines.append("**Result: %s**" % inj_status)
    if collisions:
        lines.append("")
        lines.append("Unresolved many-to-one collisions (FAIL — must be fixed):")
        lines.append("")
        for c in collisions[:50]:
            lines.append("- `%s` <- %s" % (c["target"], ", ".join(c["sources"])))
        if len(collisions) > 50:
            lines.append("- ... %d more" % (len(collisions) - 50))
    else:
        lines.append("")
        lines.append("No unresolved collisions after context-disambiguation. "
                     "Every kept source maps to a distinct target.")
    lines.append("")

    lines.append("## Subject × year (kept files)")
    lines.append("")
    if sub_rows:
        header = "| subject | " + " | ".join(year_cols) + " | total |"
        lines.append(header)
        lines.append("|" + "---|" * (len(year_cols) + 2))
        for s in sub_rows:
            row_total = sum(matrix[s].values())
            cells = [str(matrix[s].get(y, 0)) for y in year_cols]
            lines.append("| %s | %s | %d |" % (s, " | ".join(cells), row_total))
    else:
        lines.append("_(no kept files in scope)_")
    lines.append("")

    lines.append("## Exact-dupe drop log")
    lines.append("")
    lines.append("Total dropped: **%d** (full machine log: `out/dupe-log.csv`)." % len(dupe_log))
    if dupe_log:
        lines.append("")
        for d in dupe_log[:30]:
            lines.append("- DROP `%s` → keep `%s`" % (d["dropped"], d["canonical"]))
        if len(dupe_log) > 30:
            lines.append("- ... %d more in dupe-log.csv" % (len(dupe_log) - 30))
    lines.append("")

    lines.append("## Anomalies (human decision needed)")
    lines.append("")
    lines.append("### Scanned / errored PDFs (byte-fallback, %d)"
                 % len(anomalies["scanned"]))
    for p in anomalies["scanned"][:25]:
        lines.append("- %s" % p)
    if len(anomalies["scanned"]) > 25:
        lines.append("- ... %d more" % (len(anomalies["scanned"]) - 25))
    lines.append("")
    lines.append("### Files with read/extract errors (%d)" % len(anomalies["errors"]))
    for p in anomalies["errors"][:25]:
        lines.append("- %s" % p)
    lines.append("")
    lines.append("### _neclasificat (no subject token, %d)"
                 % len(anomalies["neclasificat"]))
    for p in anomalies["neclasificat"][:25]:
        lines.append("- %s" % p)
    if len(anomalies["neclasificat"]) > 25:
        lines.append("- ... %d more" % (len(anomalies["neclasificat"]) - 25))
    lines.append("")
    lines.append("### Study-guide PDFs — route to subject tree or _derived? (%d)"
                 % len(anomalies["study_guide_pdfs"]))
    lines.append("")
    lines.append("These are NOT auto-quarantined (req 7). They classified into the "
                 "subject tree normally; confirm or re-route to `_derived/`:")
    for p in anomalies["study_guide_pdfs"][:25]:
        lines.append("- %s" % p)
    if len(anomalies["study_guide_pdfs"]) > 25:
        lines.append("- ... %d more" % (len(anomalies["study_guide_pdfs"]) - 25))
    lines.append("")
    lines.append("### Curs_Optional / elective flagged (%d)"
                 % len(anomalies["optional"]))
    for p in anomalies["optional"][:25]:
        lines.append("- %s" % p)
    lines.append("")
    lines.append("### Near-dup pairs — ADVISORY ONLY, never auto-collapsed (%d)"
                 % len(near_dups))
    lines.append("")
    lines.append("Scoped within (subject, year). Full list: `out/near-dups.csv`.")
    for d in near_dups[:25]:
        lines.append("- jaccard=%.3f  `%s`  ~  `%s`" % (d["jaccard"], d["a"], d["b"]))
    if len(near_dups) > 25:
        lines.append("- ... %d more in near-dups.csv" % (len(near_dups) - 25))
    lines.append("")

    lines.append("## Machine outputs")
    lines.append("")
    lines.append("- `out/manifest.json` — one record per file")
    lines.append("- `out/move-map.csv` — source,target,action,reason")
    lines.append("- `out/dupe-log.csv` — every dropped exact-dupe + its canonical")
    lines.append("- `out/near-dups.csv` — advisory near-dup pairs")
    lines.append("")

    with open(os.path.join(OUT_DIR, "dry-run-report.md"), "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")


# --------------------------------------------------------------------------- #
# Main.
# --------------------------------------------------------------------------- #
def main():
    ap = argparse.ArgumentParser(description="material-reorg dry-run manifest tool")
    ap.add_argument("--sample", type=int, default=0,
                    help="process only the first N files (fast iteration)")
    ap.add_argument("--subject", type=str, default=None,
                    help="keep only files classified to this subject (e.g. RC)")
    ap.add_argument("--jobs", type=int, default=0,
                    help="worker processes (default: cpu_count)")
    ap.add_argument("--no-cache", action="store_true",
                    help="ignore the (path,size,mtime) hash cache")
    args = ap.parse_args()

    os.makedirs(OUT_DIR, exist_ok=True)

    # 1. Walk + stat.
    print("[1/6] walking corpus ...", file=sys.stderr)
    entries = []  # (abspath, is_survey, size, mtime)
    for abspath, is_survey in walk_corpus():
        try:
            st = os.stat(abspath)
        except OSError:
            continue
        entries.append((abspath, is_survey, st.st_size, st.st_mtime))

    if args.sample:
        entries = entries[:args.sample]
    print("    %d files in scope" % len(entries), file=sys.stderr)

    # 2. Hash (parallel, cached).
    print("[2/6] hashing (pdftotext + sha256) ...", file=sys.stderr)
    cache = load_cache(not args.no_cache)
    to_hash = []
    cached_hits = {}
    for abspath, is_survey, size, mtime in entries:
        k = cache_key(abspath, size, mtime)
        if k in cache:
            cached_hits[abspath] = cache[k]
        else:
            to_hash.append((abspath, size, mtime))

    print("    %d cached, %d to hash" % (len(cached_hits), len(to_hash)),
          file=sys.stderr)

    hashed = {}
    if to_hash:
        jobs = args.jobs or max(1, cpu_count())
        with Pool(jobs) as pool:
            for out in pool.imap_unordered(hash_worker, to_hash, chunksize=8):
                p = out["_path"]
                hashed[p] = out
                cache[cache_key(p, out["_size"], out["_mtime"])] = {
                    "content_hash": out["content_hash"],
                    "hash_method": out["hash_method"],
                    "frontmatter_source_pdf": out["frontmatter_source_pdf"],
                    "frontmatter_sha256": out["frontmatter_sha256"],
                    "error": out["error"],
                }
        save_cache(cache)

    # 3. Build records.
    print("[3/6] classifying ...", file=sys.stderr)
    records = []
    for abspath, is_survey, size, mtime in entries:
        h = hashed.get(abspath) or cached_hits.get(abspath)
        if h is None:
            continue
        relpath = rel_to_repo(abspath) if not is_survey else \
            "_material_survey/" + posix(os.path.relpath(abspath, SURVEY_ROOT))
        rl = relpath.lower()
        ext = os.path.splitext(abspath)[1].lower()
        subject, optional = classify_subject(rl)
        typ = classify_type(rl, ext)
        year = extract_year(relpath)
        rec = {
            "path": relpath,
            "relpath": relpath,
            "abspath": abspath,
            "basename": os.path.basename(abspath),
            "ext": ext,
            "size": size,
            "content_hash": h["content_hash"],
            "hash_method": h["hash_method"],
            "frontmatter_source_pdf": h.get("frontmatter_source_pdf"),
            "frontmatter_sha256": h.get("frontmatter_sha256"),
            "error": h.get("error"),
            "subject": subject,
            "optional": optional,
            "type": typ,
            "year": year,
            "is_survey": is_survey,
            "study_guide": is_study_guide(rl),
            "md_link": None,
            "action": "keep",
            "dupe_of": None,
            "reason": "kept",
        }
        records.append(rec)

    if args.subject:
        records = [r for r in records if r["subject"] == args.subject]
        print("    filtered to subject=%s : %d files" % (args.subject, len(records)),
              file=sys.stderr)

    # 4. md<->pdf linkage; exact-dupe collapse.
    print("[4/6] linkage + exact-dupe collapse ...", file=sys.stderr)
    link_md_to_pdf(records)
    dupe_log = collapse_exact_dupes(records)

    # mark derived action for study-guide jsx/js/site assets (target -> _derived)
    for r in records:
        if r["action"] == "drop":
            continue
        if r["study_guide"] and r["ext"] in {".jsx", ".js", ".html", ".htm"}:
            r["action"] = "derived"
            r["reason"] = "study-guide derived asset -> _derived"

    # 5. Plan targets; injective gate (over kept only).
    print("[5/6] planning targets + injective gate ...", file=sys.stderr)
    for r in records:
        r["target"] = plan_target(r) if r["action"] != "drop" else ""
    kept = [r for r in records if r["action"] in ("keep", "derived")]
    inj_status, collisions = enforce_injective(kept)
    # re-pull targets after disambiguation already mutated in place

    # 6. Near-dup advisory; anomalies; outputs.
    print("[6/6] near-dup advisory + writing outputs ...", file=sys.stderr)
    # Build shingles only for text-extracted files (cheap; only when needed).
    # We re-extract text lazily for near-dup; to stay cheap we only shingle md/txt
    # (already in memory cheaply) — skip re-running pdftotext here.
    for r in records:
        r["_shingles"] = None
    _attach_shingles(records)
    near_dups = near_dup_pass(records)

    hash_breakdown = defaultdict(int)
    for r in records:
        hash_breakdown[r["hash_method"]] += 1

    anomalies = {
        "scanned": [r["relpath"] for r in records if r["hash_method"] == "scanned"],
        "errors": [r["relpath"] for r in records if r.get("error")],
        "neclasificat": [r["relpath"] for r in records
                         if r["subject"] == "_neclasificat"],
        "neclasificat_count": sum(1 for r in records
                                  if r["subject"] == "_neclasificat"),
        "study_guide_pdfs": [r["relpath"] for r in records
                             if r["study_guide"] and r["ext"] == ".pdf"
                             and r["action"] != "drop"],
        "optional": [r["relpath"] for r in records if r["optional"]],
        "md_derived_count": sum(1 for r in records
                                if r["ext"] == ".md"
                                and (r["md_link"] or "").startswith("derived-of:")),
        "md_native_count": sum(1 for r in records
                               if r["ext"] == ".md" and r["md_link"] == "md-native"),
    }

    write_outputs(records, dupe_log, near_dups)
    write_report(records, dupe_log, inj_status, collisions, near_dups,
                 anomalies, hash_breakdown, args)

    print("DONE. inj_gate=%s  processed=%d  kept=%d  dropped=%d  "
          "text=%d byte=%d scanned=%d  neclasificat=%d"
          % (inj_status, len(records), len(kept),
             len(records) - len(kept),
             hash_breakdown["text"], hash_breakdown["byte"],
             hash_breakdown["scanned"], anomalies["neclasificat_count"]),
          file=sys.stderr)
    print("Outputs in: %s" % OUT_DIR, file=sys.stderr)


def _attach_shingles(records):
    """Attach _shingles for md/txt/code/html text files (read from disk, strip FM)."""
    for r in records:
        if r["hash_method"] != "text":
            continue
        if r["ext"] in PDF_EXTS:
            # would require re-running pdftotext; skip to stay cheap (advisory pass)
            continue
        try:
            with open(r["abspath"], "rb") as f:
                text = f.read().decode("utf-8", "replace")
            body, _, _ = strip_frontmatter(text)
            r["_shingles"] = shingles_of(body)
        except Exception:
            r["_shingles"] = None


def write_outputs(records, dupe_log, near_dups):
    # manifest.json
    manifest = []
    for r in records:
        manifest.append({
            "path": r["path"],
            "ext": r["ext"],
            "size": r["size"],
            "content_hash": r["content_hash"],
            "hash_method": r["hash_method"],
            "frontmatter_source_pdf": r["frontmatter_source_pdf"],
            "frontmatter_sha256": r["frontmatter_sha256"],
            "md_link": r["md_link"],
            "subject": r["subject"],
            "optional": r["optional"],
            "type": r["type"],
            "year": r["year"],
            "is_survey": r["is_survey"],
            "study_guide": r["study_guide"],
            "target": r["target"],
            "action": r["action"],
            "dupe_of": r["dupe_of"],
            "error": r["error"],
        })
    with open(os.path.join(OUT_DIR, "manifest.json"), "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=1, ensure_ascii=False)

    # move-map.csv
    with open(os.path.join(OUT_DIR, "move-map.csv"), "w", encoding="utf-8",
              newline="") as f:
        w = csv.writer(f)
        w.writerow(["source", "target", "action", "reason"])
        for r in records:
            w.writerow([r["path"], r["target"], r["action"], r["reason"]])

    # dupe-log.csv
    with open(os.path.join(OUT_DIR, "dupe-log.csv"), "w", encoding="utf-8",
              newline="") as f:
        w = csv.writer(f)
        w.writerow(["dropped", "canonical", "content_hash", "format",
                    "subject", "year"])
        for d in dupe_log:
            w.writerow([d["dropped"], d["canonical"], d["content_hash"],
                        d["format"], d["subject"], d["year"]])

    # near-dups.csv
    with open(os.path.join(OUT_DIR, "near-dups.csv"), "w", encoding="utf-8",
              newline="") as f:
        w = csv.writer(f)
        w.writerow(["a", "b", "subject", "year", "jaccard"])
        for d in near_dups:
            w.writerow([d["a"], d["b"], d["subject"], d["year"], d["jaccard"]])


if __name__ == "__main__":
    main()
