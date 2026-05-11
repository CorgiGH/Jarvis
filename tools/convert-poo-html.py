#!/usr/bin/env python3
"""Convert scraped POO mdBook HTML pages to .md."""
import os
import re
import glob
import time
from markdownify import markdownify as md
from bs4 import BeautifulSoup

OUT_ROOT = 'tmp-md/_fii/POO/site'
SRC_ROOT = 'tmp-secondbrain-scrape/_fii/POO/site'
os.makedirs(OUT_ROOT, exist_ok=True)
ts = time.strftime('%Y-%m-%dT%H:%M:%S+00:00', time.gmtime())
n_written = 0
for html_path in glob.glob(os.path.join(SRC_ROOT, '**/*.html'), recursive=True):
    with open(html_path, encoding='utf-8') as f:
        html = f.read()
    soup = BeautifulSoup(html, 'html.parser')
    content = soup.select_one('main') or soup.select_one('.content') or soup.select_one('article') or soup.body
    if not content:
        continue
    for s in content.select('script, style, nav, .nav-chapters, .nav-wrapper, .header-bar'):
        s.decompose()
    body_md = md(str(content), heading_style='ATX', bullets='-', code_language='', strong_em_symbol='*')
    body_md = re.sub(r'\n{3,}', '\n\n', body_md).strip()
    if len(body_md) < 50:
        continue
    title = (soup.title.text if soup.title else '').strip()
    h1 = (soup.h1.text if soup.h1 else '').strip()
    rel = os.path.relpath(html_path, SRC_ROOT).replace(os.sep, '/')
    out_md = os.path.join(OUT_ROOT, rel.replace('.html', '.md'))
    os.makedirs(os.path.dirname(out_md), exist_ok=True)
    src = 'https://gdt050579.github.io/poo_course_fii/' + rel.replace('index.html', '')
    fm = f'---\nsource_url: {src}\ntitle: {title}\nh1: {h1}\nextracted_at: {ts}\n---\n\n'
    with open(out_md, 'w', encoding='utf-8') as f:
        f.write(fm + body_md + '\n')
    n_written += 1
print(f'POO HTML->md: {n_written} files written')
