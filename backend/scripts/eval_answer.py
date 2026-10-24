"""Quantitative evaluation of the RAG answer pipeline.

Two layers:

* **Gating (default, free)** — ``retrieve_for_answer`` is deterministic, so the
  candidate set can be scored without calling the model: does an expected book
  survive the title path / score-cluster gate, and how many candidates are
  produced?
* **Answers (``--live``)** — also calls DeepSeek Flash and scores the reply:
  ``no_answer`` accuracy, grounding (the cited book is an expected one),
  citation validity, and latency.

Usage::

    python scripts/eval_answer.py --db /app/data/<uuid>.db
    python scripts/eval_answer.py --db /app/data/<uuid>.db --live
    python scripts/eval_answer.py --db /app/data/<uuid>.db --live --limit 5

The golden file is JSONL, one query per line::

    {"query": "...", "kind": "title|topic|out_of_scope",
     "expect_any": ["..."], "expect_no_answer": true}
"""

import argparse
import json
import os
import re
import sys
import time
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from sqlalchemy import create_engine, event  # noqa: E402
from sqlalchemy.orm import Session  # noqa: E402

from database import _load_vec_extension  # noqa: E402
from rag.answer import (  # noqa: E402
    REASON_INVALID_CITATION,
    _excerpt,
    answer_question,
    deepseek_chat,
    retrieve_for_answer,
    title_matches,
)

def any_title_hit(titles: list[str], expected_titles: list[str]) -> bool:
    """True when any actual title matches any expected work."""
    return any(
        title_matches(expected, title)
        for expected in expected_titles
        for title in titles
        if title
    )


def candidate_hit(candidates: list[dict], expected_titles: list[str]) -> bool:
    return any_title_hit(
        [c["title_cn"] or c["title"] for c in candidates], expected_titles
    )


def source_hit(sources: list[dict], expected_titles: list[str]) -> bool:
    return any_title_hit(
        [s.get("title_cn") or s.get("title") or "" for s in sources],
        expected_titles,
    )


_VERDICT_RE = re.compile(r'"(supported|partial|unsupported)"', re.IGNORECASE)
_VERDICTS = {"supported", "partial", "unsupported"}


def parse_verdict(raw: str) -> str:
    """Extract the judge's verdict; ``error`` when it is unusable."""
    if not raw:
        return "error"
    match = _VERDICT_RE.search(raw)
    if match:
        return match.group(1).lower()
    try:
        verdict = str(json.loads(raw).get("verdict", "")).lower()
    except Exception:  # noqa: BLE001 - any malformed output is an error
        return "error"
    return verdict if verdict in _VERDICTS else "error"


def judge_answer(query: str, candidates: list[dict], answer: str) -> str:
    """LLM-as-judge: is every claim in ``answer`` supported by the candidates?"""
    context = "\n".join(
        f"[book_id={c['book_id']}] {c['title_cn'] or c['title']}｜{_excerpt(c)}"
        for c in candidates
    )
    prompt = (
        f"用户问题：{query}\n\n"
        f"候选书籍摘要：\n{context}\n\n"
        f"被评估的回答：\n{answer}\n\n"
        "请判断回答中的事实性内容能否由候选摘要支持。只输出 JSON：\n"
        '{"verdict": "supported|partial|unsupported", "reason": "一句话"}\n'
        "- supported：所有事实论断都有摘要依据；\n"
        "- partial：部分有依据，部分超出摘要或无法核实；\n"
        "- unsupported：核心论断没有依据或与摘要矛盾。"
    )
    raw = deepseek_chat(
        prompt,
        system="你是严格的事实核验员。只输出 JSON，不要多余文字。",
        max_tokens=200,
        temperature=0.0,
    )
    return parse_verdict(raw or "")


def load_golden(path: str) -> list[dict]:
    items = []
    for line in Path(path).read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        items.append(json.loads(line))
    return items


def evaluate(
    db: Session,
    golden: list[dict],
    live: bool,
    top_k: int = 5,
    judge: bool = False,
) -> dict:
    rows = []
    for item in golden:
        query = item["query"]
        expected = item.get("expect_any") or []
        candidates = retrieve_for_answer(db, query, top_k)

        row = {
            "query": query,
            "kind": item.get("kind", "topic"),
            "candidates": len(candidates),
            "expect_no_answer": bool(item.get("expect_no_answer")),
            "candidate_hit": candidate_hit(candidates, expected)
            if expected
            else None,
        }

        if live:
            started = time.time()
            try:
                result = answer_question(db, query, top_k)
                row["latency"] = time.time() - started
                row["no_answer"] = result.no_answer
                row["grounded"] = (
                    source_hit(result.sources, expected) if expected else None
                )
                row["reason"] = result.reason
                row["invalid_citation"] = (
                    result.reason == REASON_INVALID_CITATION
                )
                row["sources"] = [
                    s.get("title_cn") or s.get("title") or "" for s in result.sources
                ]
                row["answer"] = result.answer
                if judge and not result.no_answer:
                    row["verdict"] = judge_answer(query, candidates, result.answer)
            except Exception as exc:  # noqa: BLE001 - reported per row
                row["latency"] = time.time() - started
                row["error"] = str(exc)

        rows.append(row)
    return {"rows": rows}


def summarise(report: dict, live: bool) -> dict:
    rows = report["rows"]
    answer_cases = [r for r in rows if not r["expect_no_answer"]]
    no_answer_cases = [r for r in rows if r["expect_no_answer"]]

    stats = {
        "queries": len(rows),
        "answer_cases": len(answer_cases),
        "no_answer_cases": len(no_answer_cases),
        "candidate_recall": (
            sum(1 for r in answer_cases if r["candidate_hit"]) / len(answer_cases)
            if answer_cases
            else 0.0
        ),
        "mean_candidates": (
            sum(r["candidates"] for r in rows) / len(rows) if rows else 0.0
        ),
    }

    if live:
        answered = [r for r in answer_cases if "error" not in r]
        no_answered = [r for r in no_answer_cases if "error" not in r]
        stats.update(
            {
                "grounded": (
                    sum(1 for r in answered if r["grounded"]) / len(answered)
                    if answered
                    else 0.0
                ),
                "no_answer_accuracy": (
                    sum(1 for r in no_answered if r["no_answer"])
                    / len(no_answered)
                    if no_answered
                    else 0.0
                ),
                "invalid_citation_rejections": sum(
                    1 for r in rows if r.get("invalid_citation")
                ),
                "errors": sum(1 for r in rows if "error" in r),
                "faithfulness": (
                    sum(1 for r in answered if r.get("verdict") == "supported")
                    / sum(1 for r in answered if "verdict" in r)
                    if any("verdict" in r for r in answered)
                    else None
                ),
                "partial": sum(1 for r in rows if r.get("verdict") == "partial"),
                "unsupported": sum(
                    1 for r in rows if r.get("verdict") == "unsupported"
                ),
                "judge_errors": sum(
                    1 for r in rows if r.get("verdict") == "error"
                ),
                "latency_mean": (
                    sum(r["latency"] for r in rows if "latency" in r)
                    / sum(1 for r in rows if "latency" in r)
                    if any("latency" in r for r in rows)
                    else 0.0
                ),
            }
        )
    return stats


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--db", required=True, help="Path to the user's SQLite database")
    parser.add_argument(
        "--golden",
        default=os.path.join(os.path.dirname(__file__), "rag_answer_golden.jsonl"),
    )
    parser.add_argument("--top-k", type=int, default=5)
    parser.add_argument(
        "--live",
        action="store_true",
        help="Also call DeepSeek and score the answers (costs API calls)",
    )
    parser.add_argument(
        "--judge",
        action="store_true",
        help="Also run LLM-as-judge faithfulness on each answer (implies --live)",
    )
    parser.add_argument("--limit", type=int, default=0)
    parser.add_argument("--show", action="store_true", help="Print every row")
    args = parser.parse_args()

    if args.judge:
        args.live = True
    if args.live and not os.environ.get("DEEPSEEK_API_KEY", "").strip():
        raise SystemExit("--live needs DEEPSEEK_API_KEY")

    golden = load_golden(args.golden)
    if args.limit:
        golden = golden[: args.limit]
    if not golden:
        raise SystemExit(f"No queries in {args.golden}")

    engine = create_engine(
        f"sqlite:///{args.db}", connect_args={"check_same_thread": False}
    )
    event.listen(engine, "connect", _load_vec_extension)

    with Session(engine) as db:
        report = evaluate(db, golden, args.live, args.top_k, args.judge)
    stats = summarise(report, args.live)

    print(f"DB: {args.db}")
    print(f"Golden: {args.golden} ({stats['queries']} queries)")
    print(
        f"  answer={stats['answer_cases']}  no_answer={stats['no_answer_cases']}"
        f"  top_k={args.top_k}"
    )
    print()
    print(f"candidate recall      : {stats['candidate_recall']:.3f}")
    print(f"mean candidates       : {stats['mean_candidates']:.1f}")
    if args.live:
        print(f"grounded answers      : {stats['grounded']:.3f}")
        print(f"no_answer accuracy    : {stats['no_answer_accuracy']:.3f}")
        print(
            f"invalid citations     : {stats['invalid_citation_rejections']}"
        )
        print(f"errors                : {stats['errors']}")
        print(f"latency mean          : {stats['latency_mean']:.2f}s")
        if stats.get("faithfulness") is not None:
            print(f"faithfulness (judge)  : {stats['faithfulness']:.3f}")
            print(
                f"  partial={stats['partial']} unsupported={stats['unsupported']}"
                f" judge_errors={stats['judge_errors']}"
            )

    if args.show or args.live:
        print()
        for row in report["rows"]:
            status = "ok"
            if row.get("error"):
                status = f"ERROR {row['error'][:40]}"
            elif args.live:
                expected_no_answer = row["expect_no_answer"]
                if expected_no_answer and not row["no_answer"]:
                    status = "FALSE ANSWER"
                elif not expected_no_answer and row["no_answer"]:
                    status = "MISS (no_answer)"
                elif not expected_no_answer and not row["grounded"]:
                    status = "UNGROUNDED"
            verdict = f" judge={row['verdict']}" if "verdict" in row else ""
            print(
                f"  [{row['kind']:<12}] {row['query'][:24]:<26}"
                f" cands={row['candidates']} {status}{verdict}"
            )


if __name__ == "__main__":
    main()
