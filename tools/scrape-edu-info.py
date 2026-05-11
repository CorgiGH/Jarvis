#!/usr/bin/env python3
"""Crawl edu.info.uaic.ro per-subject sites + download all PDF assets.

Output:
  tmp-secondbrain-scrape/_fii/<SUBJECT>/site/<page>.html  (raw HTML pages)
  tmp-secondbrain-scrape/_fii/<SUBJECT>/files/<filename>.pdf  (downloaded PDFs)

Subject map (Alex's 6, mapped to edu.info slugs):
  RC  -> computer-networks
  SO  -> sisteme-de-operare
  PS  -> probabilitati-si-statistica
  PA  -> programare-avansata     (Y2 Sem 4 Advanced Programming)
  ALO -> algoritmica-grafuri      (Y2 Sem 3 Graph Algorithms — best mapping)
"""
import os
import re
import sys
import time
import urllib.parse
import urllib.request
from urllib.error import URLError, HTTPError

BASE_HOST = 'https://edu.info.uaic.ro'
SUBJECTS = {
    'RC': 'computer-networks',
    'SO': 'sisteme-de-operare',
    'PS': 'probabilitati-si-statistica',
    'PA': 'programare-avansata',
    'ALO': 'algoritmica-grafuri',
}
COMMON_PAGES = ['index.php', 'cursullaboratorul.php', 'resurse.php',
                'notare.php', 'noutati.php', 'proiecte.php', 'contact.php']

UA = 'Mozilla/5.0 (jarvis-kotlin scraper; AI-bachelor-corpus-augment)'
TIMEOUT = 25
OUT_ROOT = 'tmp-secondbrain-scrape/_fii'


def fetch(url):
    # Percent-encode path component (spaces, romanian diacritics)
    parts = urllib.parse.urlsplit(url)
    safe_path = urllib.parse.quote(parts.path, safe='/%')
    safe_query = urllib.parse.quote(parts.query, safe='=&')
    safe_url = urllib.parse.urlunsplit((parts.scheme, parts.netloc, safe_path, safe_query, ''))
    req = urllib.request.Request(safe_url, headers={'User-Agent': UA})
    with urllib.request.urlopen(req, timeout=TIMEOUT) as resp:
        return resp.read(), resp.headers.get_content_type()


MAX_PAGES_PER_SUBJECT = 200


def crawl_subject(short, slug):
    subj_root = f'{BASE_HOST}/{slug}/'
    out_subj = os.path.join(OUT_ROOT, short)
    out_site = os.path.join(out_subj, 'site')
    out_files = os.path.join(out_subj, 'files')
    os.makedirs(out_site, exist_ok=True)
    os.makedirs(out_files, exist_ok=True)

    # BFS within same-prefix domain
    visited_pages = set()
    to_visit = [subj_root]
    pdf_urls = set()
    page_count = 0

    while to_visit and page_count < MAX_PAGES_PER_SUBJECT:
        u = to_visit.pop(0)
        if u in visited_pages:
            continue
        visited_pages.add(u)
        try:
            body, ctype = fetch(u)
        except (HTTPError, URLError) as e:
            print(f'  [{short}] SKIP {u}: {e}')
            continue
        if 'html' not in (ctype or '').lower():
            continue
        try:
            html = body.decode('utf-8', errors='replace')
        except Exception:
            continue
        # Save raw HTML — use full relative path under subj_root as filename
        rel_path = u[len(subj_root):] or 'index.html'
        rel_path = rel_path.split('?')[0].split('#')[0]
        if rel_path.endswith('/'):
            rel_path += 'index.html'
        if not rel_path.endswith(('.html', '.htm', '.php')):
            rel_path += '.html'
        safe = re.sub(r'[<>:"|*?]', '_', rel_path)
        page_path = os.path.join(out_site, safe)
        os.makedirs(os.path.dirname(page_path), exist_ok=True)
        with open(page_path, 'w', encoding='utf-8') as f:
            f.write(html)
        page_count += 1

        # Extract all href + src links
        all_refs = set(re.findall(r'(?:href|src)="([^"]+)"', html))
        for ref in all_refs:
            if ref.startswith('#') or ref.startswith('mailto:') or ref.startswith('javascript:'):
                continue
            absu = urllib.parse.urljoin(u, ref).split('#')[0]
            # PDF
            if re.search(r'\.pdf($|\?)', absu, re.IGNORECASE):
                pdf_urls.add(absu)
                continue
            # Same-prefix HTML/PHP page → enqueue
            if absu.startswith(subj_root) and re.search(r'\.(php|html|htm)($|\?)', absu, re.IGNORECASE):
                if absu not in visited_pages and absu not in to_visit:
                    to_visit.append(absu)
            # Bare same-prefix path (directory listing) → also enqueue
            elif absu.startswith(subj_root) and absu.endswith('/'):
                if absu not in visited_pages and absu not in to_visit:
                    to_visit.append(absu)
        time.sleep(0.15)

    # Download all PDFs
    pdf_ok, pdf_fail = 0, 0
    for purl in sorted(pdf_urls):
        # Output filename preserves subpath under files/
        m = re.search(r'/files/(.+?)\.pdf$', purl, re.IGNORECASE)
        if m:
            rel = m.group(1) + '.pdf'
        else:
            rel = urllib.parse.urlparse(purl).path.split('/')[-1]
            if not rel.lower().endswith('.pdf'):
                rel = re.sub(r'[^A-Za-z0-9._-]', '_', urllib.parse.urlparse(purl).path) + '.pdf'
        out_pdf = os.path.join(out_files, rel)
        os.makedirs(os.path.dirname(out_pdf), exist_ok=True)
        if os.path.exists(out_pdf) and os.path.getsize(out_pdf) > 0:
            pdf_ok += 1
            continue
        try:
            body, _ = fetch(purl)
            with open(out_pdf, 'wb') as f:
                f.write(body)
            pdf_ok += 1
        except (HTTPError, URLError) as e:
            print(f'  [{short}] PDF FAIL {purl}: {e}')
            pdf_fail += 1
        time.sleep(0.3)

    return page_count, len(pdf_urls), pdf_ok, pdf_fail


def main():
    print(f'Output root: {OUT_ROOT}')
    summary = []
    for short, slug in SUBJECTS.items():
        print(f'\n=== {short} ({slug}) ===')
        pages, pdfs, ok, fail = crawl_subject(short, slug)
        summary.append((short, pages, pdfs, ok, fail))
        print(f'  pages={pages}  pdfs_seen={pdfs}  downloaded={ok}  failed={fail}')

    print('\n=== Summary ===')
    print(f'{"subj":<6}{"pages":>8}{"pdfs":>8}{"got":>6}{"fail":>6}')
    for row in summary:
        print(f'{row[0]:<6}{row[1]:>8}{row[2]:>8}{row[3]:>6}{row[4]:>6}')


if __name__ == '__main__':
    main()
