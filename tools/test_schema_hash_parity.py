#!/usr/bin/env python3
"""Cross-language schema-hash PARITY (master-plan-v2 Phase-0, audit F-schema-parity).

Two independent impls compute a SHA-256 over the SQLite schema and MUST stay
byte-identical:
  - Python: schema_hash() in tools/db-backup.py:88-99 (the impl under test here)
  - Kotlin: MigrationBackupGate.liveSchemaHash
            (src/main/kotlin/jarvis/tutor/MigrationBackupGate.kt:93-101)

MigrationBackupGate.assertSafeToMutate REFUSES a backup when
manifest.schema_hash != live schema hash (MigrationBackupGate.kt:65-67). A SILENT
divergence between these two impls therefore DoSes the migration path: a manifest
written by THIS Python tool would never match the Kotlin-computed live hash, and
every protected migration would refuse forever.

This is a GOLDEN-VECTOR test: a tiny fixed canonical schema with a PINNED expected
hex. Editing either impl's canonicalisation (the SELECT, strip/trim, sort,
"\n"-join, or sha256-hex) reds this test. Its byte-identical twin is
src/test/kotlin/jarvis/tutor/SchemaHashParityTest.kt, which asserts the SAME
EXPECTED_HEX from Kotlin over the SAME DDL.

--- SHARED CANONICAL VECTOR (keep byte-identical with SchemaHashParityTest.kt) ---
  DDL (created verbatim; SQLite stores sqlite_master.sql exactly as written):
    CREATE TABLE alpha (id INTEGER PRIMARY KEY, name TEXT NOT NULL)
    CREATE TABLE beta (alpha_id INTEGER REFERENCES alpha(id), payload TEXT)
  Canonical string fed to sha256 (rows trimmed, sorted, joined with '\n'):
    "CREATE TABLE alpha (id INTEGER PRIMARY KEY, name TEXT NOT NULL)\n"
    "CREATE TABLE beta (alpha_id INTEGER REFERENCES alpha(id), payload TEXT)"
  EXPECTED_HEX = f5fb8caadbfb1315238437e81ebb6b745841f898349bdaabe89450ab59ec7860
----------------------------------------------------------------------------------

Convention: stdlib unittest, no external deps (matches tools/test_db_backup*.py).
db-backup.py is imported as a module (not run as a subprocess) so the REAL
schema_hash() is exercised directly.
"""

from __future__ import annotations

import importlib.util
import sqlite3
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent

# tools/db-backup.py has a hyphen -> not a valid module name; load it by path.
_SPEC = importlib.util.spec_from_file_location("db_backup", HERE / "db-backup.py")
assert _SPEC is not None and _SPEC.loader is not None
db_backup = importlib.util.module_from_spec(_SPEC)
_SPEC.loader.exec_module(db_backup)

# The fixed canonical schema. Single-spaced, no indentation -> stored verbatim.
CANONICAL_DDL = [
    "CREATE TABLE alpha (id INTEGER PRIMARY KEY, name TEXT NOT NULL)",
    "CREATE TABLE beta (alpha_id INTEGER REFERENCES alpha(id), payload TEXT)",
]

# Pinned sha256 hex of the canonical schema. MUST equal the Kotlin side's
# EXPECTED_HEX in src/test/kotlin/jarvis/tutor/SchemaHashParityTest.kt. Recompute
# (offline) ONLY if the canonical DDL changes, and update BOTH files in lockstep.
EXPECTED_HEX = "f5fb8caadbfb1315238437e81ebb6b745841f898349bdaabe89450ab59ec7860"


class SchemaHashParityTests(unittest.TestCase):
    def test_schema_hash_matches_pinned_golden_vector(self):
        """schema_hash() over the canonical schema == the pinned cross-language hex."""
        conn = sqlite3.connect(":memory:")
        try:
            # Build the EXACT canonical schema, inserted in NON-sorted order on
            # purpose (beta before alpha) so the test also exercises the sort step.
            for ddl in reversed(CANONICAL_DDL):
                conn.execute(ddl)
            conn.commit()

            actual = db_backup.schema_hash(conn)
        finally:
            conn.close()

        self.assertEqual(
            actual,
            EXPECTED_HEX,
            "Python schema_hash() diverged from the pinned golden vector. Either the "
            "Python canonicalisation changed, or it no longer matches Kotlin "
            "MigrationBackupGate.liveSchemaHash. Both impls MUST agree byte-for-byte "
            "(MigrationBackupGate.kt:65-67 refuses backups on mismatch). Reconcile "
            "tools/db-backup.py schema_hash() and MigrationBackupGate.liveSchemaHash.",
        )


if __name__ == "__main__":
    unittest.main(verbosity=2)
