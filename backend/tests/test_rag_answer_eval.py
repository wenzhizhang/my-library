import pytest

"""Scoring helpers of the answer evaluation harness (no model calls)."""

from scripts.eval_answer import (
    candidate_hit,
    parse_verdict,
    source_hit,
    summarise,
    title_matches,
)


def test_title_matches_editions_and_containment():
    assert title_matches("三国演义", "毛宗岗批评本·三国演义")
    assert title_matches("人类简史", "人类简史：从动物到上帝")
    assert not title_matches("沙陀往事", "沙丘")


def test_candidate_hit_needs_one_expected_work():
    candidates = [
        {"title": "毛宗岗批评本·三国演义", "title_cn": "毛宗岗批评本·三国演义"},
        {"title": "水浒传", "title_cn": "水浒传"},
    ]

    assert candidate_hit(candidates, ["三国演义"])
    assert not candidate_hit(candidates, ["红楼梦"])


def test_source_hit_matches_cited_titles():
    sources = [{"title": "沙陀往事", "title_cn": "沙陀往事"}]

    assert source_hit(sources, ["沙陀往事"])
    assert not source_hit(sources, ["旧唐书"])


def test_summarise_reports_gating_and_answer_metrics():
    report = {
        "rows": [
            {
                "kind": "title",
                "query": "a",
                "candidates": 1,
                "expect_no_answer": False,
                "candidate_hit": True,
                "no_answer": False,
                "grounded": True,
                "invalid_citation": False,
                "latency": 1.0,
            },
            {
                "kind": "topic",
                "query": "b",
                "candidates": 5,
                "expect_no_answer": False,
                "candidate_hit": False,
                "no_answer": False,
                "grounded": False,
                "invalid_citation": False,
                "latency": 2.0,
            },
            {
                "kind": "out_of_scope",
                "query": "c",
                "candidates": 5,
                "expect_no_answer": True,
                "candidate_hit": None,
                "no_answer": True,
                "grounded": None,
                "invalid_citation": True,
                "latency": 1.0,
            },
        ]
    }

    stats = summarise(report, live=True)

    assert stats["queries"] == 3
    assert stats["answer_cases"] == 2
    assert stats["candidate_recall"] == 0.5
    assert stats["grounded"] == 0.5
    assert stats["no_answer_accuracy"] == 1.0
    assert stats["invalid_citation_rejections"] == 1
    assert stats["latency_mean"] == pytest.approx(4 / 3)


def test_parse_verdict_variants():
    assert parse_verdict('{"verdict": "supported", "reason": "ok"}') == "supported"
    assert parse_verdict('结论："unsupported"，因为…') == "unsupported"
    assert parse_verdict("garbage") == "error"
    assert parse_verdict("") == "error"
    assert parse_verdict('{"verdict": "maybe"}') == "error"


def test_summarise_includes_faithfulness_when_judged():
    rows = [
        {
            "kind": "title",
            "query": "a",
            "candidates": 1,
            "expect_no_answer": False,
            "candidate_hit": True,
            "no_answer": False,
            "grounded": True,
            "invalid_citation": False,
            "latency": 1.0,
            "verdict": "supported",
        },
        {
            "kind": "topic",
            "query": "b",
            "candidates": 3,
            "expect_no_answer": False,
            "candidate_hit": True,
            "no_answer": False,
            "grounded": True,
            "invalid_citation": False,
            "latency": 1.0,
            "verdict": "partial",
        },
        {
            "kind": "out_of_scope",
            "query": "c",
            "candidates": 5,
            "expect_no_answer": True,
            "candidate_hit": None,
            "no_answer": True,
            "grounded": None,
            "invalid_citation": True,
            "latency": 1.0,
        },
    ]

    stats = summarise({"rows": rows}, live=True)

    assert stats["faithfulness"] == 0.5
    assert stats["partial"] == 1
    assert stats["unsupported"] == 0
