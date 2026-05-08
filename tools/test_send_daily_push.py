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


class PriorityActionTests(unittest.TestCase):
    """v2 selector — council 1778256562 fix. Picks across ALL subjects;
    no-restanță beats faster ratio; ratio is only a final tiebreaker."""

    def _summaries(self, **ratios):
        # Convert {"POO": 0.02, "PS": 0.35} → list of summary dicts.
        return [
            {"subject": s, "ratio": r, "earned": 0, "max": 100, "components": 1}
            for s, r in ratios.items()
        ]

    def test_no_restanta_beats_weakest(self):
        # The bug Devil's Advocate found: POO weakest 2%, but PS has a
        # no-restanță deadline closer than POO's lab eval. PS must win.
        rows = [
            assign_set("POO", "Lab eval 2", "2026-05-25"),
            assign_set("PS", "Tema A", "2026-05-21"),
        ]
        summaries = self._summaries(POO=0.02, PS=0.35)
        a = m.pick_priority_action(rows, summaries, today="2026-05-09")
        self.assertEqual(a["subject"], "PS")
        self.assertEqual(a["title"], "Tema A")
        self.assertTrue(a["no_restanta"])

    def test_overdue_no_restanta_first(self):
        rows = [
            assign_set("POO", "Lab eval 2", "2026-05-25"),
            assign_set("PS", "Tema A", "2026-04-30"),  # already overdue
        ]
        summaries = self._summaries(POO=0.02, PS=0.35)
        a = m.pick_priority_action(rows, summaries, today="2026-05-09")
        self.assertEqual(a["subject"], "PS")
        self.assertLess(a["daysToDue"], 0)

    def test_no_restanta_loses_when_far_future(self):
        # If PS Tema dueDate is after a closer non-PS deadline, the closer
        # one wins ONLY if it is also a no-restanță subject. Otherwise
        # no-restanță wins regardless of date.
        rows = [
            assign_set("POO", "Lab eval 2", "2026-05-12"),  # 3d away
            assign_set("PS", "Tema A", "2026-05-21"),       # 12d away
        ]
        summaries = self._summaries(POO=0.02, PS=0.35)
        a = m.pick_priority_action(rows, summaries, today="2026-05-09")
        # Both have due dates; PS is no-restanță → key (0,0,12,0.35)
        # POO has restanța → key (0,1,3,0.02). Tuple sort: PS wins.
        self.assertEqual(a["subject"], "PS")

    def test_ratio_breaks_tie_among_same_due(self):
        rows = [
            assign_set("POO", "Lab", "2026-05-15"),
            assign_set("ALO", "Tema", "2026-05-15"),  # same due, no restanță neither
        ]
        summaries = self._summaries(POO=0.02, ALO=0.23)
        a = m.pick_priority_action(rows, summaries, today="2026-05-09")
        # Same due-date, neither no-restanță → ratio breaks tie, weakest first
        self.assertEqual(a["subject"], "POO")

    def test_no_due_date_ranks_last(self):
        rows = [
            assign_set("ALO", "Tema 1", None),
            assign_set("POO", "Lab", "2026-05-25"),
        ]
        summaries = self._summaries(POO=0.02, ALO=0.23)
        a = m.pick_priority_action(rows, summaries, today="2026-05-09")
        self.assertEqual(a["subject"], "POO")

    def test_skips_done(self):
        rows = [
            assign_set("PS", "Tema A", "2026-05-21"),
            assign_done("PS", "Tema A", "2026-05-21"),
            assign_set("PS", "Tema B", "2026-05-21"),
        ]
        summaries = self._summaries(PS=0.35)
        a = m.pick_priority_action(rows, summaries, today="2026-05-09")
        self.assertEqual(a["title"], "Tema B")

    def test_returns_none_when_empty(self):
        self.assertIsNone(m.pick_priority_action([], [], today="2026-05-09"))


class FormatV2Tests(unittest.TestCase):
    def test_includes_day_stamp(self):
        summaries = [{"subject": "POO", "ratio": 0.02, "earned": 1.6, "max": 100, "components": 4}]
        action = {
            "subject": "PS",
            "title": "Tema A",
            "dueDate": "2026-05-21",
            "daysToDue": 12,
            "status": "active",
            "no_restanta": True,
            "ratio": 0.35,
        }
        msg = m.format_message(summaries, action, today="2026-05-09", day_n=1)
        self.assertIn("Day 1", msg)
        self.assertIn("PS", msg)
        self.assertIn("Tema A", msg)
        self.assertIn("NO restanță", msg)

    def test_overdue_marked(self):
        summaries = [{"subject": "PS", "ratio": 0.0, "earned": 0, "max": 20, "components": 1}]
        action = {
            "subject": "PS", "title": "Tema A", "dueDate": "2026-04-01",
            "daysToDue": -38, "status": "overdue",
            "no_restanta": True, "ratio": 0.0,
        }
        msg = m.format_message(summaries, action, today="2026-05-09", day_n=2)
        self.assertIn("OVERDUE", msg.upper())

    def test_no_action_fallback(self):
        summaries = [{"subject": "POO", "ratio": 0.02, "earned": 1.6, "max": 100, "components": 4}]
        msg = m.format_message(summaries, None, today="2026-05-09", day_n=1)
        self.assertIn("Day 1", msg)
        self.assertIn("Standing", msg)

    def test_empty_grades(self):
        msg = m.format_message([], None, today="2026-05-09", day_n=1)
        self.assertIn("Day 1", msg)


class ComputeDayNTests(unittest.TestCase):
    def test_anchor_day(self):
        self.assertEqual(m.compute_day_n("2026-05-09"), 1)
        self.assertEqual(m.compute_day_n("2026-05-10"), 2)
        self.assertEqual(m.compute_day_n("2026-05-21"), 13)


# Keep test_no_action_fallback signature stable across the v1 → v2 rename
# (this test was on the old format_message signature). Drop the old form
# since the api changed; the new FormatV2Tests cover the same surface.


if __name__ == "__main__":
    unittest.main(verbosity=2)
