#!/usr/bin/env python3
"""Process vidrascu-so-crawl.json — save raw HTML + convert to md."""
import json
import os
import re
import time
from urllib.parse import urlparse
from markdownify import markdownify as md
from bs4 import BeautifulSoup

BASE = 'https://edu.info.uaic.ro/operating-systems-and-computer-networks/SO/'
SITE_OUT = 'tmp-secondbrain-scrape/_fii/SO_vidrascu/site'
MD_OUT = 'tmp-md/_fii/SO_vidrascu'

data = json.load(open('vidrascu-so-crawl.json', encoding='utf-8'))
pages = data['pages']

os.makedirs(SITE_OUT, exist_ok=True)
os.makedirs(MD_OUT, exist_ok=True)

ts = time.strftime('%Y-%m-%dT%H:%M:%S+00:00', time.gmtime())
n_html, n_md = 0, 0

for url, html in pages.items():
    if not url.startswith(BASE):
        continue
    rel = url[len(BASE):]
    rel = rel.split('?')[0] or 'index.html'
    if rel.endswith('/'):
        rel += 'index.html'
    rel_safe = re.sub(r'[<>:"|*?]', '_', rel)
    # Save raw HTML
    html_path = os.path.join(SITE_OUT, rel_safe)
    os.makedirs(os.path.dirname(html_path), exist_ok=True)
    with open(html_path, 'w', encoding='utf-8') as f:
        f.write(html)
    n_html += 1
    # Convert to md (use body content + drop nav/script/style)
    soup = BeautifulSoup(html, 'html.parser')
    for tag in soup(['script', 'style', 'nav']):
        tag.decompose()
    # Drop nav table at top (Home/Cursuri/Examinare/Bibliografie/Echipa)
    for t in soup.find_all('table'):
        txt = t.get_text(' ', strip=True)
        if 'Home' in txt and ('Cursuri' in txt or 'Examinare' in txt):
            t.decompose()
    body = soup.body or soup
    body_md = md(str(body), heading_style='ATX', bullets='-', code_language='', strong_em_symbol='*')
    body_md = re.sub(r'\n{3,}', '\n\n', body_md).strip()
    if len(body_md) < 30:
        continue
    title = (soup.title.text if soup.title else '').strip()
    h1 = (soup.h1.text if soup.h1 else '').strip()
    fm = (
        f"---\n"
        f"source_url: {url}\n"
        f"title: {title}\n"
        f"h1: {h1}\n"
        f"extracted_at: {ts}\n"
        f"---\n\n"
    )
    md_rel = rel_safe.replace('.html', '.md').replace('.htm', '.md')
    md_path = os.path.join(MD_OUT, md_rel)
    os.makedirs(os.path.dirname(md_path), exist_ok=True)
    with open(md_path, 'w', encoding='utf-8') as f:
        f.write(fm + body_md + '\n')
    n_md += 1

print(f'HTML saved: {n_html}, md written: {n_md}')
