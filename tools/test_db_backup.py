#!/usr/bin/env python3
"""Tests for tools/db-backup.py — the MIN_EXPECTED_CARDS fail-closed floor.

Proves the schema-ALTER precondition (M-DB): db-backup.py MUST refuse
(exit != 0, no trusted dump) when the source fsrs_cards count is below the
floor, and MUST succeed above it. A guard that has never been seen to abort
is indistinguishable from no guard, so the load-bearing case is the 799-row
ABORT.

Convention: stdlib unittest, no external deps (matches tools/test_*.py).
The script is driven as a subprocess so each case gets a clean
JARVIS_DB / MIN_EXPECTED_CARDS env + argv, exactly as CI / an operator would
invoke it.
"""

from __future__ import annotations

import glob
import os
import sqlite3
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
SCRIPT = HERE / "db-backup.py"


def _make_fixture_db(db_path: Path, n_cards: int) -> None:
    """Create a SQLite DB with an fsrs_cards table holding exactly n_cards rows."""
    conn = sqlite3.connect(db_path)
    try:
        conn.execute(
            "CREATE TABLE fsrs_cards (id INTEGER PRIMARY KEY, due TEXT, stability REAL)"
        )
        conn.executemany(
            "INSERT INTO fsrs_cards (id, due, stability) VALUES (?, ?, ?)",
            [(i, f"2026-06-{(i % 28) + 1:02d}", float(i)) for i in range(n_cards)],
        )
        conn.commit()
    finally:
        conn.close()


def _run_backup(db_path: Path, out_dir: Path, min_cards: str | None = None):
    """Invoke db-backup.py as a subprocess; return CompletedProcess."""
    env = dict(os.environ)
    env["JARVIS_DB"] = str(db_path)
    if min_cards is not None:
        env["MIN_EXPECTED_CARDS"] = min_cards
    else:
        env.pop("MIN_EXPECTED_CARDS", None)
    return subprocess.run(
        [sys.executable, str(SCRIPT), str(out_dir)],
        env=env,
        capture_output=True,
        text=True,
    )


class MinExpectedCardsFloorTests(unittest.TestCase):
    def test_passes_above_floor_801(self):
        """801 rows (>= default 800) -> exit 0 and a .sql.gz is produced."""
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            db = tmp / "above.db"
            out = tmp / "backups"
            _make_fixture_db(db, 801)

            proc = _run_backup(db, out)

            self.assertEqual(
                proc.returncode, 0,
                f"expected exit 0 at 801 cards; stderr={proc.stderr!r} stdout={proc.stdout!r}",
            )
            dumps = glob.glob(str(out / "*.sql.gz"))
            self.assertEqual(
                len(dumps), 1,
                f"expected exactly one .sql.gz at 801 cards, found {dumps}",
            )
            # restore-integrity path: 801 in -> 801 out
            self.assertIn("integrity: PASS", proc.stdout)
            self.assertIn("backed up 801 fsrs_cards", proc.stdout)

    def test_aborts_below_floor_799(self):
        """799 rows (< default 800) -> exit != 0, NO trusted dump, MIN_EXPECTED_CARDS in stderr.

        This is the load-bearing fail-closed case (M-DB): a truncated corpus
        must NOT produce a green backup right before a schema ALTER.
        """
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            db = tmp / "below.db"
            out = tmp / "backups"
            _make_fixture_db(db, 799)

            proc = _run_backup(db, out)

            self.assertNotEqual(
                proc.returncode, 0,
                f"expected NON-ZERO exit at 799 cards (fail-closed); "
                f"stdout={proc.stdout!r} stderr={proc.stderr!r}",
            )
            self.assertIn(
                "MIN_EXPECTED_CARDS", proc.stderr,
                f"expected MIN_EXPECTED_CARDS in stderr; stderr={proc.stderr!r}",
            )
            # No trusted dump may exist: the guard must refuse BEFORE writing.
            dumps = glob.glob(str(out / "*.sql.gz"))
            self.assertEqual(
                dumps, [],
                f"a backup file was produced below the floor (must NOT be): {dumps}",
            )

    def test_boundary_800_inclusive_passes(self):
        """Floor is INCLUSIVE: exactly 800 (== MIN_EXPECTED_CARDS) passes (src_count < 800 aborts)."""
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            db = tmp / "boundary.db"
            out = tmp / "backups"
            _make_fixture_db(db, 800)

            proc = _run_backup(db, out)

            self.assertEqual(
                proc.returncode, 0,
                f"expected exit 0 at exactly 800 cards (inclusive floor); "
                f"stderr={proc.stderr!r} stdout={proc.stdout!r}",
            )
            self.assertEqual(len(glob.glob(str(out / "*.sql.gz"))), 1)

    def test_env_override_lowers_floor(self):
        """MIN_EXPECTED_CARDS env override is honored: 50 rows passes when floor=10."""
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            db = tmp / "override.db"
            out = tmp / "backups"
            _make_fixture_db(db, 50)

            proc = _run_backup(db, out, min_cards="10")

            self.assertEqual(
                proc.returncode, 0,
                f"expected exit 0 with floor=10 at 50 cards; stderr={proc.stderr!r}",
            )
            self.assertEqual(len(glob.glob(str(out / "*.sql.gz"))), 1)

    def test_env_override_raises_floor_aborts(self):
        """MIN_EXPECTED_CARDS env override is honored: 50 rows aborts when floor=100."""
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            db = tmp / "override_high.db"
            out = tmp / "backups"
            _make_fixture_db(db, 50)

            proc = _run_backup(db, out, min_cards="100")

            self.assertNotEqual(proc.returncode, 0)
            self.assertIn("MIN_EXPECTED_CARDS", proc.stderr)
            self.assertEqual(glob.glob(str(out / "*.sql.gz")), [])


if __name__ == "__main__":
    unittest.main(verbosity=2)
