#!/usr/bin/env python3
"""
tools/db-backup.py — off-box SQLite backup tool for jarvis tutor.db
Gap-ledger must-resolve #3: schema-deploy precondition. Plan-1 hardening:
argparse flag parsing (final-audit limitation #12: `--help` used to execute a
live backup into a directory named `--help`).

Usage:
    python3 tools/db-backup.py [output_dir] [--db PATH] [--min-cards N]

    output_dir defaults to ./backups (created if absent).
    DB path: --db flag, else $JARVIS_DB env var, else ~/.jarvis/tutor.db.
    Floor: --min-cards, else $MIN_EXPECTED_CARDS, else 800.

Output: backups/jarvis-tutor-db-YYYYMMDD-HHMMSS.sql.gz
Exit codes: 0 = success, 1 = any failure, 2 = bad CLI usage (argparse).
"""

import argparse
import gzip
import os
import sqlite3
import sys
from datetime import datetime
from pathlib import Path

# Fail-closed schema-ALTER precondition (M-DB). The backup MUST refuse if the
# source corpus looks truncated, so the first ALTER can never run over a
# culled/wrong-path DB. Default 800; the live corpus is 828 (verified
# 2026-06-11). Overridable via --min-cards or the MIN_EXPECTED_CARDS env var.
DEFAULT_MIN_EXPECTED_CARDS = 800


def parse_args(argv=None) -> argparse.Namespace:
    p = argparse.ArgumentParser(
        prog="db-backup.py",
        description=(
            "Off-box SQLite backup for the jarvis tutor DB. Refuses (exit 1) when "
            "fsrs_cards < MIN_EXPECTED_CARDS — the fail-closed M-DB floor that "
            "protects every schema ALTER."
        ),
    )
    p.add_argument(
        "output_dir", nargs="?", default="./backups",
        help="directory the .sql.gz dump lands in (default: ./backups)",
    )
    p.add_argument(
        "--db", default=None,
        help="source DB path (default: $JARVIS_DB, else ~/.jarvis/tutor.db)",
    )
    p.add_argument(
        "--min-cards", type=int, default=None,
        help="override the fail-closed floor (default: $MIN_EXPECTED_CARDS, else 800)",
    )
    return p.parse_args(argv)


def resolve_floor(args: argparse.Namespace) -> int | None:
    """--min-cards > $MIN_EXPECTED_CARDS > default. None means: error already printed."""
    if args.min_cards is not None:
        return args.min_cards
    try:
        return int(os.environ.get("MIN_EXPECTED_CARDS", DEFAULT_MIN_EXPECTED_CARDS))
    except ValueError:
        print(
            f"ERROR: MIN_EXPECTED_CARDS env var is not an integer: "
            f"{os.environ.get('MIN_EXPECTED_CARDS')!r}",
            file=sys.stderr,
        )
        return None


def open_ro(db_path: Path) -> sqlite3.Connection:
    return sqlite3.connect(db_path.as_uri() + "?mode=ro", uri=True)


def cmd_backup(db_path: Path, out_dir: Path, min_expected_cards: int) -> int:
    try:
        src = open_ro(db_path)
    except sqlite3.OperationalError as exc:
        print(f"ERROR: cannot open DB {db_path}: {exc}", file=sys.stderr)
        return 1

    try:
        src_count = src.execute("SELECT COUNT(*) FROM fsrs_cards").fetchone()[0]
    except sqlite3.OperationalError as exc:
        print(f"ERROR: cannot read fsrs_cards: {exc}", file=sys.stderr)
        src.close()
        return 1

    # --- fail-closed floor: refuse BEFORE writing any dump (M-DB) ---
    if src_count < min_expected_cards:
        print(
            f"ERROR: fsrs_cards={src_count} < MIN_EXPECTED_CARDS={min_expected_cards}"
            f" — refusing backup (corpus looks truncated; do NOT migrate)",
            file=sys.stderr,
        )
        src.close()
        return 1

    out_dir.mkdir(parents=True, exist_ok=True)

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
    # (Task 2 replaces this with the full restore drill to a scratch FILE
    #  + schema-hash equality + the manifest sidecar.)
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

    status = "PASS" if integrity_ok else "FAIL"
    print(f"[db-backup] integrity: {status}")
    print(
        f"[db-backup] backed up {src_count} fsrs_cards"
        f" -> {out_path}"
        f" ({dump_size:,} bytes);"
        f" integrity: {integrity_label}"
    )
    return 0 if integrity_ok else 1


def main(argv=None) -> int:
    args = parse_args(argv)
    floor = resolve_floor(args)
    if floor is None:
        return 1
    db_path = Path(args.db) if args.db else Path(
        os.environ.get("JARVIS_DB", Path.home() / ".jarvis" / "tutor.db")
    )
    if not db_path.exists():
        print(f"ERROR: DB not found at {db_path}", file=sys.stderr)
        return 1
    return cmd_backup(db_path, Path(args.output_dir), floor)


if __name__ == "__main__":
    sys.exit(main())
