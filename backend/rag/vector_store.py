"""sqlite-vec vector store + FTS5 full-text index for book search.

All tables live inside the same per-user SQLite database used by the
application, maintaining data isolation with zero extra infrastructure.
"""

import logging
import re
from typing import Optional

from sqlalchemy import text
from sqlalchemy.orm import Session

from .embedding import EMBEDDING_DIMS, EMBEDDING_MODEL_NAME, embed_text

logger = logging.getLogger(__name__)

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
    prefix='2 3'
)
"""

# Identifies the model that produced book_vectors.  A different model makes
# the stored vectors incomparable with new query vectors — silently: search
# just gets worse.  Recorded on index build, compared on startup.
_EMBEDDING_FINGERPRINT_KEY = "embedding_fingerprint"
_UNKNOWN_FINGERPRINT = "unknown"

# Bump this whenever the FTS field encoding changes.  Existing tables are
# dropped and rebuilt by the next reindex.
_FTS_SCHEMA_VERSION = "cjk_spaced_v1"

_SQL_CREATE_META = """
CREATE TABLE IF NOT EXISTS rag_meta (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
)
"""

# Per-book state for incremental reindex.  ``summary_done=0`` means the next
# incremental run must (re)build this book's vector/FTS and try a summary.
_SQL_CREATE_BOOK_STATE = """
CREATE TABLE IF NOT EXISTS rag_book_state (
    book_id INTEGER PRIMARY KEY,
    summary_done INTEGER NOT NULL DEFAULT 0
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

_SQL_DELETE_FTS = """
DELETE FROM book_fts WHERE rowid = :book_id
"""

# Remove index rows whose book no longer exists.
_SQL_PRUNE_FTS = """
DELETE FROM book_fts WHERE rowid NOT IN (SELECT id FROM books)
"""

_SQL_PRUNE_VECTORS = """
DELETE FROM book_vectors WHERE book_id NOT IN (SELECT id FROM books)
"""

_SQL_UPSERT_BOOK_STATE = """
INSERT OR REPLACE INTO rag_book_state (book_id, summary_done)
VALUES (:book_id, :summary_done)
"""

_SQL_DELETE_BOOK_STATE = """
DELETE FROM rag_book_state WHERE book_id = :book_id
"""

_SQL_PRUNE_BOOK_STATE = """
DELETE FROM rag_book_state WHERE book_id NOT IN (SELECT id FROM books)
"""

# Existing databases predate rag_book_state.  Mark already-indexed books as
# done once, so the first incremental run does not rebuild the whole library.
_SQL_BOOTSTRAP_BOOK_STATE = """
INSERT OR IGNORE INTO rag_book_state (book_id, summary_done)
SELECT b.id, 1
FROM books b
WHERE EXISTS (SELECT 1 FROM book_vectors v WHERE v.book_id = b.id)
  AND EXISTS (SELECT 1 FROM book_fts f WHERE f.book_id = b.id)
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
ORDER BY rank
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


# Every range the indexer treats as CJK: Extension A, the unified ideographs,
# and the compatibility ideographs.  ``_space_cjk`` (indexing) and
# ``_fts_tokenize`` (queries) must agree on this set: a character stored as its
# own token but dropped from the query can never be found.
_CJK_RANGES = "\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff"
_CJK_CHAR_RE = re.compile(f"([{_CJK_RANGES}])")
_CJK_RUN_RE = re.compile(f"[{_CJK_RANGES}]+")


def _space_cjk(text: str) -> str:
    """Space CJK characters so FTS5 ``unicode61`` indexes them individually.

    ``unicode61`` treats a whole CJK run as one token, so ``甘道夫`` inside
    ``睿智巫师甘道夫的到来`` is not searchable.  Spacing each CJK character
    keeps Latin words intact while making every CJK substring matchable.
    """
    if not text:
        return ""
    return re.sub(r"\s+", " ", _CJK_CHAR_RE.sub(r" \1 ", text)).strip()


def _fts_table_is_current(db: Session) -> bool:
    """False when the FTS table uses an old encoding or column set."""
    try:
        version = db.execute(
            text("SELECT value FROM rag_meta WHERE key='fts_tokenizer'")
        ).scalar()
    except Exception:
        version = None
    if version != _FTS_SCHEMA_VERSION:
        return False

    row = db.execute(
        text("SELECT sql FROM sqlite_master WHERE type='table' AND name='book_fts'")
    ).fetchone()
    if row is None:
        return True
    sql = re.sub(r"\s+", "", (row[0] or "")).lower()
    # contentless tables cannot delete/update reliably; they must be rebuilt.
    return "content=''" not in sql and "brand_name" in sql


def _sqlite_vec_version(db: Session) -> str:
    try:
        return str(db.execute(text("SELECT vec_version()")).scalar() or "?")
    except Exception:
        return "?"


def _fastembed_version() -> str:
    try:
        from importlib.metadata import version

        return version("fastembed")
    except Exception:
        return "?"


def current_embedding_fingerprint(db: Session) -> str:
    """Identify the model and libraries that produce/read these vectors."""
    return (
        f"{EMBEDDING_MODEL_NAME}|dim{EMBEDDING_DIMS}"
        f"|vec{_sqlite_vec_version(db)}|fe{_fastembed_version()}"
    )


def stored_embedding_fingerprint(db: Session) -> Optional[str]:
    """Fingerprint recorded in ``rag_meta``, or None when it was never set."""
    try:
        return db.execute(
            text("SELECT value FROM rag_meta WHERE key = :key"),
            {"key": _EMBEDDING_FINGERPRINT_KEY},
        ).scalar()
    except Exception:
        return None


def record_embedding_fingerprint(db: Session, *, commit: bool = False) -> str:
    """Record the runtime fingerprint after the vectors were rebuilt."""
    fingerprint = current_embedding_fingerprint(db)
    db.execute(
        text("INSERT OR REPLACE INTO rag_meta (key, value) VALUES (:key, :value)"),
        {"key": _EMBEDDING_FINGERPRINT_KEY, "value": fingerprint},
    )
    if commit:
        db.commit()
    return fingerprint


def embedding_fingerprint_status(db: Session) -> tuple[str, bool]:
    """Return ``(stored fingerprint, matches the running model)``."""
    stored = stored_embedding_fingerprint(db)
    return stored or "", stored == current_embedding_fingerprint(db)


def _check_embedding_fingerprint(db: Session) -> None:
    """Record the fingerprint for an empty index; flag legacy or changed ones.

    Vectors from another model are not comparable with query vectors, and
    nothing else would ever notice, so a mismatch is logged loudly and left
    for a full reindex to fix.  A pre-fingerprint database is not blessed:
    its stored value becomes ``unknown`` until a full reindex rewrites it.
    """
    stored = stored_embedding_fingerprint(db)
    current = current_embedding_fingerprint(db)
    if stored is None:
        has_vectors = (
            db.execute(text("SELECT 1 FROM book_vectors LIMIT 1")).first() is not None
        )
        if has_vectors:
            logger.warning(
                "book_vectors predate model fingerprinting (runtime is %s) — "
                "run a full reindex to confirm they are comparable",
                current,
            )
            db.execute(
                text(
                    "INSERT OR REPLACE INTO rag_meta (key, value) "
                    "VALUES (:key, :value)"
                ),
                {"key": _EMBEDDING_FINGERPRINT_KEY, "value": _UNKNOWN_FINGERPRINT},
            )
        else:
            record_embedding_fingerprint(db)
    elif stored != current:
        logger.warning(
            "RAG vectors were built with %s but this runtime is %s — "
            "run a full reindex before trusting semantic search",
            stored,
            current,
        )


def ensure_rag_tables(db: Session) -> None:
    """Create book_vectors and book_fts tables if missing.

    Drops and recreates the vector table if dimensions have changed, and
    rebuilds the FTS table if it was created with an old encoding or as a
    contentless table (whose deletes corrupted the index).
    """
    db.execute(text(_SQL_CREATE_META))
    if not _vec_table_dims_match(db):
        logger.warning("book_vectors dimensions changed — rebuilding vector table")
        db.execute(text("DROP TABLE IF EXISTS book_vectors"))
    if not _fts_table_is_current(db):
        logger.warning("book_fts schema is outdated — rebuilding FTS table (run reindex)")
        db.execute(text("DROP TABLE IF EXISTS book_fts"))
    db.execute(text(_SQL_CREATE_VEC_TABLE))
    db.execute(text(_SQL_CREATE_FTS_TABLE))
    db.execute(text(_SQL_CREATE_BOOK_STATE))
    bootstrapped = db.execute(
        text("SELECT value FROM rag_meta WHERE key='book_state_bootstrapped'")
    ).scalar()
    has_books = db.execute(
        text("SELECT 1 FROM sqlite_master WHERE type='table' AND name='books'")
    ).scalar()
    if has_books and bootstrapped != "1":
        db.execute(text(_SQL_BOOTSTRAP_BOOK_STATE))
        db.execute(
            text(
                "INSERT OR REPLACE INTO rag_meta (key, value) "
                "VALUES ('book_state_bootstrapped', '1')"
            )
        )
    db.execute(
        text(
            "INSERT OR REPLACE INTO rag_meta (key, value) "
            "VALUES ('fts_tokenizer', :version)"
        ),
        {"version": _FTS_SCHEMA_VERSION},
    )
    _check_embedding_fingerprint(db)
    db.commit()


# ── CRUD ─────────────────────────────────────────────────────────────────────


def upsert_book_vector(
    db: Session, book_id: int, embedding: list[float], *, commit: bool = True
) -> None:
    """Insert or update a book's vector embedding.

    vec0 tables do not support INSERT OR REPLACE on the PK, so we
    delete first, then insert.
    """
    import json

    vec_blob = json.dumps(embedding)
    # Savepoint: a failed insert must not leave the old vector deleted.
    with db.begin_nested():
        db.execute(text(_SQL_DELETE_VECTOR), {"book_id": book_id})
        db.execute(text(_SQL_INSERT_VECTOR), {"book_id": book_id, "embedding": vec_blob})
    if commit:
        db.commit()


def delete_book_vector(db: Session, book_id: int, *, commit: bool = True) -> None:
    """Remove a book's vector and FTS entry."""
    with db.begin_nested():
        db.execute(text(_SQL_DELETE_VECTOR), {"book_id": book_id})
        db.execute(text(_SQL_DELETE_FTS), {"book_id": book_id})
    if commit:
        db.commit()


def prune_missing_books(db: Session, *, commit: bool = True) -> None:
    """Drop index rows for books that no longer exist in this database."""
    with db.begin_nested():
        db.execute(text(_SQL_PRUNE_FTS))
        db.execute(text(_SQL_PRUNE_VECTORS))
        db.execute(text(_SQL_PRUNE_BOOK_STATE))
    if commit:
        db.commit()


def upsert_book_state(
    db: Session, book_id: int, *, summary_done: bool, commit: bool = True
) -> None:
    """Record whether incremental reindex still needs to process this book."""
    db.execute(
        text(_SQL_UPSERT_BOOK_STATE),
        {"book_id": book_id, "summary_done": int(summary_done)},
    )
    if commit:
        db.commit()


def delete_book_state(db: Session, book_id: int, *, commit: bool = True) -> None:
    """Drop a book's incremental state."""
    db.execute(text(_SQL_DELETE_BOOK_STATE), {"book_id": book_id})
    if commit:
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
    commit: bool = True,
) -> None:
    """Insert or update a book's FTS5 index entry."""
    db.execute(
        text(_SQL_INSERT_FTS),
        {
            "book_id": book_id,
            "title": _space_cjk(title),
            "title_cn": _space_cjk(title_cn),
            "authors_text": _space_cjk(authors_text),
            "tags_text": _space_cjk(tags_text),
            "introduction": _space_cjk(introduction),
            "summary": _space_cjk(summary),
            "catalog": _space_cjk(catalog),
            "publisher_name": _space_cjk(publisher_name),
            "series_name": _space_cjk(series_name),
            "brand_name": _space_cjk(brand_name),
        },
    )
    if commit:
        db.commit()


# ── Search ───────────────────────────────────────────────────────────────────


def search_vectors(
    db: Session, query_text: str, top_k: int = 10
) -> list[dict]:
    """Semantic vector search via sqlite-vec kNN.

    fastembed returns unit-length vectors, so L2 distance d maps to cosine
    similarity ``1 - d²/2``.  Keeping the absolute value (instead of dividing
    by this query's best hit) means scores are comparable across queries.
    """
    query_vec = embed_text(normalize_query(query_text))
    import json

    vec_json = json.dumps(query_vec)
    rows = db.execute(
        text(_SQL_VEC_SEARCH), {"query_vec": vec_json, "top_k": top_k}
    ).fetchall()
    return [
        {"book_id": r[0], "score": max(0.0, min(1.0, 1.0 - (r[1] * r[1]) / 2.0))}
        for r in rows
    ]


_MAX_FTS_PARTS = 20

# Interrogative tails add no retrieval signal and can match unrelated text
# (e.g. "出自哪本书" matching a book summary's "出自哪位名家").
# One table for every trailing phrase the query normaliser and the FTS
# tokenizer drop; keep them in sync by adding to this tuple only.  Longer
# entries must win ("这本书讲了什么" before "这本书"), so consumers sort by
# length descending.
_QUESTION_SUFFIXES = (
    "这本书讲的是什么",
    "这本书讲了什么",
    "这本书说了什么",
    "这本书怎么样",
    "这本书",
    "讲的是什么",
    "讲了什么",
    "说了什么",
    "讲了啥",
    "主要内容",
    "内容简介",
    "的相关书籍",
    "相关的书",
    "的书籍",
    "的书",
    "书籍",
    "的图书",
    "图书",
    "出自哪本书",
    "是哪本书",
    "是什么书",
    "出自哪里",
    "是谁写的",
    "哪本书",
    "有哪些",
    "是什么",
    "是谁",
    "怎么样",
    "怎么",
    "如何",
    "为什么",
)
_QUESTION_SUFFIXES_BY_LENGTH = tuple(
    sorted(_QUESTION_SUFFIXES, key=len, reverse=True)
)


# Interrogative scaffolding around the topic: "有哪些讲唐史的书" → "唐史".
# The FTS tokenizer otherwise turns the wrapper itself into trigrams
# ("有 哪 些", "史 的 书") that match unrelated introductions and outrank the
# books actually about the topic.  Only anchored wrappers are removed, so a
# title that merely contains these characters ("旧唐书", "讲谈社·中国的历史")
# is left alone.
_LEADING_SCAFFOLD_RE = re.compile(
    r"^(?:请问|帮我找|帮我|我想找|想找|找找|有没有|有哪些|有什么|哪些|"
    r"请推荐|推荐一些|推荐|介绍一下|介绍|讲讲|说说|聊聊|谈谈|关于)\s*"
)
_TRAILING_SCAFFOLD_RE = re.compile(
    "(?:"
    + "|".join(re.escape(suffix) for suffix in _QUESTION_SUFFIXES_BY_LENGTH)
    + r")\s*$"
)
_LEADING_VERB_RE = re.compile(r"^(?:讲|写|谈|介绍)\s*")


def normalize_query(query_text: str) -> str:
    """Strip the question wrapper so retrieval sees the topic itself.

    Applied before both FTS tokenization and embedding, so the topic — not the
    phrasing — drives the match.
    """
    normalized = (query_text or "").strip().strip("《》“”\"'‘’")
    for _ in range(2):
        previous = normalized
        normalized = _LEADING_SCAFFOLD_RE.sub("", normalized).strip()
        normalized = _TRAILING_SCAFFOLD_RE.sub("", normalized).strip()
        if normalized != previous:
            # "讲唐史的书" only betrays its verb once the wrapper is gone.
            normalized = _LEADING_VERB_RE.sub("", normalized).strip()
        normalized = normalized.strip("《》“”\"'‘’").strip()
        if normalized == previous:
            break
    return normalized or (query_text or "").strip()


def _strip_question_suffix(query_text: str) -> str:
    text = query_text.strip().rstrip("？?！!。.，,、;；:：")
    for suffix in _QUESTION_SUFFIXES_BY_LENGTH:
        if text.endswith(suffix):
            return text[: -len(suffix)].strip()
    return text


def _fts_tokenize(query_text: str) -> str:
    """Turn a user query into a safe FTS5 query string.

    CJK fields are stored space-separated, so long runs are matched with
    overlapping 3-grams (OR).  ASCII/digit runs become prefix terms.  Only
    alphanumerics and CJK reach the query, so quotes and FTS operators in the
    user's input cannot produce a syntax error.
    """
    import re

    terms = _strip_question_suffix(query_text).split()
    if not terms:
        return ""

    fts_parts: list[str] = []
    for term in terms:
        for run in _CJK_RUN_RE.findall(term):
            if len(run) <= 4:
                fts_parts.append(f'"{_space_cjk(run)}"')
            else:
                # Overlapping trigrams: 甘道夫 in 甘道夫出自哪本书 must survive
                # a boundary that 4-char fixed chunks would cut through.
                chunks = [run[i : i + 3] for i in range(min(len(run) - 2, _MAX_FTS_PARTS))]
                if chunks:
                    fts_parts.append(
                        " OR ".join(f'"{_space_cjk(c)}"' for c in chunks)
                    )

        for token in re.findall(r"[0-9A-Za-z]+", term):
            fts_parts.append(f'"{token[:32]}"*')

        if len(fts_parts) >= _MAX_FTS_PARTS:
            break

    fts_parts = fts_parts[:_MAX_FTS_PARTS]
    if not fts_parts:
        return ""
    return " AND ".join(f"({p})" if " OR " in p else p for p in fts_parts)


def search_fts(
    db: Session, query_text: str, top_k: int = 10
) -> list[dict]:
    """FTS5 full-text search with BM25 scoring."""
    fts_query = _fts_tokenize(normalize_query(query_text))
    if not fts_query:
        return []
    rows = db.execute(
        text(_SQL_FTS_SEARCH), {"query": fts_query, "top_k": top_k}
    ).fetchall()
    return [{"book_id": r[0], "score": -r[1]} for r in rows]


_RRF_K = 60


def hybrid_search(
    db: Session,
    query_text: str,
    top_k: int = 10,
    alpha: float = 0.5,
    min_score: float = 0.0,
    min_fts_results: int = 0,
) -> list[dict]:
    """Hybrid search — weighted reciprocal-rank fusion of vector and FTS5.

    Raw cosine scores are not comparable with BM25 and, for a whole-book
    vector, are often high for unrelated books.  RRF uses ranks instead, so a
    strong FTS exact match is not buried by many mediocre vector hits.
    ``alpha`` is the vector weight (0 = FTS only, 1 = vector only).
    When FTS finds at least ``min_fts_results`` hits, vector hits are only
    used to re-rank those FTS candidates; vector-only noise cannot enter the
    result list.  Scores are normalised to the best hit so ``min_score``
    stays 0..1.  Either branch may fail without taking the other down.
    """
    if not query_text.strip():
        return []

    vec_results: list[dict] = []
    if alpha > 0:
        try:
            vec_results = search_vectors(db, query_text, top_k=top_k * 2)
        except Exception:
            logger.exception("vector search failed — falling back to FTS")

    fts_results: list[dict] = []
    if alpha < 1:
        try:
            fts_results = search_fts(db, query_text, top_k=top_k * 2)
        except Exception:
            logger.exception("FTS search failed — falling back to vectors")

    if min_fts_results and len(fts_results) >= min_fts_results:
        fts_ids = {r["book_id"] for r in fts_results}
        vec_results = [r for r in vec_results if r["book_id"] in fts_ids]

    merged: dict[int, float] = {}
    for rank, r in enumerate(vec_results, 1):
        merged[r["book_id"]] = merged.get(r["book_id"], 0.0) + alpha / (
            _RRF_K + rank
        )
    for rank, r in enumerate(fts_results, 1):
        merged[r["book_id"]] = merged.get(r["book_id"], 0.0) + (1 - alpha) / (
            _RRF_K + rank
        )

    ranked = sorted(merged.items(), key=lambda x: -x[1])
    best = ranked[0][1] if ranked else 0.0
    return [
        {"book_id": bid, "score": s / best}
        for bid, s in ranked
        if best and s / best >= min_score
    ][:top_k]
