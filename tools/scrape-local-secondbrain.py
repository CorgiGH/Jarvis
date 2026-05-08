#!/usr/bin/env python3
"""One-time bulk ingest of local course material from
Desktop/Second brain/ + Desktop/SO/ into the VPS archival.

User's "all three" answer 2026-05-09 included a "deep dive into
what we actually have" — the earlier scrape-so-rc-content.py only
covered live SO+RC sites. This script walks the user's local mirror
of every subject and pushes them into /opt/jarvis/data/archival/_extras/
{SUBJECT}/{kind}/ where {kind} ∈ {courses, labs, hw, lectures}.

Source layout (verified by inventory agent 2026-05-09):
- C:/Users/User/Desktop/Second brain/Courses/{ALO,PA,POO,PS,RC}/  (PDFs)
- C:/Users/User/Desktop/Second brain/Labs/{ALO,PA,POO,PS,RC}/      (PDFs + HTML)
- C:/Users/User/Desktop/Second brain/HW/{ALO,PS}/                  (PDFs + R)
- C:/Users/User/Desktop/Second brain/PS HW/                        (PDFs + R)
- C:/Users/User/Desktop/SO/                                        (OS (1-11).pdf)
- C:/Users/User/Desktop/Second brain/                              (root SO+RC overview)

Target layout on VPS:
- /opt/jarvis/data/archival/_extras/{ALO,PA,POO,PS,SO}/{courses,labs,hw,lectures,local_lectures}/

For each PDF: pdftotext -layout, optional password (RC Curs needs
"so+rc" — same password also unlocks rc_c1-3.pdf locally).
For HTML: regex tag-strip.
For .R / .c / .sh / .py / .txt: copy verbatim.
For .docx / .ppt / .doc: skip with warning (no text extractor configured).

Re-runs are safe via SKIP_EXISTING (default on): skips files where the
target .md already exists and is non-empty. Pass --no-skip-existing to
force re-extraction.

After local extraction, scp the OUT_DIR contents to the VPS in a
hand-driven step (the script doesn't auto-scp because rsync would be
cleaner and I want to keep this script offline-safe).

Usage:
    python3 tools/scrape-local-secondbrain.py [OUT_DIR=./tmp-secondbrain-scrape]
"""

from __future__ import annotations

import re
import shutil
import subprocess
import sys
import time
from html import unescape
from pathlib import Path

OUT_DIR = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("./tmp-secondbrain-scrape")
SKIP_EXISTING = "--no-skip-existing" not in sys.argv

DESKTOP = Path("C:/Users/User/Desktop")
SB = DESKTOP / "Second brain"
SO_LOCAL = DESKTOP / "SO"

# Each entry: (source_dir, dest_subject, dest_kind, pdf_password_or_None,
#              max_depth_or_None, name_pattern_or_None).
# max_depth=0 means root-only (no recursion). name_pattern is a glob;
# None matches any file. SO_LOCAL needed both because Desktop/SO/ is
# also the parent of the os-study-guide React app + experiment/ dirs
# (node_modules), and rglob without filter ate 1.1 GB / 28k files in
# the first run.
SOURCES: list[tuple[Path, str, str, str | None, int | None, str | None]] = [
    # Courses (lectures): one folder per subject
    (SB / "Courses" / "ALO", "ALO", "courses", None, None, None),
    (SB / "Courses" / "PA", "PA", "courses", None, None, None),
    (SB / "Courses" / "POO", "POO", "courses", None, None, None),
    (SB / "Courses" / "PS", "PS", "courses", None, None, None),
    (SB / "Courses" / "RC", "RC", "courses_local", "so+rc", None, None),
    # Labs
    (SB / "Labs" / "ALO", "ALO", "labs", None, None, None),
    (SB / "Labs" / "PA", "PA", "labs", None, None, None),
    (SB / "Labs" / "POO", "POO", "labs", None, None, None),
    (SB / "Labs" / "PS", "PS", "labs", None, None, None),
    (SB / "Labs" / "RC", "RC", "labs_local", None, None, None),
    # Homework
    (SB / "HW" / "ALO", "ALO", "hw", None, None, None),
    (SB / "HW" / "PS", "PS", "hw", None, None, None),
    (SB / "PS HW", "PS", "ps_hw", None, None, None),
    # SO local lectures: ONLY root-level OS-prefixed files. Skip
    # os-study-guide/ + experiment/ (React app + node_modules).
    (SO_LOCAL, "SO", "local_lectures", None, 0, "OS*.pdf"),
]

# Root-level files in Second brain that aren't under Courses/Labs/HW.
ROOT_FILES: list[tuple[Path, str, str]] = [
    (SB / "Operating Systems course.pdf", "SO", "local_lectures"),
    (SB / "SO&RC 2026 OVERVIEW (1).pdf", "SO", "local_lectures"),
]

# File extensions to ingest. Any extension not listed → skip.
PDF_EXTS = {".pdf"}
HTML_EXTS = {".html", ".htm"}
TEXT_EXTS = {".r", ".c", ".sh", ".py", ".txt", ".md"}
SKIP_EXTS = {".docx", ".doc", ".ppt", ".pptx", ".xls", ".xlsx", ".jsx", ".tsx"}


def safe_name(name: str) -> str:
    return re.sub(r'[<>:"|?*]', "_", name)


def html_to_text(html: str) -> str:
    no_script = re.sub(r"<script[^>]*>.*?</script>", " ", html, flags=re.DOTALL | re.IGNORECASE)
    no_style = re.sub(r"<style[^>]*>.*?</style>", " ", no_script, flags=re.DOTALL | re.IGNORECASE)
    no_tags = re.sub(r"<[^>]+>", " ", no_style)
    return re.sub(r"\s+", " ", unescape(no_tags)).strip()


def pdf_to_text(pdf_path: Path, password: str | None = None) -> str:
    args = ["pdftotext", "-layout", "-enc", "UTF-8"]
    if password:
        args += ["-upw", password]
    args += [str(pdf_path), "-"]
    try:
        proc = subprocess.run(args, capture_output=True, timeout=180)
        if proc.returncode == 0:
            text = proc.stdout.decode("utf-8", errors="replace")
            if text.strip():
                return text
    except (FileNotFoundError, subprocess.TimeoutExpired):
        pass
    # Fallback: PyPDF2 (no password support in fallback — accept failure).
    try:
        import PyPDF2
        reader = PyPDF2.PdfReader(str(pdf_path))
        if password:
            try:
                reader.decrypt(password)
            except Exception:
                pass
        return "\n\n".join(p.extract_text() or "" for p in reader.pages)
    except Exception:
        return ""


def process_file(
    src: Path, dest_dir: Path, password: str | None, failures: list[str]
) -> tuple[bool, str]:
    """Returns (ok, kind). kind is 'pdf'/'html'/'text'/'skip'."""
    ext = src.suffix.lower()
    if ext in SKIP_EXTS:
        return False, "skip"

    safe = safe_name(src.name)
    bin_path = dest_dir / safe
    md_path = dest_dir / (safe.rsplit(".", 1)[0] + ".md")

    if SKIP_EXISTING and md_path.exists() and md_path.stat().st_size > 0:
        return True, "cached"

    dest_dir.mkdir(parents=True, exist_ok=True)
    # Always copy the binary alongside (so the operator can re-extract
    # later with different settings).
    if not bin_path.exists():
        try:
            shutil.copy2(src, bin_path)
        except Exception as e:
            failures.append(f"{src}: copy failed: {e}")
            return False, "skip"

    text = ""
    kind = "skip"
    if ext in PDF_EXTS:
        text = pdf_to_text(src, password)
        kind = "pdf"
    elif ext in HTML_EXTS:
        text = html_to_text(src.read_text(encoding="utf-8", errors="replace"))
        kind = "html"
    elif ext in TEXT_EXTS:
        text = src.read_text(encoding="utf-8", errors="replace")
        kind = "text"
    else:
        return False, "skip"

    if text:
        md_path.write_text(text + "\n", encoding="utf-8")
        return True, kind
    failures.append(f"{src}: text extract empty (ext={ext})")
    return False, "fail"


def process_dir(src_dir: Path, dest: Path, password: str | None,
                failures: list[str], max_depth: int | None = None,
                name_pattern: str | None = None) -> dict[str, int]:
    counts = {"pdf": 0, "html": 0, "text": 0, "cached": 0, "skip": 0, "fail": 0}
    if not src_dir.exists():
        return counts
    if max_depth == 0:
        # Root-only: no recursion. Apply name_pattern filter if given.
        candidates = src_dir.glob(name_pattern) if name_pattern else src_dir.iterdir()
    else:
        candidates = src_dir.rglob(name_pattern) if name_pattern else src_dir.rglob("*")
    for src in candidates:
        if not src.is_file():
            continue
        if max_depth is not None and max_depth > 0:
            depth = len(src.relative_to(src_dir).parts) - 1
            if depth > max_depth:
                continue
        ok, kind = process_file(src, dest, password, failures)
        counts[kind] = counts.get(kind, 0) + 1
    return counts


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    failures: list[str] = []
    total = {"pdf": 0, "html": 0, "text": 0, "cached": 0, "skip": 0, "fail": 0}
    t0 = time.time()
    for src_dir, subj, kind, pwd, max_depth, pattern in SOURCES:
        dest = OUT_DIR / subj / kind
        c = process_dir(src_dir, dest, pwd, failures, max_depth, pattern)
        for k, v in c.items():
            total[k] = total.get(k, 0) + v
        print(f"{subj}/{kind:<14} pdf={c['pdf']:>3} html={c['html']:>3} "
              f"text={c['text']:>3} cached={c['cached']:>3} "
              f"skip={c['skip']:>3} fail={c['fail']:>3}")
    for src, subj, kind in ROOT_FILES:
        if not src.exists():
            continue
        dest = OUT_DIR / subj / kind
        ok, k = process_file(src, dest, None, failures)
        total[k] = total.get(k, 0) + 1
        print(f"{subj}/{kind:<14} root file {src.name} -> {k}")
    elapsed = time.time() - t0
    if failures:
        (OUT_DIR / "_failures.log").write_text(
            "\n".join(failures) + "\n", encoding="utf-8"
        )
    print(f"\nTotal: pdf={total['pdf']} html={total['html']} text={total['text']} "
          f"cached={total['cached']} skip={total['skip']} fail={total['fail']}")
    print(f"Failures: {len(failures)} (see {OUT_DIR}/_failures.log)")
    print(f"Elapsed: {elapsed:.1f}s — files at {OUT_DIR.resolve()}")


if __name__ == "__main__":
    main()
