#!/usr/bin/env python3
"""Quick dump of /opt/jarvis/data/grades.jsonl rolled up per subject,
weakest first. Just for human verification — the same logic lives in
Kotlin Grades.summaryBySubject(). Pipe via:
    ssh root@46.247.109.91 "python3 -" < tools/dump-grades.py
"""
import json

P = "/opt/jarvis/data/grades.jsonl"

latest = {}
with open(P) as f:
    for line in f:
        line = line.strip()
        if not line:
            continue
        r = json.loads(line)
        rid = r["id"]
        if rid not in latest or r["ts"] > latest[rid]["ts"]:
            latest[rid] = r

by_subj = {}
for r in latest.values():
    by_subj.setdefault(r["subject"], []).append(r)

results = []
for subj, rows in by_subj.items():
    earned = sum(x["earned"] for x in rows)
    mx = sum(x["max"] for x in rows)
    ratio = earned / mx if mx > 0 else 0.0
    results.append((subj, earned, mx, ratio, rows))

results.sort(key=lambda t: t[3])

for subj, earned, mx, ratio, rows in results:
    pct = int(ratio * 100)
    print(f"\n=== {subj}: {earned:.2f}/{mx:.2f} ({pct}%) ===")
    for r in sorted(rows, key=lambda x: x["component"]):
        comp = r["component"]
        e = r["earned"]
        m = r["max"]
        note = r.get("note", "") or ""
        tag = f"  [{note}]" if "(not yet graded)" in note else ""
        print(f"  {comp}: {e}/{m}{tag}")
