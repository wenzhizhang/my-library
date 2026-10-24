"""Index sync pipeline — keeps RAG vectors + FTS in sync with book CRUD.

Call ``sync_book()`` after create/update, ``remove_book()`` after delete.
All operations are isolated: failures in indexing do NOT affect the
database transaction (errors are logged and swallowed).

Background reindex: use ``start_reindex(db_path)`` to begin a full reindex
in a daemon thread, then poll ``get_reindex_state()`` for progress.
"""

import logging
import os
import threading
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from typing import Optional

from sqlalchemy import text
from sqlalchemy.orm import Session, selectinload

from . import (
    delete_book_state,
    delete_book_vector,
    embed_text,
    is_model_loaded,
    prune_missing_books,
    record_embedding_fingerprint,
    upsert_book_fts,
    upsert_book_state,
    upsert_book_vector,
)
from .document import (
    build_book_embedding_document,
    build_book_fts_fields,
    summary_char_budget,
)
from .summarize import summarize_introduction, summarize_with_llm
from .vector_store import _space_cjk

logger = logging.getLogger(__name__)

_SUMMARY_WORKERS = 4

# A summary only pays for itself once the introduction no longer fits the
# embedding window (512 tokens), and the full-document fallback embeds the
# introduction next to the title/author/publisher/tag header.  400 leaves the
# ~112 characters that header needs, so the fallback is not silently cut by
# the tokenizer.  Below that the introduction itself is the best book
# representation: summarizing it would add a lossy copy and a pointless call.
MIN_SUMMARY_INTRODUCTION_CHARS = 400

# ── Background reindex state ──────────────────────────────────────────────────


@dataclass
class ReindexState:
    """Mutable progress tracker for the background reindex task."""
    status: str = "idle"       # idle | indexing | completed | error
    total: int = 0
    indexed: int = 0
    failed: int = 0
    detail: str = ""


# One progress tracker per database: a reindex of demo.db must not block or
# overwrite the status of a logged-in user's reindex.
_reindex_states: dict[str, ReindexState] = {}
_reindex_lock = threading.Lock()


def _update_progress(progress: Optional[ReindexState], **kwargs):
    """Thread-safe progress update if progress tracker is provided."""
    if progress is None:
        return
    with _reindex_lock:
        for k, v in kwargs.items():
            setattr(progress, k, v)


def get_reindex_state(db_path: Optional[str] = None) -> ReindexState:
    """Return a snapshot of the reindex progress for one database."""
    with _reindex_lock:
        state = _reindex_states.get(db_path) if db_path else None
        if state is None:
            return ReindexState()
        return ReindexState(
            status=state.status,
            total=state.total,
            indexed=state.indexed,
            failed=state.failed,
            detail=state.detail,
        )


def start_reindex(db_path: str, incremental: bool = False) -> bool:
    """Launch a reindex of ``db_path`` in a background daemon thread.

    ``incremental`` limits the run to new or pending books.  Returns False
    when that database is already being reindexed.
    """
    with _reindex_lock:
        state = _reindex_states.get(db_path)
        if state is None:
            state = ReindexState()
            _reindex_states[db_path] = state
        if state.status == "indexing":
            logger.warning("reindex already in progress for %s — ignoring", db_path)
            return False
        state.status = "indexing"
        state.total = 0
        state.indexed = 0
        state.failed = 0
        state.detail = ""

    t = threading.Thread(
        target=_run_reindex, args=(db_path, state, incremental), daemon=True
    )
    t.start()
    return True


def _run_reindex(db_path: str, state: ReindexState, incremental: bool = False):
    """Background runner: own session, tables, progress updates.

    The whole body is guarded: ``start_reindex`` has already flipped the
    status to ``indexing``, so a setup failure (engine, tables, ...) must still
    end in ``error``.  An escaping exception would leave the status stuck on
    ``indexing`` and reject every later reindex as "already in progress".
    """
    from database import _create_sqlite_engine
    from sqlalchemy.orm import sessionmaker

    engine = None
    try:
        engine = _create_sqlite_engine(db_path)
        SessionLocal = sessionmaker(
            autocommit=False, autoflush=False, bind=engine
        )

        # Ensure RAG tables exist before the session is handed to reindex_all
        from .vector_store import ensure_rag_tables

        with SessionLocal() as db:
            ensure_rag_tables(db)
            result = reindex_all(db, progress=state, incremental=incremental)

        _update_progress(
            state,
            status="completed",
            total=result["total"],
            indexed=result["indexed"],
            failed=result["failed"],
            detail=result.get("detail", ""),
        )
    except Exception as exc:
        logger.exception("background reindex failed")
        _update_progress(state, status="error", detail=str(exc))
    finally:
        if engine is not None:
            engine.dispose()


# ── Per-book CRUD ─────────────────────────────────────────────────────────────


def _summary_inputs(book) -> tuple[str, list[str], str]:
    title = book.title_cn or book.title or ""
    authors = [a.name_cn or a.name for a in book.authors or []]
    return title, authors, book.introduction or ""


def _introduction_needs_summary(book) -> bool:
    """True when the introduction is long enough to be worth summarizing."""
    introduction = (book.introduction or "").strip()
    return len(introduction) >= MIN_SUMMARY_INTRODUCTION_CHARS


def _summary_done(book) -> bool:
    """True when this book has a summary or has nothing to summarize.

    A missing or short introduction counts as done: the full document is
    embedded as-is, so there is nothing to generate (and nothing to retry).
    """
    return bool((book.summary or "").strip()) or not _introduction_needs_summary(book)


def generate_book_summary(book, force: bool = False) -> tuple[Optional[str], str]:
    """Generate ``(summary, source)`` for one book without indexing it.

    ``source`` is ``existing``, ``local``, ``deepseek``,
    ``short_introduction``, ``no_introduction`` or ``failed``.  Existing
    summaries are preserved unless ``force`` is set.  An introduction shorter
    than ``MIN_SUMMARY_INTRODUCTION_CHARS`` is embedded as-is, so no summary
    is generated for it unless ``force`` asks for one anyway.  A missing key
    uses the local extractive fallback; a configured key that fails leaves the
    summary unchanged so a later call can retry.
    """
    if not force and (book.summary or "").strip():
        return book.summary, "existing"
    if not (book.introduction or "").strip():
        return None, "no_introduction"
    if not force and not _introduction_needs_summary(book):
        return None, "short_introduction"
    # The summary is embedded next to the book header, so cap it to what is
    # left of the 512-token window after that header.
    budget = summary_char_budget(book)
    if not os.environ.get("DEEPSEEK_API_KEY", "").strip():
        return summarize_introduction(book.introduction, max_chars=budget), "local"
    summary = summarize_with_llm(*_summary_inputs(book), max_chars=budget)
    return (summary, "deepseek") if summary else (None, "failed")


def _generate_summaries(books, progress: Optional[ReindexState]) -> int:
    """Fill empty summaries, using DeepSeek V4.1 Flash when configured.

    Only books with an empty summary and an introduction long enough to be
    worth summarizing (``MIN_SUMMARY_INTRODUCTION_CHARS``) are touched; shorter
    introductions are embedded as full documents.  Existing summaries (Open
    Library/Google Books or user-edited) are preserved.  Without
    ``DEEPSEEK_API_KEY`` the local extractive fallback is used.  With a key,
    failed calls leave the summary empty so the next reindex retries them
    instead of locking in a local summary forever.
    """
    pending = [
        b
        for b in books
        if not (b.summary or "").strip() and _introduction_needs_summary(b)
    ]
    if not pending:
        return 0

    total = len(pending)
    _update_progress(progress, detail=f"Summarizing 0/{total}...")

    if not os.environ.get("DEEPSEEK_API_KEY", "").strip():
        for i, book in enumerate(pending, 1):
            book.summary, _ = generate_book_summary(book)
            _update_progress(progress, detail=f"Summarizing {i}/{total} (local)...")
        return total

    first = pending[0]
    first_summary, source = generate_book_summary(first)
    if source == "failed":
        logger.warning(
            "DeepSeek summary unavailable — leaving summaries empty for retry"
        )
        _update_progress(
            progress, detail="Summarizing skipped (DeepSeek unavailable)"
        )
        return 0

    first.summary = first_summary
    done = 1
    processed = 1
    _update_progress(progress, detail=f"Summarizing {processed}/{total}...")

    if len(pending) > 1:
        with ThreadPoolExecutor(max_workers=_SUMMARY_WORKERS) as pool:
            futures = {
                pool.submit(generate_book_summary, book): book
                for book in pending[1:]
            }
            for future in as_completed(futures):
                book = futures[future]
                try:
                    summary, _ = future.result()
                except Exception:
                    logger.exception("DeepSeek summary worker failed")
                    summary = None
                if summary:
                    book.summary = summary
                    done += 1
                processed += 1
                _update_progress(
                    progress, detail=f"Summarizing {processed}/{total}..."
                )

    if done < total:
        logger.warning(
            "DeepSeek generated %s/%s summaries; %s left empty for the next reindex",
            done,
            total,
            total - done,
        )
    return done


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

        document = build_book_embedding_document(book)
        fts_fields = build_book_fts_fields(book)
        summary_done = _summary_done(book)

        # Invalidate first, in its own committed transaction.  If the writes
        # below fail, the old vector/FTS row may survive, but the missing state
        # makes the next incremental reindex retry this book instead of
        # treating it as done.
        delete_book_state(db, book_id, commit=True)

        # One savepoint for vector + FTS: never leave half an index entry, and
        # a rollback here must not touch the caller's already-committed book.
        # The model is only loaded by search/reindex; never download it inside
        # a create/update request (that can block on the network for minutes).
        vector_written = False
        with db.begin_nested():
            if is_model_loaded():
                try:
                    upsert_book_vector(db, book.id, embed_text(document), commit=False)
                    vector_written = True
                except Exception:
                    # FTS must still be updated even when embedding fails.
                    logger.exception("vector sync failed for book %s", book_id)
            else:
                # Drop the old vector rather than leaving it pointing at stale
                # text. FTS still finds the book; reindex restores the vector.
                logger.info("embedding model not loaded; vector deferred for book %s", book_id)
            if not vector_written:
                delete_book_vector(db, book.id, commit=False)
            upsert_book_fts(db, book.id, commit=False, **fts_fields)
            # Summarise only after the vector and FTS row are in: a book with
            # no vector must stay pending, or semantic search keeps serving
            # whatever was there before.
            if vector_written:
                upsert_book_state(
                    db, book.id, summary_done=summary_done, commit=False
                )
        db.commit()
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
        with db.begin_nested():
            delete_book_vector(db, book_id, commit=False)
            delete_book_state(db, book_id, commit=False)
        db.commit()
        return True
    except Exception:
        logger.exception("remove_book failed for book %s", book_id)
        return False


# ── Full / incremental reindex ────────────────────────────────────────────────


def _books_needing_index(db: Session, books) -> list:
    """Books whose vector, FTS, summary state, or summary text is stale."""
    vector_ids = {
        row[0] for row in db.execute(text("SELECT book_id FROM book_vectors"))
    }
    fts_summaries = {
        row[0]: row[1] or ""
        for row in db.execute(text("SELECT book_id, summary FROM book_fts"))
    }
    state = {
        row[0]: row[1]
        for row in db.execute(
            text("SELECT book_id, summary_done FROM rag_book_state")
        )
    }

    pending = []
    for book in books:
        if book.id not in vector_ids or book.id not in fts_summaries:
            pending.append(book)
        elif state.get(book.id) != 1:
            pending.append(book)
        elif fts_summaries[book.id] != _space_cjk(book.summary or ""):
            pending.append(book)
    return pending


def _book_version_changed(row, version: Optional[str]) -> bool:
    """True when a book changed or disappeared between snapshot and write.

    ``row is None`` means the book was deleted mid-run.  A NULL ``updated_at``
    (imported/legacy rows) is not an edit: ``str(None)`` matches the snapshot's
    ``"None"``, so those books are still indexed.
    """
    return row is None or str(row[0]) != version


def _end_transaction(db: Session) -> None:
    """Commit and close the session's transaction without expiring loaded rows.

    A reindex can run for minutes, and SQLite holds its lock for the lifetime
    of the transaction.  In rollback-journal mode an open *read* transaction
    blocks writers just like a write transaction, so both the cleanup DELETE
    and the book loading must be released before the slow phases.  Expiring is
    disabled for the commit so the already-loaded books stay usable without
    issuing more queries.
    """
    expire_on_commit = db.expire_on_commit
    db.expire_on_commit = False
    try:
        db.commit()
    finally:
        db.expire_on_commit = expire_on_commit


def reindex_all(
    db: Session,
    batch_size: int = 32,
    progress: Optional[ReindexState] = None,
    incremental: bool = False,
) -> dict:
    """Rebuild the RAG index for the current user's database.

    ``incremental=True`` only processes books missing from the vector/FTS
    index, books whose summary was regenerated, and books whose summary is
    still pending.  A full run rebuilds every book.

    Uses batched ONNX inference (batch_size=32) for ~10× speedup over
    per-book embedding.  Falls back gracefully on individual failures.

    No SQLite transaction is held while the summaries or embeddings are
    computed, so book create/update/delete keeps working during a reindex.  A
    book edited while the run is in progress is left pending rather than
    having its index overwritten with the older snapshot.

    Args:
        db: Active database session.
        batch_size: ONNX batch size for embedding (default 32).
        progress: Optional ReindexState to update during execution.
        incremental: Only process books that are new or need reindexing.

    Returns:
        Dict with total/indexed/failed counts.  ``failed`` also counts books
        that could not be indexed in this run (embedding failure or a
        concurrent edit); those stay pending for the next incremental pass.
    """
    from models import Book

    _update_progress(
        progress, indexed=0, failed=0, detail="Cleaning stale index rows..."
    )

    # Drop entries for books deleted while the old delete hook was disabled.
    # Its own short transaction: the DELETE takes SQLite's write lock, and the
    # summaries and embeddings below can run for minutes.
    prune_missing_books(db, commit=True)

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

    if incremental:
        books = _books_needing_index(db, books)
        _update_progress(
            progress,
            total=len(books),
            detail=f"Incremental: {len(books)} book(s) pending...",
        )

    total = len(books)
    indexed = 0
    failed = 0
    _update_progress(progress, indexed=0, failed=0, total=total)

    # Loading the books opened a read transaction.  Release it before the
    # network/CPU phases, or the reindex still blocks every writer.
    _end_transaction(db)

    # Phase 1: summaries, then documents (no inference yet)
    summaries_added = _generate_summaries(books, progress)
    if summaries_added:
        logger.info("generated %s book summaries", summaries_added)

    # Persist the summaries, then snapshot each book's version *before* the
    # re-read below: if an edit races this window, the version stays older
    # than the row, so phase 3 skips the book instead of writing anything
    # stale.  Snapshotting after the re-read could miss the edit entirely.
    _end_transaction(db)
    versions = {
        row[0]: str(row[1])
        for row in db.execute(text("SELECT id, updated_at FROM books"))
    }

    # Re-read the books so the documents include everything committed since
    # they were first loaded: our own summaries and any concurrent edit.
    selected_ids = {book.id for book in books}
    if selected_ids:
        books = [
            book
            for book in db.query(Book)
            .options(
                selectinload(Book.authors),
                selectinload(Book.publisher),
                selectinload(Book.brand),
                selectinload(Book.book_series),
                selectinload(Book.category),
            )
            .populate_existing()
            .all()
            if book.id in selected_ids
        ]
    else:
        books = []

    _update_progress(progress, detail="Building documents...")

    _indexed: list[int] = []
    _documents: list[str] = []
    _fts_fields: list[tuple[int, dict]] = []
    _summary_flags: list[bool] = []

    for book in books:
        try:
            doc = build_book_embedding_document(book)
            fts = build_book_fts_fields(book)
            summary_done = _summary_done(book)
            _indexed.append(book.id)
            _documents.append(doc)
            _fts_fields.append((book.id, fts))
            _summary_flags.append(summary_done)
        except Exception:
            logger.exception("build_document failed for book %s", book.id)
            failed += 1
            _update_progress(progress, failed=failed)

    # Release the read transaction opened by the re-read before embedding.
    _end_transaction(db)

    _update_progress(progress, detail=f"Embedding {len(_documents)} documents...")

    # Phase 2: batched embeddings. One bad batch retries document-by-document
    # instead of failing the entire rebuild.
    from .embedding import embed_text, embed_texts

    vectors: list[list[float] | None] = []
    for start in range(0, len(_documents), batch_size):
        batch = _documents[start : start + batch_size]
        try:
            vectors.extend(embed_texts(batch, batch_size=batch_size))
        except Exception:
            logger.exception("batch embedding failed — retrying one by one")
            for doc in batch:
                try:
                    vectors.append(embed_text(doc))
                except Exception:
                    logger.exception("embedding failed for one document")
                    vectors.append(None)

        # Phase 2 used to show one static message for the whole run, which
        # looked like a hang on slow CPUs.
        _update_progress(
            progress,
            detail=(
                f"Embedding {min(start + batch_size, len(_documents))}/"
                f"{len(_documents)}..."
            ),
        )

    if len(vectors) < len(_documents):
        vectors.extend([None] * (len(_documents) - len(vectors)))
    vectors = vectors[: len(_documents)]

    _update_progress(progress, indexed=0, total=total, detail="Writing to database...")

    # Invalidate the state of every book about to be written before touching
    # the index.  These DELETEs stay in the same transaction as the first
    # batch instead of being committed separately: SQLite would otherwise
    # treat every per-book savepoint below as its own transaction (one fsync
    # per book, ~120 ms each on a real disk).  A failed write still only rolls
    # back its own savepoint, so the book stays pending; a crash before the
    # first commit loses the invalidation together with the writes, leaving
    # the previous, self-consistent index in place.
    for book_id in _indexed:
        delete_book_state(db, book_id, commit=False)

    # Phase 3: upsert vectors + FTS, committing every 100 books. A failed
    # vector write is rolled back on its own so it cannot delete the old row.
    for i, book_id in enumerate(_indexed):
        vector = vectors[i]
        try:
            stale = False
            with db.begin_nested():
                # The book may have been edited (or deleted) while phase 2 was
                # embedding: writing this snapshot would silently undo the
                # edit.  Its version changed, so leave it for the next run.
                row = db.execute(
                    text("SELECT updated_at FROM books WHERE id = :id"),
                    {"id": book_id},
                ).fetchone()
                stale = _book_version_changed(row, versions.get(book_id))
                if not stale:
                    if vector is None:
                        # Do not keep an old vector around: search would serve
                        # it while the book is pending.
                        delete_book_vector(db, book_id, commit=False)
                    else:
                        upsert_book_vector(db, book_id, vector, commit=False)
                    upsert_book_fts(db, book_id, commit=False, **_fts_fields[i][1])
                    if vector is not None:
                        # Summary state only counts once the vector and the
                        # FTS row are actually in.
                        upsert_book_state(
                            db,
                            book_id,
                            summary_done=_summary_flags[i],
                            commit=False,
                        )
            if stale:
                logger.info(
                    "book %s changed during the reindex — left pending", book_id
                )
                failed += 1
            elif vector is None:
                failed += 1
            else:
                indexed += 1
        except Exception:
            logger.exception("upsert failed for book %s", book_id)
            failed += 1
        if (i + 1) % 100 == 0:
            db.commit()
        _update_progress(progress, indexed=indexed, failed=failed)

    # Only a fully rebuilt index can claim the running model; a partial run
    # may still hold vectors from an older one.
    if failed == 0:
        record_embedding_fingerprint(db, commit=False)
    else:
        logger.warning(
            "reindex left %s book(s) pending — model fingerprint not updated", failed
        )
    db.commit()
    return {"total": total, "indexed": indexed, "failed": failed}
