#!/usr/bin/env python3
"""Download Google Drive folders from fiimaterials per-subject."""
import subprocess
import os
import sys

FOLDERS = {
    'SO':   ('Operating Systems Y1',     '15OTn0CI7imWqXjt0NkXNuafKwMRhNEGW'),
    'PS':   ('Probabilities Y1',         '1wQHIvIMmtShvfJyjQMedu4XUHHs3Gedw'),
    'PA_Y1':('Algorithm Design Y1',      '1--ilhjnmy9DD4ugCUQphiM6wYsr5f7Ds'),
    'POO':  ('OOP Y1',                   '1zCleJlXHf2BKm10qqkE3eGqW75_3-AAW'),
    'RC':   ('Computer Networks Y2',     '1LsoKbfnrq6HR_8J7U1D897-tZOhfcaXz'),
    'ALO':  ('Graph Algorithms Y2',      '1SSPbBMtNCPBDYmhhIsm9b7ErUCu_4E0k'),
    'PA':   ('Advanced Programming Y2',  '12v6JRdIPM0oaErXhjyA329BNTZpOVeGJ'),
}

OUT_BASE = 'tmp-secondbrain-scrape/_fii/_gdrive'
os.makedirs(OUT_BASE, exist_ok=True)

for key, (label, fid) in FOLDERS.items():
    out_dir = os.path.join(OUT_BASE, key)
    print(f'\n=== {key} : {label} ===')
    print(f'  folder ID: {fid}')
    print(f'  output:    {out_dir}')
    os.makedirs(out_dir, exist_ok=True)
    cmd = [
        sys.executable, '-m', 'gdown',
        '--folder', f'https://drive.google.com/drive/folders/{fid}',
        '-O', out_dir,
        '--continue',
    ]
    try:
        r = subprocess.run(cmd, capture_output=True, text=True, timeout=900)
        out = (r.stdout or '') + (r.stderr or '')
        # Print last 12 lines for visibility
        for line in out.strip().splitlines()[-12:]:
            print(f'  {line}')
        if r.returncode != 0:
            print(f'  [warn] exit={r.returncode}')
    except subprocess.TimeoutExpired:
        print('  [timeout]')
