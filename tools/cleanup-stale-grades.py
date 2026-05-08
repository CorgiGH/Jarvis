#!/usr/bin/env python3
"""One-shot rewrite of /opt/jarvis/data/grades.jsonl that drops two
known-stale rows the initial seed-grades.py introduced before the
Google-Sheets sync took over:

1. ALO/"Tema 1 — Machine precision" — 55/55 single combined row from
   the council-cache seed. The sheet sync now records "Tema 1" (50/50)
   and "Tema 1 bonus" (5/5) as separate rows, which sums to the same
   55 but at the right granularity.
2. ALO/"Lab total" — 120/325 sheet column-24 row. Same total as
   T_n + B_n components combined; including it caused
   summaryBySubject to double-count ALO's lab points.

Drops by computing the canonical id for each (subject, component) and
filtering them out. Kept rows are written back atomically (write to
.tmp + rename) so a crash mid-run won't corrupt the ledger.

Pipe:
  ssh root@46.247.109.91 "python3 -" < tools/cleanup-stale-grades.py
"""

import hashlib
import json
import os

P = "/opt/jarvis/data/grades.jsonl"

STALE = [
    ("ALO", "Tema 1 — Machine precision"),
    ("ALO", "Lab total"),
    # Replaced by weighted "Seminar test (x10 weight)" 77.5/100 since the
    # ALO final-score formula multiplies the raw seminar score by 10.
    ("ALO", "Seminar test (week 7)"),
]


def compute_id(subject: str, component: str) -> str:
    raw = f"{subject}|{component}".encode("utf-8")
    return hashlib.sha256(raw).hexdigest()[:16]


def main():
    if not os.path.exists(P):
        print("nothing to clean — ledger missing")
        return
    stale_ids = {compute_id(s, c) for (s, c) in STALE}
    kept = []
    dropped = 0
    with open(P, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                row = json.loads(line)
            except Exception:
                continue
            if row.get("id") in stale_ids:
                dropped += 1
                continue
            kept.append(line)

    tmp = P + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        for line in kept:
            f.write(line + "\n")
    os.replace(tmp, P)

    print(f"dropped {dropped} stale rows; kept {len(kept)} rows in {P}")


if __name__ == "__main__":
    main()
