"""Evaluate RAG retrieval against a hand-labelled golden set.

Usage:
    python scripts/eval_rag.py --db /app/data/<uuid>.db
    python scripts/eval_rag.py --db /app/data/<uuid>.db --alpha 0.5 --top-k 10
    python scripts/eval_rag.py --db /app/data/<uuid>.db --show-results

The golden file is JSONL, one query per line:

    {"query": "甘道夫出自哪本书？", "relevant": [{"title": "霍比特人"}, {"title": "魔戒"}]}

``title`` is matched after edition normalisation, so "魔戒" matches
"魔戒60周年套装" but not "魔戒 第二部：双塔奇兵".  ``author`` is optional and
matched as a substring of the result's author names.
"""

import argparse
import glob
import json
import math
import os
import re
import sqlite3
import sys
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from sqlalchemy import create_engine, event  # noqa: E402
from sqlalchemy.orm import Session  # noqa: E402

from database import _load_vec_extension  # noqa: E402
from routers.rag import _work_key, rag_search  # noqa: E402
from schemas.rag import RagSearchRequest  # noqa: E402

_SKIP_DBS = {"auth.db", "stats.db", "applications.db", "root.db"}


def _norm(text: str) -> str:
    return re.sub(r"[\W_]+", "", (text or "").lower(), flags=re.UNICODE)


def _matches(result, expected: dict) -> bool:
    title = result.title_cn or result.title or ""
    if _work_key(title) != _work_key(expected.get("title")):
        return False
    author = expected.get("author")
    if author:
        authors = " ".join(result.authors or [])
        if _norm(author) not in _norm(authors):
            return False
    return True


def _score_query(results: list, expected: list[dict], k: int) -> dict:
    # One result satisfies at most one expected item: consume it on match.
    # Otherwise two expected titles that normalise to the same work (e.g.
    # "魔戒" and "魔戒60周年套装") both claim the same hit, DCG counts two
    # gains at one rank while the ideal assumes distinct ranks, and nDCG can
    # exceed 1 and hide regressions.
    ranks: dict[int, int] = {}
    used: set[int] = set()
    for idx, item in enumerate(expected):
        for pos, result in enumerate(results[:k]):
            if pos in used or not _matches(result, item):
                continue
            ranks[idx] = pos + 1
            used.add(pos)
            break

    recall = len(ranks) / len(expected) if expected else 0.0
    hit = 1.0 if ranks else 0.0
    first_rank = min(ranks.values()) if ranks else None
    mrr = 1.0 / first_rank if first_rank else 0.0
    dcg = sum(1.0 / math.log2(rank + 1) for rank in ranks.values())
    ideal = sum(
        1.0 / math.log2(rank + 1)
        for rank in range(1, min(len(expected), k) + 1)
    )
    ndcg = dcg / ideal if ideal else 0.0
    return {
        "recall": recall,
        "hit": hit,
        "mrr": mrr,
        "ndcg": ndcg,
        "ranks": ranks,
    }


def _load_golden(path: str) -> list[dict]:
    queries = []
    for line in Path(path).read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        item = json.loads(line)
        if item.get("relevant"):
            queries.append(item)
    return queries


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


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--db", help="Path to the user's SQLite database")
    parser.add_argument(
        "--golden",
        default=os.path.join(os.path.dirname(__file__), "rag_golden.jsonl"),
    )
    parser.add_argument("--top-k", type=int, default=10)
    parser.add_argument("--alpha", type=float, default=0.5)
    parser.add_argument("--min-score", type=float, default=0.0)
    parser.add_argument("--show-results", action="store_true")
    args = parser.parse_args()

    db_path = args.db or _pick_db(os.environ.get("DATA_DIR", "/app/data"))
    golden = _load_golden(args.golden)
    if not golden:
        raise SystemExit(f"No labelled queries in {args.golden}")

    engine = create_engine(
        f"sqlite:///{db_path}", connect_args={"check_same_thread": False}
    )
    event.listen(engine, "connect", _load_vec_extension)

    rows = []
    with Session(engine) as db:
        for item in golden:
            response = rag_search(
                RagSearchRequest(
                    query=item["query"],
                    top_k=args.top_k,
                    alpha=args.alpha,
                    min_score=args.min_score,
                ),
                db,
            )
            score = _score_query(response.results, item["relevant"], args.top_k)
            rows.append((item, response, score))

    print(f"DB: {db_path}")
    print(f"Golden: {args.golden} ({len(golden)} queries)")
    print(f"alpha={args.alpha:.2f}  top_k={args.top_k}")
    print()
    print(f"{'query':<34} {'recall':>7} {'hit':>5} {'mrr':>6} {'ndcg':>6}")
    for item, response, score in rows:
        print(
            f"{item['query'][:32]:<34} "
            f"{score['recall']:>7.3f} {score['hit']:>5.1f} "
            f"{score['mrr']:>6.3f} {score['ndcg']:>6.3f}"
        )
        if args.show_results:
            got = [r.title_cn or r.title for r in response.results]
            print(f"  got: {got}")

    print()
    print(
        f"{'Mean':<34} "
        f"{sum(r[2]['recall'] for r in rows) / len(rows):>7.3f} "
        f"{sum(r[2]['hit'] for r in rows) / len(rows):>5.3f} "
        f"{sum(r[2]['mrr'] for r in rows) / len(rows):>6.3f} "
        f"{sum(r[2]['ndcg'] for r in rows) / len(rows):>6.3f}"
    )

    misses = [(item, response, score) for item, response, score in rows if not score["ranks"]]
    if misses:
        print("\nMisses:")
        for item, response, _ in misses:
            got = [r.title_cn or r.title for r in response.results[:5]]
            print(f"- {item['query']}  expected={item['relevant']}  got={got}")


if __name__ == "__main__":
    main()
