#!/usr/bin/env python3
"""Append placeholder exam rows to /opt/jarvis/data/schedule.json on VPS.

Idempotent — skips an exam row if (date, kind=exam, subject) already exists.
Run via stdin pipe so the user doesn't have to scp first:

  ssh root@46.247.109.91 "python3 -" < tools/append-exams.py
"""

import json

P = "/opt/jarvis/data/schedule.json"

NEW_EXAMS = [
    {"date": "2026-05-21", "start": "23:59", "end": "23:59",
     "kind": "exam", "subject": "PS",
     "topic": "PS HW deadline (no restanta)"},
    {"date": "2026-06-05", "start": "09:00", "end": "12:00",
     "kind": "exam", "subject": "ALO",
     "topic": "placeholder - UPDATE WHEN CONFIRMED"},
    {"date": "2026-06-08", "start": "09:00", "end": "12:00",
     "kind": "exam", "subject": "POO",
     "topic": "placeholder - UPDATE WHEN CONFIRMED"},
    {"date": "2026-06-12", "start": "09:00", "end": "12:00",
     "kind": "exam", "subject": "PA",
     "topic": "placeholder - UPDATE WHEN CONFIRMED"},
    {"date": "2026-06-15", "start": "09:00", "end": "12:00",
     "kind": "exam", "subject": "PS",
     "topic": "placeholder - UPDATE WHEN CONFIRMED"},
    {"date": "2026-06-19", "start": "09:00", "end": "12:00",
     "kind": "exam", "subject": "T.RC",
     "topic": "placeholder - UPDATE WHEN CONFIRMED"},
]

with open(P) as f:
    s = json.load(f)

existing = {(b.get("date"), b.get("kind"), b.get("subject")) for b in s.get("blocks", [])}
added = 0
for ex in NEW_EXAMS:
    key = (ex["date"], "exam", ex["subject"])
    if key not in existing:
        s["blocks"].append(ex)
        added += 1

# Sort blocks by date+start for tidy diffs.
s["blocks"].sort(key=lambda b: (b.get("date", ""), b.get("start", "")))

with open(P, "w") as f:
    json.dump(s, f, indent=2, ensure_ascii=False)

print(f"appended {added} exam rows; total blocks: {len(s['blocks'])}")
