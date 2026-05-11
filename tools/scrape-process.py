#!/usr/bin/env python3
"""Convert scrape-*.json (HTML payload) → per-subject .md files."""
import json
import glob
import os
import time
import re
from markdownify import markdownify as md

# URL path → (subject, subdir, filename-without-ext)
MAPPING = {
    '/operating-systems-and-computer-networks/overview/': ('SO', 'course_pages', 'overview'),
    '/operating-systems-and-computer-networks/announcements/': ('SO', 'course_pages', 'announcements'),
    '/operating-systems-and-computer-networks/calendar/': ('SO', 'course_pages', 'calendar'),
    '/operating-systems-and-computer-networks/courses/week01/': ('SO', 'courses_local', 'week01_intro_os'),
    '/operating-systems-and-computer-networks/courses/week03/': ('SO', 'courses_local', 'week03_bash'),
    '/operating-systems-and-computer-networks/courses/week05/': ('SO', 'courses_local', 'week05_processes'),
    '/operating-systems-and-computer-networks/courses/week06/': ('SO', 'courses_local', 'week06_mmap_sync'),
    '/operating-systems-and-computer-networks/courses/week07/': ('SO', 'courses_local', 'week07_ipc_pipes'),
    '/operating-systems-and-computer-networks/courses/week09/': ('RC', 'courses_local', 'week09_intro_networking'),
    '/operating-systems-and-computer-networks/networking/': ('RC', 'course_pages', 'networking_index'),
    '/operating-systems-and-computer-networks/networking/lab1/': ('RC', 'labs_local', 'lab1_ipc_to_networks'),
    '/operating-systems-and-computer-networks/networking/lab1/challenges/': ('RC', 'labs_local', 'lab1_challenges'),
    '/operating-systems-and-computer-networks/networking/lab2/': ('RC', 'labs_local', 'lab2_udp_clientserver'),
    '/operating-systems-and-computer-networks/networking/lab2/challenges/': ('RC', 'labs_local', 'lab2_challenges'),
    '/operating-systems-and-computer-networks/networking/lab3/': ('RC', 'labs_local', 'lab3_tcp_clientserver'),
    '/operating-systems-and-computer-networks/networking/lab3/challenges/': ('RC', 'labs_local', 'lab3_challenges'),
    '/operating-systems-and-computer-networks/networking/lab4/': ('RC', 'labs_local', 'lab4_advanced_tcp'),
    '/operating-systems-and-computer-networks/os/lab05/': ('SO', 'labs_local', 'oslab05_processes_guide'),
    '/operating-systems-and-computer-networks/os/lab06/': ('SO', 'labs_local', 'oslab06_mmap_guide'),
    '/operating-systems-and-computer-networks/os/lab07/': ('SO', 'labs_local', 'oslab07_pipes_guide'),
    '/operating-systems-and-computer-networks/resources/getting-started/': ('SO', 'resources', 'getting_started_c_posix'),
    '/operating-systems-and-computer-networks/resources/posix-networking-api/': ('RC', 'resources', 'posix_networking_api'),
}

ts = time.strftime('%Y-%m-%dT%H:%M:%S+00:00', time.gmtime())
written = []
skipped = []
for jf in sorted(glob.glob('scrape-*.json')):
    d = json.load(open(jf, encoding='utf-8'))
    url = d.get('url', '')
    status = d.get('status', '')
    if status != 'ok' or not d.get('html'):
        skipped.append((jf, status, url))
        continue
    if url not in MAPPING:
        skipped.append((jf, 'unmapped', url))
        continue
    subj, subdir, fname = MAPPING[url]
    body_html = d['html']
    body_md = md(body_html, heading_style='ATX', bullets='-', code_language='', strong_em_symbol='*')
    body_md = re.sub(r'\n{3,}', '\n\n', body_md).strip()
    full_url = f"https://education-hub.adrian-cert.workers.dev{url}"
    fm = (
        f"---\n"
        f"source_url: {full_url}\n"
        f"title: {d.get('title','').strip()}\n"
        f"h1: {d.get('h1','').strip()}\n"
        f"extracted_at: {ts}\n"
        f"---\n\n"
    )
    out_path = f"tmp-md/{subj}/{subdir}/{fname}.md"
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    with open(out_path, 'w', encoding='utf-8') as f:
        f.write(fm + body_md + '\n')
    written.append((out_path, len(body_md)))

print(f"Written {len(written)}:")
for p, n in written:
    print(f"  {n:>6}  {p}")
print(f"\nSkipped {len(skipped)}:")
for jf, st, url in skipped:
    print(f"  {jf}  status={st}  {url}")
