#!/usr/bin/env python3
"""Daily forcing-function push — Telegram MVP.

Reads /opt/jarvis/data/grades.jsonl + assignments.jsonl, picks the
weakest subject (lowest earned/max ratio) and that subject's next
concrete action (closest due-date, overdue first), then POSTs a
single Telegram message to api.telegram.org.

Why this exists (council 1778..., round-3 First Principles): the
chat-bot is pull-mode. Without a server-side daily push the entire
study-companion is conditional on the user remembering to open the
app. This script is the push channel.

Schema mirrors:
- Grades.kt computeId / summaryBySubject (latest-row-wins per
  sha256(subject|component)[:16]; sorted by ratio ascending).
- Assignments.kt currentFrom (set/done kinds; sort by daysToDue
  ascending; overdue surface first).

No log, no acknowledgment loop in v1. Run via VPS cron:
    0 9 * * * /opt/jarvis/tools/send-daily-push.py >> /var/log/jarvis-push.log 2>&1

Required env (read from /opt/jarvis/.env via systemd or shell):
    TELEGRAM_BOT_TOKEN  — from @BotFather
    TELEGRAM_CHAT_ID    — user's numeric chat id

Optional:
    JARVIS_GRADES_FILE       (default /opt/jarvis/data/grades.jsonl)
    JARVIS_ASSIGNMENTS_FILE  (default /opt/jarvis/data/assignments.jsonl)
    JARVIS_PUSH_DRY_RUN      ("1" prints the message instead of POSTing)
"""

from __future__ import annotations

import hashlib
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from datetime import date, datetime, timedelta, timezone

GRADES_FILE = os.environ.get(
    "JARVIS_GRADES_FILE", "/opt/jarvis/data/grades.jsonl"
)
ASSIGN_FILE = os.environ.get(
    "JARVIS_ASSIGNMENTS_FILE", "/opt/jarvis/data/assignments.jsonl"
)


def compute_grade_id(subject: str, component: str) -> str:
    raw = f"{subject}|{component}".encode("utf-8")
    return hashlib.sha256(raw).hexdigest()[:16]


def compute_assign_id(subject: str, title: str, due: str | None) -> str:
    raw = f"{subject}|{title}|{due or ''}".encode("utf-8")
    return hashlib.sha256(raw).hexdigest()[:16]


def read_jsonl(path: str) -> list[dict]:
    if not os.path.exists(path):
        return []
    out = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                row = json.loads(line)
                if row.get("v") != 1:
                    continue
                out.append(row)
            except Exception:
                continue
    return out


def summarize_by_subject(grades: list[dict]) -> list[dict]:
    """Mirror Grades.summaryBySubject: latest-per-id rollup grouped by
    subject, ratio = earned/max (0.0 if max=0), sorted ratio asc."""
    by_id: dict[str, dict] = {}
    for r in grades:
        rid = r.get("id") or compute_grade_id(r["subject"], r["component"])
        prev = by_id.get(rid)
        if prev is None or r["ts"] > prev["ts"]:
            by_id[rid] = r
    by_subject: dict[str, list[dict]] = {}
    for r in by_id.values():
        by_subject.setdefault(r["subject"], []).append(r)
    summaries = []
    for subject, rows in by_subject.items():
        earned = sum(r["earned"] for r in rows)
        mx = sum(r["max"] for r in rows)
        ratio = earned / mx if mx > 0 else 0.0
        summaries.append({
            "subject": subject,
            "earned": earned,
            "max": mx,
            "ratio": ratio,
            "components": len(rows),
        })
    summaries.sort(key=lambda s: s["ratio"])
    return summaries


def pick_weakest(summaries: list[dict]) -> dict | None:
    return summaries[0] if summaries else None


def _days_to(due: str | None, today: str) -> int | None:
    if not due:
        return None
    try:
        d = date.fromisoformat(due)
        t = date.fromisoformat(today)
        return (d - t).days
    except Exception:
        return None


def pick_next_action(
    assignments: list[dict], today: str, subject: str
) -> dict | None:
    """Mirror Assignments.currentFrom: filter `set` rows for subject,
    drop those with a matching `done` row, sort overdue-first then by
    days-to-due ascending. Returns view dict or None."""
    sets = [a for a in assignments if a.get("kind") == "set" and a["subject"] == subject]
    done_ids = {
        a["id"] for a in assignments if a.get("kind") == "done"
    }
    views = []
    for a in sets:
        if a["id"] in done_ids:
            continue
        due = a.get("dueDate")
        days = _days_to(due, today)
        if days is None:
            status = "active"
        elif days < 0:
            status = "overdue"
        elif days <= 3:
            status = "due-soon"
        else:
            status = "active"
        views.append({
            "subject": a["subject"],
            "title": a["title"],
            "dueDate": due,
            "daysToDue": days,
            "status": status,
        })
    if not views:
        return None
    views.sort(key=lambda v: (
        v["daysToDue"] if v["daysToDue"] is not None else 10**9,
    ))
    return views[0]


# Subjects with NO restanță (failed = no makeup exam = course re-take next year).
# Council 1778256562 Devil's Advocate flagged this: priority sort must surface
# no-restanță deadlines BEFORE weakest-subject-by-ratio, otherwise a forcing
# function pointed at the weakest subject silently misdirects the user away
# from the actual cliff. PS is the only "V" (Verificare) subject in user's
# 2026 spring semester per memory user_uaic_finals_2026.md; the rest are "E"
# (Examen) with restanță in late June.
NO_RESTANTA_SUBJECTS = {"PS"}


def pick_priority_action(
    assignments: list[dict], summaries: list[dict], today: str
) -> dict | None:
    """v2 selector (council 1778256562 fix). Picks the single highest-stakes
    open assignment across ALL subjects, ordered by:
      1. has-due-date AND no-restanță (PS Tema A-D rank above all)
      2. has-due-date AND days-to-due ascending (overdue → due-today → far)
      3. weakest-subject-by-ratio as tiebreaker among similar due dates
      4. no-due-date items rank last
    Returns view dict (with subject, title, dueDate, daysToDue, status,
    no_restanta) or None when zero active assignments anywhere.
    """
    done_ids = {a["id"] for a in assignments if a.get("kind") == "done"}
    ratio_by_subject = {s["subject"]: s["ratio"] for s in summaries}
    views: list[dict] = []
    for a in assignments:
        if a.get("kind") != "set" or a["id"] in done_ids:
            continue
        due = a.get("dueDate")
        days = _days_to(due, today)
        subj = a["subject"]
        if days is None:
            status = "active"
        elif days < 0:
            status = "overdue"
        elif days <= 3:
            status = "due-soon"
        else:
            status = "active"
        views.append({
            "subject": subj,
            "title": a["title"],
            "dueDate": due,
            "daysToDue": days,
            "status": status,
            "no_restanta": subj in NO_RESTANTA_SUBJECTS,
            "ratio": ratio_by_subject.get(subj, 1.0),
        })
    if not views:
        return None
    # Ordering key — multi-tier tuple, all ascending sort:
    #  (a) has_due == 0 if days is not None else 1  → dated items first
    #  (b) -no_restanta_int                          → no-restanță items first
    #  (c) days                                      → soonest first (overdue negative)
    #  (d) ratio                                     → weakest first as tiebreaker
    def key(v: dict):
        days = v["daysToDue"]
        return (
            0 if days is not None else 1,
            0 if v["no_restanta"] else 1,
            days if days is not None else 10**9,
            v["ratio"],
        )
    views.sort(key=key)
    return views[0]


def format_message(
    summaries: list[dict], action: dict | None, today: str, day_n: int,
    adherence: dict | None = None,
) -> str:
    """v3 (closed-loop fix 2026-05-09). Surfaces priority action + standing
    line + yesterday's adherence so the user (and the bot itself, on the
    next round) can see whether the same recommendation has been ignored
    repeatedly. day_n is the day-stamp sequence number (HIGH-A mitigation:
    missing days become visible). adherence is the result of
    `adherence.compute_adherence(history, activity, yesterday)` or None
    when no yesterday push exists yet."""
    standing = (
        ", ".join(
            f"{s['subject']} {round(s['ratio'] * 100)}%"
            for s in summaries[:5]  # weakest-first per summarize_by_subject
        )
        if summaries else "no grades yet"
    )
    adherence_line = ""
    if adherence is not None and adherence.get("recommended"):
        rec = adherence["recommended"]
        ratio = adherence.get("ratio", 0.0)
        rec_min = adherence.get("recommended_minutes", 0)
        total = adherence.get("total_study_minutes", 0)
        if total == 0:
            adherence_line = f"Yesterday: pushed {rec}, no study activity captured.\n"
        else:
            pct = round(ratio * 100)
            # Surface delta — what got worked on instead.
            obs = adherence.get("observed", {})
            others = sorted(
                ((s, mn) for s, mn in obs.items() if s != rec and s != "OTHER"),
                key=lambda kv: -kv[1],
            )[:2]
            delta = ", ".join(f"{s} {mn}m" for s, mn in others) or "no other study"
            adherence_line = (
                f"Yesterday: {pct}% adherence to {rec} "
                f"({rec_min}m / {total}m). Other: {delta}.\n"
            )
    if action is None:
        head = "No active assignments tracked."
        return (
            f"Day {day_n} ({today})\n"
            f"{adherence_line}"
            f"{head}\n"
            f"Standing: {standing}.\n"
            f"Open the chat: [[plan: today]]."
        )
    subj = action["subject"]
    title = action["title"]
    due = action["dueDate"] or "no due date"
    days = action["daysToDue"]
    if days is None:
        tag = "no due date"
    elif days < 0:
        tag = f"OVERDUE by {abs(days)}d"
    elif days == 0:
        tag = "DUE TODAY"
    elif days == 1:
        tag = "due tomorrow"
    else:
        tag = f"due in {days}d"
    cliff_note = " — NO restanță, hard cliff" if action["no_restanta"] else ""
    return (
        f"Day {day_n} ({today})\n"
        f"{adherence_line}"
        f"PRIORITY: {subj} — {title}\n"
        f"Due {due} ({tag}){cliff_note}.\n"
        f"Standing: {standing}."
    )


def post_telegram(token: str, chat_id: str, text: str,
                   max_attempts: int = 3) -> tuple[int, str]:
    """POST with retry. Council Risk Analyst HIGH mitigation: a single
    network blip at 09:00 UTC must not silently lose the day's reminder.
    3 attempts with 30s/60s/90s backoff before failing."""
    url = f"https://api.telegram.org/bot{token}/sendMessage"
    body = urllib.parse.urlencode({
        "chat_id": chat_id,
        "text": text,
        "disable_notification": "false",
    }).encode("utf-8")
    last_err = ""
    for attempt in range(1, max_attempts + 1):
        try:
            req = urllib.request.Request(url, data=body, method="POST")
            with urllib.request.urlopen(req, timeout=20) as resp:
                return resp.status, ""
        except urllib.error.HTTPError as e:
            err_body = e.read().decode("utf-8", "replace")
            # 4xx (auth/chat-not-found) is permanent — don't retry.
            if 400 <= e.code < 500:
                return e.code, f"HTTP {e.code}: {err_body[:200]}"
            last_err = f"HTTP {e.code}: {err_body[:200]}"
        except Exception as e:
            last_err = f"{type(e).__name__}: {e}"
        if attempt < max_attempts:
            import time as _t
            _t.sleep(30 * attempt)
    return 0, last_err


def compute_day_n(today: str, anchor_iso: str = "2026-05-09") -> int:
    """Day-stamp sequence: 1-based count from the first push day. Lets the
    user notice gaps in the sequence (e.g. "Day 12" Tuesday + "Day 14"
    Thursday = Wednesday silently lost)."""
    try:
        return (date.fromisoformat(today) - date.fromisoformat(anchor_iso)).days + 1
    except Exception:
        return 0


def write_status(path: str, status: dict) -> None:
    import json as _json
    try:
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            _json.dump(status, f, ensure_ascii=False, indent=2)
    except Exception:
        pass


STATUS_PATH = os.environ.get(
    "JARVIS_PUSH_STATUS_FILE", "/opt/jarvis/data/last_push_status.json"
)


def _load_adherence_module():
    """Side-load tools/adherence.py without polluting Python path."""
    import importlib.util
    here = os.path.dirname(os.path.abspath(__file__))
    spec = importlib.util.spec_from_file_location(
        "adherence", os.path.join(here, "adherence.py"),
    )
    if spec is None or spec.loader is None:
        return None
    mod = importlib.util.module_from_spec(spec)
    try:
        spec.loader.exec_module(mod)
        return mod
    except Exception:
        return None


def main(argv: list[str]) -> int:
    today = datetime.now(timezone.utc).date().isoformat()
    day_n = compute_day_n(today)
    grades = read_jsonl(GRADES_FILE)
    assignments = read_jsonl(ASSIGN_FILE)
    summaries = summarize_by_subject(grades)
    action = pick_priority_action(assignments, summaries, today)

    # v3: closed-loop adherence — read yesterday's push from push_history
    # + cross-reference activity.jsonl to compute what got worked on.
    adherence = None
    adh = _load_adherence_module()
    if adh is not None:
        try:
            yesterday = (
                datetime.now(timezone.utc) - timedelta(days=1)
            ).date().isoformat()
            history = adh.read_jsonl(adh.PUSH_HISTORY_FILE)
            activity = adh.read_jsonl(adh.ACTIVITY_FILE)
            adherence = adh.compute_adherence(history, activity, yesterday)
        except Exception:
            adherence = None

    msg = format_message(summaries, action, today, day_n, adherence)

    if os.environ.get("JARVIS_PUSH_DRY_RUN") == "1":
        print(msg)
        return 0

    token = os.environ.get("TELEGRAM_BOT_TOKEN", "").strip()
    chat = os.environ.get("TELEGRAM_CHAT_ID", "").strip()
    status_record: dict = {
        "ts": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "today": today,
        "day_n": day_n,
        "subject": action["subject"] if action else None,
        "title": action["title"] if action else None,
        "due_in_days": action["daysToDue"] if action else None,
    }
    if not token or not chat:
        status_record["http_code"] = -1
        status_record["error"] = "TELEGRAM_BOT_TOKEN/TELEGRAM_CHAT_ID missing"
        write_status(STATUS_PATH, status_record)
        print("ERROR: TELEGRAM_BOT_TOKEN and TELEGRAM_CHAT_ID required", file=sys.stderr)
        return 2
    code, err = post_telegram(token, chat, msg)
    status_record["http_code"] = code
    if err:
        status_record["error"] = err
    write_status(STATUS_PATH, status_record)
    # v3: append every push to push_history.jsonl (used by adherence
    # tomorrow), regardless of HTTP success. If HTTP failed we still
    # logged what the bot WOULD have surfaced.
    if adh is not None:
        try:
            adh.append_push_history(status_record)
        except Exception:
            pass
    if code < 200 or code >= 300:
        print(f"telegram FAILED code={code} err={err}", file=sys.stderr)
        return 3
    print(f"sent day={day_n} ts={today} status={code} "
          f"subject={action['subject'] if action else '-'}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
