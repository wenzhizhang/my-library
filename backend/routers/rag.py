"""RAG search and index management endpoints."""

import re

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session, selectinload
from sqlalchemy import text

from auth import require_user_id
from database import get_db
from models import Book
from rag.answer import AnswerUnavailable, BookNotFound, answer_question
from rag.intent import resolve_entity_books
from rag.pipeline import (
    MIN_SUMMARY_INTRODUCTION_CHARS,
    generate_book_summary,
    get_reindex_state,
    start_reindex,
)
from rag.vector_store import (
    _fts_tokenize,
    embedding_fingerprint_status,
    hybrid_search,
    delete_book_state,
)
from rag.embedding import is_model_loaded
from schemas.rag import (
    RagAnswerRequest,
    RagAnswerResponse,
    RagSearchRequest,
    RagSearchResponse,
    RagSearchResultItem,
    RagReindexResponse,
    RagStatusResponse,
    RagSummarizeResponse,
)

router = APIRouter(prefix="/api/rag", tags=["rag"])


def _db_path(db: Session) -> str:
    """The SQLite file backing the current request's (per-user) session."""
    bind = db.get_bind()
    # A Session can be bound to an Engine or (in tests) a Connection.
    url = bind.engine.url if hasattr(bind, "engine") else bind.url
    return url.database


_EDITION_SUFFIX = re.compile(
    r"\s*(?:[（(\[]\s*"
    r"(?:第\s*[0-9一二三四五六七八九十]+\s*版|修订版|典藏版|珍藏版|纪念版|"
    r"精装版?|平装版?|套装|盒装|插图本|新版|特装本)\s*[）)\]])\s*$"
    r"|\s*(?:\d+\s*周年)?"
    r"(?:第\s*[0-9一二三四五六七八九十]+\s*版|纪念版|典藏版|珍藏版|"
    r"精装版|平装版|修订版|新版|插图本|套装|盒装|全集|特装本)\s*$"
)


def _work_key(title: str | None) -> str:
    """Group editions of one work without merging distinct volumes."""
    original = (title or "").strip()
    key = original
    while True:
        stripped = _EDITION_SUFFIX.sub("", key).strip()
        if stripped == key:
            return key or original
        key = stripped


def _diversify_results(
    results: list[RagSearchResultItem], limit: int
) -> list[RagSearchResultItem]:
    """Keep one edition per work, preferring the shortest/canonical title."""
    best: dict[str, RagSearchResultItem] = {}
    order: list[str] = []
    for item in results:
        title = item.title_cn or item.title or ""
        author = item.authors[0] if item.authors else ""
        key = f"{_work_key(title)}|{author}" if title else f"#{item.book_id}"
        current = best.get(key)
        if current is None:
            best[key] = item
            order.append(key)
            continue
        current_title = current.title_cn or current.title or ""
        if len(title) < len(current_title):
            best[key] = item
    return [best[key] for key in order][:limit]


def _enrich_results(db: Session, raw: list[dict]) -> list[RagSearchResultItem]:
    if not raw:
        return []
    ids = [r["book_id"] for r in raw]
    score_map = {r["book_id"]: r["score"] for r in raw}
    books = (
        db.query(Book)
        .options(selectinload(Book.authors))
        .filter(
            Book.id.in_(ids),
            Book.archived == False,  # noqa: E712
            Book.in_wish == False,  # noqa: E712
        )
        .all()
    )
    book_map = {b.id: b for b in books}
    results = []
    for bid in ids:
        b = book_map.get(bid)
        if b is None:
            continue
        results.append(
            RagSearchResultItem(
                book_id=bid,
                score=round(score_map[bid], 4),
                title=b.title,
                title_cn=b.title_cn,
                authors=[a.name_cn or a.name for a in (b.authors or [])],
            )
        )
    return results


@router.post(
    "/answer",
    response_model=RagAnswerResponse,
    responses={
        503: {"description": "Answer model not configured or unreachable"},
    },
)
def rag_answer(req: RagAnswerRequest, db: Session = Depends(get_db)):
    """Answer a question strictly from the caller's own library.

    Retrieval is narrower than ``/search``: an exact title match answers from
    that work only, otherwise just the top score cluster is used.  When
    nothing relevant is found (or the model cannot ground its answer in the
    candidates), the reply is ``no_answer`` instead of a guess.
    """
    try:
        result = answer_question(
            db,
            req.query,
            req.top_k,
            [turn.model_dump() for turn in req.history],
            req.seen_book_ids,
            req.focus_book_id,
            req.page_context.model_dump() if req.page_context else None,
        )
    except BookNotFound as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    except AnswerUnavailable as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc

    return RagAnswerResponse(
        query=result.query,
        answer=result.answer,
        sources=result.sources,
        no_answer=result.no_answer,
        rewritten_query=result.rewritten_query,
        more=result.more,
        has_more=result.has_more,
        reason=result.reason,
        scope=result.scope or None,
        stats=result.stats or None,
    )


@router.post("/search", response_model=RagSearchResponse)
def rag_search(req: RagSearchRequest, db: Session = Depends(get_db)):
    indexed = db.execute(text("SELECT COUNT(*) FROM book_vectors")).scalar() or 0
    keyword = db.execute(text("SELECT COUNT(*) FROM book_fts")).scalar() or 0
    if indexed == 0 and keyword == 0:
        return RagSearchResponse(query=req.query, total=0, results=[])

    # Over-fetch: archived/wishlist rows are filtered out during enrichment.
    # Exact entity queries trust FTS; long natural-language queries still let
    # vector-only hits fill the list.
    exact_query = " OR " not in _fts_tokenize(req.query)
    raw = hybrid_search(
        db,
        req.query,
        top_k=req.top_k * 3,
        alpha=req.alpha,
        min_score=req.min_score,
        min_fts_results=req.top_k if exact_query else 0,
    )
    # Author/series questions ("苏轼的书", "甲骨文丛书有哪些") must not rely on
    # BM25: resolve the entity deterministically and put its books first.
    entity_ids = resolve_entity_books(db, req.query, req.top_k * 3)
    if entity_ids:
        seen = set(entity_ids)
        raw = [{"book_id": bid, "score": 1.0} for bid in entity_ids] + [
            item for item in raw if item["book_id"] not in seen
        ]

    results = _diversify_results(_enrich_results(db, raw), req.top_k)
    return RagSearchResponse(query=req.query, total=len(results), results=results)


@router.post(
    "/summarize/{book_id}",
    response_model=RagSummarizeResponse,
    responses={
        400: {"description": "No introduction, or it is too short to summarize"},
        404: {"description": "Book not found"},
        502: {"description": "DeepSeek failed; stored summary left unchanged"},
    },
)
def rag_summarize(
    book_id: int,
    force: bool = False,
    db: Session = Depends(get_db),
    user_id: int = Depends(require_user_id),
):
    """Generate and persist one book's summary without a full reindex.

    Existing summaries are returned as-is unless ``force=true``.  An
    introduction shorter than ``MIN_SUMMARY_INTRODUCTION_CHARS`` is embedded
    as-is and is not summarized (400), unless ``force=true``.  A missing
    ``DEEPSEEK_API_KEY`` uses the local extractive fallback.  A configured key
    that fails returns 502 and leaves the stored summary unchanged.
    """
    book = (
        db.query(Book)
        .options(selectinload(Book.authors))
        .filter(Book.id == book_id)
        .first()
    )
    if book is None:
        raise HTTPException(status_code=404, detail="Book not found")

    summary, source = generate_book_summary(book, force=force)
    if source == "no_introduction":
        raise HTTPException(status_code=400, detail="Book has no introduction")
    if source == "short_introduction":
        raise HTTPException(
            status_code=400,
            detail=(
                f"Introduction is shorter than {MIN_SUMMARY_INTRODUCTION_CHARS} "
                "characters and is embedded as-is; pass force=true to "
                "summarize it anyway"
            ),
        )
    if source == "failed":
        raise HTTPException(
            status_code=502,
            detail="DeepSeek summary failed; stored summary left unchanged",
        )

    if source != "existing":
        book.summary = summary
        delete_book_state(db, book.id, commit=False)
        db.commit()
        db.refresh(book)

    return RagSummarizeResponse(
        book_id=book.id,
        summary=book.summary,
        source=source,
        updated=source != "existing",
    )


@router.post("/reindex", response_model=RagReindexResponse)
def rag_reindex(
    incremental: bool = False,
    db: Session = Depends(get_db),
    user_id: int = Depends(require_user_id),
):
    """Start a reindex of the caller's database in the background.

    ``incremental=true`` only processes new or pending books.  Returns
    immediately; poll ``GET /api/rag/status`` to track progress.
    """
    if not start_reindex(_db_path(db), incremental=incremental):
        return RagReindexResponse(status="indexing", message="Reindex already in progress")
    message = (
        "Incremental reindex started in background"
        if incremental
        else "Reindex started in background"
    )
    return RagReindexResponse(status="indexing", message=message)


@router.get("/status", response_model=RagStatusResponse)
def rag_status(db: Session = Depends(get_db)):
    total_books = db.query(Book).count()
    indexed_count = db.execute(
        text("SELECT COUNT(*) FROM book_vectors")
    ).scalar() or 0
    state = get_reindex_state(_db_path(db))
    index_model, index_model_matches = embedding_fingerprint_status(db)
    return RagStatusResponse(
        indexed_count=indexed_count,
        total_books=total_books,
        model_loaded=is_model_loaded(),
        index_model=index_model,
        index_model_matches=index_model_matches,
        reindex_status=state.status,
        reindex_indexed=state.indexed,
        reindex_failed=state.failed,
        reindex_total=state.total,
        reindex_detail=state.detail,
    )
