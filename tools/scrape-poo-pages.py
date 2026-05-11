#!/usr/bin/env python3
"""Crawl gdt050579.github.io/poo_course_fii/ — static mdBook HTML."""
import os
import re
import time
import urllib.parse
import urllib.request

ROOT = 'https://gdt050579.github.io/poo_course_fii/'
OUT_DIR = 'tmp-secondbrain-scrape/_fii/POO/site'
PDF_DIR = 'tmp-secondbrain-scrape/_fii/POO/files'
UA = 'Mozilla/5.0 (jarvis-kotlin scraper)'
MAX_PAGES = 300


def fetch(url):
    parts = urllib.parse.urlsplit(url)
    safe_path = urllib.parse.quote(parts.path, safe='/%')
    safe_url = urllib.parse.urlunsplit((parts.scheme, parts.netloc, safe_path, '', ''))
    req = urllib.request.Request(safe_url, headers={'User-Agent': UA})
    with urllib.request.urlopen(req, timeout=25) as r:
        return r.read(), r.headers.get_content_type()


os.makedirs(OUT_DIR, exist_ok=True)
os.makedirs(PDF_DIR, exist_ok=True)

visited = set()
to_visit = [ROOT]
pdf_urls = set()
pages = 0

while to_visit and pages < MAX_PAGES:
    u = to_visit.pop(0)
    if u in visited:
        continue
    visited.add(u)
    try:
        body, ctype = fetch(u)
    except Exception as e:
        print(f'SKIP {u}: {e}'); continue
    if 'html' not in (ctype or '').lower():
        continue
    html = body.decode('utf-8', errors='replace')
    rel = u[len(ROOT):] or 'index.html'
    rel = rel.split('?')[0].split('#')[0]
    if rel.endswith('/'):
        rel += 'index.html'
    if not rel.endswith(('.html', '.htm')):
        rel += '.html'
    rel = re.sub(r'[<>:"|*?]', '_', rel)
    out = os.path.join(OUT_DIR, rel)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, 'w', encoding='utf-8') as f:
        f.write(html)
    pages += 1

    refs = set(re.findall(r'(?:href|src)="([^"]+)"', html))
    for ref in refs:
        if ref.startswith(('#', 'mailto:', 'javascript:')):
            continue
        absu = urllib.parse.urljoin(u, ref).split('#')[0]
        if re.search(r'\.pdf($|\?)', absu, re.IGNORECASE):
            pdf_urls.add(absu)
        elif absu.startswith(ROOT) and re.search(r'\.html($|\?)', absu, re.IGNORECASE):
            if absu not in visited and absu not in to_visit:
                to_visit.append(absu)
    time.sleep(0.15)

# Download PDFs
pdf_ok = 0
for purl in sorted(pdf_urls):
    name = os.path.basename(urllib.parse.urlparse(purl).path)
    out = os.path.join(PDF_DIR, name)
    if os.path.exists(out) and os.path.getsize(out) > 0:
        pdf_ok += 1
        continue
    try:
        b, _ = fetch(purl)
        with open(out, 'wb') as f:
            f.write(b)
        pdf_ok += 1
    except Exception as e:
        print(f'PDF FAIL {purl}: {e}')
    time.sleep(0.3)

print(f'POO: pages={pages}  pdfs={len(pdf_urls)}  got={pdf_ok}')
