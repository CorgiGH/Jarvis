#!/usr/bin/env python3
"""Ingest the os-study-guide curated content tree into archival.

This is the user's primary curated study material — bilingual course
JSON, test problem sets, source PDFs, reference texts, seminars,
practice instances. Should have been in archival from day 1.

Source: C:/Users/User/Desktop/SO/os-study-guide/src/content/{alo,oop,os,pa}/

Strategy:
- For each subject dir, walk recursively but skip:
    .curate/      — multi-stage curation pipeline intermediate output
                    (stage1-extraction, stage2-crossref, etc.); the final
                    courses/course-NN.json captures the result.
    media/        — binary images (course-media/<subject>/<lecture>/page-NN.png)
    course-media/ — same
    diagrams/     — image binaries
- For .json files: write a sidecar .md that pretty-prints with json.dumps
  indent=2 so [[search]] (which filters to *.md) finds the content.
- For .jsx / .js / .md / .txt / .partial.md: copy as-is.
- For .pdf (in source/ subdirs): pdftotext -layout to .md.

Subject-to-archival mapping:
  alo → /opt/jarvis/data/archival/_extras/ALO/study_guide/
  oop → /opt/jarvis/data/archival/_extras/POO/study_guide/
  os  → /opt/jarvis/data/archival/_extras/SO/study_guide_curated/
  pa  → /opt/jarvis/data/archival/_extras/PA/study_guide/

Usage:
    python3 tools/scrape-study-guide-content.py [OUT_DIR=./tmp-study-guide-scrape]
"""

from __future__ import annotations

import json
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path

OUT_DIR = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("./tmp-study-guide-scrape")
SKIP_EXISTING = "--no-skip-existing" not in sys.argv

SRC_BASE = Path("C:/Users/User/Desktop/SO/os-study-guide/src/content")

SUBJECT_MAP = {
    "alo": ("ALO", "study_guide"),
    "oop": ("POO", "study_guide"),
    "os":  ("SO",  "study_guide_curated"),
    "pa":  ("PA",  "study_guide"),
}

SKIP_DIR_NAMES = {".curate", "media", "course-media", "diagrams"}
PDF_EXTS = {".pdf"}
TEXT_EXTS = {".md", ".jsx", ".mjs", ".js", ".ts", ".tsx", ".txt", ".css", ".html"}
JSON_EXTS = {".json"}


def safe_name(name: str) -> str:
    return re.sub(r'[<>:"|?*]', "_", name)


def pdf_to_text(pdf_path: Path) -> str:
    try:
        proc = subprocess.run(
            ["pdftotext", "-layout", "-enc", "UTF-8", str(pdf_path), "-"],
            capture_output=True, timeout=120,
        )
        if proc.returncode == 0:
            return proc.stdout.decode("utf-8", errors="replace")
    except Exception:
        pass
    return ""


def json_to_md(path: Path) -> str:
    """Pretty-print the JSON so substring search hits its content."""
    try:
        data = json.loads(path.read_text(encoding="utf-8", errors="replace"))
        return json.dumps(data, indent=2, ensure_ascii=False)
    except Exception:
        return path.read_text(encoding="utf-8", errors="replace")


def should_skip(rel_path: Path) -> bool:
    return any(part in SKIP_DIR_NAMES for part in rel_path.parts)


def process_subject(subj_dir: Path, dest_root: Path,
                    failures: list[str]) -> dict[str, int]:
    counts = {"json": 0, "pdf": 0, "text": 0, "cached": 0, "skip": 0, "fail": 0}
    if not subj_dir.exists():
        return counts
    for src in subj_dir.rglob("*"):
        if not src.is_file():
            continue
        rel = src.relative_to(subj_dir)
        if should_skip(rel):
            counts["skip"] += 1
            continue
        ext = src.suffix.lower()
        # Mirror directory layout under dest_root
        dest_dir = dest_root / rel.parent
        dest_dir.mkdir(parents=True, exist_ok=True)
        bin_path = dest_dir / safe_name(src.name)

        # For .json: write a .md sidecar (search needs .md ext).
        if ext in JSON_EXTS:
            md_path = dest_dir / (safe_name(src.stem) + ".md")
            if SKIP_EXISTING and md_path.exists() and md_path.stat().st_size > 0:
                counts["cached"] += 1
                continue
            try:
                md_path.write_text(json_to_md(src), encoding="utf-8")
                counts["json"] += 1
            except Exception as e:
                failures.append(f"{src}: json convert failed: {e}")
                counts["fail"] += 1
            continue

        # For .pdf: text-extract to .md, also copy binary.
        if ext in PDF_EXTS:
            md_path = dest_dir / (safe_name(src.stem) + ".md")
            if SKIP_EXISTING and md_path.exists() and md_path.stat().st_size > 0:
                counts["cached"] += 1
                continue
            try:
                if not bin_path.exists():
                    shutil.copy2(src, bin_path)
                text = pdf_to_text(src)
                if text:
                    md_path.write_text(text + "\n", encoding="utf-8")
                    counts["pdf"] += 1
                else:
                    failures.append(f"{src}: pdftotext returned empty")
                    counts["fail"] += 1
            except Exception as e:
                failures.append(f"{src}: pdf process failed: {e}")
                counts["fail"] += 1
            continue

        # For .md/.jsx/etc: copy verbatim, but also create a .md alias if
        # the source isn't already .md (so search finds .jsx content).
        if ext in TEXT_EXTS:
            try:
                if SKIP_EXISTING and bin_path.exists() and bin_path.stat().st_size > 0:
                    counts["cached"] += 1
                    continue
                shutil.copy2(src, bin_path)
                if ext != ".md":
                    md_alias = dest_dir / (safe_name(src.stem) + f".{ext.lstrip('.')}.md")
                    md_alias.write_text(
                        src.read_text(encoding="utf-8", errors="replace"),
                        encoding="utf-8",
                    )
                counts["text"] += 1
            except Exception as e:
                failures.append(f"{src}: text copy failed: {e}")
                counts["fail"] += 1
            continue
        # Otherwise skip (binary).
        counts["skip"] += 1
    return counts


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    failures: list[str] = []
    total = {"json": 0, "pdf": 0, "text": 0, "cached": 0, "skip": 0, "fail": 0}
    t0 = time.time()
    for src_subj, (vps_subj, vps_kind) in SUBJECT_MAP.items():
        src_dir = SRC_BASE / src_subj
        dest = OUT_DIR / vps_subj / vps_kind
        c = process_subject(src_dir, dest, failures)
        for k, v in c.items():
            total[k] = total.get(k, 0) + v
        print(f"{vps_subj}/{vps_kind:<22} json={c['json']:>4} pdf={c['pdf']:>3} "
              f"text={c['text']:>4} cached={c['cached']:>4} "
              f"skip={c['skip']:>4} fail={c['fail']:>3}")
    elapsed = time.time() - t0
    if failures:
        (OUT_DIR / "_failures.log").write_text(
            "\n".join(failures) + "\n", encoding="utf-8"
        )
    print(f"\nTotal: json={total['json']} pdf={total['pdf']} text={total['text']} "
          f"cached={total['cached']} skip={total['skip']} fail={total['fail']}")
    print(f"Failures: {len(failures)} (see {OUT_DIR}/_failures.log)")
    print(f"Elapsed: {elapsed:.1f}s -- files at {OUT_DIR.resolve()}")


if __name__ == "__main__":
    main()
