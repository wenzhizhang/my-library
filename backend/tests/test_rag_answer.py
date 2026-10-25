"""Answer generation: gated retrieval, prompt rules, citation validation."""

import pytest

from datetime import datetime

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
    retrieve_candidates,
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


REAL_PARSE_INTENT = answer_module.parse_intent


def retrieve_for_answer(db, query, top_k=5):
    """Test helper: the narrow view an answer is built from."""
    top_k = min(top_k, answer_module.MAX_CANDIDATES)
    return retrieve_candidates(db, query, limit=top_k)[:top_k]


@pytest.fixture(autouse=True)
def _topic_intent(monkeypatch):
    """Most tests are not about intent; classify them as a topic."""
    monkeypatch.setattr(
        answer_module,
        "parse_intent",
        lambda query: {"kind": "topic", "value": ""},
    )


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
        "thumb_image": "",
    }


def test_followup_is_rewritten_before_retrieval(db, monkeypatch):
    book = _book(db, "沙陀往事")
    seen = {}

    def fake_retrieve(db_, query, *args, **kwargs):
        seen["query"] = query
        return [_cand(book)]

    monkeypatch.setattr(answer_module, "retrieve_candidates", fake_retrieve)
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

    def fake_retrieve(db_, query, *args, **kwargs):
        seen["query"] = query
        return [_cand(book)]

    monkeypatch.setattr(answer_module, "retrieve_candidates", fake_retrieve)
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
        answer_module, "retrieve_candidates", lambda *a, **k: [_cand(book)]
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


def test_long_question_about_a_short_title_still_fast_paths(db, monkeypatch):
    book = _book(db, "沙丘")
    seen = []
    monkeypatch.setattr(
        answer_module, "search_fts", lambda *a, **k: seen.append("fts") or []
    )
    monkeypatch.setattr(answer_module, "search_vectors", lambda *a, **k: [])

    candidates = retrieve_for_answer(db, "我想了解一下沙丘这本书到底怎么样")

    assert [candidate["book_id"] for candidate in candidates] == [book.id]
    assert seen == []


def test_rewrite_is_exposed_when_the_answer_is_none(db, monkeypatch):
    book = _book(db, "沙陀往事")
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")
    monkeypatch.setattr(
        answer_module, "retrieve_candidates", lambda *a, **k: [_cand(book)]
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
    monkeypatch.setattr(answer_module, "retrieve_candidates", lambda *a, **k: [])
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


@pytest.mark.parametrize(
    "text",
    [
        "还有么？",
        "还有更多么",
        "再来几种",
        "还有其他版本吗",
        "more",
        "show me more",
    ],
)
def test_more_intent_phrases(text):
    assert answer_module._looks_like_more(text)


def test_plain_questions_are_not_more_requests():
    assert not answer_module._looks_like_more("红楼梦讲了什么")
    assert not answer_module._looks_like_more("有哪些讲唐史的书")


def test_closing_phrases_are_not_more_requests():
    assert not answer_module._looks_like_more("就这些吗")
    assert not answer_module._looks_like_more("没了")


def test_more_context_prefers_the_followups_own_topic():
    history = [{"role": "user", "content": "红楼梦有什么版本"}]

    assert (
        answer_module._more_context("还有哪些讲唐史的书", history)
        == "还有哪些讲唐史的书"
    )
    assert answer_module._more_context("还有么？", history) == "红楼梦有什么版本"


def test_sources_carry_thumbnails(db, monkeypatch):
    book = Book(
        title="沙陀往事",
        title_cn="沙陀往事",
        summary="s",
        introduction="i",
        thumb_image="/images/books/1.webp",
    )
    db.add(book)
    db.commit()
    db.refresh(book)
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")
    _model_says(monkeypatch, f"答案。[book_id={book.id}]")

    result = answer_question(db, "沙陀往事")

    assert result.sources[0]["thumb_image"] == "/images/books/1.webp"


def test_more_request_returns_the_next_page_without_the_model(db, monkeypatch):
    _book(db, "红楼梦")
    books = []
    for index in range(10):
        book = Book(
            title=f"红楼梦（版本{index}）",
            title_cn=f"红楼梦（版本{index}）",
            summary="s",
            introduction="i",
            thumb_image=f"/images/books/{index}.webp",
        )
        db.add(book)
        books.append(book)
    db.commit()
    for book in books:
        db.refresh(book)

    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")
    calls = []
    monkeypatch.setattr(
        answer_module, "deepseek_chat", lambda *a, **k: calls.append(a)
    )
    monkeypatch.setattr(
        answer_module,
        "resolve_followup_query",
        lambda query, turns: "红楼梦有哪些版本",
    )
    history = [
        {"role": "user", "content": "红楼梦有哪些版本"},
        {"role": "assistant", "content": "有这些版本。"},
    ]
    seen = [book.id for book in books[:3]]

    result = answer_question(
        db, "还有么？", history=history, seen_book_ids=seen
    )

    assert calls == []  # a "more" reply is pure retrieval
    assert result.no_answer is False
    assert result.more
    assert all(book["book_id"] not in seen for book in result.more)
    assert result.has_more is True


def test_more_request_is_exhausted_after_the_last_page(db, monkeypatch):
    book = _book(db, "红楼梦")
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")
    monkeypatch.setattr(
        answer_module, "deepseek_chat", lambda *a, **k: "不应调用"
    )

    result = answer_question(
        db,
        "还有么？",
        history=[{"role": "user", "content": "红楼梦有哪些版本"}],
        seen_book_ids=[book.id],
    )

    assert result.no_answer is True
    assert result.reason == answer_module.REASON_NO_MORE


def test_normal_answer_reports_when_more_books_exist(db, monkeypatch):
    book = _book(db, "红楼梦")
    pool = [{**_cand(book), "book_id": index} for index in range(6)]
    monkeypatch.setattr(
        answer_module, "retrieve_candidates", lambda *a, **k: pool
    )
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")
    _model_says(monkeypatch, f"答案。[book_id={book.id}]")

    result = answer_question(db, "红楼梦")

    assert result.has_more is True


def test_comparison_keeps_one_edition_of_each_work(db, monkeypatch):
    for index in range(6):
        _book(db, f"三体（版本{index}）")
    for index in range(6):
        _book(db, f"沙丘（版本{index}）")
    _book(db, "三体")
    _book(db, "沙丘")
    _no_search(monkeypatch, [])

    candidates = retrieve_for_answer(db, "《沙丘》和《三体》有什么区别")

    titles = [candidate["title_cn"] for candidate in candidates]
    assert any("沙丘" in title for title in titles)
    assert any("三体" in title for title in titles)


def test_blank_history_turns_are_accepted_and_dropped():
    from schemas.rag import RagAnswerRequest

    request = RagAnswerRequest(
        query="x", history=[{"role": "assistant", "content": ""}]
    )
    assert request.history[0].content == ""
    assert answer_module._normalise_turns(
        [{"role": "assistant", "content": ""}]
    ) == []


def test_answer_endpoint_accepts_a_blank_history_turn(client, monkeypatch):
    monkeypatch.delenv("DEEPSEEK_API_KEY", raising=False)

    resp = client.post(
        "/api/rag/answer",
        json={"query": "x", "history": [{"role": "assistant", "content": ""}]},
    )

    assert resp.status_code == 503  # validated, then blocked on the missing key


def test_focus_book_answers_from_that_book_only(db, monkeypatch):
    book = _book(db, "沙陀往事", summary="讲沙陀人的历史。")
    _book(db, "别的书")
    called = []
    _no_search(monkeypatch, called)
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")
    _model_says(monkeypatch, f"它讲沙陀人的历史。[book_id={book.id}]")

    result = answer_question(db, "这本书讲了什么", focus_book_id=book.id)

    assert [candidate["book_id"] for candidate in result.candidates] == [book.id]
    assert [source["book_id"] for source in result.sources] == [book.id]
    assert called == []


def test_focus_book_missing_is_not_found(db, monkeypatch):
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")

    with pytest.raises(answer_module.BookNotFound):
        answer_question(db, "这本书讲了什么", focus_book_id=999999)


def test_answer_endpoint_404_for_missing_focus_book(client, monkeypatch):
    from routers import rag as rag_router

    def boom(*args, **kwargs):
        raise rag_router.BookNotFound("book 999999 not found")

    monkeypatch.setattr(rag_router, "answer_question", boom)

    resp = client.post(
        "/api/rag/answer", json={"query": "x", "focus_book_id": 999999}
    )

    assert resp.status_code == 404


def test_prompt_includes_publication_metadata_for_a_quoted_book(db, monkeypatch):
    book = Book(
        title="沙陀往事",
        title_cn="沙陀往事",
        summary="讲沙陀人的历史。",
        introduction="i",
        publish_date=datetime(2020, 1, 1),
        isbn="9787000000000",
        pages=300,
        price=59.0,
        binding_type="精装",
    )
    db.add(book)
    db.commit()
    db.refresh(book)
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")
    prompts = []

    def fake_chat(prompt, **kwargs):
        prompts.append(prompt)
        return f"答案。[book_id={book.id}]"

    monkeypatch.setattr(answer_module, "deepseek_chat", fake_chat)

    answer_question(db, "这本书哪年出版的", focus_book_id=book.id)

    prompt = prompts[0]
    assert "2020-01-01" in prompt
    assert "9787000000000" in prompt
    assert "精装" in prompt
    assert "300" in prompt


def test_book_meta_skips_empty_fields():
    meta = answer_module._book_meta(
        {
            "publish_date": "2015-11-01",
            "publisher": "",
            "edition": "",
            "printing_info": "",
            "isbn": "",
            "pages": "",
            "binding_type": "",
            "language": "",
            "price": "",
            "book_count": "",
            "series": "",
            "category": "",
            "douban_score": "",
            "tags": "[]",
        }
    )

    assert meta == "出版日期：2015-11-01"


def test_series_question_returns_every_volume_distinct_titles_first(db, monkeypatch):
    from models import BookSeries

    series = BookSeries(name="尚古一读")
    db.add(series)
    db.commit()
    db.refresh(series)
    for title in ("喻世明言", "喻世明言", "警世通言", "醒世恒言", "文选"):
        book = Book(
            title=title,
            title_cn=title,
            summary="s",
            introduction="i",
            book_series_id=series.id,
        )
        db.add(book)
    db.commit()
    called = []
    _no_search(monkeypatch, called)

    candidates = retrieve_candidates(db, "尚古一读有哪些书", limit=40)

    assert called == []
    assert len(candidates) == 5
    assert [c["title_cn"] for c in candidates] == [
        "喻世明言",
        "警世通言",
        "醒世恒言",
        "文选",
        "喻世明言",
    ]


def test_prompt_notes_that_more_books_exist(db):
    candidates = [
        {
            "book_id": 1,
            "title": "文选",
            "title_cn": "文选",
            "summary": "s",
            "introduction": "",
            "authors": [],
        }
    ]

    prompt = build_prompt(
        "尚古一读有哪些书",
        candidates,
        total_candidates=8,
        total_titles=8,
    )

    assert "共 8 本（8 种）" in prompt
    assert "还有 7 种没有列出" in prompt


def test_author_question_returns_books_by_that_author(db, monkeypatch):
    from models import Author

    tolkien = Author(name="J.R.R. Tolkien", name_cn="约翰·罗纳德·瑞尔·托尔金")
    carpenter = Author(name="Humphrey Carpenter", name_cn="汉弗莱·卡彭特")
    db.add_all([tolkien, carpenter])
    db.commit()

    works = []
    for title in ("魔戒", "霍比特人", "精灵宝钻"):
        book = _book(db, title)
        book.authors.append(tolkien)
        works.append(book)
    biography = _book(db, "托尔金传")
    biography.authors.append(carpenter)
    db.commit()
    called = []
    _no_search(monkeypatch, called)

    candidates = retrieve_candidates(db, "托尔金著的书有哪些", limit=40)

    assert called == []
    assert {c["title_cn"] for c in candidates} == {"魔戒", "霍比特人", "精灵宝钻"}
    assert "托尔金传" not in {c["title_cn"] for c in candidates}


def test_title_match_still_wins_for_a_biography(db, monkeypatch):
    from models import Author

    tolkien = Author(name="J.R.R. Tolkien", name_cn="约翰·罗纳德·瑞尔·托尔金")
    carpenter = Author(name="Humphrey Carpenter", name_cn="汉弗莱·卡彭特")
    db.add_all([tolkien, carpenter])
    db.commit()
    biography = _book(db, "托尔金传")
    biography.authors.append(carpenter)
    work = _book(db, "魔戒")
    work.authors.append(tolkien)
    db.commit()
    called = []
    _no_search(monkeypatch, called)

    candidates = retrieve_candidates(db, "托尔金传", limit=40)

    assert [c["title_cn"] for c in candidates] == ["托尔金传"]


def test_short_author_name_matches_an_exact_author_query(db, monkeypatch):
    from models import Author

    sushi = Author(name="Su Shi", name_cn="苏轼")
    other = Author(name="Someone", name_cn="他人")
    db.add_all([sushi, other])
    db.commit()
    book = _book(db, "东坡志林")
    book.authors.append(sushi)
    db.commit()
    called = []
    _no_search(monkeypatch, called)

    candidates = retrieve_candidates(db, "苏轼的书", limit=40)

    assert [c["title_cn"] for c in candidates] == ["东坡志林"]


def test_exact_author_name_beats_a_title_mentioning_it(db, monkeypatch):
    from models import Author

    sushi = Author(name="Su Shi", name_cn="苏轼")
    db.add(sushi)
    db.commit()
    by_him = _book(db, "东坡志林")
    by_him.authors.append(sushi)
    _book(db, "苏轼")  # a book whose *title* is the author's name
    db.commit()
    called = []
    _no_search(monkeypatch, called)

    candidates = retrieve_candidates(db, "苏轼的书", limit=40)

    assert [c["title_cn"] for c in candidates] == ["东坡志林"]


def test_author_intent_resolves_a_paraphrase(db, monkeypatch):
    from models import Author

    tolkien = Author(name="J.R.R. Tolkien", name_cn="约翰·罗纳德·瑞尔·托尔金")
    carpenter = Author(name="Humphrey Carpenter", name_cn="汉弗莱·卡彭特")
    db.add_all([tolkien, carpenter])
    db.commit()
    work = _book(db, "魔戒")
    work.authors.append(tolkien)
    biography = _book(db, "托尔金传")
    biography.authors.append(carpenter)
    db.commit()
    called = []
    _no_search(monkeypatch, called)
    monkeypatch.setattr(
        answer_module,
        "parse_intent",
        lambda query: {"kind": "author", "value": "托尔金"},
    )
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")
    _model_says(monkeypatch, f"托尔金著有《魔戒》。[book_id={work.id}]")

    result = answer_question(db, "托尔金写过哪些书")

    assert [c["title_cn"] for c in result.candidates] == ["魔戒"]
    assert called == []
    assert result.intent["kind"] == "author"


def test_unresolved_intent_falls_back_to_search(db, monkeypatch):
    seen = []
    monkeypatch.setattr(
        answer_module, "search_fts", lambda *a, **k: seen.append("fts") or []
    )
    monkeypatch.setattr(answer_module, "search_vectors", lambda *a, **k: [])
    monkeypatch.setattr(
        answer_module,
        "parse_intent",
        lambda query: {"kind": "author", "value": "查无此人"},
    )

    retrieve_candidates(db, "查无此人的书")

    assert seen == ["fts"]


def test_parse_intent_parses_the_model_json(monkeypatch):
    from rag import intent as intent_module

    monkeypatch.setattr(
        intent_module,
        "deepseek_chat",
        lambda *a, **k: '{"kind": "author", "value": "托尔金"}',
    )
    assert REAL_PARSE_INTENT("托尔金写过哪些书") == {
        "kind": "author",
        "value": "托尔金",
    }

    monkeypatch.setattr(intent_module, "deepseek_chat", lambda *a, **k: "not json")
    assert REAL_PARSE_INTENT("x") == {"kind": "topic", "value": ""}


def test_prompt_includes_the_author_intent(db):
    candidates = [
        {
            "book_id": 1,
            "title": "魔戒",
            "title_cn": "魔戒",
            "summary": "s",
            "introduction": "",
            "authors": ["托尔金"],
        }
    ]

    prompt = build_prompt(
        "托尔金写过哪些书",
        candidates,
        intent={"kind": "author", "value": "托尔金"},
    )

    assert "用户意图" in prompt
    assert "托尔金" in prompt
    assert "不要把他人的传记" in prompt


def test_focus_beats_a_more_like_question(db, monkeypatch):
    book = _book(db, "史记")
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")
    _model_says(monkeypatch, f"这本书是《史记》。[book_id={book.id}]")

    result = answer_question(db, "还有其他版本吗？", focus_book_id=book.id)

    assert [c["title_cn"] for c in result.candidates] == ["史记"]
    assert [s["title_cn"] for s in result.sources] == ["史记"]


def test_more_after_an_author_intent_keeps_the_intent(db, monkeypatch):
    from models import Author

    tolkien = Author(name="J.R.R. Tolkien", name_cn="约翰·罗纳德·瑞尔·托尔金")
    other = Author(name="Humphrey Carpenter", name_cn="汉弗莱·卡彭特")
    db.add_all([tolkien, other])
    db.commit()
    works = []
    for title in ("魔戒", "霍比特人", "精灵宝钻"):
        book = _book(db, title)
        book.authors.append(tolkien)
        works.append(book)
    biography = _book(db, "托尔金传")
    biography.authors.append(other)
    db.commit()

    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")
    called = []
    monkeypatch.setattr(
        answer_module, "search_fts", lambda *a, **k: called.append("fts") or []
    )
    monkeypatch.setattr(answer_module, "search_vectors", lambda *a, **k: [])
    monkeypatch.setattr(
        answer_module,
        "parse_intent",
        lambda query: {"kind": "author", "value": "托尔金"},
    )
    history = [
        {"role": "user", "content": "托尔金写过哪些书"},
        {"role": "assistant", "content": "有《魔戒》《霍比特人》。"},
    ]

    result = answer_module.answer_question(
        db, "还有么？", history=history, seen_book_ids=[works[0].id]
    )

    assert called == []
    assert {book["title_cn"] for book in result.more} == {"霍比特人", "精灵宝钻"}


def test_seen_book_ids_accepts_a_long_conversation():
    from schemas.rag import RagAnswerRequest

    request = RagAnswerRequest(query="x", seen_book_ids=list(range(250)))

    assert len(request.seen_book_ids) == 250


def test_list_continuations_are_more_requests():
    assert answer_module._looks_like_more("列出来")
    assert answer_module._looks_like_more("都列出来")
    assert answer_module._looks_like_more("具体说说")


def test_focus_author_question_expands_to_the_authors_works(db, monkeypatch):
    from models import Author

    liu = Author(name="Liu Cixin", name_cn="刘慈欣")
    db.add(liu)
    db.commit()
    works = []
    for title in ("三体", "流浪地球", "带上她的眼睛"):
        book = _book(db, title)
        book.authors.append(liu)
        works.append(book)
    db.commit()
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")
    _model_says(monkeypatch, f"刘慈欣还写过《流浪地球》。[book_id={works[1].id}]")

    result = answer_question(db, "该书作者还有哪些作品", focus_book_id=works[0].id)

    titles = {candidate["title_cn"] for candidate in result.candidates}
    assert "流浪地球" in titles
    assert "三体" in titles


def test_more_after_an_author_answer_keeps_the_author_intent(db, monkeypatch):
    from models import Author

    liu = Author(name="Liu Cixin", name_cn="刘慈欣")
    db.add(liu)
    db.commit()
    works = []
    for title in ("三体", "流浪地球", "带上她的眼睛", "球状闪电"):
        book = _book(db, title)
        book.authors.append(liu)
        works.append(book)
    db.commit()
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")
    monkeypatch.setattr(
        answer_module,
        "resolve_followup_query",
        lambda query, turns: "刘慈欣的作品有哪些",
    )
    monkeypatch.setattr(
        answer_module,
        "parse_intent",
        lambda query: {"kind": "author", "value": "刘慈欣"},
    )

    result = answer_module.answer_question(
        db,
        "列出来",
        history=[
            {"role": "user", "content": "该书作者还有哪些作品"},
            {"role": "assistant", "content": "刘慈欣还写过《流浪地球》。"},
        ],
        seen_book_ids=[works[0].id],
    )

    assert {book["title_cn"] for book in result.more} == {
        "流浪地球",
        "带上她的眼睛",
        "球状闪电",
    }


def test_resolve_entity_books_for_author_and_series(db):
    from models import Author, BookSeries
    from rag.intent import resolve_entity_books

    author = Author(name="Su Shi", name_cn="苏轼")
    series = BookSeries(name="甲骨文丛书")
    db.add_all([author, series])
    db.commit()

    by_him = _book(db, "东坡乐府笺")
    by_him.authors.append(author)
    series_book = _book(db, "秦汉帝国：中国古代帝国之兴亡")
    series_book.book_series_id = series.id
    db.commit()

    author_ids = resolve_entity_books(db, "苏轼的书", 10)
    assert {db.get(Book, bid).title_cn for bid in author_ids} == {"东坡乐府笺"}

    series_ids = resolve_entity_books(db, "甲骨文丛书有哪些", 10)
    assert {db.get(Book, bid).title_cn for bid in series_ids} == {
        "秦汉帝国：中国古代帝国之兴亡"
    }

    assert resolve_entity_books(db, "关于人工智能的书", 10) == []
