"""sqlite-vec vector store + FTS5 full-text index for book search.

All tables live inside the same per-user SQLite database used by the
application, maintaining data isolation with zero extra infrastructure.
"""

from sqlalchemy import text
from sqlalchemy.orm import Session

from .embedding import embed_text

# ── DDL ──────────────────────────────────────────────────────────────────────

_SQL_CREATE_VEC_TABLE = """
CREATE VIRTUAL TABLE IF NOT EXISTS book_vectors USING vec0(
    book_id INTEGER PRIMARY KEY,
    embedding FLOAT[512]
)
"""

_SQL_CREATE_FTS_TABLE = """
CREATE VIRTUAL TABLE IF NOT EXISTS book_fts USING fts5(
    book_id UNINDEXED,
    title,
    title_cn,
    authors_text,
    tags_text,
    introduction,
    summary,
    catalog,
    publisher_name,
    series_name,
    brand_name,
    content='',
    prefix='2 3'
)
"""

# ── DML ──────────────────────────────────────────────────────────────────────

_SQL_INSERT_VECTOR = """
INSERT INTO book_vectors (book_id, embedding)
VALUES (:book_id, :embedding)
"""

_SQL_DELETE_VECTOR = """
DELETE FROM book_vectors WHERE book_id = :book_id
"""

_SQL_INSERT_FTS = """
INSERT OR REPLACE INTO book_fts (
    rowid, book_id, title, title_cn, authors_text, tags_text,
    introduction, summary, catalog, publisher_name, series_name, brand_name
) VALUES (
    :book_id, :book_id, :title, :title_cn, :authors_text, :tags_text,
    :introduction, :summary, :catalog, :publisher_name, :series_name, :brand_name
)
"""

# contentless FTS5 cannot use DELETE FROM — use the 'delete' command
_SQL_DELETE_FTS = """
INSERT INTO book_fts(book_fts, rowid) VALUES('delete', :book_id)
"""

# ── Queries ──────────────────────────────────────────────────────────────────

_SQL_VEC_SEARCH = """
SELECT book_id, distance
FROM book_vectors
WHERE embedding MATCH :query_vec AND k = :top_k
"""

_SQL_FTS_SEARCH = """
SELECT rowid AS book_id, rank
FROM book_fts
WHERE book_fts MATCH :query
LIMIT :top_k
"""

# ── Schema helpers ───────────────────────────────────────────────────────────


def _vec_table_dims_match(db: Session) -> bool:
    """Check existing book_vectors has the expected 512-dim column."""
    try:
        row = db.execute(
            text(
                "SELECT sql FROM sqlite_master "
                "WHERE type='table' AND name='book_vectors'"
            )
        ).fetchone()
        if row is None:
            return True
        return "FLOAT[512]" in row[0]
    except Exception:
        return False


def ensure_rag_tables(db: Session) -> None:
    """Create book_vectors and book_fts tables if missing.

    Drops and recreates the vector table if dimensions have changed.
    """
    if not _vec_table_dims_match(db):
        db.execute(text("DROP TABLE IF EXISTS book_vectors"))
    db.execute(text(_SQL_CREATE_VEC_TABLE))
    db.execute(text(_SQL_CREATE_FTS_TABLE))
    db.commit()


# ── CRUD ─────────────────────────────────────────────────────────────────────


def upsert_book_vector(db: Session, book_id: int, embedding: list[float]) -> None:
    """Insert or update a book's vector embedding.

    vec0 tables do not support INSERT OR REPLACE on the PK, so we
    delete first, then insert.
    """
    import json

    db.execute(text(_SQL_DELETE_VECTOR), {"book_id": book_id})
    vec_blob = json.dumps(embedding)
    db.execute(text(_SQL_INSERT_VECTOR), {"book_id": book_id, "embedding": vec_blob})
    db.commit()


def delete_book_vector(db: Session, book_id: int) -> None:
    """Remove a book's vector and FTS entry."""
    db.execute(text(_SQL_DELETE_VECTOR), {"book_id": book_id})
    db.execute(text(_SQL_DELETE_FTS), {"book_id": book_id})
    db.commit()


def upsert_book_fts(
    db: Session,
    book_id: int,
    *,
    title: str = "",
    title_cn: str = "",
    authors_text: str = "",
    tags_text: str = "",
    introduction: str = "",
    summary: str = "",
    catalog: str = "",
    publisher_name: str = "",
    series_name: str = "",
    brand_name: str = "",
) -> None:
    """Insert or update a book's FTS5 index entry."""
    db.execute(
        text(_SQL_INSERT_FTS),
        {
            "book_id": book_id,
            "title": title or "",
            "title_cn": title_cn or "",
            "authors_text": authors_text or "",
            "tags_text": tags_text or "",
            "introduction": introduction or "",
            "summary": summary or "",
            "catalog": catalog or "",
            "publisher_name": publisher_name or "",
            "series_name": series_name or "",
            "brand_name": brand_name or "",
        },
    )
    db.commit()


# ── Search ───────────────────────────────────────────────────────────────────


def search_vectors(
    db: Session, query_text: str, top_k: int = 10
) -> list[dict]:
    """Semantic vector search via sqlite-vec kNN."""
    query_vec = embed_text(query_text)
    import json

    vec_json = json.dumps(query_vec)
    rows = db.execute(
        text(_SQL_VEC_SEARCH), {"query_vec": vec_json, "top_k": top_k}
    ).fetchall()
    return [{"book_id": r[0], "score": 1.0 / (1.0 + r[1])} for r in rows]


def _fts_tokenize(query_text: str) -> str:
    """Split a query into FTS5-friendly tokens, handling CJK text properly.

    CJK characters are each single tokens under FTS5's unicode61 tokenizer.
    A long CJK phrase like "甘道夫是哪本书里的" would require every character
    to appear in exact sequence — nearly impossible to match.  This function
    breaks long CJK runs into shorter 3-4 char overlapping chunks joined with
    OR, so "甘道夫" alone matches most CJK introductions.

    Non-CJK tokens are handled as prefix queries (existing behaviour).
    """
    import re
    import unicodedata

    terms = query_text.strip().split()
    if not terms:
        return ""

    def _looks_like_punct(s: str) -> bool:
        """Check if a string is composed entirely of punctuation/whitespace."""
        return all(unicodedata.category(c).startswith("P") or c.isspace() for c in s)

    fts_parts: list[str] = []
    for term in terms:
        cjk_runs = re.findall(r'[\u4e00-\u9fff]+', term)
        non_cjk_raw = re.findall(r'[^\u4e00-\u9fff]+', term)
        non_cjk = [t for t in non_cjk_raw if t.strip() and not _looks_like_punct(t)]

        if not cjk_runs:
            # Pure non-CJK → original behaviour: prefix match on each term
            fts_parts.append(f'"{term}"*')
            continue

        for run in cjk_runs:
            if len(run) <= 4:
                fts_parts.append(f'"{run}"')
            else:
                chunks = [run[i:i+4] for i in range(0, len(run), 3)]
                chunks = [c for c in chunks if len(c) >= 2]
                if chunks:
                    fts_parts.append(" OR ".join(f'"{c}"' for c in chunks))

        for token in non_cjk:
            if len(token) <= 4:
                fts_parts.append(f'"{token}"*')
            else:
                fts_parts.append(f'"{token[:4]}"*')

    if not fts_parts:
        return ""
    return " AND ".join(f"({p})" if " OR " in p else p for p in fts_parts)


def search_fts(
    db: Session, query_text: str, top_k: int = 10
) -> list[dict]:
    """FTS5 full-text search with BM25 scoring."""
    fts_query = _fts_tokenize(query_text)
    if not fts_query:
        return []
    rows = db.execute(
        text(_SQL_FTS_SEARCH), {"query": fts_query, "top_k": top_k}
    ).fetchall()
    return [{"book_id": r[0], "score": -r[1]} for r in rows]


def hybrid_search(
    db: Session,
    query_text: str,
    top_k: int = 10,
    alpha: float = 0.5,
) -> list[dict]:
    """Hybrid search — merge vector + FTS5 scores via weighted sum."""
    if not query_text.strip():
        return []

    vec_results = search_vectors(db, query_text, top_k=top_k * 2)
    fts_results = search_fts(db, query_text, top_k=top_k * 2)

    merged: dict[int, float] = {}

    def _max_score(items):
        return max((i["score"] for i in items), default=1.0)

    fts_max = _max_score(fts_results)
    vec_max = _max_score(vec_results)

    for r in vec_results:
        merged[r["book_id"]] = alpha * (r["score"] / vec_max if vec_max else 0)

    for r in fts_results:
        norm = (1 - alpha) * (r["score"] / fts_max if fts_max else 0)
        merged[r["book_id"]] = merged.get(r["book_id"], 0) + norm

    sorted_items = sorted(merged.items(), key=lambda x: -x[1])[:top_k]
    return [{"book_id": bid, "score": s} for bid, s in sorted_items]
