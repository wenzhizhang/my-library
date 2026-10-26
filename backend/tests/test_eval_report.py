"""Evaluation report persistence and comparison (no model/database calls)."""

import json

from datetime import datetime

from scripts import eval_report


def test_build_report_envelope():
    report = eval_report.build_report(
        "answer", {"queries": 3, "recall": 1.0}, [{"query": "a"}], {"live": True}
    )

    assert report["kind"] == "answer"
    assert report["live"] is True
    assert report["stats"] == {"queries": 3, "recall": 1.0}
    assert report["rows"] == [{"query": "a"}]
    # ISO timestamp, sortable and timezone-aware
    datetime.fromisoformat(report["created_at"])


def test_write_report_roundtrip(tmp_path):
    report = eval_report.build_report("answer", {"recall": 0.9}, [])

    target = eval_report.write_report(report, str(tmp_path / "run.json"))

    assert target.exists()
    loaded = eval_report.load_report(str(target))
    assert loaded["kind"] == "answer"
    assert loaded["stats"] == {"recall": 0.9}
    assert json.loads(target.read_text(encoding="utf-8"))["created_at"]


def test_write_report_auto_names_by_kind(tmp_path, monkeypatch):
    monkeypatch.setattr(eval_report, "REPORT_DIR", tmp_path)
    report = eval_report.build_report("retrieval", {"mrr": 0.9}, [])

    target = eval_report.write_report(report, "auto")

    assert target.parent == tmp_path
    assert target.name.startswith("retrieval-")
    assert target.suffix == ".json"


def test_compare_stats_only_numeric_metrics():
    previous = {"recall": 0.9, "queries": 10, "note": "x", "flag": True}
    current = {"recall": 0.95, "queries": 10, "note": "y", "flag": False}

    deltas = {item["metric"]: item for item in eval_report.compare_stats(current, previous)}

    assert set(deltas) == {"recall", "queries"}
    assert deltas["recall"]["delta"] == 0.05
    assert deltas["queries"]["delta"] == 0
    assert "flag" not in deltas and "note" not in deltas


def test_format_comparison_mentions_previous_file():
    text = eval_report.format_comparison(
        [{"metric": "recall", "previous": 0.9, "current": 0.95, "delta": 0.05}],
        "/tmp/old.json",
    )

    assert "old.json" in text
    assert "recall" in text
    assert "+0.050" in text
