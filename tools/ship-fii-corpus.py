#!/usr/bin/env python3
"""Build single tar from tmp-md/_fii + tmp-md/_curriculum, remapped to VPS _extras paths."""
import os
import sys
import tarfile

# (local-src-root, archive-name-prefix-on-VPS)
MAPPING = [
    ('tmp-md/_fii/RC',       'RC/_fii/edu'),
    ('tmp-md/_fii/SO',       'SO/_fii/edu'),
    ('tmp-md/_fii/PS',       'PS/_fii/edu'),
    ('tmp-md/_fii/PA',       'PA/_fii/edu'),
    ('tmp-md/_fii/ALO',      'ALO/_fii/edu'),
    ('tmp-md/_fii/POO',      'POO/_fii'),
    ('tmp-md/_fii/_gdrive/SO',    'SO/_fii/gdrive'),
    ('tmp-md/_fii/_gdrive/PS',    'PS/_fii/gdrive'),
    ('tmp-md/_fii/_gdrive/PA_Y1', 'PA/_fii/gdrive_y1_algodesign'),
    ('tmp-md/_fii/_gdrive/POO',   'POO/_fii/gdrive'),
    ('tmp-md/_fii/_gdrive/RC',    'RC/_fii/gdrive'),
    ('tmp-md/_fii/_gdrive/ALO',   'ALO/_fii/gdrive'),
    ('tmp-md/_fii/_gdrive/PA',    'PA/_fii/gdrive_y2_advanced'),
]
CURRICULUM_SRC = 'tmp-md/_curriculum'
CURRICULUM_TGT = '_curriculum'  # but we tar starting from inside _extras root — curriculum goes one level UP
# Approach: produce two tars (one for _extras content + one for _curriculum top-level).

tar_extras = '_ship_fii_extras.tar'
tar_curric = '_ship_fii_curriculum.tar'

n_extras = 0
with tarfile.open(tar_extras, 'w', format=tarfile.PAX_FORMAT) as tf:
    for local, target in MAPPING:
        if not os.path.isdir(local):
            print(f'skip missing: {local}')
            continue
        for root, dirs, files in os.walk(local):
            for fn in files:
                if not fn.lower().endswith('.md'):
                    continue
                local_path = os.path.join(root, fn)
                rel = os.path.relpath(local_path, local).replace(os.sep, '/')
                arc = f'{target}/{rel}'
                tf.add(local_path, arcname=arc)
                n_extras += 1
print(f'{tar_extras}: {n_extras} files, {os.path.getsize(tar_extras)} bytes')

n_curric = 0
with tarfile.open(tar_curric, 'w', format=tarfile.PAX_FORMAT) as tf:
    if os.path.isdir(CURRICULUM_SRC):
        for root, dirs, files in os.walk(CURRICULUM_SRC):
            for fn in files:
                if not fn.lower().endswith('.md'):
                    continue
                local_path = os.path.join(root, fn)
                rel = os.path.relpath(local_path, CURRICULUM_SRC).replace(os.sep, '/')
                arc = f'{CURRICULUM_TGT}/{rel}'
                tf.add(local_path, arcname=arc)
                n_curric += 1
print(f'{tar_curric}: {n_curric} files, {os.path.getsize(tar_curric)} bytes')
