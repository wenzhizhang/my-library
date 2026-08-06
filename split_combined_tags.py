#!/usr/bin/env python3
"""
Split space-combined tags (e.g. "巾箱本 线装古籍") into separate tags.

Data migrated from the old Django system stored space-separated words as a
single tag element ("巾箱本 线装古籍", "限量编号 钤印本"), while Django
recognized them as two distinct tags. This script rewrites such elements into
individual tags:

    ["崇贤馆藏书", "巾箱本 线装古籍"]  ->  ["崇贤馆藏书", "巾箱本", "线装古籍"]

Each whitespace-delimited word becomes its own tag (stripped, empty words
dropped), and the resulting tag list is deduplicated preserving order.

Databases touched (auto-discovered unless paths are given):
  - every *.db under DATA_DIR (env, default /app/data) that has a `books`
    table with a `tags` column — covers demo.db and per-user <uuid>.db
  - the shared root DB at ROOT_DB_PATH (env, default <DATA_DIR>/root.db)

Usage:
  python3 split_combined_tags.py              # apply to all discovered DBs
  python3 split_combined_tags.py --dry-run    # print changes, write nothing
  python3 split_combined_tags.py path/to/a.db path/to/b.db   # explicit DBs

After running, if the RAG semantic index has been built, trigger a full
reindex (POST /api/rag/reindex) so embedded documents reflect the new tags.
"""

import argparse
import json
import os
import sqlite3
import sys
from typing import Optional

_SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))

DATA_DIR = os.environ.get("DATA_DIR", "/app/data")
ROOT_DB_PATH = os.environ.get("ROOT_DB_PATH", os.path.join(DATA_DIR, "root.db"))

# Fallback locations for running outside the container (repo checkout layout).
_FALLBACK_DIRS = [
    os.path.join(_SCRIPT_DIR, "backend", "data"),
    os.path.join(_SCRIPT_DIR, "data"),
]
_FALLBACK_ROOT_DBS = [
    os.path.join(_SCRIPT_DIR, "backend", "root-db", "root.db"),
]


# ── DB discovery ─────────────────────────────────────────────────────────────

def _has_books_tags(db_path: str) -> bool:
    """True if the DB has a books table carrying a tags column."""
    try:
        conn = sqlite3.connect(f"file:{db_path}?mode=ro", uri=True)
        try:
            rows = conn.execute(
                "SELECT name FROM pragma_table_info('books') WHERE name = 'tags'"
            ).fetchall()
            return bool(rows)
        finally:
            conn.close()
    except sqlite3.Error:
        return False


def discover_databases(explicit: list[str]) -> list[str]:
    """Collect book DB paths: explicit args first, then auto-discovery."""
    if explicit:
        return [os.path.abspath(p) for p in explicit]

    found = []
    candidates = [DATA_DIR] if os.path.isdir(DATA_DIR) else []
    candidates += [d for d in _FALLBACK_DIRS if os.path.isdir(d)]
    seen = set()
    for d in candidates:
        for name in sorted(os.listdir(d)):
            if not name.endswith(".db"):
                continue
            path = os.path.join(d, name)
            if path in seen:
                continue
            seen.add(path)
            found.append(path)

    # Shared root DB (usually a copy of demo books).
    root_candidates = [ROOT_DB_PATH] + _FALLBACK_ROOT_DBS
    for path in root_candidates:
        if path and os.path.isfile(path) and path not in seen:
            seen.add(path)
            found.append(path)

    return [p for p in found if _has_books_tags(p)]


# ── Tag splitting ────────────────────────────────────────────────────────────

def split_tag_list(tags: object) -> Optional[list[str]]:
    """Return the split/deduped tag list, or None if the input is unchanged.

    ``tags`` is the decoded JSON value of a book's tags column. Every element
    containing whitespace is broken into its words; the full list is stripped,
    empty words dropped, and duplicates removed preserving first occurrence.
    """
    if not isinstance(tags, list):
        return None

    out: list[str] = []
    for tag in tags:
        if not isinstance(tag, str):
            return None  # unexpected shape — leave the book untouched
        words = tag.split()
        if len(words) == 1:
            words = [tag.strip()] if tag != tag.strip() else [tag]
        for w in words:
            if w and w not in out:
                out.append(w)

    if out == tags:
        return None
    return out


# ── Per-DB processing ────────────────────────────────────────────────────────

def process_db(db_path: str, dry_run: bool) -> int:
    """Split combined tags in one DB. Returns the number of books changed."""
    conn = sqlite3.connect(db_path)
    try:
        rows = conn.execute(
            "SELECT id, tags FROM books WHERE tags IS NOT NULL AND tags != '[]'"
        ).fetchall()
    except sqlite3.Error as exc:
        print(f"  [SKIP] {os.path.basename(db_path)}: {exc}", file=sys.stderr)
        return 0

    changed = 0
    for book_id, raw in rows:
        try:
            tags = json.loads(raw)
        except (json.JSONDecodeError, TypeError):
            print(f"  [WARN] {os.path.basename(db_path)} book {book_id}: "
                  f"tags not valid JSON — skipped", file=sys.stderr)
            continue

        new_tags = split_tag_list(tags)
        if new_tags is None:
            continue

        changed += 1
        action = "would change" if dry_run else "changed"
        print(f"  [{action}] {os.path.basename(db_path)} book {book_id}: "
              f"{json.dumps(tags, ensure_ascii=False)} -> "
              f"{json.dumps(new_tags, ensure_ascii=False)}")
        if not dry_run:
            conn.execute(
                "UPDATE books SET tags = ? WHERE id = ?",
                (json.dumps(new_tags, ensure_ascii=False), book_id),
            )

    if not dry_run and changed:
        conn.commit()
    conn.close()
    return changed


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Split space-combined tags (e.g. '巾箱本 线装古籍') into "
                    "separate tags in book databases."
    )
    parser.add_argument(
        "--dry-run", action="store_true",
        help="print what would change without writing anything",
    )
    parser.add_argument(
        "dbs", nargs="*", metavar="DB_PATH",
        help="explicit database files; defaults to discovery from DATA_DIR / "
             "ROOT_DB_PATH",
    )
    args = parser.parse_args()

    dbs = discover_databases(args.dbs)
    if not dbs:
        print("No book databases found. Set DATA_DIR / ROOT_DB_PATH or pass "
              "paths explicitly.", file=sys.stderr)
        return 1

    print(f"{'Dry run — nothing will be written.' if args.dry_run else 'Splitting tags:'}")
    total = 0
    for db_path in dbs:
        print(f"Processing {db_path}")
        total += process_db(db_path, args.dry_run)

    print(f"\nTotal books {'to change' if args.dry_run else 'changed'}: {total}")
    if not args.dry_run:
        print("Note: if the RAG semantic index was built, run a full reindex "
              "(POST /api/rag/reindex) so embedded documents reflect the new "
              "tags.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
