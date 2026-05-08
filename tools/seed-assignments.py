#!/usr/bin/env python3
"""Idempotent seed of known UAIC homework assignments into
/opt/jarvis/data/assignments.jsonl.

Schema (Assignments.kt AssignmentEntry):
  {"id": <sha256(subject|title|dueDate)[:16]>,
   "ts": <ISO Instant>, "kind": "set",
   "subject": <SUBJECT>, "title": <TITLE>,
   "dueDate": <YYYY-MM-DD or null>, "v": 1}

Re-runs collapse via id (same subject|title|dueDate hashes identically) so
this can be re-piped after schedule changes.

Discovered from C:\\Users\\User\\Desktop\\Second brain (Explore agent
2026-05-08): PS HW Tema_A.pdf, Tema_B.pdf, Tema_C.pdf, Tema_D.pdf (deadline
2026-05-21 per memory user_uaic_finals_2026.md, no restanță) + ALO/alo_t1
.. alo_t5.pdf (deadlines unknown, registered without dueDate so they
appear as "active" in [[assignments]]).

Pipe:
  ssh root@46.247.109.91 "python3 -" < tools/seed-assignments.py
"""

import hashlib
import json
import os
from datetime import datetime, timezone

P = "/opt/jarvis/data/assignments.jsonl"

PS_DEADLINE = "2026-05-21"

ASSIGNMENTS = [
    # PS Tema A-D, all due May 21 (no restanta).
    ("PS",  "Tema A — Laplace + LLN + CLT (R)",            PS_DEADLINE),
    ("PS",  "Tema B — Monte Carlo + integrals + Poisson",  PS_DEADLINE),
    ("PS",  "Tema C",                                      PS_DEADLINE),
    ("PS",  "Tema D — unemployment data analysis (R)",     PS_DEADLINE),
    # ALO Tema 1-5, deadlines unknown.
    ("ALO", "Tema 1", None),
    ("ALO", "Tema 2", None),
    ("ALO", "Tema 3", None),
    ("ALO", "Tema 4", None),
    ("ALO", "Tema 5", None),
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

    print(f"appended {appended} assignment rows; total in ledger: "
          f"{len(existing_ids) + appended}")


if __name__ == "__main__":
    main()
