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
from datetime import date, datetime, timezone

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


def format_message(
    weakest: dict | None, action: dict | None, today: str
) -> str:
    if weakest is None:
        return "No grades recorded yet — open the app once to seed grades.jsonl."
    subj = weakest["subject"]
    pct = round(weakest["ratio"] * 100)
    earned = weakest["earned"]
    mx = weakest["max"]
    head = f"Weakest: {subj} ({earned:g}/{mx:g} = {pct}%)"
    if action is None:
        return (
            f"{head}\n"
            f"No open assignments tracked for {subj}. "
            f"Open the chat: [[study_now: {subj}]] or [[plan: today]]."
        )
    title = action["title"]
    due = action["dueDate"] or "no due date"
    days = action["daysToDue"]
    if action["status"] == "overdue":
        tag = f"OVERDUE by {abs(days)}d"
    elif days is None:
        tag = "no due date"
    elif days == 0:
        tag = "DUE TODAY"
    elif days == 1:
        tag = "due tomorrow"
    else:
        tag = f"due in {days}d"
    return (
        f"{head}\n"
        f"Next: {title}\n"
        f"Due {due} ({tag})."
    )


def post_telegram(token: str, chat_id: str, text: str) -> int:
    url = f"https://api.telegram.org/bot{token}/sendMessage"
    body = urllib.parse.urlencode({
        "chat_id": chat_id,
        "text": text,
        "disable_notification": "false",
    }).encode("utf-8")
    req = urllib.request.Request(url, data=body, method="POST")
    with urllib.request.urlopen(req, timeout=15) as resp:
        return resp.status


def main(argv: list[str]) -> int:
    today = datetime.now(timezone.utc).date().isoformat()
    grades = read_jsonl(GRADES_FILE)
    assignments = read_jsonl(ASSIGN_FILE)
    summaries = summarize_by_subject(grades)
    weakest = pick_weakest(summaries)
    action = (
        pick_next_action(assignments, today, weakest["subject"])
        if weakest else None
    )
    msg = format_message(weakest, action, today)

    if os.environ.get("JARVIS_PUSH_DRY_RUN") == "1":
        print(msg)
        return 0

    token = os.environ.get("TELEGRAM_BOT_TOKEN", "").strip()
    chat = os.environ.get("TELEGRAM_CHAT_ID", "").strip()
    if not token or not chat:
        print("ERROR: TELEGRAM_BOT_TOKEN and TELEGRAM_CHAT_ID required", file=sys.stderr)
        return 2
    try:
        status = post_telegram(token, chat, msg)
    except urllib.error.HTTPError as e:
        print(f"telegram HTTP {e.code}: {e.read().decode('utf-8', 'replace')}", file=sys.stderr)
        return 3
    except Exception as e:
        print(f"telegram error: {e}", file=sys.stderr)
        return 4
    print(f"sent ts={today} status={status} subject={weakest['subject'] if weakest else '-'}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
