#!/usr/bin/env python3
"""One-shot cleanup of double-counted POO Lab N activity rows.

The earlier extract_poo emitted both `Lab activity total` (max 10) AND
per-lab `Lab 01..07 activity` (max 1 each). Since Lab activity total IS
the sum of the per-lab rows, both rolling up into Grades.summaryBySubject
inflated POO max from 100 (the actual scheme: 10 lab + 30 lab eval 1 +
30 lab eval 2 + 30 final) to 100+N where N = count of per-lab rows
that landed (was 7 = 102 with 2 labs scored, would be 107 fully).

User caught it on 2026-05-09 first daily Telegram push: "why is it
3.2/102? it's max 100".

Fix: extract_poo no longer emits per-lab rows. This script appends
mask rows for the per-lab ids already in grades.jsonl — same id
(same subject|component) but earned=0 and max=0, so latestPerComponent
picks them and contributes 0 to both numerator and denominator. The
cleaner alternative would be a physical delete from the JSONL, but
the file is append-only by convention (race-safety + audit trail);
mask-via-zero-row preserves history while restoring correct sums.

Re-runs are safe: id-collision append with same (earned=0, max=0)
just adds another no-op row.

Pipe form:
    ssh root@46.247.109.91 "python3 -" < tools/cleanup-poo-double-counts.py
"""

import hashlib
import json
import os
from datetime import datetime, timezone

GRADES_FILE = os.environ.get(
    "JARVIS_GRADES_FILE", "/opt/jarvis/data/grades.jsonl"
)

LAB_COMPONENTS = [
    "Lab 01 activity",
    "Lab 02 activity",
    "Lab 03 activity",
    "Lab 04 activity",
    "Lab 05 activity",
    "Lab 06 activity",
    "Lab 07 activity",
]


def compute_id(subject: str, component: str) -> str:
    raw = f"{subject}|{component}".encode("utf-8")
    return hashlib.sha256(raw).hexdigest()[:16]


def main():
    target_ids: dict[str, str] = {
        compute_id("POO", c): c for c in LAB_COMPONENTS
    }
    # Find which of the target ids actually exist (across all rows).
    present_ids: set[str] = set()
    if os.path.exists(GRADES_FILE):
        with open(GRADES_FILE, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                try:
                    row = json.loads(line)
                    if row.get("id") in target_ids:
                        present_ids.add(row["id"])
                except Exception:
                    continue

    if not present_ids:
        print("cleanup-poo-double-counts: no per-lab rows present, no-op.")
        return

    now_iso = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
    appended = 0
    with open(GRADES_FILE, "a", encoding="utf-8") as f:
        for pid in present_ids:
            component = target_ids[pid]
            entry = {
                "id": pid,
                "ts": now_iso,
                "subject": "POO",
                "component": component,
                "earned": 0.0,
                "max": 0.0,
                "note": (
                    "masked: per-lab rollup folded into 'Lab activity "
                    "total' to fix double-count (POO max 102 → 100)"
                ),
                "v": 1,
            }
            f.write(json.dumps(entry, ensure_ascii=False) + "\n")
            appended += 1
    print(f"cleanup-poo-double-counts: masked={appended} rows ({sorted(present_ids)})")


if __name__ == "__main__":
    main()
