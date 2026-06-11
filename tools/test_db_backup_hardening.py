#!/usr/bin/env python3
"""Tests for the HARDENED tools/db-backup.py.

Final-audit limitation #12: the tool had NO flag parsing — `--help` executed a
live backup into a directory literally named `--help`. These tests pin the fix
(argparse) and, from Task 2 on, the manifest sidecar + restore drill
(backup -> restore to scratch -> row-count + schema-hash equality vs live).

Convention: stdlib unittest, subprocess-driven, no external deps — matches
tools/test_db_backup.py, which MUST keep passing unchanged (its stdout/exit
contract is the regression net for the old behavior).
"""

from __future__ import annotations

import glob
import hashlib
import json
import os
import sqlite3
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
SCRIPT = HERE / "db-backup.py"


def make_db(db_path: Path, n_cards: int) -> None:
    conn = sqlite3.connect(db_path)
    try:
        conn.execute(
            "CREATE TABLE fsrs_cards (id INTEGER PRIMARY KEY, due TEXT, stability REAL)"
        )
        conn.executemany(
            "INSERT INTO fsrs_cards (id, due, stability) VALUES (?, ?, ?)",
            [(i, "2026-06-11", float(i)) for i in range(n_cards)],
        )
        conn.commit()
    finally:
        conn.close()


def run_tool(args: list[str], db: Path | None, cwd: Path, min_cards: str | None = "10"):
    env = dict(os.environ)
    if db is not None:
        env["JARVIS_DB"] = str(db)
    else:
        env.pop("JARVIS_DB", None)
    if min_cards is not None:
        env["MIN_EXPECTED_CARDS"] = min_cards
    else:
        env.pop("MIN_EXPECTED_CARDS", None)
    return subprocess.run(
        [sys.executable, str(SCRIPT), *args],
        env=env, capture_output=True, text=True, cwd=cwd,
    )


class FlagSafetyTests(unittest.TestCase):
    def test_help_exits_0_and_never_backs_up(self):
        """--help prints usage, exits 0, creates NO '--help' dir and NO dump."""
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            db = tmp / "live.db"
            make_db(db, 50)

            proc = run_tool(["--help"], db, cwd=tmp)

            self.assertEqual(proc.returncode, 0, proc.stderr)
            self.assertIn("usage", proc.stdout.lower())
            self.assertFalse(
                (tmp / "--help").exists(),
                "a directory named '--help' was created — flag parsing is broken "
                "(final-audit limitation #12)",
            )
            self.assertEqual(
                glob.glob(str(tmp / "**" / "*.sql.gz"), recursive=True), [],
                "--help executed a live backup",
            )

    def test_unknown_flag_exits_2_without_backing_up(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            db = tmp / "live.db"
            make_db(db, 50)

            proc = run_tool(["--bogus"], db, cwd=tmp)

            self.assertEqual(proc.returncode, 2, proc.stdout + proc.stderr)
            self.assertEqual(glob.glob(str(tmp / "**" / "*.sql.gz"), recursive=True), [])

    def test_db_flag_overrides_env(self):
        """--db points at the real source even when JARVIS_DB points elsewhere."""
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            real = tmp / "real.db"
            make_db(real, 50)
            decoy = tmp / "decoy.db"
            make_db(decoy, 5)  # below floor 10 — would refuse if (wrongly) used
            out = tmp / "backups"

            proc = run_tool(["--db", str(real), str(out)], decoy, cwd=tmp)

            self.assertEqual(proc.returncode, 0, proc.stderr)
            self.assertIn("backed up 50 fsrs_cards", proc.stdout)
            self.assertEqual(len(glob.glob(str(out / "*.sql.gz"))), 1)

    def test_positional_output_dir_still_works(self):
        """The legacy invocation `db-backup.py <outdir>` is unchanged."""
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            db = tmp / "live.db"
            make_db(db, 50)
            out = tmp / "backups"

            proc = run_tool([str(out)], db, cwd=tmp)

            self.assertEqual(proc.returncode, 0, proc.stderr)
            self.assertEqual(len(glob.glob(str(out / "*.sql.gz"))), 1)


if __name__ == "__main__":
    unittest.main(verbosity=2)
