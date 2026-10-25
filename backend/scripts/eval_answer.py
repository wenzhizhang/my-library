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
from scripts.eval_report import (
    build_report,
    compare_stats,
    format_comparison,
    load_report,
    write_report,
)
from rag.answer import (  # noqa: E402
    REASON_INVALID_CITATION,
    _excerpt,
    answer_question,
    deepseek_chat,
    retrieve_candidates,
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


_CLAIMS_PROMPT = """把被评估的回答拆成若干条独立的事实性论断，逐条判断候选书籍摘要是否支持它。
只输出 JSON：
{"claims": [{"claim": "…", "supported": true}], "reason": "一句话"}
不要评判文风；摘要无法核实或与回答矛盾的论断记为 false。"""


def _extract_json(raw: str) -> dict | None:
    start, end = raw.find("{"), raw.rfind("}")
    if start < 0 or end <= start:
        return None
    try:
        value = json.loads(raw[start : end + 1])
    except Exception:  # noqa: BLE001 - malformed judge output is an error
        return None
    return value if isinstance(value, dict) else None


def _claim_error() -> dict:
    return {"claims": [], "supported": 0, "total": 0, "error": True}


def parse_claims(raw: str) -> dict:
    """Parse the judge's per-claim verdicts; ``error`` when unusable."""
    payload = _extract_json(raw or "")
    claims = payload.get("claims") if payload else None
    if not isinstance(claims, list) or not claims:
        return _claim_error()

    parsed = []
    for item in claims:
        if not isinstance(item, dict):
            return _claim_error()
        claim = str(item.get("claim", "")).strip()
        supported = item.get("supported")
        if not claim or not isinstance(supported, bool):
            return _claim_error()
        parsed.append({"claim": claim, "supported": supported})

    supported = sum(1 for claim in parsed if claim["supported"])
    return {
        "claims": parsed,
        "supported": supported,
        "total": len(parsed),
        "error": False,
    }


def judge_answer(
    query: str,
    candidates: list[dict],
    answer: str,
    total_candidates: int = 0,
    total_titles: int = 0,
) -> dict:
    """LLM-as-judge: is every claim in ``answer`` supported by the candidates?"""
    context = "\n".join(
        f"[book_id={c['book_id']}] {c['title_cn'] or c['title']}｜{_excerpt(c)}"
        for c in candidates
    )
    scope = (
        f"（回答只看到前 {len(candidates)} 本；书库中相关书籍共 "
        f"{total_candidates} 本、{total_titles or total_candidates} 种）"
        if total_candidates > len(candidates)
        else ""
    )
    prompt = (
        f"用户问题：{query}\n\n"
        f"候选书籍摘要{scope}：\n{context}\n\n"
        f"被评估的回答：\n{answer}"
    )
    raw = deepseek_chat(
        prompt,
        system=_CLAIMS_PROMPT,
        max_tokens=500,
        temperature=0.0,
    )
    return parse_claims(raw or "")


def item_hit(titles: list[str], item: dict) -> bool:
    """Match a golden item: every ``expect_all`` title, else any ``expect_any``."""
    expected_all = item.get("expect_all") or []
    if expected_all:
        return all(any_title_hit(titles, [title]) for title in expected_all)
    return any_title_hit(titles, item.get("expect_any") or [])


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
        history = item.get("history") or None
        expected = item.get("expect_any") or []
        expect_all = item.get("expect_all") or []

        if history and not live:
            # A follow-up needs the rewriter, i.e. a model call.
            rows.append(
                {
                    "query": query,
                    "kind": item.get("kind", "topic"),
                    "skipped": "needs_history",
                }
            )
            continue

        row = {
            "query": query,
            "kind": item.get("kind", "topic"),
            "expect_no_answer": bool(item.get("expect_no_answer")),
            "expect_all": bool(expect_all),
        }

        if live:
            started = time.time()
            try:
                result = answer_question(db, query, top_k, history)
                row["latency"] = time.time() - started
                row["no_answer"] = result.no_answer
                row["reason"] = result.reason
                row["invalid_citation"] = (
                    result.reason == REASON_INVALID_CITATION
                )
                candidates = result.more or result.candidates
                source_titles = [
                    s.get("title_cn") or s.get("title") or ""
                    for s in result.sources
                ]
                row["grounded"] = (
                    item_hit(source_titles, item)
                    if (expected or expect_all)
                    else None
                )
                if judge and not result.no_answer:
                    verdict = judge_answer(
                        query,
                        candidates,
                        result.answer,
                        result.total_candidates,
                        result.total_titles,
                    )
                    row["claims_total"] = verdict["total"]
                    row["claims_supported"] = verdict["supported"]
                    row["claims_error"] = verdict["error"]
            except Exception as exc:  # noqa: BLE001 - reported per row
                row["latency"] = time.time() - started
                row["error"] = str(exc)
                candidates = []
        else:
            candidates = retrieve_candidates(db, query, limit=top_k)

        row["candidates"] = len(candidates)
        row["candidate_hit"] = (
            item_hit([c["title_cn"] or c["title"] for c in candidates], item)
            if (expected or expect_all)
            else None
        )
        rows.append(row)
    return {"rows": rows}


def summarise(report: dict, live: bool) -> dict:
    all_rows = report["rows"]
    skipped = [r for r in all_rows if r.get("skipped")]
    rows = [r for r in all_rows if not r.get("skipped")]
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
        "skipped_offline": len(skipped),
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
                "claims_total": sum(
                    r.get("claims_total", 0) for r in rows if "claims_total" in r
                ),
                "claims_supported": sum(
                    r.get("claims_supported", 0)
                    for r in rows
                    if "claims_supported" in r
                ),
                "answers_with_unsupported": sum(
                    1
                    for r in rows
                    if r.get("claims_total", 0) > r.get("claims_supported", 0)
                ),
                "judge_errors": sum(1 for r in rows if r.get("claims_error")),
                "multi_source_cases": sum(
                    1 for r in answered if r.get("expect_all")
                ),
                "multi_source_ok": sum(
                    1 for r in answered if r.get("expect_all") and r["grounded"]
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
    parser.add_argument(
        "--json",
        nargs="?",
        const="auto",
        metavar="PATH",
        help="Save the report as JSON (default: scripts/reports/answer-<ts>.json)",
    )
    parser.add_argument(
        "--compare",
        metavar="PATH",
        help="Print metric deltas against a previous JSON report",
    )
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
    print(f"Golden: {args.golden} ({stats['queries']} scored queries)")
    if stats.get("skipped_offline"):
        print(f"  skipped offline (need --live for history): {stats['skipped_offline']}")
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
        if stats.get("claims_total"):
            print(
                f"claim support (judge): {stats['claims_supported']}/"
                f"{stats['claims_total']} = "
                f"{stats['claims_supported'] / stats['claims_total']:.3f}"
            )
            print(
                f"  answers_with_unsupported={stats['answers_with_unsupported']}"
                f" judge_errors={stats['judge_errors']}"
            )
        if stats.get("multi_source_cases"):
            print(
                f"multi-source coverage : {stats['multi_source_ok']}/"
                f"{stats['multi_source_cases']}"
            )

    if args.show or args.live:
        print()
        for row in report["rows"]:
            if row.get("skipped"):
                print(f"  [{row['kind']:<12}] {row['query'][:24]:<26} skipped ({row['skipped']})")
                continue
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
            verdict = (
                f" claims={row['claims_supported']}/{row['claims_total']}"
                if "claims_total" in row
                else ""
            )
            print(
                f"  [{row['kind']:<12}] {row['query'][:24]:<26}"
                f" cands={row['candidates']} {status}{verdict}"
            )


    if args.json:
        payload = build_report(
            "answer",
            stats,
            report["rows"],
            {
                "db": args.db,
                "golden": args.golden,
                "live": args.live,
                "judge": args.judge,
                "top_k": args.top_k,
                "limit": args.limit,
            },
        )
        target = write_report(payload, args.json)
        print(f"\nReport: {target}")

    if args.compare:
        previous = load_report(args.compare)
        print()
        print(
            format_comparison(
                compare_stats(stats, previous.get("stats", {})), args.compare
            )
        )


if __name__ == "__main__":
    main()
