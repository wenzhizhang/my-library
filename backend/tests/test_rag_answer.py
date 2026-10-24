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


def _cand(book):
    return {
        "book_id": book.id,
        "title": book.title,
        "title_cn": book.title_cn,
        "summary": "简介",
        "introduction": "",
        "authors": [],
    }


def test_followup_is_rewritten_before_retrieval(db, monkeypatch):
    book = _book(db, "沙陀往事")
    seen = {}

    def fake_retrieve(db_, query, top_k=5):
        seen["query"] = query
        return [_cand(book)]

    monkeypatch.setattr(answer_module, "retrieve_for_answer", fake_retrieve)
    systems = []

    def fake_chat(prompt, *, system, **kwargs):
        systems.append(system)
        if "改写" in system:
            return "沙陀往事 作者"
        return f"作者是樊文礼。[book_id={book.id}]"

    monkeypatch.setattr(answer_module, "deepseek_chat", fake_chat)
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")

    history = [
        {"role": "user", "content": "沙陀往事讲了什么"},
        {"role": "assistant", "content": "它讲沙陀人的历史。"},
    ]
    result = answer_question(db, "作者是谁", history=history)

    assert seen["query"] == "沙陀往事 作者"
    assert result.rewritten_query == "沙陀往事 作者"
    assert result.no_answer is False
    assert len(systems) == 2  # one rewrite, one answer


def test_followup_rewrite_failure_falls_back(db, monkeypatch):
    book = _book(db, "沙陀往事")
    seen = {}

    def fake_retrieve(db_, query, top_k=5):
        seen["query"] = query
        return [_cand(book)]

    monkeypatch.setattr(answer_module, "retrieve_for_answer", fake_retrieve)
    replies = iter([None, f"答案。[book_id={book.id}]"])
    monkeypatch.setattr(
        answer_module, "deepseek_chat", lambda prompt, **kw: next(replies)
    )
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")

    result = answer_question(
        db, "作者是谁", history=[{"role": "user", "content": "沙陀往事"}]
    )

    assert seen["query"] == "作者是谁"
    assert result.no_answer is False
    assert result.rewritten_query == ""


def test_no_history_skips_the_rewrite(db, monkeypatch):
    book = _book(db, "沙陀往事")
    monkeypatch.setattr(
        answer_module, "retrieve_for_answer", lambda *a, **k: [_cand(book)]
    )
    systems = []

    def fake_chat(prompt, *, system, **kwargs):
        systems.append(system)
        return f"答案。[book_id={book.id}]"

    monkeypatch.setattr(answer_module, "deepseek_chat", fake_chat)
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")

    answer_question(db, "沙陀往事")

    assert len(systems) == 1


def test_prompt_includes_history_and_caps_it(db):
    candidates = [
        {
            "book_id": 1,
            "title": "沙陀往事",
            "title_cn": "沙陀往事",
            "summary": "s",
            "introduction": "",
            "authors": [],
        }
    ]
    history = [{"role": "user", "content": f"第{i}轮"} for i in range(10)]

    prompt = build_prompt("作者是谁", candidates, history)

    assert "对话历史" in prompt
    assert "第9轮" in prompt
    assert "第0轮" not in prompt


def test_rewrite_only_sees_recent_turns(monkeypatch):
    prompts = []
    monkeypatch.setattr(
        answer_module,
        "deepseek_chat",
        lambda prompt, **kw: prompts.append(prompt) or "独立查询",
    )
    history = [{"role": "user", "content": f"第{i}轮"} for i in range(12)]

    answer_module.resolve_followup_query("接下来呢", history)

    assert "第11轮" in prompts[0]
    assert "第0轮" not in prompts[0]


def test_rewrite_strips_stray_book_brackets(monkeypatch):
    monkeypatch.setattr(
        answer_module,
        "deepseek_chat",
        lambda prompt, **kw: "《沙陀往事》的作者是谁",
    )

    rewritten = answer_module.resolve_followup_query(
        "作者是谁", [{"role": "user", "content": "沙陀往事"}]
    )

    assert rewritten == "沙陀往事的作者是谁"


def test_multi_work_query_returns_every_named_work(db, monkeypatch):
    books = [_book(db, title) for title in ("沙丘", "三体", "魔戒", "活着")]
    called = []
    _no_search(monkeypatch, called)

    candidates = retrieve_for_answer(
        db, "《沙丘》和三体、魔戒与活着分别讲了什么"
    )

    assert {c["book_id"] for c in candidates} == {book.id for book in books}
    assert called == []


def test_series_query_skips_the_title_fast_path(db, monkeypatch):
    _book(db, "沙丘")
    seen = []
    monkeypatch.setattr(
        answer_module, "search_fts", lambda *a, **k: seen.append("fts") or []
    )
    monkeypatch.setattr(answer_module, "search_vectors", lambda *a, **k: [])

    candidates = retrieve_for_answer(db, "沙丘系列有哪些")

    assert seen == ["fts"]
    assert candidates == []


def test_short_title_in_a_long_question_does_not_fast_path(db, monkeypatch):
    _book(db, "沙丘")
    seen = []
    monkeypatch.setattr(
        answer_module, "search_fts", lambda *a, **k: seen.append("fts") or []
    )
    monkeypatch.setattr(answer_module, "search_vectors", lambda *a, **k: [])

    retrieve_for_answer(db, "我想了解一下沙丘这本书到底怎么样")

    assert seen == ["fts"]


def test_rewrite_is_exposed_when_the_answer_is_none(db, monkeypatch):
    book = _book(db, "沙陀往事")
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")
    monkeypatch.setattr(
        answer_module, "retrieve_for_answer", lambda *a, **k: [_cand(book)]
    )
    replies = iter(["沙陀往事 作者", "NONE"])
    monkeypatch.setattr(
        answer_module, "deepseek_chat", lambda prompt, **kw: next(replies)
    )

    result = answer_question(
        db, "作者是谁", history=[{"role": "user", "content": "沙陀往事"}]
    )

    assert result.no_answer is True
    assert result.rewritten_query == "沙陀往事 作者"


def test_rewrite_is_exposed_when_there_are_no_candidates(db, monkeypatch):
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")
    monkeypatch.setattr(answer_module, "retrieve_for_answer", lambda *a, **k: [])
    monkeypatch.setattr(
        answer_module, "deepseek_chat", lambda prompt, **kw: "沙陀往事 作者"
    )

    result = answer_question(
        db, "作者是谁", history=[{"role": "user", "content": "沙陀往事"}]
    )

    assert result.reason == REASON_NO_CANDIDATES
    assert result.rewritten_query == "沙陀往事 作者"


def test_short_title_with_a_short_wrapper_fast_paths(db, monkeypatch):
    book = _book(db, "三体")
    called = []
    _no_search(monkeypatch, called)

    candidates = retrieve_for_answer(db, "三体的作者是谁")

    assert [c["book_id"] for c in candidates] == [book.id]
    assert called == []
