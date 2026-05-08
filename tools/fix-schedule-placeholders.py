#!/usr/bin/env python3
"""Reconcile /opt/jarvis/data/schedule.json placeholders with the
authoritative facts from BScIA-2025-2028.pdf curriculum + course pages
fetched 2026-05-08:

1. **Remove the wrong PS June 15 placeholder** — PS is form V
   (Verificare) per AI1204, has NO June exam. The 2026-05-21 HW
   deadline IS the final evaluation point.
2. **Add SO+RC June exam placeholder** if not present — AI1202 is
   form E (Examen), has a June final but date not yet published.
   Use 2026-06-19 as placeholder slot (was the T.RC slot).
3. **Reconcile T.RC row** — memory called it `T.RC` but curriculum
   shows AI1202 is the combined SO+RC course. Rename topic to flag
   it's the combined-course exam, not a separate T.RC.

Idempotent: keys on (date, kind, subject) so re-runs no-op after
the first.

Pipe:
  ssh root@46.247.109.91 "python3 -" < tools/fix-schedule-placeholders.py
"""

import json

P = "/opt/jarvis/data/schedule.json"

with open(P, encoding="utf-8") as f:
    s = json.load(f)

blocks = s.get("blocks", [])
removed = 0
modified = 0
added = 0

# 1. Remove the wrong PS exam placeholder at 2026-06-15.
new_blocks = []
for b in blocks:
    if (b.get("date") == "2026-06-15" and b.get("kind") == "exam"
            and b.get("subject") == "PS"
            and "placeholder" in (b.get("topic") or "").lower()):
        removed += 1
        continue
    new_blocks.append(b)
blocks = new_blocks

# 2. Reconcile T.RC June 19 → SO&RC.
for b in blocks:
    if (b.get("date") == "2026-06-19" and b.get("kind") == "exam"
            and b.get("subject") == "T.RC"):
        b["subject"] = "SO&RC"
        b["topic"] = ("placeholder - AI1202 SO+RC final exam, "
                      "UPDATE WHEN CONFIRMED")
        modified += 1

# 3. Ensure there's an SO+RC exam placeholder if step 2 didn't
#    find one (e.g., user already cleaned T.RC manually).
existing = {(b.get("date"), b.get("kind"), b.get("subject"))
            for b in blocks}
needs_so_rc = not any(
    b.get("kind") == "exam" and b.get("subject") == "SO&RC"
    for b in blocks
)
if needs_so_rc:
    blocks.append({
        "date": "2026-06-19",
        "start": "09:00",
        "end": "12:00",
        "kind": "exam",
        "subject": "SO&RC",
        "topic": ("placeholder - AI1202 SO+RC final exam, "
                  "UPDATE WHEN CONFIRMED"),
    })
    added += 1

blocks.sort(key=lambda b: (b.get("date", ""), b.get("start", "")))
s["blocks"] = blocks

with open(P, "w", encoding="utf-8") as f:
    json.dump(s, f, indent=2, ensure_ascii=False)

print(f"removed {removed} wrong PS exam rows; "
      f"modified {modified} T.RC -> SO&RC; "
      f"added {added} SO&RC exam placeholders. "
      f"total blocks: {len(blocks)}")
