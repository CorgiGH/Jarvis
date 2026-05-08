#!/usr/bin/env python3
"""Tests for tools/adherence.py classifier + per-subject minute attribution."""

from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path


def _load_module():
    here = Path(__file__).resolve().parent
    spec = importlib.util.spec_from_file_location("adherence", here / "adherence.py")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


m = _load_module()


def entry(ts, process="code.exe", title="main.kt"):
    return {"ts": ts, "process": process, "title": title}


class ClassifyTests(unittest.TestCase):
    def test_ps_via_rstudio(self):
        self.assertEqual("PS",
            m.classify_activity({"process": "rstudio.exe", "title": "tema_a.R"}))

    def test_ps_via_title_keyword(self):
        self.assertEqual("PS",
            m.classify_activity({"process": "code.exe", "title": "Probabilități curs"}))

    def test_pa_via_keyword(self):
        self.assertEqual("PA",
            m.classify_activity({"process": "code.exe", "title": "Algorithm Design HW"}))

    def test_alo_via_keyword(self):
        self.assertEqual("ALO",
            m.classify_activity({"process": "chrome.exe", "title": "spațiu liniar wiki"}))

    def test_so_via_wsl(self):
        self.assertEqual("SO",
            m.classify_activity({"process": "wsl.exe", "title": "ubuntu"}))

    def test_rc_via_socket(self):
        self.assertEqual("RC",
            m.classify_activity({"process": "code.exe", "title": "tcp socket lab"}))

    def test_poo_via_intellij(self):
        self.assertEqual("POO",
            m.classify_activity({"process": "idea64.exe", "title": "intellij idea"}))

    def test_other_when_no_match(self):
        self.assertEqual("OTHER",
            m.classify_activity({"process": "notepad.exe", "title": "shopping list"}))

    def test_non_study_process_overrides(self):
        # Discord title that mentions PA still classified OTHER
        self.assertEqual("OTHER",
            m.classify_activity({"process": "discord.exe", "title": "PA help channel"}))


class MinutesTests(unittest.TestCase):
    def test_empty(self):
        self.assertEqual({}, m.per_subject_minutes([], "2026-05-09"))

    def test_filters_to_target_date(self):
        # 3 entries: 2 on target date (PS), 1 on different date
        rows = [
            entry("2026-05-08T22:00:00Z", "rstudio.exe", "tema_a.R"),
            entry("2026-05-09T10:00:00Z", "rstudio.exe", "tema_a.R"),
            entry("2026-05-09T10:05:00Z", "rstudio.exe", "tema_a.R"),
        ]
        out = m.per_subject_minutes(rows, "2026-05-09")
        # 5min gap between two PS entries + tail 5min default = ~10
        self.assertEqual(["PS"], list(out.keys()))
        self.assertGreaterEqual(out["PS"], 5)
        self.assertLessEqual(out["PS"], 11)

    def test_caps_long_gaps(self):
        # Big gap (4h) between samples → second sample only counts as
        # one ~5min sample, not the whole gap.
        rows = [
            entry("2026-05-09T08:00:00Z", "rstudio.exe", "tema_a.R"),
            entry("2026-05-09T12:00:00Z", "rstudio.exe", "tema_a.R"),
        ]
        out = m.per_subject_minutes(rows, "2026-05-09")
        self.assertLessEqual(out.get("PS", 0), 11)


class AdherenceTests(unittest.TestCase):
    def test_no_push(self):
        r = m.compute_adherence([], [], "2026-05-09")
        self.assertEqual(0.0, r["ratio"])
        self.assertIsNone(r["recommended"])
        self.assertIn("No push", r["summary"])

    def test_push_no_study(self):
        history = [{"ts": "2026-05-09T09:00:00Z", "today": "2026-05-09", "subject": "PS"}]
        r = m.compute_adherence(history, [], "2026-05-09")
        self.assertEqual("PS", r["recommended"])
        self.assertEqual(0, r["total_study_minutes"])
        self.assertIn("no study activity", r["summary"])

    def test_full_adherence(self):
        history = [{"ts": "2026-05-09T09:00:00Z", "today": "2026-05-09", "subject": "PS"}]
        # 4 PS samples at 5min gaps = ~20min PS study
        activity = [
            entry("2026-05-09T10:00:00Z", "rstudio.exe", "tema_a.R"),
            entry("2026-05-09T10:05:00Z", "rstudio.exe", "tema_a.R"),
            entry("2026-05-09T10:10:00Z", "rstudio.exe", "tema_a.R"),
            entry("2026-05-09T10:15:00Z", "rstudio.exe", "tema_a.R"),
        ]
        r = m.compute_adherence(history, activity, "2026-05-09")
        self.assertEqual("PS", r["recommended"])
        self.assertEqual(1.0, r["ratio"])
        self.assertGreater(r["total_study_minutes"], 0)

    def test_zero_adherence_pushed_other(self):
        history = [{"ts": "2026-05-09T09:00:00Z", "today": "2026-05-09", "subject": "PS"}]
        # User worked POO instead — 0% PS adherence.
        activity = [
            entry("2026-05-09T10:00:00Z", "idea64.exe", "POO main.java"),
            entry("2026-05-09T10:05:00Z", "idea64.exe", "POO Inheritance.java"),
        ]
        r = m.compute_adherence(history, activity, "2026-05-09")
        self.assertEqual("PS", r["recommended"])
        self.assertEqual(0.0, r["ratio"])
        self.assertGreater(r["total_study_minutes"], 0)
        # Check observed contains POO
        self.assertIn("POO", r["observed"])

    def test_partial_adherence(self):
        history = [{"ts": "2026-05-09T09:00:00Z", "today": "2026-05-09", "subject": "PA"}]
        # 50% PA, 50% other study
        activity = [
            entry("2026-05-09T10:00:00Z", "code.exe", "Algorithm Design HW"),
            entry("2026-05-09T10:05:00Z", "code.exe", "Algorithm Design HW"),
            entry("2026-05-09T10:10:00Z", "rstudio.exe", "tema_a.R"),
            entry("2026-05-09T10:15:00Z", "rstudio.exe", "tema_a.R"),
        ]
        r = m.compute_adherence(history, activity, "2026-05-09")
        self.assertGreater(r["ratio"], 0.0)
        self.assertLess(r["ratio"], 1.0)


class LatestPushTests(unittest.TestCase):
    def test_picks_latest_for_date(self):
        history = [
            {"ts": "2026-05-09T09:00:00Z", "today": "2026-05-09", "subject": "PS"},
            {"ts": "2026-05-09T15:00:00Z", "today": "2026-05-09", "subject": "PA"},
            {"ts": "2026-05-08T09:00:00Z", "today": "2026-05-08", "subject": "POO"},
        ]
        latest = m.latest_push_for_date(history, "2026-05-09")
        self.assertEqual("PA", latest["subject"])

    def test_none_when_no_match(self):
        history = [{"ts": "2026-05-08T09:00:00Z", "today": "2026-05-08", "subject": "PS"}]
        self.assertIsNone(m.latest_push_for_date(history, "2026-05-09"))


if __name__ == "__main__":
    unittest.main(verbosity=2)
