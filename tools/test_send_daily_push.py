#!/usr/bin/env python3
"""Pure-function tests for send-daily-push.py selector logic.

Run from repo root:
    python3 tools/test_send_daily_push.py
"""

from __future__ import annotations

import importlib.util
import os
import sys
import unittest
from pathlib import Path


def _load_module():
    """Load tools/send-daily-push.py as a module despite the hyphen."""
    here = Path(__file__).resolve().parent
    spec = importlib.util.spec_from_file_location(
        "send_daily_push", here / "send-daily-push.py"
    )
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


m = _load_module()


def grade(subject, component, earned, max_, ts="2026-05-08T12:00:00Z"):
    return {
        "id": m.compute_grade_id(subject, component),
        "ts": ts,
        "subject": subject,
        "component": component,
        "earned": earned,
        "max": max_,
        "v": 1,
    }


def assign_set(subject, title, due, ts="2026-05-08T12:00:00Z"):
    return {
        "id": m.compute_assign_id(subject, title, due),
        "ts": ts,
        "kind": "set",
        "subject": subject,
        "title": title,
        "dueDate": due,
        "v": 1,
    }


def assign_done(subject, title, due, ts="2026-05-08T13:00:00Z"):
    return {
        "id": m.compute_assign_id(subject, title, due),
        "ts": ts,
        "kind": "done",
        "subject": subject,
        "title": title,
        "dueDate": due,
        "v": 1,
    }


class SummarizeTests(unittest.TestCase):
    def test_empty(self):
        self.assertEqual(m.summarize_by_subject([]), [])

    def test_latest_wins_per_component(self):
        rows = [
            grade("PA", "Test 1", 10.0, 35.0, ts="2026-05-01T00:00:00Z"),
            grade("PA", "Test 1", 14.5, 35.0, ts="2026-05-08T00:00:00Z"),
            grade("PA", "Attendance", 11.0, 30.0),
        ]
        out = m.summarize_by_subject(rows)
        pa = next(s for s in out if s["subject"] == "PA")
        self.assertAlmostEqual(pa["earned"], 25.5, places=4)
        self.assertAlmostEqual(pa["max"], 65.0, places=4)

    def test_sort_ascending_by_ratio(self):
        rows = [
            grade("POO", "Lab", 3.0, 42.0),
            grade("PS", "Sem", 42.0, 120.0),
            grade("ALO", "Lab", 197.5, 825.0),
        ]
        ordering = [s["subject"] for s in m.summarize_by_subject(rows)]
        self.assertEqual(ordering, ["POO", "ALO", "PS"])

    def test_zero_max_ratio_zero(self):
        rows = [grade("X", "c", 0.0, 0.0)]
        s = m.summarize_by_subject(rows)
        self.assertEqual(s[0]["ratio"], 0.0)


class WeakestTests(unittest.TestCase):
    def test_picks_lowest_ratio(self):
        rows = [
            grade("POO", "lab", 3.2, 42.0),
            grade("PS", "sem", 42.0, 120.0),
        ]
        w = m.pick_weakest(m.summarize_by_subject(rows))
        self.assertEqual(w["subject"], "POO")

    def test_none_on_empty(self):
        self.assertIsNone(m.pick_weakest([]))


class NextActionTests(unittest.TestCase):
    def test_picks_active_for_subject_sorted_by_due(self):
        today = "2026-05-09"
        rows = [
            assign_set("PS", "Tema A", "2026-05-21"),
            assign_set("PS", "Tema B", "2026-05-15"),
            assign_set("ALO", "Tema 1", None),
        ]
        a = m.pick_next_action(rows, today, "PS")
        self.assertEqual(a["title"], "Tema B")

    def test_skips_done(self):
        today = "2026-05-09"
        rows = [
            assign_set("PS", "Tema A", "2026-05-15"),
            assign_done("PS", "Tema A", "2026-05-15"),
            assign_set("PS", "Tema B", "2026-05-21"),
        ]
        a = m.pick_next_action(rows, today, "PS")
        self.assertEqual(a["title"], "Tema B")

    def test_overdue_first(self):
        today = "2026-05-09"
        rows = [
            assign_set("PS", "Tema A", "2026-05-21"),
            assign_set("PS", "Tema OLD", "2026-04-01"),
        ]
        a = m.pick_next_action(rows, today, "PS")
        self.assertEqual(a["title"], "Tema OLD")
        self.assertEqual(a["status"], "overdue")

    def test_no_due_date_active_after_dated(self):
        today = "2026-05-09"
        rows = [
            assign_set("ALO", "Tema 1", None),
            assign_set("ALO", "Tema 2", "2026-05-15"),
        ]
        a = m.pick_next_action(rows, today, "ALO")
        self.assertEqual(a["title"], "Tema 2")

    def test_returns_none_when_none_for_subject(self):
        rows = [assign_set("PS", "Tema A", "2026-05-21")]
        self.assertIsNone(m.pick_next_action(rows, "2026-05-09", "POO"))


class FormatTests(unittest.TestCase):
    def test_with_action(self):
        weakest = {"subject": "POO", "earned": 3.2, "max": 42.0, "ratio": 3.2 / 42.0}
        action = {
            "subject": "POO",
            "title": "Lab eval 2",
            "dueDate": "2026-05-15",
            "daysToDue": 6,
            "status": "active",
        }
        msg = m.format_message(weakest, action, today="2026-05-09")
        self.assertIn("POO", msg)
        self.assertIn("Lab eval 2", msg)
        self.assertIn("2026-05-15", msg)

    def test_overdue_marked(self):
        weakest = {"subject": "PS", "earned": 0.0, "max": 20.0, "ratio": 0.0}
        action = {
            "subject": "PS",
            "title": "Tema A",
            "dueDate": "2026-04-01",
            "daysToDue": -38,
            "status": "overdue",
        }
        msg = m.format_message(weakest, action, today="2026-05-09")
        self.assertIn("OVERDUE", msg.upper())

    def test_no_action_fallback(self):
        weakest = {"subject": "POO", "earned": 3.2, "max": 42.0, "ratio": 3.2 / 42.0}
        msg = m.format_message(weakest, None, today="2026-05-09")
        self.assertIn("POO", msg)
        # No assignment → still readable
        self.assertGreater(len(msg), 10)

    def test_empty_grades(self):
        msg = m.format_message(None, None, today="2026-05-09")
        self.assertIn("no", msg.lower())


if __name__ == "__main__":
    unittest.main(verbosity=2)
