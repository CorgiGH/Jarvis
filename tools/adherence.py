#!/usr/bin/env python3
"""Adherence — did the user actually work on what the bot recommended?

The daily Telegram push picks a single PRIORITY subject + assignment.
Without a closed loop the same priority can fire for 12 days while
reality drifts elsewhere. This module:

1. Classifies each ActivityEntry into a subject ∈ {PS, POO, PA, ALO,
   SO, RC, OTHER} via process-name + window-title regex hints.
2. Sums per-subject "hours" by counting samples between consecutive
   non-idle activity entries (logger fires every ~5 min).
3. Compares last push's recommended subject vs observed subjects for
   that day and emits an adherence ratio + concrete delta.

Designed to import from send-daily-push.py; also runnable standalone
for diagnostics.
"""

from __future__ import annotations

import json
import os
import re
import sys
from datetime import datetime, timedelta, timezone
from typing import Iterable

ACTIVITY_FILE = os.environ.get(
    "JARVIS_ACTIVITY_FILE", "/opt/jarvis/data/activity.jsonl"
)
PUSH_HISTORY_FILE = os.environ.get(
    "JARVIS_PUSH_HISTORY_FILE", "/opt/jarvis/data/push_history.jsonl"
)

# Per-subject lowercase substring rules. First-match wins; check order
# matters. PA-vs-POO and PS-vs-stats are the ambiguous pairs — keep
# higher-specificity rules earlier.
SUBJECT_RULES: list[tuple[str, list[str]]] = [
    ("PS", [
        "rstudio", "rmarkdown", "tema_a", "tema_b", "tema_c", "tema_d",
        "ps lab", "ps hw", "probabilit", "statistic", " ps -",
        "monte carlo", "kolmogorov", "laplace", "poisson", "binomial",
    ]),
    ("ALO", [
        "alo curs", "alo lab", "alo seminar", "alo tema",
        "spațiu liniar", "algebra liniar", "diagonalizab",
        "vector propriu", "valori proprii",
    ]),
    ("PA", [
        "algorithm design", "pa test", "pa curs", "pa seminar",
        "proiectarea algoritm", "np-complete", "np complete",
        "greedy algorithm", "huffman", "dijkstra", "kruskal",
        "dynamic programming", " pa ", "ed-pa", "pa-tests",
    ]),
    ("POO", [
        "object-oriented", "poo curs", "poo lab",
        "intellij idea", " poo ", " oop ", "schildt", "kaler",
        ".java", "javac", "java :", "polymorphism", "inheritance",
    ]),
    ("RC", [
        "socket", "tcp", "udp", "wireshark", "rc lab", "rc curs",
        "retele de calculatoare", "computer networks",
        " rc -", "ip header", "subnet", "ethernet",
    ]),
    ("SO", [
        "wsl", "linux", "ubuntu", "kernel", "fork()", "syscall",
        "/proc", "/dev/", "bash script", "shell script",
        "sisteme de operare", "sisteme-de-operare",
        "operating system", "operating-systems-and-computer-networks",
        "cristian.vidrascu", "tlpi", "kerrisk", "tanenbaum",
        "pid 0", "posix",
    ]),
]

# Apps that are NEVER considered productive study time, regardless of title.
NON_STUDY_PROCESSES = {
    "discord.exe", "spotify.exe", "steam.exe",
    "league of legends.exe", "leagueclient.exe",
    "applicationframehost.exe", "explorer.exe",
    "minecraftlauncher.exe", "javaw.exe",
}


def classify_activity(entry: dict) -> str:
    """Return subject ∈ {PS, POO, PA, ALO, SO, RC, OTHER}."""
    proc = (entry.get("process") or "").lower()
    title = (entry.get("title") or "").lower()
    if proc in NON_STUDY_PROCESSES:
        return "OTHER"
    haystack = f"{proc} {title}"
    for subject, rules in SUBJECT_RULES:
        for rule in rules:
            if rule in haystack:
                return subject
    return "OTHER"


def per_subject_minutes(activity: list[dict], date_iso: str) -> dict[str, int]:
    """Sum minutes per subject for the given UTC date (YYYY-MM-DD).
    Each activity entry counts as the gap-to-next-entry duration capped
    at 10 minutes (logger samples every 5min; gaps >10min likely user-
    away or sleep). Returns dict subject → minutes."""
    if not activity:
        return {}
    target_prefix = date_iso  # ISO date string, compared as substring on entry["ts"]
    rows = sorted(
        (e for e in activity if (e.get("ts") or "").startswith(target_prefix)),
        key=lambda e: e["ts"],
    )
    if not rows:
        return {}
    minutes: dict[str, int] = {}
    for i, row in enumerate(rows):
        # Compute window for this row's classification: time until next row,
        # capped to 10min (samples every 5min; bigger gap = away).
        ts = row["ts"]
        try:
            t = datetime.fromisoformat(ts.replace("Z", "+00:00"))
        except Exception:
            continue
        if i + 1 < len(rows):
            try:
                t_next = datetime.fromisoformat(rows[i + 1]["ts"].replace("Z", "+00:00"))
            except Exception:
                t_next = t + timedelta(minutes=5)
        else:
            t_next = t + timedelta(minutes=5)
        gap = (t_next - t).total_seconds() / 60.0
        if gap > 10:
            gap = 5  # treat as one sample
        if gap < 0:
            continue
        subj = classify_activity(row)
        minutes[subj] = minutes.get(subj, 0) + int(gap)
    return minutes


def latest_push_for_date(history: list[dict], date_iso: str) -> dict | None:
    """Return the most recent push recorded for the given UTC date, or
    None. push_history rows have schema: {ts, today, day_n, subject,
    title, due_in_days, http_code}."""
    matching = [r for r in history if r.get("today") == date_iso]
    if not matching:
        return None
    return max(matching, key=lambda r: r.get("ts", ""))


def compute_adherence(
    history: list[dict], activity: list[dict], target_date_iso: str
) -> dict:
    """For target_date_iso (YYYY-MM-DD), return:
        {
          recommended: <subject or None>,
          observed: {subject: minutes},
          recommended_minutes: int,
          total_study_minutes: int,
          ratio: float (0..1, recommended/total_study),
          summary: "human readable line",
        }
    """
    push = latest_push_for_date(history, target_date_iso)
    observed = per_subject_minutes(activity, target_date_iso)
    total_study = sum(v for k, v in observed.items() if k != "OTHER")
    rec_subj = (push or {}).get("subject")
    rec_minutes = observed.get(rec_subj, 0) if rec_subj else 0
    ratio = (rec_minutes / total_study) if total_study > 0 else 0.0
    parts = []
    for s, m in sorted(observed.items(), key=lambda kv: -kv[1])[:5]:
        if m > 0:
            parts.append(f"{s} {m}m")
    obs_str = ", ".join(parts) if parts else "no activity captured"
    if rec_subj is None:
        summary = f"No push recorded for {target_date_iso}."
    elif total_study == 0:
        summary = f"Pushed {rec_subj}, no study activity captured for {target_date_iso}."
    else:
        pct = round(ratio * 100)
        summary = (
            f"Pushed {rec_subj}, observed {obs_str}. "
            f"Adherence: {rec_minutes}m / {total_study}m study = {pct}%."
        )
    return {
        "recommended": rec_subj,
        "observed": observed,
        "recommended_minutes": rec_minutes,
        "total_study_minutes": total_study,
        "ratio": ratio,
        "summary": summary,
    }


def append_push_history(record: dict) -> None:
    try:
        os.makedirs(os.path.dirname(PUSH_HISTORY_FILE), exist_ok=True)
        with open(PUSH_HISTORY_FILE, "a", encoding="utf-8") as f:
            f.write(json.dumps(record, ensure_ascii=False) + "\n")
    except Exception:
        pass


def read_jsonl(path: str) -> list[dict]:
    if not os.path.exists(path):
        return []
    out: list[dict] = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                out.append(json.loads(line))
            except Exception:
                continue
    return out


def main(argv: list[str]) -> int:
    """CLI: report adherence for a given date (default: yesterday UTC)."""
    if len(argv) > 1 and argv[1] in ("-h", "--help"):
        print(__doc__)
        return 0
    if len(argv) > 1:
        target = argv[1]
    else:
        yesterday = (datetime.now(timezone.utc) - timedelta(days=1)).date()
        target = yesterday.isoformat()
    history = read_jsonl(PUSH_HISTORY_FILE)
    activity = read_jsonl(ACTIVITY_FILE)
    result = compute_adherence(history, activity, target)
    print(f"Target: {target}")
    print(result["summary"])
    if result["observed"]:
        for s, m in sorted(result["observed"].items(), key=lambda kv: -kv[1]):
            print(f"  {s}: {m} min")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
