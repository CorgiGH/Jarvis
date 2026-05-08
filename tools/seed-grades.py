#!/usr/bin/env python3
"""Seed /opt/jarvis/data/grades.jsonl from grade data the user already
has in their Second brain (council cache from 2026-05-04 + the
PA_Rezultate_Partial PDF). Idempotent: id = sha256(subject|component)[:16]
matches Kotlin Grades.computeId, so re-runs collapse on (subject,
component); the LATEST timestamp wins on read so a corrected grade
re-recorded later just overrides.

Sources for these values:
- C:\\Users\\User\\Desktop\\Second brain\\.claude\\council-cache\\
  council-1777881900.md (council strategy review 2026-05-04, lists
  per-subject standing + thresholds)
- C:\\Users\\User\\Desktop\\Second brain\\PA_Rezultate_Partial (2).pdf
  (UAIC official PA Test 1 spreadsheet, matches council Test 1=14.5/35)
- Memory user_uaic_finals_2026.md (SO continuous eval locked at 24.5/50)

Pipe:
  ssh root@46.247.109.91 "python3 -" < tools/seed-grades.py
"""

import hashlib
import json
import os
from datetime import datetime, timezone

P = "/opt/jarvis/data/grades.jsonl"

# Council snapshot ts. Use the council-cache filename ts (1777881900) for
# provenance. If user re-records via [[grade_record]] later, the new ts
# wins per latest-row semantics in Kotlin Grades.latestPerComponent.
COUNCIL_TS = datetime.fromtimestamp(1777881900, tz=timezone.utc).isoformat().replace("+00:00", "Z")

GRADES = [
    # SO+RC combined course (AI1202).
    ("SO&RC", "Linux quiz",                  10.0, 10.0,
     "from council-1777881900.md"),
    ("SO&RC", "Lab activity",                 1.0, 10.0,
     "from council-1777881900.md"),
    ("SO&RC", "Continuous eval (SO half)",   24.5, 50.0,
     "locked per memory user_uaic_finals_2026.md; cannot reevaluate"),
    # T.SO unknown — leave for user to record manually.

    # PA Test 1.
    ("PA",    "Test 1",                      14.5, 35.0,
     "from PA_Rezultate_Partial.pdf + council-1777881900.md; "
     "needs 30.5+ from Test 2 + seminar + attendance to pass"),

    # PS HW so far. 0/20 reflects current state (no Tema submitted yet).
    # When user submits Tema A/B/C/D before 2026-05-21, re-record.
    ("PS",    "Homework total",              0.0, 20.0,
     "from council-1777881900.md as of 2026-05-04 (lab cap 40 if HW "
     "stays at 0); deadline 2026-05-21 NO restanta"),

    # ALO: lab strong, written exam pending.
    ("ALO",   "Seminar test (week 7)",        7.75, 10.0,
     "from council-1777881900.md"),
    ("ALO",   "Tema 1 — Machine precision",  55.0, 55.0,
     "from council-1777881900.md; aced"),

    # POO Lab 1 = 0/30, the lab eval 1 from week 8.
    ("POO",   "Lab evaluation 1",             0.0, 30.0,
     "from council-1777881900.md; needs 64 percent on remaining "
     "70 points to pass"),
]


def compute_id(subject, component):
    raw = f"{subject}|{component}".encode("utf-8")
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

    appended = 0
    with open(P, "a", encoding="utf-8") as f:
        for subject, component, earned, max_, note in GRADES:
            gid = compute_id(subject, component)
            if gid in existing_ids:
                continue
            row = {
                "id": gid,
                "ts": COUNCIL_TS,
                "subject": subject,
                "component": component,
                "earned": earned,
                "max": max_,
                "note": note,
                "v": 1,
            }
            f.write(json.dumps(row, ensure_ascii=False) + "\n")
            appended += 1

    print(f"appended {appended} grade rows; total ids: "
          f"{len(existing_ids) + appended}")


if __name__ == "__main__":
    main()
