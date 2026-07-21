"""Index sync pipeline — keeps RAG vectors + FTS in sync with book CRUD.

Call ``sync_book()`` after create/update, ``remove_book()`` before delete.
All operations are isolated: failures in indexing do NOT affect the
database transaction (errors are logged and swallowed).

Background reindex: use ``start_reindex(db_path)`` to begin a full reindex
in a daemon thread, then poll ``get_reindex_state()`` for progress.
"""

import logging
import threading
from dataclasses import dataclass
from typing import Optional

from sqlalchemy.orm import Session, selectinload

from . import build_book_document, embed_text, upsert_book_vector, upsert_book_fts, delete_book_vector
from .document import build_book_fts_fields

logger = logging.getLogger(__name__)

# ── Background reindex state ──────────────────────────────────────────────────


@dataclass
class ReindexState:
    """Mutable progress tracker for the background reindex task."""
    status: str = "idle"       # idle | indexing | completed | error
    total: int = 0
    indexed: int = 0
    failed: int = 0
    detail: str = ""


_reindex_state = ReindexState()
_reindex_lock = threading.Lock()


def _update_progress(progress: Optional[ReindexState], **kwargs):
    """Thread-safe progress update if progress tracker is provided."""
    if progress is None:
        return
    with _reindex_lock:
        for k, v in kwargs.items():
            setattr(progress, k, v)


def _set_state(**kwargs):
    with _reindex_lock:
        for k, v in kwargs.items():
            setattr(_reindex_state, k, v)


def get_reindex_state() -> ReindexState:
    """Return a snapshot of the current reindex progress."""
    with _reindex_lock:
        return ReindexState(
            status=_reindex_state.status,
            total=_reindex_state.total,
            indexed=_reindex_state.indexed,
            failed=_reindex_state.failed,
            detail=_reindex_state.detail,
        )


def start_reindex(db_path: str):
    """Launch a full reindex in a background daemon thread.

    Args:
        db_path: Absolute path to the SQLite database file.
    """
    if _reindex_state.status == "indexing":
        logger.warning("reindex already in progress — ignoring duplicate start")
        return

    _set_state(status="idle", total=0, indexed=0, failed=0, detail="")

    t = threading.Thread(target=_run_reindex, args=(db_path,), daemon=True)
    t.start()


def _run_reindex(db_path: str):
    """Background runner: own session, tables, progress updates."""
    from database import _create_sqlite_engine
    from sqlalchemy.orm import sessionmaker

    engine = _create_sqlite_engine(db_path)
    SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

    # Ensure RAG tables exist before the session is handed to reindex_all
    from .vector_store import ensure_rag_tables
    tmp_session = SessionLocal()
    try:
        ensure_rag_tables(tmp_session)
    finally:
        tmp_session.close()

    _set_state(status="indexing")

    try:
        db = SessionLocal()
        try:
            result = reindex_all(db, progress=_reindex_state)
            _set_state(
                status="completed",
                total=result["total"],
                indexed=result["indexed"],
                failed=result["failed"],
                detail=result.get("detail", ""),
            )
        finally:
            db.close()
    except Exception as exc:
        logger.exception("background reindex failed")
        _set_state(status="error", detail=str(exc))


# ── Per-book CRUD ─────────────────────────────────────────────────────────────


def _load_book(db: Session, book_id: int):
    """Re-query a book with all relations needed for document building."""
    from models import Book

    return (
        db.query(Book)
        .options(
            selectinload(Book.authors),
            selectinload(Book.publisher),
            selectinload(Book.brand),
            selectinload(Book.book_series),
            selectinload(Book.category),
        )
        .filter(Book.id == book_id)
        .first()
    )


def sync_book(db: Session, book_id: int) -> bool:
    """Re-index a single book: rebuild document, embed, upsert vector + FTS.

    Args:
        db: Active database session.
        book_id: ID of the book to index.

    Returns:
        True if indexed successfully, False on failure (logged).
    """
    try:
        book = _load_book(db, book_id)
        if book is None:
            logger.warning("sync_book: book %s not found, skipping", book_id)
            return False

        document = build_book_document(book)
        vector = embed_text(document)
        upsert_book_vector(db, book.id, vector)

        fts_fields = build_book_fts_fields(book)
        upsert_book_fts(db, book.id, **fts_fields)
        return True
    except Exception:
        logger.exception("sync_book failed for book %s", book_id)
        return False


def remove_book(db: Session, book_id: int) -> bool:
    """Remove a book from vector and FTS indexes.

    Args:
        db: Active database session.
        book_id: ID of the book to remove.

    Returns:
        True on success, False on failure (logged).
    """
    try:
        delete_book_vector(db, book_id)
        return True
    except Exception:
        logger.exception("remove_book failed for book %s", book_id)
        return False


# ── Full reindex ──────────────────────────────────────────────────────────────


def reindex_all(db: Session, batch_size: int = 32, progress: Optional[ReindexState] = None) -> dict:
    """Rebuild the entire RAG index for the current user's database.

    Uses batched ONNX inference (batch_size=32) for ~10× speedup over
    per-book embedding.  Falls back gracefully on individual failures.

    Args:
        db: Active database session.
        batch_size: ONNX batch size for embedding (default 32).
        progress: Optional ReindexState to update during execution.

    Returns:
        Dict with total/indexed/failed counts.
    """
    from models import Book

    books = (
        db.query(Book)
        .options(
            selectinload(Book.authors),
            selectinload(Book.publisher),
            selectinload(Book.brand),
            selectinload(Book.book_series),
            selectinload(Book.category),
        )
        .all()
    )

    total = len(books)
    indexed = 0
    failed = 0
    _update_progress(progress, indexed=0, failed=0, total=total, detail="Building documents...")

    _indexed: list[int] = []
    _documents: list[str] = []
    _fts_fields: list[tuple[int, dict]] = []

    # Phase 1: build all documents (fast, no inference)
    for book in books:
        try:
            doc = build_book_document(book)
            fts = build_book_fts_fields(book)
            _indexed.append(book.id)
            _documents.append(doc)
            _fts_fields.append((book.id, fts))
        except Exception:
            logger.exception("build_document failed for book %s", book.id)
            failed += 1
            _update_progress(progress, failed=failed)

    _update_progress(progress, detail=f"Embedding {len(_documents)} documents...")

    # Phase 2: batch embeddings
    from .embedding import embed_texts

    vectors: list = []
    _embed_error = ""
    try:
        vectors = embed_texts(_documents, batch_size=batch_size)
    except Exception as exc:
        _embed_error = str(exc)
        logger.exception("batch embedding failed: %s", _embed_error)

    if len(vectors) != len(_indexed):
        msg = f"embedding count mismatch: {len(vectors)} vectors vs {len(_indexed)} books"
        if _embed_error:
            msg += f"; error: {_embed_error}"
        logger.error(msg)
        failed += len(_indexed) - len(vectors)
        min_len = min(len(vectors), len(_indexed))
        _indexed = _indexed[:min_len]
        _fts_fields = _fts_fields[:min_len]
        vectors = vectors[:min_len]
        if not _indexed and _embed_error:
            _update_progress(progress, failed=total, detail=_embed_error)
            return {"total": total, "indexed": 0, "failed": total, "detail": _embed_error}

    _update_progress(progress, indexed=0, total=total, detail="Writing to database...")

    # Phase 3: bulk upsert vectors + FTS
    for i, book_id in enumerate(_indexed):
        try:
            upsert_book_vector(db, book_id, vectors[i])
            upsert_book_fts(db, book_id, **_fts_fields[i][1])
            indexed += 1
        except Exception:
            logger.exception("upsert failed for book %s", book_id)
            failed += 1
        _update_progress(progress, indexed=indexed, failed=failed)

    return {"total": total, "indexed": indexed, "failed": failed}
