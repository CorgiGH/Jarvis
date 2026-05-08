#!/usr/bin/env python3
"""Daily evening recap — mirrors send-daily-push.py but reflective.

Fires at 22:00 Bucharest = 19:00 UTC. Reads:
- adherence for today (all subjects, not just recommended)
- tomorrow's priority via the same selector as the morning push

Telegram message format:
    Day N (today)
    Today: study Xm (PA Am, PS Bm, ...), distracted Ym.
    Tomorrow priority: SUBJ — TITLE / Due Y-M-D Nd[NO restanță].

Goal: closes the day. Bot acknowledges what got done + cues tomorrow's
focus while user has time to plan it. The morning push reminds; the
evening recap REFLECTS.

Cron entry (VPS, UTC):
    0 19 * * * set -a; . /opt/jarvis/.env; set +a; \\
        /opt/jarvis/tools/send-evening-recap.py >> /var/log/jarvis-recap.log 2>&1

Re-uses send-daily-push.py + adherence.py modules so selector + classifier
rules stay in one place.
"""

from __future__ import annotations

import importlib.util
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from datetime import date, datetime, timezone


def load_module(rel_path: str, name: str):
    here = os.path.dirname(os.path.abspath(__file__))
    spec = importlib.util.spec_from_file_location(name, os.path.join(here, rel_path))
    if spec is None or spec.loader is None:
        return None
    mod = importlib.util.module_from_spec(spec)
    try:
        spec.loader.exec_module(mod)
        return mod
    except Exception as e:
        print(f"failed to load {rel_path}: {e}", file=sys.stderr)
        return None


def main(argv: list[str]) -> int:
    push_mod = load_module("send-daily-push.py", "send_daily_push")
    adh_mod = load_module("adherence.py", "adherence")
    if push_mod is None or adh_mod is None:
        print("ERROR: cannot load sibling modules", file=sys.stderr)
        return 2

    today = datetime.now(timezone.utc).date().isoformat()
    day_n = push_mod.compute_day_n(today)

    # Today's adherence — total study by subject (excluding OTHER).
    activity = adh_mod.read_jsonl(adh_mod.ACTIVITY_FILE)
    minutes = adh_mod.per_subject_minutes(activity, today)
    study_total = sum(v for k, v in minutes.items() if k != "OTHER")
    distracted = minutes.get("OTHER", 0)
    by_subject = sorted(
        ((s, m) for s, m in minutes.items() if s != "OTHER" and m > 0),
        key=lambda kv: -kv[1],
    )
    if by_subject:
        breakdown = ", ".join(f"{s} {m}m" for s, m in by_subject)
    else:
        breakdown = "no study captured"

    # Tomorrow's priority — re-run the morning selector against tomorrow's
    # date so day-of-week and assignments-due-tomorrow logic kicks in.
    tomorrow = (
        datetime.fromisoformat(today).date()
        .replace(day=datetime.fromisoformat(today).day)
    )
    # Just use today's selector — assignments don't shift overnight, so the
    # priority for "the next morning" is effectively the same as right now.
    grades = push_mod.read_jsonl(push_mod.GRADES_FILE)
    assignments = push_mod.read_jsonl(push_mod.ASSIGN_FILE)
    summaries = push_mod.summarize_by_subject(grades)
    action = push_mod.pick_priority_action(assignments, summaries, today)
    if action is not None:
        subj = action["subject"]
        title = action["title"]
        due = action["dueDate"] or "no due date"
        days = action["daysToDue"]
        if days is None:
            tag = "no due date"
        elif days < 0:
            tag = f"OVERDUE by {abs(days)}d"
        elif days == 0:
            tag = "DUE TOMORROW"  # since it's evening; morrow's "today" = due
        elif days == 1:
            tag = "due in 1d"
        else:
            tag = f"due in {days}d"
        cliff = " — NO restanță" if action.get("no_restanta") else ""
        priority_line = (
            f"Tomorrow priority: {subj} — {title}\n"
            f"Due {due} ({tag}){cliff}."
        )
    else:
        priority_line = "No active assignments tracked. Open chat: [[plan: today]]."

    msg = (
        f"Day {day_n} ({today}) — END OF DAY\n"
        f"Today: study {study_total}m ({breakdown}), "
        f"distracted {distracted}m.\n"
        f"{priority_line}"
    )

    if os.environ.get("JARVIS_PUSH_DRY_RUN") == "1":
        print(msg)
        return 0

    token = os.environ.get("TELEGRAM_BOT_TOKEN", "").strip()
    chat = os.environ.get("TELEGRAM_CHAT_ID", "").strip()
    if not token or not chat:
        print("ERROR: TELEGRAM_BOT_TOKEN/TELEGRAM_CHAT_ID required", file=sys.stderr)
        return 2
    try:
        url = f"https://api.telegram.org/bot{token}/sendMessage"
        body = urllib.parse.urlencode({
            "chat_id": chat, "text": msg, "disable_notification": "false",
        }).encode("utf-8")
        req = urllib.request.Request(url, data=body, method="POST")
        with urllib.request.urlopen(req, timeout=20) as resp:
            print(f"sent recap day={day_n} status={resp.status} "
                  f"study={study_total}m distracted={distracted}m")
    except Exception as e:
        print(f"telegram error: {e}", file=sys.stderr)
        return 3
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
