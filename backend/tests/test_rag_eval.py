"""Unit tests for the RAG golden-set scoring helpers."""

import math

from schemas.rag import RagSearchResultItem
from scripts.eval_rag import _matches, _score_query


def _item(book_id: int, title: str, authors: list[str] | None = None):
    return RagSearchResultItem(
        book_id=book_id,
        score=1.0,
        title=title,
        title_cn=title,
        authors=authors or [],
    )


def test_matches_editions_but_not_volumes():
    assert _matches(_item(1, "魔戒60周年套装"), {"title": "魔戒"})
    assert not _matches(_item(2, "魔戒 第二部：双塔奇兵"), {"title": "魔戒"})


def test_matches_author_substring():
    result = _item(1, "魔戒", ["约翰·罗纳德·瑞尔·托尔金"])
    assert _matches(result, {"title": "魔戒", "author": "托尔金"})


def test_score_query_recall_mrr_and_ndcg():
    results = [_item(1, "魔戒"), _item(2, "霍比特人")]

    score = _score_query(results, [{"title": "霍比特人"}], 10)
    assert score["recall"] == 1.0
    assert score["hit"] == 1.0
    assert score["mrr"] == 0.5
    assert math.isclose(score["ndcg"], 1 / math.log2(3))


def test_score_query_multiple_expected():
    results = [_item(1, "魔戒"), _item(2, "霍比特人")]
    score = _score_query(
        results, [{"title": "魔戒"}, {"title": "霍比特人"}], 10
    )
    assert score["recall"] == 1.0
    assert score["mrr"] == 1.0
    assert score["ndcg"] == 1.0


def test_score_query_miss():
    score = _score_query([_item(1, "魔戒")], [{"title": "三体"}], 10)
    assert score == {
        "recall": 0.0,
        "hit": 0.0,
        "mrr": 0.0,
        "ndcg": 0.0,
        "ranks": {},
    }


def test_score_query_does_not_reuse_one_result_for_two_expected():
    """Two expected titles for the same work must not both claim one hit."""
    score = _score_query(
        [_item(1, "魔戒")],
        [{"title": "魔戒"}, {"title": "魔戒60周年套装"}],
        10,
    )

    assert score["ranks"] == {0: 1}
    assert score["recall"] == 0.5
    assert score["ndcg"] <= 1.0


def test_ndcg_never_exceeds_one_for_duplicate_expected_works():
    results = [_item(1, "魔戒60周年套装"), _item(2, "霍比特人")]
    score = _score_query(
        results,
        [{"title": "魔戒"}, {"title": "魔戒60周年套装"}],
        10,
    )

    assert len(set(score["ranks"].values())) == len(score["ranks"])
    assert score["ndcg"] <= 1.0
