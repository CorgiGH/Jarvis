#!/usr/bin/env python3
"""Idempotent one-time seed of POO graded-component sentinel rows.

Writes Lab evaluation 2 (30pt) + Final exam (30pt) as 0/30 zero-rows
into /opt/jarvis/data/grades.jsonl. Both components are listed in the
POO grading scheme but are not in the gradebook spreadsheet (no
column for them yet — they happen later in the term). Without
sentinels the bot's [[grades]] roll-up sees POO max = 42 instead of
the actual ~100, producing a misleading 8% standing instead of a
~3% standing that better signals the catch-up debt.

Why a separate seed instead of extending sync-grades-from-sheets.py:
the hourly cron would re-emit (0, 30) every run. Once the user gets a
real grade and writes via `[[grade_record: POO/Lab evaluation 2/X/30]]`,
the next cron round would compare extractor (0, 30) vs existing (X, 30),
see them differ, and append a fresh 0/30 row that wins on next read —
overwriting the legitimate grade. This is the exact failure mode
documented at extract_ps line ~173 ("v3 post session-review council
RA HIGH: sentinel-zero on empty cell created a persistent false-low
grade — every hourly cron rewrote 0/20 with a fresh ts").

Pipe form (matches existing one-shot seed pattern):
    ssh root@46.247.109.91 "python3 -" < tools/seed-poo-sentinels.py

Re-runs no-op via id-collapse: the seed only writes a row if no row
with that id already exists. Once a real [[grade_record]] lands later
the seed will leave that row in place.
"""

import hashlib
import json
import os
from datetime import datetime, timezone

GRADES_FILE = os.environ.get(
    "JARVIS_GRADES_FILE", "/opt/jarvis/data/grades.jsonl"
)

SENTINELS = [
    ("POO", "Lab evaluation 2", 0.0, 30.0,
     "sentinel — actual grade fills in via [[grade_record]] when assigned"),
    ("POO", "Final exam", 0.0, 30.0,
     "sentinel — actual grade fills in via [[grade_record]] when assigned"),
]


def compute_id(subject: str, component: str) -> str:
    raw = f"{subject}|{component}".encode("utf-8")
    return hashlib.sha256(raw).hexdigest()[:16]


def main():
    existing_ids: set[str] = set()
    if os.path.exists(GRADES_FILE):
        with open(GRADES_FILE, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                try:
                    row = json.loads(line)
                    existing_ids.add(row.get("id"))
                except Exception:
                    continue

    now_iso = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
    appended = 0
    with open(GRADES_FILE, "a", encoding="utf-8") as f:
        for subject, component, earned, max_, note in SENTINELS:
            gid = compute_id(subject, component)
            if gid in existing_ids:
                continue
            entry = {
                "id": gid,
                "ts": now_iso,
                "subject": subject,
                "component": component,
                "earned": earned,
                "max": max_,
                "note": note,
                "v": 1,
            }
            f.write(json.dumps(entry, ensure_ascii=False) + "\n")
            appended += 1

    print(f"seed-poo-sentinels: appended={appended} (total tracked: "
          f"{len(SENTINELS)}, already-present: "
          f"{len(SENTINELS) - appended})")


if __name__ == "__main__":
    main()
