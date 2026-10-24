"""Incremental reindex tests."""

from sqlalchemy import text

from models import Book
from rag.pipeline import reindex_all, sync_book


def _book(db, title_cn: str, introduction: str = "简介"):
    book = Book(title=title_cn, title_cn=title_cn, introduction=introduction)
    db.add(book)
    db.commit()
    db.refresh(book)
    return book


def _fake_embeddings(monkeypatch, calls):
    def fake_embed_texts(texts, batch_size=32):
        calls.extend(texts)
        return [[0.0] * 512 for _ in texts]

    monkeypatch.setattr("rag.embedding.embed_texts", fake_embed_texts)
    monkeypatch.setattr("rag.embedding.embed_text", lambda text: [0.0] * 512)


def test_incremental_skips_unchanged_books(db, monkeypatch):
    monkeypatch.delenv("DEEPSEEK_API_KEY", raising=False)
    calls = []
    _fake_embeddings(monkeypatch, calls)
    _book(db, "甲")
    _book(db, "乙")

    assert reindex_all(db)["total"] == 2

    calls.clear()
    result = reindex_all(db, incremental=True)

    assert result == {"total": 0, "indexed": 0, "failed": 0}
    assert calls == []


def test_incremental_indexes_only_new_book(db, monkeypatch):
    monkeypatch.delenv("DEEPSEEK_API_KEY", raising=False)
    calls = []
    _fake_embeddings(monkeypatch, calls)
    _book(db, "甲")
    assert reindex_all(db)["total"] == 1

    _book(db, "乙")
    calls.clear()
    result = reindex_all(db, incremental=True)

    assert result["total"] == 1
    assert result["indexed"] == 1
    assert len(calls) == 1
    assert "乙" in calls[0]


def test_incremental_picks_up_book_indexed_by_sync_book(db, monkeypatch):
    monkeypatch.delenv("DEEPSEEK_API_KEY", raising=False)
    monkeypatch.setattr("rag.pipeline.is_model_loaded", lambda: True)
    monkeypatch.setattr("rag.pipeline.embed_text", lambda text: [0.0] * 512)
    book = _book(db, "新书", introduction="甘道夫是迈雅，也是霍比特人的朋友。" * 40)

    assert sync_book(db, book.id) is True
    assert db.execute(text("SELECT count(*) FROM book_vectors")).scalar() == 1
    state = db.execute(
        text("SELECT summary_done FROM rag_book_state WHERE book_id=:id"),
        {"id": book.id},
    ).scalar()
    assert state == 0

    calls = []
    _fake_embeddings(monkeypatch, calls)
    result = reindex_all(db, incremental=True)

    assert result["total"] == 1
    assert result["indexed"] == 1
    assert len(calls) == 1
    db.refresh(book)
    assert book.summary


def test_incremental_picks_up_changed_summary(db, monkeypatch):
    monkeypatch.delenv("DEEPSEEK_API_KEY", raising=False)
    calls = []
    _fake_embeddings(monkeypatch, calls)
    book = _book(db, "甲")
    assert reindex_all(db)["total"] == 1

    book.summary = "新的摘要"
    db.commit()
    calls.clear()
    result = reindex_all(db, incremental=True)

    assert result["total"] == 1
    assert len(calls) == 1
    assert "新的摘要" in calls[0]


def test_rag_reindex_incremental_flag(client, monkeypatch):
    import routers.rag as rag_router

    seen = {}

    def fake_start(path, incremental=False):
        seen.update(path=path, incremental=incremental)
        return True

    monkeypatch.setattr(rag_router, "start_reindex", fake_start)

    resp = client.post("/api/rag/reindex?incremental=true")

    assert resp.status_code == 200, resp.text
    assert seen["incremental"] is True
    assert "Incremental" in resp.json()["message"]


# ── Concurrency + failure-mode regressions ───────────────────────────────────
#
# These use a file-backed database so two sessions really are two SQLite
# connections; the in-memory ``db`` fixture shares one connection.

import time

import pytest
from sqlalchemy.orm import sessionmaker

from database import Base, _create_sqlite_engine
from rag.pipeline import _book_version_changed, get_reindex_state, start_reindex
from rag.vector_store import ensure_rag_tables


@pytest.fixture
def file_db(tmp_path):
    """File-backed engine whose connections time out fast when locked."""
    engine = _create_sqlite_engine(
        str(tmp_path / "rag.db"),
        connect_args={"check_same_thread": False, "timeout": 0.5},
    )
    Base.metadata.create_all(engine)
    SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
    session = SessionLocal()
    ensure_rag_tables(session)
    try:
        yield session, SessionLocal
    finally:
        session.close()
        engine.dispose()


def _summary_state(db, book_id):
    return db.execute(
        text("SELECT summary_done FROM rag_book_state WHERE book_id=:id"),
        {"id": book_id},
    ).scalar()


def _wait_for_status(db_path: str, expected: str, timeout: float = 5.0):
    deadline = time.time() + timeout
    while time.time() < deadline:
        state = get_reindex_state(db_path)
        if state.status == expected:
            return state
        time.sleep(0.01)
    raise AssertionError(
        f"reindex never reached {expected!r}: {get_reindex_state(db_path)}"
    )


def _insert_from_other_session(SessionLocal, title: str):
    """What a UI request does: its own connection while the reindex runs."""
    other = SessionLocal()
    try:
        other.add(Book(title=title, title_cn=title, introduction="简介"))
        other.commit()
    finally:
        other.close()


def _assert_no_open_sqlite_transaction(db):
    """The reindex must not hold a transaction while DeepSeek/ONNX runs."""
    dbapi = db.connection().connection.dbapi_connection
    assert dbapi.in_transaction is False, (
        "reindex still holds a SQLite transaction during the slow phase"
    )


def test_reindex_holds_no_transaction_while_summarizing(file_db, monkeypatch):
    """DeepSeek can take minutes; no lock may be held for its duration."""
    db, SessionLocal = file_db
    db.add(
        Book(
            title="甲",
            title_cn="甲",
            introduction="甘道夫是迈雅，也是霍比特人的朋友。" * 40,
        )
    )
    db.commit()

    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")
    inserted = {}

    def summarize(title, authors, intro, max_chars=None):
        _assert_no_open_sqlite_transaction(db)
        _insert_from_other_session(SessionLocal, "乙")
        inserted["ok"] = True
        return f"LLM:{title}"

    monkeypatch.setattr("rag.pipeline.summarize_with_llm", summarize)
    monkeypatch.setattr(
        "rag.embedding.embed_texts",
        lambda texts, batch_size=32: [[0.0] * 512 for _ in texts],
    )
    monkeypatch.setattr("rag.embedding.embed_text", lambda text: [0.0] * 512)

    result = reindex_all(db)

    assert inserted["ok"] is True
    assert result["indexed"] == 1
    assert db.execute(text("SELECT count(*) FROM books")).scalar() == 2


def test_reindex_holds_no_transaction_while_embedding(file_db, monkeypatch):
    """ONNX inference must not hold the write lock either."""
    db, SessionLocal = file_db
    db.add(Book(title="甲", title_cn="甲", introduction=""))
    db.commit()

    monkeypatch.delenv("DEEPSEEK_API_KEY", raising=False)
    inserted = {}

    def embed(texts, batch_size=32):
        _assert_no_open_sqlite_transaction(db)
        _insert_from_other_session(SessionLocal, "乙")
        inserted["ok"] = True
        return [[0.0] * 512 for _ in texts]

    monkeypatch.setattr("rag.embedding.embed_texts", embed)
    monkeypatch.setattr("rag.embedding.embed_text", lambda text: [0.0] * 512)

    result = reindex_all(db)

    assert inserted["ok"] is True
    assert result["indexed"] == 1


def test_reindex_setup_failure_reports_error(tmp_path, monkeypatch):
    """A failing setup must end in ``error``, not a permanent ``indexing``."""
    db_path = str(tmp_path / "setup-failure.db")

    def boom(session):
        raise RuntimeError("ensure_rag_tables failed")

    monkeypatch.setattr("rag.vector_store.ensure_rag_tables", boom)

    assert start_reindex(db_path) is True
    try:
        state = _wait_for_status(db_path, "error")
        assert "ensure_rag_tables failed" in state.detail

        # The failed run must not reject the next attempt as "in progress".
        assert start_reindex(db_path) is True
        _wait_for_status(db_path, "error")
    finally:
        from rag.pipeline import _reindex_states

        _wait_for_status(db_path, "error")
        _reindex_states.pop(db_path, None)


def test_failed_embedding_drops_stale_vector_and_stays_pending(db, monkeypatch):
    """A book whose vector write failed must not look done to incremental."""
    monkeypatch.delenv("DEEPSEEK_API_KEY", raising=False)
    _fake_embeddings(monkeypatch, [])
    book = _book(db, "甲")
    assert reindex_all(db)["indexed"] == 1
    assert db.execute(text("SELECT count(*) FROM book_vectors")).scalar() == 1
    assert _summary_state(db, book.id) == 1

    def down(texts, batch_size=32):
        raise RuntimeError("onnx down")

    monkeypatch.setattr("rag.embedding.embed_texts", down)
    monkeypatch.setattr(
        "rag.embedding.embed_text",
        lambda text: (_ for _ in ()).throw(RuntimeError("onnx down")),
    )
    result = reindex_all(db)

    assert result["failed"] == 1
    assert db.execute(text("SELECT count(*) FROM book_vectors")).scalar() == 0
    assert _summary_state(db, book.id) != 1

    # Incremental retries it instead of reporting everything is indexed.
    _fake_embeddings(monkeypatch, [])
    result = reindex_all(db, incremental=True)
    assert result["total"] == 1
    assert result["indexed"] == 1


def test_sync_book_write_failure_keeps_book_pending(db, monkeypatch):
    """A failed sync_book write must leave the book for the next reindex."""
    import rag.pipeline as pipeline

    monkeypatch.setattr(pipeline, "is_model_loaded", lambda: True)
    monkeypatch.setattr(pipeline, "embed_text", lambda text: [0.0] * 512)
    book = _book(db, "甲", introduction="")
    assert sync_book(db, book.id) is True
    assert _summary_state(db, book.id) == 1

    real_upsert_fts = pipeline.upsert_book_fts

    def boom(*args, **kwargs):
        raise RuntimeError("fts write failed")

    monkeypatch.setattr(pipeline, "upsert_book_fts", boom)
    assert sync_book(db, book.id) is False
    assert _summary_state(db, book.id) is None

    monkeypatch.setattr(pipeline, "upsert_book_fts", real_upsert_fts)
    calls = []
    _fake_embeddings(monkeypatch, calls)
    result = reindex_all(db, incremental=True)
    assert result["total"] == 1
    assert result["indexed"] == 1


def test_edit_during_reindex_is_not_overwritten(file_db, monkeypatch):
    """Phase 3 must not write the pre-embedding snapshot over a fresh edit."""
    db, SessionLocal = file_db
    book = Book(title="旧标题", title_cn="旧标题", introduction="")
    db.add(book)
    db.commit()
    book_id = book.id

    monkeypatch.delenv("DEEPSEEK_API_KEY", raising=False)

    def edit_then_embed(texts, batch_size=32):
        other = SessionLocal()
        try:
            row = other.get(Book, book_id)
            row.title = "新标题"
            row.title_cn = "新标题"
            other.commit()
        finally:
            other.close()
        return [[0.0] * 512 for _ in texts]

    monkeypatch.setattr("rag.embedding.embed_texts", edit_then_embed)
    monkeypatch.setattr("rag.embedding.embed_text", lambda text: [0.0] * 512)

    result = reindex_all(db)

    # The stale snapshot was skipped, and no index row claims the old title.
    assert result["indexed"] == 0
    assert result["failed"] == 1
    assert (
        db.execute(
            text("SELECT count(*) FROM book_fts WHERE book_id=:id"),
            {"id": book_id},
        ).scalar()
        == 0
    )
    assert _summary_state(db, book_id) is None

    # The next incremental run indexes the new title.
    _fake_embeddings(monkeypatch, [])
    result = reindex_all(db, incremental=True)
    assert result["indexed"] == 1
    stored = db.execute(
        text("SELECT title_cn FROM book_fts WHERE book_id=:id"), {"id": book_id}
    ).scalar()
    assert stored.replace(" ", "") == "新标题"


def test_edit_during_summarization_is_not_overwritten(file_db, monkeypatch):
    """Documents are built from a re-read, not the pre-summary snapshot."""
    db, SessionLocal = file_db
    book = Book(
        title="旧标题",
        title_cn="旧标题",
        introduction="甘道夫是迈雅，也是霍比特人的朋友。" * 40,
    )
    db.add(book)
    db.commit()
    book_id = book.id

    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")

    def summarize(title, authors, intro, max_chars=None):
        other = SessionLocal()
        try:
            row = other.get(Book, book_id)
            row.title = "新标题"
            row.title_cn = "新标题"
            other.commit()
        finally:
            other.close()
        return "摘要"

    monkeypatch.setattr("rag.pipeline.summarize_with_llm", summarize)
    monkeypatch.setattr(
        "rag.embedding.embed_texts",
        lambda texts, batch_size=32: [[0.0] * 512 for _ in texts],
    )
    monkeypatch.setattr("rag.embedding.embed_text", lambda text: [0.0] * 512)

    result = reindex_all(db)

    assert result["indexed"] == 1
    stored = db.execute(
        text("SELECT title_cn FROM book_fts WHERE book_id=:id"), {"id": book_id}
    ).scalar()
    assert stored.replace(" ", "") == "新标题"


def test_version_check_only_treats_missing_rows_as_changed():
    """NULL updated_at is unchanged; a missing row (deleted) is changed."""
    assert _book_version_changed(None, "None") is True
    assert _book_version_changed((None,), "None") is False
    assert _book_version_changed(("2026-01-01 00:00:00",), "2026-01-01 00:00:00") is False
    assert _book_version_changed(("2026-01-02 00:00:00",), "2026-01-01 00:00:00") is True
