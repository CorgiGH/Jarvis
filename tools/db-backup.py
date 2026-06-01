#!/usr/bin/env python3
"""
tools/db-backup.py — off-box SQLite backup tool for jarvis tutor.db
Gap-ledger must-resolve #3: schema-deploy precondition.

Usage:
    python3 tools/db-backup.py [output_dir]

    output_dir defaults to ./backups (created if absent).
    DB path: $JARVIS_DB env var, or ~/.jarvis/tutor.db.

Output: backups/jarvis-tutor-db-YYYYMMDD-HHMMSS.sql.gz
Exit codes: 0 = success, 1 = any failure.
"""

import gzip
import os
import sqlite3
import sys
from datetime import datetime
from pathlib import Path


def main() -> int:
    # --- resolve paths ---
    db_path = Path(os.environ.get("JARVIS_DB", Path.home() / ".jarvis" / "tutor.db"))
    out_dir = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("./backups")

    if not db_path.exists():
        print(f"ERROR: DB not found at {db_path}", file=sys.stderr)
        return 1

    out_dir.mkdir(parents=True, exist_ok=True)

    # --- open source DB read-only ---
    db_uri = db_path.as_uri() + "?mode=ro"
    try:
        src = sqlite3.connect(db_uri, uri=True)
    except sqlite3.OperationalError as exc:
        print(f"ERROR: cannot open DB {db_path}: {exc}", file=sys.stderr)
        return 1

    try:
        src_count = src.execute("SELECT COUNT(*) FROM fsrs_cards").fetchone()[0]
    except sqlite3.OperationalError as exc:
        print(f"ERROR: cannot read fsrs_cards: {exc}", file=sys.stderr)
        src.close()
        return 1

    # --- dump to gzip ---
    ts = datetime.now().strftime("%Y%m%d-%H%M%S")
    out_path = out_dir / f"jarvis-tutor-db-{ts}.sql.gz"

    try:
        with gzip.open(out_path, "wt", encoding="utf-8") as gz:
            for line in src.iterdump():
                gz.write(line + "\n")
    except Exception as exc:
        print(f"ERROR: dump failed: {exc}", file=sys.stderr)
        src.close()
        return 1

    src.close()
    dump_size = out_path.stat().st_size

    # --- integrity check: restore to :memory: and compare card count ---
    integrity_ok = False
    try:
        with gzip.open(out_path, "rt", encoding="utf-8") as gz:
            sql_text = gz.read()

        mem = sqlite3.connect(":memory:")
        mem.executescript(sql_text)
        restored_count = mem.execute("SELECT COUNT(*) FROM fsrs_cards").fetchone()[0]
        mem.close()

        if restored_count == src_count:
            integrity_ok = True
            integrity_label = "OK"
        else:
            integrity_label = f"FAIL (source={src_count}, restored={restored_count})"
    except Exception as exc:
        integrity_label = f"FAIL ({exc})"

    # --- report ---
    status = "PASS" if integrity_ok else "FAIL"
    print(f"[db-backup] integrity: {status}")
    print(
        f"[db-backup] backed up {src_count} fsrs_cards"
        f" -> {out_path}"
        f" ({dump_size:,} bytes);"
        f" integrity: {integrity_label}"
    )

    return 0 if integrity_ok else 1


if __name__ == "__main__":
    sys.exit(main())
