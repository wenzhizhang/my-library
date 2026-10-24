"""RAG storage-level tests: FTS correctness, tokenizer safety, fallback."""

import logging
from types import SimpleNamespace

import pytest
from sqlalchemy import create_engine, event, text
from sqlalchemy.orm import Session
from sqlalchemy.pool import StaticPool

from database import _load_vec_extension
from models import Book
from rag.document import (
    EMBEDDING_WINDOW_CHARS,
    build_book_document,
    build_book_embedding_document,
    summary_char_budget,
)
from rag.pipeline import (
    MIN_SUMMARY_INTRODUCTION_CHARS,
    _generate_summaries,
    _introduction_needs_summary,
    generate_book_summary,
    reindex_all,
)
from rag.summarize import (
    _request_summary,
    summarize_introduction,
    summarize_with_llm,
)
from rag.vector_store import (
    _fts_tokenize,
    normalize_query,
    current_embedding_fingerprint,
    embedding_fingerprint_status,
    delete_book_vector,
    ensure_rag_tables,
    hybrid_search,
    stored_embedding_fingerprint,
    search_fts,
    upsert_book_fts,
    upsert_book_vector,
)
from routers.rag import _diversify_results
from schemas.rag import RagSearchResultItem


@pytest.fixture
def rag_db():
    engine = create_engine(
        "sqlite://", connect_args={"check_same_thread": False}, poolclass=StaticPool
    )
    event.listen(engine, "connect", _load_vec_extension)
    session = Session(engine)
    ensure_rag_tables(session)
    yield session
    session.close()
    engine.dispose()


# Comfortably above MIN_SUMMARY_INTRODUCTION_CHARS (512), so the summary
# strategy actually runs for fixtures that expect a generated summary.
_LONG_INTRODUCTION = "甘道夫是迈雅，也是霍比特人的朋友。" * 40


def _fake_book(**overrides):
    fields = {
        "title": "The Hobbit",
        "title_cn": "霍比特人",
        "translator": None,
        "publisher": None,
        "brand": None,
        "book_series": None,
        "category": None,
        "tags": ["奇幻"],
        "authors": [
            SimpleNamespace(
                name_cn="托尔金", name="Tolkien", dynasty=None, nation=None
            )
        ],
        "introduction": "完整简介",
        "summary": None,
        "catalog": None,
        "isbn": None,
        "edition": None,
        "language": None,
        "pages": None,
    }
    fields.update(overrides)
    return SimpleNamespace(**fields)


def test_embedding_document_prefers_summary():
    book = _fake_book(summary="甘道夫是迈雅，霍比特人的朋友。")
    doc = build_book_embedding_document(book)
    assert "书名: 霍比特人 (The Hobbit)" in doc
    assert "作者: 托尔金" in doc
    assert "标签: 奇幻" in doc
    assert "摘要: 甘道夫是迈雅" in doc
    assert "完整简介" not in doc


def test_embedding_document_falls_back_without_summary():
    book = _fake_book(summary="   ", introduction="完整简介")
    doc = build_book_embedding_document(book)
    assert "简介: 完整简介" in doc
    assert "摘要:" not in doc


def test_summarize_introduction_keeps_short_text():
    text = "短简介。"
    assert summarize_introduction(text) == text


def test_summarize_introduction_is_bounded_and_keeps_key_terms():
    text = "第一句。甘道夫是迈雅。无关内容。甘道夫帮助比尔博。魔戒很重要。"
    summary = summarize_introduction(text, max_chars=30)
    assert len(summary) <= 30
    assert "甘道夫" in summary


def test_summarize_with_llm_uses_deepseek_flash(monkeypatch):
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")
    seen = {}

    class Response:
        status_code = 200

        def raise_for_status(self):
            pass

        def json(self):
            return {"choices": [{"message": {"content": "```摘要：甘道夫是迈雅。```"}}]}

    def fake_post(url, json, headers, timeout):
        seen.update(url=url, payload=json, headers=headers)
        return Response()

    monkeypatch.setattr("rag.summarize.httpx.post", fake_post)
    summary = summarize_with_llm("霍比特人", ["托尔金"], "甘道夫是迈雅。")

    assert summary == "甘道夫是迈雅。"
    assert seen["url"] == "https://api.deepseek.com/chat/completions"
    assert seen["payload"]["model"] == "deepseek-flash"
    assert seen["payload"]["thinking"] == {"type": "disabled"}
    assert seen["payload"]["temperature"] == 0.3
    assert seen["payload"]["max_tokens"] >= 512
    assert seen["headers"]["Authorization"] == "Bearer test-key"
    assert "甘道夫是迈雅。" in seen["payload"]["messages"][1]["content"]


def test_request_summary_returns_token_usage(monkeypatch):
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")

    class Response:
        status_code = 200

        def raise_for_status(self):
            pass

        def json(self):
            return {
                "choices": [{"message": {"content": "摘要"}}],
                "usage": {
                    "prompt_tokens": 10,
                    "completion_tokens": 5,
                    "total_tokens": 15,
                },
            }

    monkeypatch.setattr(
        "rag.summarize.httpx.post", lambda *args, **kwargs: Response()
    )
    summary, usage = _request_summary("霍比特人", [], "甘道夫是迈雅。")

    assert summary == "摘要"
    assert usage["total_tokens"] == 15


def test_summarize_with_llm_returns_none_on_failure(monkeypatch):
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")
    monkeypatch.setattr("rag.summarize.time.sleep", lambda _: None)
    monkeypatch.setattr(
        "rag.summarize.httpx.post",
        lambda *args, **kwargs: (_ for _ in ()).throw(RuntimeError("down")),
    )
    assert summarize_with_llm("霍比特人", [], "甘道夫是迈雅。") is None


def test_generate_summaries_uses_llm(monkeypatch):
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")
    books = [
        _fake_book(title_cn=f"书{i}", introduction=_LONG_INTRODUCTION)
        for i in range(3)
    ]
    monkeypatch.setattr(
        "rag.pipeline.summarize_with_llm",
        lambda title, authors, intro, max_chars=None: f"LLM:{title}",
    )

    assert _generate_summaries(books, None) == 3
    assert [b.summary for b in books] == ["LLM:书0", "LLM:书1", "LLM:书2"]


def test_generate_summaries_leaves_failures_empty_for_retry(monkeypatch):
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")
    books = [
        _fake_book(title_cn=f"书{i}", introduction=_LONG_INTRODUCTION)
        for i in range(3)
    ]
    calls = []

    def failing_llm(title, authors, intro, max_chars=None):
        calls.append(title)
        return None

    monkeypatch.setattr("rag.pipeline.summarize_with_llm", failing_llm)

    assert _generate_summaries(books, None) == 0
    assert calls == ["书0"]
    assert all(b.summary is None for b in books)


def test_reindex_generates_summary_and_embeds_it(db, monkeypatch):
    monkeypatch.delenv("DEEPSEEK_API_KEY", raising=False)
    introduction = _LONG_INTRODUCTION
    book = Book(title="Test", title_cn="测试", introduction=introduction)
    db.add(book)
    db.commit()
    db.refresh(book)

    seen = {}

    def fake_embed(texts, batch_size=32):
        seen["texts"] = list(texts)
        return [[0.0] * 512 for _ in texts]

    monkeypatch.setattr("rag.embedding.embed_texts", fake_embed)
    result = reindex_all(db)

    db.refresh(book)
    assert result["indexed"] == 1
    assert book.summary
    assert len(book.summary) <= 500
    assert book.summary != introduction
    assert "摘要:" in seen["texts"][0]
    assert db.execute(text("SELECT count(*) FROM book_vectors")).scalar() == 1


def test_fts_replace_drops_old_terms(rag_db):
    upsert_book_fts(rag_db, 1, title="alpha beta")
    assert [r["book_id"] for r in search_fts(rag_db, "alpha")] == [1]

    upsert_book_fts(rag_db, 1, title="gamma")
    assert search_fts(rag_db, "alpha") == []
    assert [r["book_id"] for r in search_fts(rag_db, "gamma")] == [1]


def test_fts_delete_removes_row(rag_db):
    upsert_book_fts(rag_db, 1, title="alpha")
    delete_book_vector(rag_db, 1)
    assert search_fts(rag_db, "alpha") == []


def test_fts_returns_best_match_first(rag_db):
    upsert_book_fts(rag_db, 1, title="alpha")
    upsert_book_fts(rag_db, 2, title="alpha alpha alpha alpha alpha")
    assert [r["book_id"] for r in search_fts(rag_db, "alpha")][:2] == [2, 1]


def test_fts_tokenizer_never_emits_user_quotes():
    query = _fts_tokenize('foo"bar')
    assert '"foo"bar"' not in query
    assert _fts_tokenize("!!!") == ""


def test_fts_tokenizer_keeps_short_cjk_phrase():
    assert '"甘 道 夫"' in _fts_tokenize("甘道夫出自哪本书")


def test_fts_tokenizer_strips_question_suffix():
    assert _fts_tokenize("甘道夫出自哪本书？") == '"甘 道 夫"'
    assert _fts_tokenize("魔戒是谁写的") == '"魔 戒"'


def test_fts_tokenizer_marks_exact_and_natural_queries():
    assert " OR " not in _fts_tokenize("甘道夫出自哪本书？")
    assert " OR " in _fts_tokenize("适合儿童阅读的绘本")


def test_fts_matches_cjk_inside_sentence(rag_db):
    upsert_book_fts(
        rag_db,
        1,
        introduction="清晨和煦，睿智巫师甘道夫的到来打破了宁静。",
    )
    assert [r["book_id"] for r in search_fts(rag_db, "甘道夫")] == [1]


def test_fts_matches_two_char_cjk_title(rag_db):
    upsert_book_fts(rag_db, 1, title="魔戒")
    assert [r["book_id"] for r in search_fts(rag_db, "魔戒")] == [1]


def test_hybrid_keeps_strong_fts_match(rag_db, monkeypatch):
    import rag.vector_store as vs

    monkeypatch.setattr(
        vs,
        "search_vectors",
        lambda *a, **k: [{"book_id": i, "score": 0.9} for i in range(100, 120)],
    )
    monkeypatch.setattr(
        vs,
        "search_fts",
        lambda *a, **k: [{"book_id": 1, "score": 10.0}],
    )
    ids = [r["book_id"] for r in vs.hybrid_search(rag_db, "query", top_k=5)]
    assert 1 in ids


def test_hybrid_gate_excludes_vector_only_when_fts_has_enough(rag_db, monkeypatch):
    import rag.vector_store as vs

    monkeypatch.setattr(
        vs,
        "search_vectors",
        lambda *a, **k: [{"book_id": i, "score": 0.9} for i in range(100, 120)],
    )
    monkeypatch.setattr(
        vs,
        "search_fts",
        lambda *a, **k: [{"book_id": i, "score": 10.0} for i in range(1, 21)],
    )
    ids = [
        r["book_id"]
        for r in vs.hybrid_search(rag_db, "query", top_k=10, min_fts_results=10)
    ]
    assert ids == list(range(1, 11))


def test_hybrid_gate_keeps_vector_fill_when_fts_is_sparse(rag_db, monkeypatch):
    import rag.vector_store as vs

    monkeypatch.setattr(
        vs,
        "search_vectors",
        lambda *a, **k: [{"book_id": i, "score": 0.9} for i in range(100, 120)],
    )
    monkeypatch.setattr(
        vs,
        "search_fts",
        lambda *a, **k: [{"book_id": i, "score": 10.0} for i in range(1, 4)],
    )
    ids = [
        r["book_id"]
        for r in vs.hybrid_search(rag_db, "query", top_k=10, min_fts_results=10)
    ]
    assert 100 in ids


def test_diversify_results_collapses_editions_and_prefers_canonical():
    items = [
        RagSearchResultItem(
            book_id=1, score=0.9, title="The Hobbit", title_cn="霍比特人", authors=["托尔金"]
        ),
        RagSearchResultItem(
            book_id=2, score=0.8, title="The Hobbit", title_cn="霍比特人", authors=["托尔金"]
        ),
        RagSearchResultItem(
            book_id=3,
            score=0.7,
            title="The Lord of the Rings Boxed Set",
            title_cn="魔戒60周年套装",
            authors=["托尔金"],
        ),
        RagSearchResultItem(
            book_id=4,
            score=0.6,
            title="The Lord of the Rings",
            title_cn="魔戒",
            authors=["托尔金"],
        ),
        RagSearchResultItem(
            book_id=5,
            score=0.5,
            title="The Silmarillion",
            title_cn="精灵宝钻",
            authors=["托尔金"],
        ),
    ]

    assert [r.book_id for r in _diversify_results(items, 10)] == [1, 4, 5]


def test_diversify_results_keeps_same_title_from_different_authors():
    items = [
        RagSearchResultItem(
            book_id=1, score=0.9, title="中国通史", title_cn="中国通史", authors=["甲"]
        ),
        RagSearchResultItem(
            book_id=2, score=0.8, title="中国通史", title_cn="中国通史", authors=["乙"]
        ),
    ]

    assert [r.book_id for r in _diversify_results(items, 10)] == [1, 2]


def test_diversify_results_keeps_distinct_volumes():
    items = [
        RagSearchResultItem(
            book_id=1,
            score=0.9,
            title="Cambridge History of China, Vol. 1",
            title_cn="剑桥中华民国史（上卷）",
            authors=["费正清"],
        ),
        RagSearchResultItem(
            book_id=2,
            score=0.8,
            title="Cambridge History of China, Vol. 2",
            title_cn="剑桥中华民国史（下卷）",
            authors=["费正清"],
        ),
    ]

    assert [r.book_id for r in _diversify_results(items, 10)] == [1, 2]


def test_rag_search_gates_vector_only_for_exact_queries(client, db, monkeypatch):
    import routers.rag as rag_router

    book = Book(title="Test", title_cn="测试")
    db.add(book)
    db.commit()
    db.refresh(book)
    upsert_book_fts(db, book.id, title="测试")

    seen = {}

    def fake_hybrid(*args, **kwargs):
        seen["min_fts_results"] = kwargs.get("min_fts_results")
        return []

    monkeypatch.setattr(rag_router, "hybrid_search", fake_hybrid)

    client.post("/api/rag/search", json={"query": "甘道夫出自哪本书？"})
    assert seen["min_fts_results"] == 10

    client.post("/api/rag/search", json={"query": "适合儿童阅读的绘本"})
    assert seen["min_fts_results"] == 0


def test_alpha_zero_uses_fts_only(rag_db):
    upsert_book_fts(rag_db, 1, title="三体 刘慈欣")
    assert [r["book_id"] for r in hybrid_search(rag_db, "三体", alpha=0.0)] == [1]


def test_vector_failure_falls_back_to_fts(rag_db, monkeypatch):
    import rag.vector_store as vs

    upsert_book_fts(rag_db, 1, title="alpha")

    def boom(*args, **kwargs):
        raise RuntimeError("embedding model unavailable")

    monkeypatch.setattr(vs, "search_vectors", boom)
    assert [r["book_id"] for r in vs.hybrid_search(rag_db, "alpha")] == [1]


def test_book_crud_keeps_fts_in_sync(client, db):
    payload = {
        "isbn": "978-7-01-000099-9",
        "title_cn": "同步测试",
        "title": "Sync Test",
        "author_ids": [],
    }
    resp = client.post("/api/books/", json=payload)
    assert resp.status_code == 200, resp.text
    book_id = resp.json()["id"]
    assert [r["book_id"] for r in search_fts(db, "Sync")] == [book_id]

    resp = client.put(f"/api/books/{book_id}", json={"title": "Changed Title"})
    assert resp.status_code == 200, resp.text
    assert search_fts(db, "Sync") == []
    assert [r["book_id"] for r in search_fts(db, "Changed")] == [book_id]

    resp = client.delete(f"/api/books/{book_id}")
    assert resp.status_code == 200, resp.text
    assert search_fts(db, "Changed") == []


def test_ensure_sets_fts_encoding_version(rag_db):
    rag_db.execute(text("DELETE FROM rag_meta"))
    rag_db.commit()
    ensure_rag_tables(rag_db)
    version = rag_db.execute(
        text("SELECT value FROM rag_meta WHERE key='fts_tokenizer'")
    ).scalar()
    assert version == "cjk_spaced_v1"


def test_ensure_rebuilds_contentless_fts(rag_db):
    rag_db.execute(text("DROP TABLE book_fts"))
    rag_db.execute(
        text(
            "CREATE VIRTUAL TABLE book_fts USING fts5("
            "book_id UNINDEXED, title, content='', prefix='2 3')"
        )
    )
    rag_db.commit()

    ensure_rag_tables(rag_db)

    sql = rag_db.execute(
        text("SELECT sql FROM sqlite_master WHERE type='table' AND name='book_fts'")
    ).scalar()
    assert "content=''" not in sql.replace(" ", "").replace("\n", "")

    upsert_book_fts(rag_db, 1, title="alpha")
    delete_book_vector(rag_db, 1)
    assert search_fts(rag_db, "alpha") == []


def test_fts_tokenizer_keeps_extension_a_cjk():
    """U+433D is indexed by _space_cjk, so the query tokenizer must keep it."""
    assert _fts_tokenize("䌽") == '"䌽"'


def test_fts_finds_extension_a_character(rag_db):
    upsert_book_fts(rag_db, 1, title_cn="宋世䌽堂本河东先生集")
    assert [r["book_id"] for r in search_fts(rag_db, "䌽")] == [1]


def test_generate_book_summary_skips_short_introduction(monkeypatch):
    """A short introduction is embedded as-is; no summary is generated."""
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")
    calls = []
    monkeypatch.setattr(
        "rag.pipeline.summarize_with_llm",
        lambda *args, **kwargs: calls.append(args) or "摘要",
    )
    book = _fake_book(introduction="甘道夫是迈雅。")

    assert generate_book_summary(book) == (None, "short_introduction")
    assert calls == []


def test_generate_book_summary_force_summarizes_short_introduction(monkeypatch):
    """force=true opts back in for a deliberately requested summary."""
    monkeypatch.delenv("DEEPSEEK_API_KEY", raising=False)
    book = _fake_book(introduction="很短的简介。")

    summary, source = generate_book_summary(book, force=True)

    assert source == "local"
    assert summary == "很短的简介。"


def test_reindex_skips_summary_for_short_introduction(db, monkeypatch):
    monkeypatch.delenv("DEEPSEEK_API_KEY", raising=False)
    texts = []

    def fake_embed(texts_batch, batch_size=32):
        texts.extend(texts_batch)
        return [[0.0] * 512 for _ in texts_batch]

    monkeypatch.setattr("rag.embedding.embed_texts", fake_embed)
    book = Book(title="短简介", title_cn="短简介", introduction="甘道夫是迈雅。")
    db.add(book)
    db.commit()
    db.refresh(book)

    result = reindex_all(db)

    assert result["indexed"] == 1
    db.refresh(book)
    assert not book.summary
    assert "简介: 甘道夫是迈雅。" in texts[0]
    assert "摘要:" not in texts[0]
    # Nothing left to summarize, so incremental must not keep retrying it.
    assert reindex_all(db, incremental=True) == {
        "total": 0,
        "indexed": 0,
        "failed": 0,
    }


def test_embedding_document_clips_summary_to_fit_window():
    """Header + summary must fit the 512-token window; the cap is per book."""
    book = _fake_book(summary="摘要内容。" * 200)  # 1000 chars, above budget

    doc = build_book_embedding_document(book)

    assert len(doc) <= EMBEDDING_WINDOW_CHARS
    assert doc.startswith("书名: 霍比特人")
    assert "摘要: " in doc


def test_summary_budget_shrinks_with_a_longer_header():
    thin = _fake_book(tags=["奇幻"])
    wide = _fake_book(tags=["奇幻"] * 30)

    assert summary_char_budget(wide) < summary_char_budget(thin)
    assert summary_char_budget(thin) <= EMBEDDING_WINDOW_CHARS - 16


def test_generate_book_summary_fits_embedding_budget(monkeypatch):
    monkeypatch.delenv("DEEPSEEK_API_KEY", raising=False)
    book = _fake_book(introduction="甘道夫是迈雅，也是霍比特人的朋友。" * 80)

    summary, source = generate_book_summary(book)

    assert source == "local"
    assert summary
    assert len(summary) <= summary_char_budget(book)


def test_summary_threshold_leaves_embedding_headroom():
    """Below the threshold the full document is embedded, so it must fit."""
    assert not _introduction_needs_summary(_fake_book(introduction="简" * 399))
    assert _introduction_needs_summary(_fake_book(introduction="简" * 400))
    assert MIN_SUMMARY_INTRODUCTION_CHARS == 400

    book = _fake_book(
        introduction="甘" * MIN_SUMMARY_INTRODUCTION_CHARS,
        publisher=SimpleNamespace(name="上海译文出版社"),
        category=SimpleNamespace(path="文学/小说", name="小说"),
        tags=["奇幻", "儿童文学", "英国文学"],
    )
    assert len(build_book_document(book)) <= EMBEDDING_WINDOW_CHARS


def test_embedding_fingerprint_recorded_for_fresh_index(rag_db):
    stored = stored_embedding_fingerprint(rag_db)

    assert stored
    assert stored == current_embedding_fingerprint(rag_db)
    assert embedding_fingerprint_status(rag_db) == (stored, True)


def test_embedding_fingerprint_mismatch_is_flagged(rag_db, caplog):
    rag_db.execute(
        text("UPDATE rag_meta SET value = 'other-model|dim512' "
             "WHERE key = 'embedding_fingerprint'")
    )
    rag_db.commit()

    with caplog.at_level(logging.WARNING, logger="rag.vector_store"):
        ensure_rag_tables(rag_db)

    assert embedding_fingerprint_status(rag_db)[1] is False
    assert "full reindex" in caplog.text


def test_legacy_vectors_without_fingerprint_are_not_blessed(rag_db):
    rag_db.execute(text("DELETE FROM rag_meta WHERE key = 'embedding_fingerprint'"))
    upsert_book_vector(rag_db, 1, [0.0] * 512)
    rag_db.commit()

    ensure_rag_tables(rag_db)

    assert stored_embedding_fingerprint(rag_db) == "unknown"
    assert embedding_fingerprint_status(rag_db)[1] is False


def test_reindex_records_fingerprint_after_full_rebuild(db, monkeypatch):
    monkeypatch.delenv("DEEPSEEK_API_KEY", raising=False)
    monkeypatch.setattr(
        "rag.embedding.embed_texts",
        lambda texts, batch_size=32: [[0.0] * 512 for _ in texts],
    )
    db.execute(
        text("INSERT OR REPLACE INTO rag_meta (key, value) "
             "VALUES ('embedding_fingerprint', 'unknown')")
    )
    db.commit()
    db.add(Book(title="Test", title_cn="测试", introduction="简介"))
    db.commit()

    assert reindex_all(db)["failed"] == 0

    assert stored_embedding_fingerprint(db) == current_embedding_fingerprint(db)


def test_rag_status_reports_the_index_model(client, db):
    resp = client.get("/api/rag/status")

    assert resp.status_code == 200, resp.text
    body = resp.json()
    assert body["index_model"] == current_embedding_fingerprint(db)
    assert body["index_model_matches"] is True


def test_normalize_query_strips_question_scaffolding():
    assert normalize_query("有哪些讲唐史的书") == "唐史"
    assert normalize_query("三国演义讲了什么") == "三国演义"
    assert normalize_query("《老人与海》这本书怎么样") == "老人与海"
    assert normalize_query("刘慈欣的书") == "刘慈欣"
    assert normalize_query("关于人工智能的书") == "人工智能"


def test_normalize_query_keeps_titles_that_are_not_questions():
    assert normalize_query("旧唐书") == "旧唐书"
    assert normalize_query("讲谈社·中国的历史") == "讲谈社·中国的历史"
    assert normalize_query("三体") == "三体"
