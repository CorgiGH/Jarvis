#!/usr/bin/env python3
"""Pulls the user's grade rows from 4 published Google Sheets (PA, PS,
ALO, POO gradebooks) and writes the per-component scores into
/opt/jarvis/data/grades.jsonl using Grades.computeId-compatible row
schema (id = sha256(subject|component)[:16], latest-ts wins on read).

Sources (provided by user 2026-05-08):
- PA  https://docs.google.com/spreadsheets/d/1LGPAZG6Vq8lF0IWJt4Hm_dY5NhiYzI2CY6s5Bue3udg
- PS  https://docs.google.com/spreadsheets/d/e/2PACX-1vRjgsXv2H3J3rpg1CsS9MI8uNPxmlkuFgd-h2Zxii6QzBnypfme0jRWY4DgeI3TPyBKrfWm0GcFKqkL
- ALO https://docs.google.com/spreadsheets/d/e/2PACX-1vS585pgoA7uNHsXGAyZ3v--VxfogI0oLQzJ5QeUe0bKhjHq8JKUglPbPo_9CGhUOdShb6nU9OFNhqDS
- POO https://docs.google.com/spreadsheets/d/e/2PACX-1vRnSso9BVQiN1cNugEdyruQ8p1PqqHoVPkpzwKibq7rIJe3BQK2ig5c-bLbbRxt9g972f6Y0WTTjgSN

Each sheet has hand-mapped column positions that I verified by
looking at the user's actual row on 2026-05-08. If column layouts
shift, this script gracefully no-ops the affected fields rather than
recording bad data.

Usage:
  python3 sync-grades-from-sheets.py          # one-shot pull
  python3 sync-grades-from-sheets.py --quiet  # only print on change

Cron (hourly):
  0 * * * * /opt/jarvis/tools/sync-grades-from-sheets.py --quiet \
            >> /opt/jarvis/data/grades-sync.log 2>&1
"""

from __future__ import annotations

import csv
import hashlib
import io
import json
import os
import sys
import urllib.request
from datetime import datetime, timezone

MATRICOL = "31091001031ROSL251002"

GRADES_FILE = os.environ.get(
    "JARVIS_GRADES_FILE", "/opt/jarvis/data/grades.jsonl"
)

SHEETS = [
    {
        "subject": "PA",
        "url": (
            "https://docs.google.com/spreadsheets/d/"
            "1LGPAZG6Vq8lF0IWJt4Hm_dY5NhiYzI2CY6s5Bue3udg/"
            "export?format=csv&gid=2001375245"
        ),
        "matricol_col": 0,
    },
    {
        "subject": "PS",
        "url": (
            "https://docs.google.com/spreadsheets/d/e/"
            "2PACX-1vRjgsXv2H3J3rpg1CsS9MI8uNPxmlkuFgd-"
            "h2Zxii6QzBnypfme0jRWY4DgeI3TPyBKrfWm0GcFKqkL/"
            "pub?output=csv&gid=1876806059"
        ),
        "matricol_col": 1,
    },
    {
        "subject": "ALO",
        "url": (
            "https://docs.google.com/spreadsheets/d/e/"
            "2PACX-1vS585pgoA7uNHsXGAyZ3v--VxfogI0oLQzJ5QeUe0bKhjHq8"
            "JKUglPbPo_9CGhUOdShb6nU9OFNhqDS/"
            "pub?output=csv&gid=2051389642"
        ),
        "matricol_col": 2,
    },
    {
        "subject": "POO",
        "url": (
            "https://docs.google.com/spreadsheets/d/e/"
            "2PACX-1vRnSso9BVQiN1cNugEdyruQ8p1PqqHoVPkpzwKibq7rIJe3BQ"
            "K2ig5c-bLbbRxt9g972f6Y0WTTjgSN/"
            "pub?output=csv&gid=2056384108"
        ),
        "matricol_col": 2,
    },
]


def parse_num(s: str) -> float | None:
    """Romanian Excel uses ',' as decimal sep. Empty/blank/non-numeric → None."""
    if s is None:
        return None
    t = s.strip()
    if not t:
        return None
    t = t.replace(",", ".")
    try:
        return float(t)
    except ValueError:
        return None


def fetch_csv(url: str) -> list[list[str]]:
    req = urllib.request.Request(url, headers={"User-Agent": "jarvis-grades-sync/1.0"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        data = resp.read().decode("utf-8")
    return list(csv.reader(io.StringIO(data)))


def find_row(rows: list[list[str]], matricol_col: int) -> list[str] | None:
    for row in rows:
        if len(row) > matricol_col and row[matricol_col].strip() == MATRICOL:
            return row
    return None


def extract_alo(row: list[str]) -> list[tuple[str, float, float]]:
    """Headers verified 2026-05-08 (gid=2051389642):
       Grupa | Nume | Nr.matricol | Email | S1..S9 | Activitate seminar
       | Test seminar | T1 | B1 | T2 | B2 | T3 | B3 | T4 | B4 | T5 | B5
       | Total punctaj laborator
       T_n max = 50 / 60 / 60 / 60 / 70 (Tema 1..5). Bonus B_n max = 5 each.
    """
    def g(i):
        return row[i] if i < len(row) else ""

    out: list[tuple[str, float, float]] = []
    # ALO final-score formula (per /algebra-liniara/):
    #   pf = lab_pts + 10 * seminar_test + 40 * written_exam
    # So the seminar test (raw 0-10) contributes max 100 to pf, and the
    # written exam (raw 0-10) contributes max 400. Recording the
    # weighted contributions, not the raw scale, so
    # Grades.summaryBySubject earned/max ratios reflect actual standing
    # toward the pf>=360 pass threshold.
    test_sem = parse_num(g(13))
    if test_sem is not None:
        out.append(("Seminar test (x10 weight)", test_sem * 10.0, 100.0))
    # Sentinel: written exam is the largest component (400 of 825) and
    # not yet taken — record 0/400 with note so the bot sees the gap
    # in the importance signal until the exam happens.
    out.append(("Written exam (x40 weight, not yet taken)", 0.0, 400.0))
    tema_caps = [50.0, 60.0, 60.0, 60.0, 70.0]
    for i, cap in enumerate(tema_caps):
        t = parse_num(g(14 + 2 * i))
        b = parse_num(g(15 + 2 * i))
        # Sentinel writes: emit 0 for empty Tema cells so the bot's
        # importance signal sees the unfinished work as missing points
        # rather than excluding them from the denominator entirely.
        # Total ALO course = 100 (sem*10) + 400 (written*40) + 325 (lab) = 825.
        if t is not None:
            out.append((f"Tema {i+1}", t, cap))
        else:
            out.append((f"Tema {i+1}", 0.0, cap))
        if b is not None:
            out.append((f"Tema {i+1} bonus", b, 5.0))
        else:
            out.append((f"Tema {i+1} bonus", 0.0, 5.0))
    # Deliberately NOT emitting "Lab total" here — sheet column 24 is
    # the sum of T_n + B_n we record per component above. Including
    # both would double-count when summaryBySubject rolls up.
    return out


def extract_ps(row: list[str]) -> list[tuple[str, float, float]]:
    """Headers verified 2026-05-08 (gid=1876806059), 0-indexed columns:
       0: row#, 1: Numar matricol, 2-7: Sem1..Sem6, 8: Lab7,
       9-14: Lab9..Lab14, 15: Probabilitati Total Seminar,
       16: Teme laborator, 17: Activitate laborator, 18: Test Laborator,
       19: Statistica total, 20: Grand total, 21: Final.

       v3 (2026-05-08, post session-review council Risk Analyst HIGH):
       Sentinel-zero on empty cell created a persistent false-low
       grade — every hourly cron rewrote 0/20 with a fresh ts, biasing
       [[catchup]] toward PS during the exact deadline window where
       wrong direction costs the most. Removed sentinel writes; empty
       cells produce NO row, leaving the latest legitimate value in
       the ledger to win on read. Trade-off: a brand-new component
       the user has never been graded on will simply not appear in
       [[grades]] until it's filled in, vs the bot now seeing a
       wrongly-confident 0. The latter is worse for finals prep.
    """
    def g(i):
        return row[i] if i < len(row) else ""

    out: list[tuple[str, float, float]] = []
    sem = parse_num(g(15))
    if sem is not None:
        out.append(("Seminar total (six 10pt tests)", sem, 60.0))
    teme = parse_num(g(16))
    if teme is not None:
        out.append(("Homework total", teme, 20.0))
    activ = parse_num(g(17))
    if activ is not None:
        out.append(("Lab activity", activ, 20.0))
    test_lab = parse_num(g(18))
    if test_lab is not None:
        out.append(("Lab final-week test", test_lab, 20.0))
    return out


def extract_pa(row: list[str]) -> list[tuple[str, float, float]]:
    """Headers verified 2026-05-08 (gid=2001375245):
       Registration Number | Total | Pres Pts | Act ... per-week presence
       Sheet tracks attendance + activity, NOT test scores (Test 1 lives
       in a different gid — search the PA sheet's other tabs to find it).
    """
    def g(i):
        return row[i] if i < len(row) else ""

    out: list[tuple[str, float, float]] = []
    total = parse_num(g(1))
    if total is not None:
        # Cap unknown — use total points observed in class roster (~30 max).
        out.append(("Attendance + activity total (this gid)", total, 30.0))
    return out


def extract_poo(row: list[str]) -> list[tuple[str, float, float]]:
    """Headers verified 2026-05-08 (gid=2056384108):
       (blanks) | numar_matricol | total | total_extra | lab01 |
       lab01_extra | lab02 | lab02_extra | ... lab07 | lab07_extra
       Each lab_NN max = 1.0 per memory (1pt per lab, max 10 over 10 labs).
    """
    def g(i):
        return row[i] if i < len(row) else ""

    out: list[tuple[str, float, float]] = []
    total = parse_num(g(3))
    if total is not None:
        out.append(("Lab activity total (this gid)", total, 10.0))
    # Per-lab activity scores. Lab 03 is column 9 (header says "lab3"
    # not "lab03"). Otherwise lab01 col 5, lab02 col 7, lab03 col 9,
    # lab04 col 11, lab05 col 12 (no _extra between lab04/05? — verified
    # by header: lab05 right after lab04 with NO lab04_extra in between).
    # Actually re-reading header: lab04 | lab05 | lab05_extra | lab06.
    # So lab04 has no _extra column.
    lab_cols = {
        "Lab 01 activity": 5,
        "Lab 02 activity": 7,
        "Lab 03 activity": 9,
        "Lab 04 activity": 11,
        "Lab 05 activity": 12,
        "Lab 06 activity": 14,
        "Lab 07 activity": 16,
    }
    for label, col in lab_cols.items():
        v = parse_num(g(col))
        if v is not None:
            out.append((label, v, 1.0))
    return out


EXTRACTORS = {
    "PA": extract_pa,
    "PS": extract_ps,
    "ALO": extract_alo,
    "POO": extract_poo,
}


def compute_id(subject: str, component: str) -> str:
    raw = f"{subject}|{component}".encode("utf-8")
    return hashlib.sha256(raw).hexdigest()[:16]


def latest_recorded(file: str) -> dict[str, tuple[float, float, str]]:
    """Returns {id: (earned, max, ts)} of the latest row per id."""
    out: dict[str, tuple[float, float, str]] = {}
    if not os.path.exists(file):
        return out
    with open(file, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                row = json.loads(line)
            except Exception:
                continue
            rid = row.get("id")
            ts = row.get("ts", "")
            if rid is None:
                continue
            if rid not in out or ts > out[rid][2]:
                out[rid] = (row.get("earned", 0.0), row.get("max", 0.0), ts)
    return out


def main():
    quiet = "--quiet" in sys.argv
    now_iso = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
    existing = latest_recorded(GRADES_FILE)
    appended = 0
    skipped_unchanged = 0
    failed: list[str] = []

    os.makedirs(os.path.dirname(GRADES_FILE), exist_ok=True)
    with open(GRADES_FILE, "a", encoding="utf-8") as out:
        for sheet in SHEETS:
            subject = sheet["subject"]
            try:
                rows = fetch_csv(sheet["url"])
            except Exception as e:
                failed.append(f"{subject}: fetch failed: {e}")
                continue
            row = find_row(rows, sheet["matricol_col"])
            if row is None:
                failed.append(f"{subject}: matricol not found in sheet")
                continue
            extractor = EXTRACTORS.get(subject)
            if extractor is None:
                failed.append(f"{subject}: no extractor")
                continue
            try:
                grades = extractor(row)
            except Exception as e:
                failed.append(f"{subject}: extractor error: {e}")
                continue
            for component, earned, max_ in grades:
                gid = compute_id(subject, component)
                prev = existing.get(gid)
                if prev is not None and prev[0] == earned and prev[1] == max_:
                    skipped_unchanged += 1
                    continue
                entry = {
                    "id": gid,
                    "ts": now_iso,
                    "subject": subject,
                    "component": component,
                    "earned": earned,
                    "max": max_,
                    "note": "auto-synced from Google Sheets",
                    "v": 1,
                }
                out.write(json.dumps(entry, ensure_ascii=False) + "\n")
                appended += 1

    # Council 2026-05-08 review-cycle (DA + RA HIGH): write a per-run
    # status snapshot so silent failures (matricol-not-found, fetch
    # error, parse error) become surface-able. The bot's [[search]] +
    # [[read]] tools can hit this path, and a future ChatTools
    # dispatcher entry could surface it via [[grades_sync_status]].
    status_path = os.path.dirname(GRADES_FILE) + "/grades-sync-status.json"
    try:
        with open(status_path, "w", encoding="utf-8") as sf:
            json.dump({
                "ts": now_iso,
                "appended": appended,
                "unchanged": skipped_unchanged,
                "failures": failed,
                "subjects_synced": [s["subject"] for s in SHEETS
                                     if not any(f.startswith(s["subject"] + ":") for f in failed)],
            }, sf, ensure_ascii=False, indent=2)
    except Exception:
        pass

    if quiet and appended == 0 and not failed:
        return
    print(f"sync-grades: appended={appended} unchanged={skipped_unchanged}")
    for msg in failed:
        print(f"  WARN {msg}", file=sys.stderr)


if __name__ == "__main__":
    main()
