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
    # Post-base variants (diacritic floats AFTER base letter with space;
    # seen in some PDFBox Type 1 font extractions):
    ("a ˘", "ă"), ("A ˘", "Ă"),
    ("s ¸", "ș"), ("S ¸", "Ș"),
    ("t ¸", "ț"), ("T ¸", "Ț"),
    # Pre-base with space (diacritic before base letter):
    ("˘ a", "ă"), ("˘ A", "Ă"),
    ("¸ s", "ș"), ("¸ S", "Ș"),
    ("¸ t", "ț"), ("¸ T", "Ț"),
    ("ˆ ı", "î"), ("ˆ I", "Î"),
    ("ˆ a", "â"), ("ˆ A", "Â"),
    # No-space variants (some extractors drop the gap):
    ("˘a", "ă"), ("˘A", "Ă"),
    ("¸s", "ș"), ("¸S", "Ș"),
    ("¸t", "ț"), ("¸T", "Ț"),
    # No-space post-base variants (diacritic floats directly AFTER base letter):
    ("t¸", "ț"), ("T¸", "Ț"),
    ("s¸", "ș"), ("S¸", "Ș"),
]


def normalize_text(raw: str) -> str:
    """Apply Romanian glyph-fix table + NFC + whitespace collapse."""
    s = raw
    # Apply glyph-fix substitutions in order
    for src, dst in GLYPH_FIXES:
        s = s.replace(src, dst)
    # Unicode NFC normalize (composes any remaining decomposed forms)
    s = unicodedata.normalize("NFC", s)
    # Join hyphenated line-breaks: word-char + hyphen + newline + word-char
    s = re.sub(r"(\w)-\n(\w)", r"\1\2", s)
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
