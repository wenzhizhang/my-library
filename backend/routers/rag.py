"""RAG search and index management endpoints."""

import os

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session, selectinload
from sqlalchemy import text

from database import get_db, DATA_DIR
from models import Book
from rag.pipeline import start_reindex, get_reindex_state
from rag.vector_store import hybrid_search
from rag.embedding import is_model_loaded
from schemas.rag import (
    RagSearchRequest,
    RagSearchResponse,
    RagSearchResultItem,
    RagReindexResponse,
    RagStatusResponse,
)

router = APIRouter(prefix="/api/rag", tags=["rag"])


def _enrich_results(db: Session, raw: list[dict]) -> list[RagSearchResultItem]:
    if not raw:
        return []
    ids = [r["book_id"] for r in raw]
    score_map = {r["book_id"]: r["score"] for r in raw}
    books = (
        db.query(Book)
        .options(selectinload(Book.authors))
        .filter(Book.id.in_(ids))
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


@router.post("/search", response_model=RagSearchResponse)
def rag_search(req: RagSearchRequest, db: Session = Depends(get_db)):
    indexed = db.execute(
        text("SELECT COUNT(*) FROM book_vectors")
    ).scalar() or 0
    if indexed == 0:
        return RagSearchResponse(query=req.query, total=0, results=[])
    raw = hybrid_search(db, req.query, top_k=req.top_k, alpha=req.alpha)
    results = _enrich_results(db, raw)
    return RagSearchResponse(query=req.query, total=len(results), results=results)

@router.post("/reindex", response_model=RagReindexResponse)
def rag_reindex():
    """Start a full reindex in the background.

    Returns immediately with the current status.  Poll ``GET /api/rag/status``
    to track progress.
    """
    state = get_reindex_state()
    if state.status == "indexing":
        return RagReindexResponse(status="indexing", message="Reindex already in progress")

    db_path = os.path.join(DATA_DIR, "demo.db")
    start_reindex(db_path)
    return RagReindexResponse(status="indexing", message="Reindex started in background")

@router.get("/status", response_model=RagStatusResponse)
def rag_status(db: Session = Depends(get_db)):
    total_books = db.query(Book).count()
    indexed_count = db.execute(
        text("SELECT COUNT(*) FROM book_vectors")
    ).scalar() or 0
    state = get_reindex_state()
    return RagStatusResponse(
        indexed_count=indexed_count,
        total_books=total_books,
        model_loaded=is_model_loaded(),
        reindex_status=state.status,
        reindex_indexed=state.indexed,
        reindex_failed=state.failed,
        reindex_total=state.total,
        reindex_detail=state.detail,
    )