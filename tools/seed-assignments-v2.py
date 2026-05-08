#!/usr/bin/env python3
"""v2 of seed-assignments.py — registers the additional ALO + POO
assignments learned from edu.info.uaic.ro on 2026-05-08, with corrected
metadata (titles, point values, approximate week-based deadlines).

Idempotent: id = sha256(subject|title|dueDate)[:16] matches Kotlin
Assignments.computeId, so re-runs collapse against the existing
assignments.jsonl without duplicating PS Tema A/B/C/D from v1.

Pipe:
  ssh root@46.247.109.91 "python3 -" < tools/seed-assignments-v2.py
"""

import hashlib
import json
import os
from datetime import datetime, timezone

P = "/opt/jarvis/data/assignments.jsonl"

# Approximate dates derived from semester-week N where week 1 ≈ 2026-02-16
# (PS lecture 1 confirmed). Tagged "(approx week N)" in the title so the
# user sees they're computed, not authoritative — UAIC weeks can shift
# around holidays/strikes.
ASSIGNMENTS = [
    # ALO Tema 1-5 (replaces v1 stub rows). v1 rows had no deadline; these
    # have approximate week-based deadlines so the planner's exam-window
    # bias has dates to work with.
    ("ALO", "Tema 1 — Machine precision & function approx (50pt, week 5 approx)", "2026-03-22"),
    ("ALO", "Tema 2 — Function minimization (60pt, week 12 approx)", "2026-05-10"),
    ("ALO", "Tema 3 — Linear systems LU decomposition (60pt, week 12 approx)", "2026-05-10"),
    ("ALO", "Tema 4 — Linear systems QR decomposition (60pt, week 14 approx)", "2026-05-24"),
    ("ALO", "Tema 5 — Eigenvalues + SVD (70pt, week 14 approx)", "2026-05-24"),
    # POO Lab evaluation 2 (Lab eval 1 was week 8 ≈ 2026-04-06, already past).
    ("POO", "Lab evaluation 2 (30pt, week 14 or 15 approx)", "2026-05-25"),
]


def compute_id(subject: str, title: str, due: str | None) -> str:
    raw = f"{subject}|{title}|{due or ''}".encode("utf-8")
    return hashlib.sha256(raw).hexdigest()[:16]


def main():
    existing_ids = set()
    if os.path.exists(P):
        with open(P, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                try:
                    row = json.loads(line)
                    existing_ids.add(row.get("id"))
                except Exception:
                    continue

    now = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
    appended = 0
    with open(P, "a", encoding="utf-8") as f:
        for subject, title, due in ASSIGNMENTS:
            aid = compute_id(subject, title, due)
            if aid in existing_ids:
                continue
            row = {
                "id": aid,
                "ts": now,
                "kind": "set",
                "subject": subject,
                "title": title,
                "dueDate": due,
                "v": 1,
            }
            f.write(json.dumps(row, ensure_ascii=False) + "\n")
            appended += 1

    print(f"v2: appended {appended} assignment rows; total ids: "
          f"{len(existing_ids) + appended}")


if __name__ == "__main__":
    main()
