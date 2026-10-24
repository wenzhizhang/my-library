"""Scoring helpers of the answer evaluation harness (no model calls)."""

import pytest

from scripts.eval_answer import (
    any_title_hit,
    item_hit,
    parse_claims,
    summarise,
    title_matches,
)


def test_title_matches_editions_and_containment():
    assert title_matches("三国演义", "毛宗岗批评本·三国演义")
    assert title_matches("人类简史", "人类简史：从动物到上帝")
    assert not title_matches("沙陀往事", "沙丘")


def test_any_title_hit_needs_one_expected_work():
    titles = ["毛宗岗批评本·三国演义", "水浒传"]

    assert any_title_hit(titles, ["三国演义"])
    assert not any_title_hit(titles, ["红楼梦"])


def test_item_hit_handles_expect_any_and_expect_all():
    titles = ["沙丘", "三体"]

    assert item_hit(titles, {"expect_any": ["三体", "魔戒"]})
    assert item_hit(titles, {"expect_all": ["沙丘", "三体"]})
    assert not item_hit(titles, {"expect_all": ["沙丘", "魔戒"]})


def test_summarise_reports_gating_and_answer_metrics():
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

    stats = summarise({"rows": rows}, live=True)

    assert stats["queries"] == 3
    assert stats["answer_cases"] == 2
    assert stats["candidate_recall"] == 0.5
    assert stats["grounded"] == 0.5
    assert stats["no_answer_accuracy"] == 1.0
    assert stats["invalid_citation_rejections"] == 1
    assert stats["latency_mean"] == pytest.approx(4 / 3)


def test_summarise_ignores_skipped_rows_and_counts_multi_source():
    rows = [
        {"kind": "multi_turn", "query": "x", "skipped": "needs_history"},
        {
            "kind": "compare",
            "query": "y",
            "candidates": 2,
            "expect_no_answer": False,
            "expect_all": True,
            "candidate_hit": True,
            "no_answer": False,
            "grounded": True,
            "invalid_citation": False,
            "latency": 1.0,
        },
    ]

    stats = summarise({"rows": rows}, live=True)

    assert stats["queries"] == 1
    assert stats["skipped_offline"] == 1
    assert stats["multi_source_cases"] == 1
    assert stats["multi_source_ok"] == 1


def test_parse_claims_variants():
    good = parse_claims(
        '{"claims": [{"claim": "a", "supported": true},'
        ' {"claim": "b", "supported": false}]}'
    )
    assert good == {
        "claims": [
            {"claim": "a", "supported": True},
            {"claim": "b", "supported": False},
        ],
        "supported": 1,
        "total": 2,
        "error": False,
    }

    fenced = parse_claims(
        '分析如下：\n```json\n{"claims": [{"claim": "x", "supported": true}]}\n```'
    )
    assert fenced["total"] == 1 and fenced["supported"] == 1

    assert parse_claims("garbage")["error"] is True
    assert parse_claims("")["error"] is True
    assert parse_claims('{"claims": []}')["error"] is True
    assert parse_claims('{"claims": [{"claim": "x", "supported": "yes"}]}')["error"] is True


def test_summarise_aggregates_per_claim_verdicts():
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
            "claims_total": 3,
            "claims_supported": 3,
            "claims_error": False,
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
            "claims_total": 2,
            "claims_supported": 1,
            "claims_error": False,
        },
        {
            "kind": "out_of_scope",
            "query": "c",
            "candidates": 5,
            "expect_no_answer": True,
            "candidate_hit": None,
            "no_answer": True,
            "grounded": None,
            "invalid_citation": False,
            "latency": 1.0,
        },
    ]

    stats = summarise({"rows": rows}, live=True)

    assert stats["claims_total"] == 5
    assert stats["claims_supported"] == 4
    assert stats["answers_with_unsupported"] == 1
    assert stats["judge_errors"] == 0
