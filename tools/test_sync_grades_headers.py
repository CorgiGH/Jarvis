#!/usr/bin/env python3
"""Tests for verify_headers / find_header_row in sync-grades-from-sheets.py.

Run from repo root:
    python3 tools/test_sync_grades_headers.py
"""

from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path


def _load_module():
    here = Path(__file__).resolve().parent
    spec = importlib.util.spec_from_file_location(
        "sync_grades", here / "sync-grades-from-sheets.py"
    )
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


m = _load_module()


# Real ALO header row layout (verified 2026-05-08):
ALO_HEADER_REAL = [
    "Grupa", "Nume", "Nr. matricol", "Email",
    "S1", "S2", "S3", "S4", "S5", "S6", "S7", "S9",
    "Activitate seminar", "Test seminar",
    "T1", "B1", "T2", "B2", "T3", "B3", "T4", "B4", "T5", "B5",
    "Total punctaj laborator",
]

# Two leading blank/sub-header rows present in the actual sheet:
ALO_LEADING = [
    [" "] * 25,
    [""] * 11 + ["Teme"] + [""] * 13,
]


class HeaderVerifyTests(unittest.TestCase):
    def test_passes_on_real_layout(self):
        rows = ALO_LEADING + [ALO_HEADER_REAL] + [["..." ] * 25]
        self.assertIsNone(m.verify_headers(rows, "ALO"))

    def test_passes_when_only_header_row(self):
        rows = [ALO_HEADER_REAL]
        self.assertIsNone(m.verify_headers(rows, "ALO"))

    def test_returns_none_for_unguarded_subject(self):
        # PA / PS / POO have no entry in EXPECTED_HEADERS yet
        rows = [["unrelated"], ["nothing"]]
        self.assertIsNone(m.verify_headers(rows, "PA"))
        self.assertIsNone(m.verify_headers(rows, "PS"))
        self.assertIsNone(m.verify_headers(rows, "POO"))

    def test_detects_renamed_t1(self):
        bad = list(ALO_HEADER_REAL)
        bad[14] = "Tema 1"  # renamed from T1
        err = m.verify_headers(ALO_LEADING + [bad], "ALO")
        self.assertIsNotNone(err)
        self.assertIn("ALO", err)
        self.assertIn("col 14", err)
        self.assertIn("'T1'", err)
        self.assertIn("'Tema 1'", err)

    def test_detects_inserted_column(self):
        # Author inserted a new column at position 14 (e.g. "Test laborator"),
        # shifting T1..B5 right by one.
        bad = list(ALO_HEADER_REAL)
        bad.insert(14, "Test laborator")
        err = m.verify_headers(ALO_LEADING + [bad], "ALO")
        self.assertIsNotNone(err)
        self.assertIn("col 14", err)

    def test_detects_truncated_row(self):
        # Header row stops short — extractor would IndexError otherwise.
        bad = ALO_HEADER_REAL[:13]  # cuts before col 13
        err = m.verify_headers(ALO_LEADING + [bad], "ALO")
        self.assertIsNotNone(err)

    def test_no_matricol_anywhere(self):
        # Sheet contents totally garbage — useful failure msg.
        rows = [["a", "b", "c"]]
        err = m.verify_headers(rows, "ALO")
        self.assertIsNotNone(err)
        self.assertIn("matricol", err)


class FindHeaderRowTests(unittest.TestCase):
    def test_skips_blank_leading_rows(self):
        rows = ALO_LEADING + [ALO_HEADER_REAL]
        h = m.find_header_row(rows, m.EXPECTED_HEADERS["ALO"])
        self.assertEqual(h[14], "T1")

    def test_returns_none_when_no_match(self):
        rows = [["foo"] * 30, ["bar"] * 30]
        h = m.find_header_row(rows, m.EXPECTED_HEADERS["ALO"])
        self.assertIsNone(h)


if __name__ == "__main__":
    unittest.main(verbosity=2)
