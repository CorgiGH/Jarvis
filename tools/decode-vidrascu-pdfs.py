#!/usr/bin/env python3
"""Decode vidrascu-pdfs-batchN.json files to actual PDFs on disk."""
import base64
import glob
import json
import os
import re
from urllib.parse import urlparse

BASE = 'https://edu.info.uaic.ro/operating-systems-and-computer-networks/SO/'
OUT = 'tmp-secondbrain-scrape/_fii/SO_vidrascu/files'
os.makedirs(OUT, exist_ok=True)

n_ok, n_err, total_size = 0, 0, 0
for jf in sorted(glob.glob('vidrascu-pdfs-batch*.json')):
    d = json.load(open(jf, encoding='utf-8'))
    for url, r in d.items():
        if 'error' in r:
            print(f'  ERR {r["error"]}: {url}')
            n_err += 1
            continue
        # Build relative path preserving subdir structure
        parsed_path = urlparse(url).path
        # Strip the common prefix
        common = '/operating-systems-and-computer-networks/'
        if parsed_path.startswith(common):
            rel = parsed_path[len(common):]
        else:
            rel = parsed_path.lstrip('/')
        # Drop leading SO/ since output already under SO_vidrascu
        if rel.startswith('SO/'):
            rel = rel[3:]
        # Cleanup duplicated SO/SO/
        rel = rel.replace('SO/SO/', 'SO/')
        rel_safe = re.sub(r'[<>:"|*?]', '_', rel)
        out_path = os.path.join(OUT, rel_safe)
        os.makedirs(os.path.dirname(out_path), exist_ok=True)
        if os.path.exists(out_path) and os.path.getsize(out_path) > 0:
            n_ok += 1
            continue
        body = base64.b64decode(r['b64'])
        with open(out_path, 'wb') as f:
            f.write(body)
        total_size += r['size']
        n_ok += 1

print(f'\nDecoded: {n_ok} ok, {n_err} errors, {total_size/1024/1024:.1f} MB')
