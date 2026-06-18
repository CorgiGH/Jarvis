#!/usr/bin/env python3
"""One-shot audit: do PA KC source quotes locate in the REAL lecture PDF?
Replicates ContentValidator.fold (NFD strip-diacritics + ws-collapse + lower)
and the span-absent fold-contains branch. Read-only."""
import subprocess, re, unicodedata, glob, os, sys

PDF = sys.argv[1] if len(sys.argv) > 1 else "tmp-secondbrain-scrape/PA/courses/lecture01_comppb.pdf"
BIN = r"C:\tools\poppler\pdftotext.exe"
if not os.path.exists(BIN):
    BIN = "pdftotext"
if PDF.lower().endswith((".md", ".txt")):
    raw = open(PDF, encoding="utf-8").read().replace("\r\n", "\n")  # already-extracted source
else:
    raw = subprocess.run([BIN, "-enc", "UTF-8", PDF, "-"],
                         capture_output=True).stdout.decode("utf-8", "replace").replace("\r\n", "\n")

WS = re.compile(r"\s+")
def strip_diac(s):
    return "".join(c for c in unicodedata.normalize("NFD", s)
                   if unicodedata.category(c) != "Mn")
def fold(s):
    return WS.sub(" ", strip_diac(s)).strip().lower()

folded_src = fold(raw)

def quotes_of(path):
    """Extract double-quoted YAML scalar values after 'quote:'. Handles the
    simple subset used in these KC files (no embedded escaped quotes)."""
    txt = open(path, encoding="utf-8").read()
    out = []
    pat = re.compile(r'quote:\s*"([^"]*)"', re.S)
    for m in pat.finditer(txt):
        # YAML double-quote: \n is a real newline; collapse it like the loader.
        q = m.group(1).replace("\\n", "\n")
        out.append(q)
    return out

print(f"REAL extraction: {raw.count(chr(12))} form-feeds, {len(raw)} chars\n")
total = found = 0
for f in sorted(glob.glob("content/PA/kcs/pa-kc-00*.yaml")):
    kc = os.path.basename(f)
    for q in quotes_of(f):
        total += 1
        fq = fold(q)
        ok = fq in folded_src
        found += ok
        snippet = q[:64].replace("\n", " ")
        print(f"[{'OK  ' if ok else 'MISS'}] {kc}: \"{snippet}...\"")
        if not ok:
            toks = [t for t in fq.split() if len(t) > 4]
            shown = False
            for key in toks[:4]:
                idx = folded_src.find(key)
                if idx >= 0:
                    print(f"        near real text: ...{folded_src[max(0,idx-15):idx+90]}...")
                    shown = True
                    break
            if not shown:
                print(f"        (no distinctive token from this quote appears in the real PDF)")
print(f"\nSUMMARY: {found}/{total} quotes locate (fold-contains) in the REAL PDF")
