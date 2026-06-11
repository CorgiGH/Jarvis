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


class ManifestAndDrillTests(unittest.TestCase):
    def _backup(self, tmp: Path, n: int = 50):
        db = tmp / "live.db"
        make_db(db, n)
        out = tmp / "backups"
        proc = run_tool([str(out)], db, cwd=tmp)
        self.assertEqual(proc.returncode, 0, proc.stderr)
        dumps = glob.glob(str(out / "*.sql.gz"))
        self.assertEqual(len(dumps), 1)
        return db, Path(dumps[0])

    def test_manifest_sidecar_written_with_schema_hash(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            db, dump = self._backup(tmp)
            mp = Path(str(dump) + ".manifest.json")
            self.assertTrue(mp.exists(), "manifest sidecar missing")
            m = json.loads(mp.read_text(encoding="utf-8"))
            self.assertEqual(m["fsrs_cards"], 50)
            self.assertEqual(m["integrity"], "PASS")
            self.assertEqual(m["restore_drill"], "PASS")
            self.assertEqual(m["dump_file"], dump.name)
            self.assertIn("created_date", m)
            # schema_hash matches an independent recompute over the live DB —
            # the EXACT algorithm MigrationBackupGate.liveSchemaHash (Kotlin) mirrors.
            conn = sqlite3.connect(db)
            rows = conn.execute(
                "SELECT sql FROM sqlite_master WHERE sql IS NOT NULL "
                "AND name NOT LIKE 'sqlite_%'"
            ).fetchall()
            conn.close()
            expect = hashlib.sha256(
                "\n".join(sorted(r[0].strip() for r in rows)).encode("utf-8")
            ).hexdigest()
            self.assertEqual(m["schema_hash"], expect)

    def test_restore_drill_runs_inside_every_backup(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            db = tmp / "live.db"
            make_db(db, 50)
            proc = run_tool([str(tmp / "backups")], db, cwd=tmp)
            self.assertEqual(proc.returncode, 0)
            self.assertIn("restore drill: PASS", proc.stdout)

    def test_restore_drill_flag_passes_on_fresh_dump(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            db, dump = self._backup(tmp)
            proc = run_tool(["--restore-drill", str(dump)], db, cwd=tmp)
            self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
            self.assertIn("restore drill: PASS", proc.stdout)

    def test_restore_drill_fails_on_row_count_drift(self):
        """Live gains a row AFTER the dump -> the dump no longer covers the data."""
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            db, dump = self._backup(tmp)
            conn = sqlite3.connect(db)
            conn.execute("INSERT INTO fsrs_cards (id, due, stability) VALUES (999, 'x', 0.0)")
            conn.commit()
            conn.close()

            proc = run_tool(["--restore-drill", str(dump)], db, cwd=tmp)

            self.assertEqual(proc.returncode, 1)
            self.assertIn("restore drill: FAIL", proc.stdout)
            m = json.loads(Path(str(dump) + ".manifest.json").read_text(encoding="utf-8"))
            self.assertEqual(m["restore_drill"], "FAIL", "manifest must be downgraded")

    def test_restore_drill_fails_on_schema_drift(self):
        """Live schema changes AFTER the dump -> schema-hash mismatch."""
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            db, dump = self._backup(tmp)
            conn = sqlite3.connect(db)
            conn.execute("ALTER TABLE fsrs_cards ADD COLUMN kc_id TEXT")
            conn.commit()
            conn.close()

            proc = run_tool(["--restore-drill", str(dump)], db, cwd=tmp)

            self.assertEqual(proc.returncode, 1)
            self.assertIn("schema-hash mismatch", proc.stdout)


if __name__ == "__main__":
    unittest.main(verbosity=2)
