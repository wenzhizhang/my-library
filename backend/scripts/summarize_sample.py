"""Dry-run DeepSeek summaries for a few books and print token usage.

This does not write to the database.  Use it to estimate cost before a full
reindex.  Only books the reindex would summarize are sampled: an empty summary
and an introduction of at least ``MIN_SUMMARY_INTRODUCTION_CHARS`` characters.

    python scripts/summarize_sample.py --limit 20
    python scripts/summarize_sample.py --limit 20 --db /app/data/<uuid>.db
"""

import argparse
import glob
import os
import sqlite3
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from rag.pipeline import MIN_SUMMARY_INTRODUCTION_CHARS  # noqa: E402
from rag.summarize import _request_summary  # noqa: E402

_SKIP_DBS = {"auth.db", "stats.db", "applications.db", "root.db"}


def _pick_db(data_dir: str) -> str:
    candidates = []
    for path in glob.glob(os.path.join(data_dir, "*.db")):
        if os.path.basename(path) in _SKIP_DBS:
            continue
        try:
            conn = sqlite3.connect(path)
            count = conn.execute("SELECT count(*) FROM books").fetchone()[0]
            conn.close()
        except sqlite3.Error:
            continue
        candidates.append((count, path))
    if not candidates:
        raise SystemExit(f"No book database found under {data_dir}")
    return max(candidates)[1]


def _authors(conn: sqlite3.Connection, book_id: int) -> list[str]:
    return [
        row[0]
        for row in conn.execute(
            """
            SELECT coalesce(nullif(a.name_cn, ''), a.name)
            FROM authors a
            JOIN book_authors ba ON ba.author_id = a.id
            WHERE ba.book_id = ?
            ORDER BY a.id
            """,
            (book_id,),
        )
    ]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--limit", type=int, default=20)
    parser.add_argument("--db", help="Path to the user's SQLite database")
    args = parser.parse_args()

    if not os.environ.get("DEEPSEEK_API_KEY", "").strip():
        raise SystemExit("DEEPSEEK_API_KEY is not set")

    db_path = args.db or _pick_db(os.environ.get("DATA_DIR", "/app/data"))
    conn = sqlite3.connect(db_path)
    rows = conn.execute(
        """
        SELECT id, coalesce(nullif(title_cn, ''), title, ''), introduction
        FROM books
        WHERE length(trim(coalesce(introduction, ''))) >= ?
          AND trim(coalesce(summary, '')) = ''
        ORDER BY id
        LIMIT ?
        """,
        (MIN_SUMMARY_INTRODUCTION_CHARS, args.limit),
    ).fetchall()

    print(f"DB: {db_path}")
    print(f"Books: {len(rows)}")
    total_prompt = total_completion = total_tokens = 0

    for index, (book_id, title, introduction) in enumerate(rows, 1):
        summary, usage = _request_summary(
            title, _authors(conn, book_id), introduction
        )
        prompt_tokens = usage.get("prompt_tokens", 0)
        completion_tokens = usage.get("completion_tokens", 0)
        tokens = usage.get("total_tokens", 0)
        total_prompt += prompt_tokens
        total_completion += completion_tokens
        total_tokens += tokens
        print(
            f"[{index}] id={book_id} chars={len(summary or '')} "
            f"prompt={prompt_tokens} completion={completion_tokens} "
            f"total={tokens}"
        )
        print(summary or "(empty)")
        print()

    print(
        f"TOTAL prompt={total_prompt} completion={total_completion} "
        f"total={total_tokens}"
    )


if __name__ == "__main__":
    main()
