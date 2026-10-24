"""Standalone summary endpoint tests."""

from sqlalchemy import text

from models import Book

from rag.pipeline import MIN_SUMMARY_INTRODUCTION_CHARS

# Above MIN_SUMMARY_INTRODUCTION_CHARS (512) so the strategy actually runs.
_LONG_INTRODUCTION = "甘道夫是迈雅，也是霍比特人的朋友。" * 40


def _book(db, **overrides):
    fields = {
        "title": "Test",
        "title_cn": "测试",
        "introduction": _LONG_INTRODUCTION,
    }
    fields.update(overrides)
    book = Book(**fields)
    db.add(book)
    db.commit()
    db.refresh(book)
    return book


def test_rag_summarize_returns_existing_without_calling_llm(
    client, db, monkeypatch
):
    book = _book(db, summary="已有摘要")
    calls = []
    monkeypatch.setattr(
        "rag.pipeline.summarize_with_llm",
        lambda *args, **kwargs: calls.append(args) or "新摘要",
    )

    resp = client.post(f"/api/rag/summarize/{book.id}")

    assert resp.status_code == 200, resp.text
    assert resp.json() == {
        "book_id": book.id,
        "summary": "已有摘要",
        "source": "existing",
        "updated": False,
    }
    assert calls == []


def test_rag_summarize_local_fallback(client, db, monkeypatch):
    monkeypatch.delenv("DEEPSEEK_API_KEY", raising=False)
    book = _book(db)

    resp = client.post(f"/api/rag/summarize/{book.id}")

    assert resp.status_code == 200, resp.text
    body = resp.json()
    assert body["source"] == "local"
    assert body["updated"] is True
    assert body["summary"]
    db.refresh(book)
    assert book.summary == body["summary"]


def test_rag_summarize_deepseek_success(client, db, monkeypatch):
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")
    book = _book(db, introduction=_LONG_INTRODUCTION)
    seen = {}

    def fake_llm(title, authors, introduction, max_chars=None):
        seen.update(title=title, authors=authors, introduction=introduction)
        return "LLM 摘要"

    monkeypatch.setattr("rag.pipeline.summarize_with_llm", fake_llm)

    resp = client.post(f"/api/rag/summarize/{book.id}")

    assert resp.status_code == 200, resp.text
    body = resp.json()
    assert body["summary"] == "LLM 摘要"
    assert body["source"] == "deepseek"
    assert body["updated"] is True
    assert seen == {
        "title": "测试",
        "authors": [],
        "introduction": _LONG_INTRODUCTION,
    }
    db.refresh(book)
    assert book.summary == "LLM 摘要"


def test_rag_summarize_force_regenerates_existing(client, db, monkeypatch):
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")
    book = _book(db, summary="旧摘要")
    monkeypatch.setattr(
        "rag.pipeline.summarize_with_llm", lambda *args, **kwargs: "新摘要"
    )

    resp = client.post(f"/api/rag/summarize/{book.id}?force=true")

    assert resp.status_code == 200, resp.text
    assert resp.json()["summary"] == "新摘要"
    assert resp.json()["source"] == "deepseek"
    assert resp.json()["updated"] is True
    db.refresh(book)
    assert book.summary == "新摘要"


def test_rag_summarize_failure_keeps_existing(client, db, monkeypatch):
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")
    book = _book(db, summary="旧摘要")
    monkeypatch.setattr("rag.pipeline.summarize_with_llm", lambda *args, **kwargs: None)

    resp = client.post(f"/api/rag/summarize/{book.id}?force=true")

    assert resp.status_code == 502
    assert "left unchanged" in resp.json()["detail"]
    db.refresh(book)
    assert book.summary == "旧摘要"


def test_rag_summarize_missing_book(client):
    resp = client.post("/api/rag/summarize/999999")
    assert resp.status_code == 404


def test_rag_summarize_without_introduction(client, db):
    book = _book(db, introduction="   ")

    resp = client.post(f"/api/rag/summarize/{book.id}")

    assert resp.status_code == 400
    assert resp.json()["detail"] == "Book has no introduction"


def test_rag_summarize_marks_book_for_incremental_reindex(
    client, db, monkeypatch
):
    from rag.vector_store import upsert_book_state

    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")
    book = _book(db, summary="旧摘要")
    upsert_book_state(db, book.id, summary_done=True)
    monkeypatch.setattr(
        "rag.pipeline.summarize_with_llm", lambda *args, **kwargs: "新摘要"
    )

    resp = client.post(f"/api/rag/summarize/{book.id}?force=true")

    assert resp.status_code == 200, resp.text
    state = db.execute(
        text("SELECT summary_done FROM rag_book_state WHERE book_id=:id"),
        {"id": book.id},
    ).scalar()
    assert state is None


def test_rag_summarize_rejects_short_introduction(client, db, monkeypatch):
    """A short introduction is embedded as-is, so no summary is generated."""
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")
    calls = []
    monkeypatch.setattr(
        "rag.pipeline.summarize_with_llm",
        lambda *args, **kwargs: calls.append(args) or "摘要",
    )
    book = _book(db, introduction="甘道夫是迈雅。")

    resp = client.post(f"/api/rag/summarize/{book.id}")

    assert resp.status_code == 400, resp.text
    detail = resp.json()["detail"]
    assert detail.startswith(
        f"Introduction is shorter than {MIN_SUMMARY_INTRODUCTION_CHARS}"
    )
    assert "force=true" in detail
    assert calls == []
    db.refresh(book)
    assert not book.summary


def test_rag_summarize_force_generates_for_short_introduction(
    client, db, monkeypatch
):
    monkeypatch.delenv("DEEPSEEK_API_KEY", raising=False)
    book = _book(db, introduction="甘道夫是迈雅。")

    resp = client.post(f"/api/rag/summarize/{book.id}?force=true")

    assert resp.status_code == 200, resp.text
    body = resp.json()
    assert body["updated"] is True
    assert body["source"] == "local"
    assert body["summary"] == "甘道夫是迈雅。"
    db.refresh(book)
    assert book.summary == "甘道夫是迈雅。"
