"""Answer generation: gated retrieval, prompt rules, citation validation."""

import pytest

from models import Book
from rag import answer as answer_module
from rag.answer import (
    NO_ANSWER_TEXT,
    REASON_INVALID_CITATION,
    REASON_MODEL_NONE,
    REASON_NO_CANDIDATES,
    AnswerUnavailable,
    answer_question,
    build_prompt,
    retrieve_for_answer,
    select_candidates,
)


@pytest.fixture(autouse=True)
def _no_embedding_model(monkeypatch):
    """Never download/run the real model in these tests."""
    monkeypatch.setattr(
        "rag.embedding.embed_texts",
        lambda texts, batch_size=32: [[0.0] * 512 for _ in texts],
    )
    monkeypatch.setattr("rag.embedding.embed_text", lambda text: [0.0] * 512)
    monkeypatch.setattr("rag.vector_store.embed_text", lambda text: [0.0] * 512)


def _book(db, title, summary=None, introduction="简介"):
    book = Book(title=title, title_cn=title, summary=summary, introduction=introduction)
    db.add(book)
    db.commit()
    db.refresh(book)
    return book


def _model_says(monkeypatch, text):
    monkeypatch.setattr(answer_module, "deepseek_chat", lambda prompt, **kw: text)


def _no_search(monkeypatch, called):
    def boom(*args, **kwargs):
        called.append(args)
        return []

    monkeypatch.setattr(answer_module, "search_fts", boom)
    monkeypatch.setattr(answer_module, "search_vectors", boom)


def test_title_match_answers_from_that_work_only(db, monkeypatch):
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")
    target = _book(db, "沙陀往事")
    _book(db, "其他书")
    called = []
    _no_search(monkeypatch, called)
    _model_says(monkeypatch, f"答案。[book_id={target.id}]")

    result = answer_question(db, "沙陀往事这本书讲了什么")

    assert result.no_answer is False
    assert [s["book_id"] for s in result.sources] == [target.id]
    assert "[book_id=" not in result.answer
    assert called == []  # title path must not touch the search branches


def test_title_fragment_of_edition_prefixed_title_still_fast_paths(db, monkeypatch):
    target = _book(db, "毛宗岗批评本·三国演义")
    _no_search(monkeypatch, [])

    candidates = retrieve_for_answer(db, "三国演义讲了什么")

    assert [c["book_id"] for c in candidates] == [target.id]


def test_author_name_is_not_a_title_fast_path(db, monkeypatch):
    """A 3-char author name must not collapse into titles containing it."""
    _book(db, "刘慈欣文集")
    _book(db, "刘慈欣合集")
    novel = _book(db, "三体")
    monkeypatch.setattr(
        answer_module,
        "search_fts",
        lambda db, q, top_k: [{"book_id": novel.id, "score": 5.0}],
    )
    monkeypatch.setattr(answer_module, "search_vectors", lambda db, q, top_k: [])

    candidates = retrieve_for_answer(db, "讲刘慈欣的书")

    assert [c["book_id"] for c in candidates] == [novel.id]


def test_keyword_gate_keeps_the_bm25_cluster():
    fts_hits = [
        {"book_id": 1, "score": 10.0},
        {"book_id": 2, "score": 6.0},
        {"book_id": 3, "score": 2.0},  # below 50% of best
    ]

    assert select_candidates(fts_hits, [], 5) == [1, 2]


def test_vector_fallback_only_keeps_clearly_similar_hits():
    vector_hits = [
        {"book_id": 4, "score": 0.72},
        {"book_id": 5, "score": 0.57},  # below the absolute floor
    ]

    assert select_candidates([], vector_hits, 5) == [4]
    assert select_candidates([], [{"book_id": 6, "score": 0.55}], 5) == []


def test_strong_vectors_augment_keyword_hits():
    """A book about the topic may not use the query's exact words."""
    fts_hits = [{"book_id": 1, "score": 10.0}]
    vector_hits = [{"book_id": 2, "score": 0.61}, {"book_id": 3, "score": 0.5}]

    assert select_candidates(fts_hits, vector_hits, 5) == [1, 2]


def test_top_k_is_capped_at_max_candidates(db, monkeypatch):
    books = [_book(db, f"书{i}") for i in range(8)]
    monkeypatch.setattr(
        answer_module,
        "search_fts",
        lambda db, q, top_k: [
            {"book_id": b.id, "score": 10.0} for b in books
        ],
    )
    monkeypatch.setattr(answer_module, "search_vectors", lambda db, q, top_k: [])

    candidates = retrieve_for_answer(db, "某个主题", top_k=10)

    assert len(candidates) == answer_module.MAX_CANDIDATES


def test_no_candidates_never_calls_the_model(db, monkeypatch):
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")
    _no_search(monkeypatch, [])
    calls = []
    monkeypatch.setattr(
        answer_module, "deepseek_chat", lambda prompt, **kw: calls.append(prompt)
    )

    result = answer_question(db, "完全不存在的主题")

    assert result.no_answer is True
    assert result.reason == REASON_NO_CANDIDATES
    assert result.answer == NO_ANSWER_TEXT
    assert result.sources == []
    assert calls == []


def test_model_none_means_not_in_library(db, monkeypatch):
    _book(db, "有用的书")
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")
    _model_says(monkeypatch, "NONE")

    result = answer_question(db, "有用的书")

    assert result.no_answer is True
    assert result.reason == REASON_MODEL_NONE
    assert result.answer == NO_ANSWER_TEXT
    assert result.sources == []


def test_none_with_a_citation_is_still_no_answer(db, monkeypatch):
    book = _book(db, "有用的书")
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")
    _model_says(monkeypatch, f"NONE [book_id={book.id}]")

    result = answer_question(db, "有用的书")

    assert result.no_answer is True
    assert result.reason == REASON_MODEL_NONE
    assert result.sources == []


def test_citation_outside_candidates_is_rejected(db, monkeypatch):
    _book(db, "有用的书")
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")
    _model_says(monkeypatch, "我编的答案。[book_id=999999]")

    result = answer_question(db, "有用的书")

    assert result.no_answer is True
    assert result.reason == REASON_INVALID_CITATION
    assert result.sources == []


def test_mixed_valid_and_invalid_citation_is_rejected(db, monkeypatch):
    book = _book(db, "有用的书")
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")
    _model_says(monkeypatch, f"答案。[book_id={book.id}][book_id=999999]")

    result = answer_question(db, "有用的书")

    assert result.no_answer is True
    assert result.reason == REASON_INVALID_CITATION
    assert result.sources == []


def test_missing_api_key_raises_unavailable(db, monkeypatch):
    monkeypatch.delenv("DEEPSEEK_API_KEY", raising=False)

    with pytest.raises(AnswerUnavailable):
        answer_question(db, "任意问题")


def test_prompt_contains_rules_and_candidates(db):
    _book(db, "沙陀往事", summary="讲沙陀突厥的历史。")
    candidates = retrieve_for_answer(db, "沙陀往事")

    prompt = build_prompt("沙陀往事讲了什么", candidates)

    assert "只输出 NONE" in prompt
    assert "实时" in prompt
    assert "[book_id=" in prompt
    assert "不要编造" in prompt
    assert "沙陀往事" in prompt


def test_answer_endpoint_returns_503_without_key(client, monkeypatch):
    monkeypatch.delenv("DEEPSEEK_API_KEY", raising=False)

    resp = client.post("/api/rag/answer", json={"query": "任意问题"})

    assert resp.status_code == 503
    assert "DEEPSEEK_API_KEY" in resp.json()["detail"]
