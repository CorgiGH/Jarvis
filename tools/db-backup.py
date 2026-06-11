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
import hashlib
import json
import os
import sqlite3
import sys
import tempfile
from datetime import datetime, timezone
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
    p.add_argument(
        "--restore-drill", metavar="DUMP_SQL_GZ", default=None,
        help="verify an EXISTING dump against the live DB (restore to scratch, "
             "row-count + schema-hash equality), update its manifest, exit 0/1",
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
    return sqlite3.connect(db_path.as_uri() + "?mode=ro&immutable=1", uri=True)


MANIFEST_VERSION = 1


def schema_hash(conn: sqlite3.Connection) -> str:
    """sha256 hex over the sorted, stripped CREATE statements of all user objects.

    MUST stay byte-identical with MigrationBackupGate.liveSchemaHash (Kotlin):
    SELECT sql FROM sqlite_master WHERE sql IS NOT NULL AND name NOT LIKE
    'sqlite_%'; strip each; sort; join with '\\n'; sha256 hex digest.
    """
    rows = conn.execute(
        "SELECT sql FROM sqlite_master WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%'"
    ).fetchall()
    canon = "\n".join(sorted(r[0].strip() for r in rows))
    return hashlib.sha256(canon.encode("utf-8")).hexdigest()


def manifest_path_for(dump_path: Path) -> Path:
    return dump_path.with_name(dump_path.name + ".manifest.json")


def run_restore_drill(db_path: Path, dump_path: Path) -> tuple[bool, str]:
    """Restore the dump into a scratch FILE db; compare row-count + schema-hash vs live."""
    src = open_ro(db_path)
    try:
        live_cards = src.execute("SELECT COUNT(*) FROM fsrs_cards").fetchone()[0]
        live_schema = schema_hash(src)
    finally:
        src.close()

    try:
        with gzip.open(dump_path, "rt", encoding="utf-8") as gz:
            sql_text = gz.read()
        fd, scratch = tempfile.mkstemp(prefix="restore-drill-", suffix=".db")
        os.close(fd)
        restored = sqlite3.connect(scratch)
        restored.executescript(sql_text)
    except Exception as exc:
        return False, f"restore failed: {exc}"

    try:
        r_cards = restored.execute("SELECT COUNT(*) FROM fsrs_cards").fetchone()[0]
        r_schema = schema_hash(restored)
    except sqlite3.OperationalError as exc:
        restored.close()
        return False, f"restored DB unreadable: {exc}"
    restored.close()
    try:
        os.unlink(scratch)
    except OSError:
        pass

    if r_cards != live_cards:
        return False, f"row-count mismatch (live={live_cards}, restored={r_cards})"
    if r_schema != live_schema:
        return False, (
            f"schema-hash mismatch (live={live_schema[:12]}…, restored={r_schema[:12]}…)"
        )
    return True, f"cards {r_cards}=={live_cards}, schema_hash match"


def write_manifest(dump_path: Path, db_path: Path, cards: int,
                   s_hash: str, drill_status: str) -> None:
    now = datetime.now(timezone.utc)
    manifest = {
        "tool": "db-backup.py",
        "manifest_version": MANIFEST_VERSION,
        "created_date": datetime.now().strftime("%Y-%m-%d"),  # LOCAL date — gate compares LocalDate.now()
        "created_at": now.strftime("%Y-%m-%dT%H:%M:%SZ"),
        "db_path": str(db_path),
        "dump_file": dump_path.name,
        "fsrs_cards": cards,
        "schema_hash": s_hash,
        "integrity": drill_status,
        "restore_drill": drill_status,
    }
    manifest_path_for(dump_path).write_text(
        json.dumps(manifest, indent=2), encoding="utf-8"
    )


def cmd_restore_drill(db_path: Path, dump_path: Path) -> int:
    if not dump_path.exists():
        print(f"ERROR: dump not found at {dump_path}", file=sys.stderr)
        return 1
    ok, detail = run_restore_drill(db_path, dump_path)
    label = "PASS" if ok else "FAIL"
    print(f"[db-backup] restore drill: {label} ({detail})")
    mp = manifest_path_for(dump_path)
    if mp.exists():
        m = json.loads(mp.read_text(encoding="utf-8"))
        m["restore_drill"] = label
        m["drilled_at"] = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
        mp.write_text(json.dumps(m, indent=2), encoding="utf-8")
    return 0 if ok else 1


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
        out_path.unlink(missing_ok=True)  # never leave a partial dump (M-DB trusted-dump contract)
        return 1
    src.close()
    dump_size = out_path.stat().st_size

    # --- restore DRILL (spec §3.6 step 1): restore to a scratch FILE,
    #     row-count + schema-hash equality vs live; then write the manifest
    #     sidecar the migration runner's INV-3.1 gate reads. ---
    src2 = open_ro(db_path)
    src_schema = schema_hash(src2)
    src2.close()
    drill_ok, drill_detail = run_restore_drill(db_path, out_path)
    status = "PASS" if drill_ok else "FAIL"
    integrity_label = "OK" if drill_ok else f"FAIL ({drill_detail})"

    write_manifest(out_path, db_path, src_count, src_schema, status)

    print(f"[db-backup] integrity: {status}")
    print(f"[db-backup] restore drill: {status} ({drill_detail})")
    print(
        f"[db-backup] backed up {src_count} fsrs_cards"
        f" -> {out_path}"
        f" ({dump_size:,} bytes);"
        f" integrity: {integrity_label}"
    )
    return 0 if drill_ok else 1


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
    if args.restore_drill:
        return cmd_restore_drill(db_path, Path(args.restore_drill))
    return cmd_backup(db_path, Path(args.output_dir), floor)


if __name__ == "__main__":
    sys.exit(main())
