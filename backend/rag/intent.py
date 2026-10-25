"""Query intent parsing and author/series resolution."""

import json
import re
from typing import Optional

from sqlalchemy import text
from sqlalchemy.orm import Session

from .llm import deepseek_chat
from .text import title_key
from .vector_store import normalize_query


_INTENT_SYSTEM = """判断用户问题的检索意图，只输出 JSON：
{"kind": "author" | "series" | "title" | "topic", "value": "实体名"}
规则：
- 问某位作者写了/著有/有哪些作品/都写过什么 → kind=author，value=作者名
- 问某个系列包含哪些书/一套书都有什么 → kind=series，value=系列名
- 问某一本具体的书的内容/版本/作者 → kind=title，value=书名
- 其他（主题、推荐、比较、常识） → kind=topic，value=核心主题词
value 只填实体名，不要带“的书”“的作品”等后缀。"""

_INTENT_KINDS = ("author", "series", "title", "topic")


def _json_object(raw: Optional[str]) -> Optional[dict]:
    if not raw:
        return None
    start, end = raw.find("{"), raw.rfind("}")
    if start < 0 or end <= start:
        return None
    try:
        value = json.loads(raw[start : end + 1])
    except ValueError:
        return None
    return value if isinstance(value, dict) else None


def parse_intent(query: str) -> dict:
    """What is the user asking for? One cheap Flash classification.

    This is the difference between "托尔金著的书" (books *by* Tolkien) and
    "魔戒的锻造者：托尔金传" (a biography whose title mentions him).  Any
    failure falls back to the topic heuristics, so a bad classification never
    makes retrieval worse than the keyword baseline.
    """
    raw = deepseek_chat(
        f"用户问题：{query}",
        system=_INTENT_SYSTEM,
        max_tokens=60,
        temperature=0.0,
    )
    payload = _json_object(raw)
    if not payload or payload.get("kind") not in _INTENT_KINDS:
        return {"kind": "topic", "value": ""}
    value = str(payload.get("value") or "").strip()
    return {"kind": payload["kind"], "value": value}


def _author_name_parts(name: str, name_cn: str) -> set[str]:
    """Name fragments: full name plus "约翰 / 罗纳德 / 瑞尔 / 托尔金"."""
    parts = set()
    for value in (name_cn, name):
        if not value:
            continue
        parts.add(title_key(value))
        parts.update(
            title_key(part)
            for part in re.split(r"[·.\s\-]+", value)
            if part.strip()
        )
    return {part for part in parts if len(part) >= 2}


def _author_ids_matching(db: Session, predicate) -> list[int]:
    return [
        author_id
        for author_id, name, name_cn in db.execute(
            text("SELECT id, name, name_cn FROM authors")
        )
        if predicate(_author_name_parts(name, name_cn))
    ]


def _books_by_author_ids(
    db: Session, author_ids: list[int], limit: int
) -> list[int]:
    if not author_ids:
        return []
    placeholders = ", ".join(f":a{i}" for i in range(len(author_ids)))
    params = {f"a{i}": author_id for i, author_id in enumerate(author_ids)}
    return [
        row[0]
        for row in db.execute(
            text(
                "SELECT DISTINCT b.id FROM books b "
                "JOIN book_authors ba ON ba.book_id = b.id "
                f"WHERE ba.author_id IN ({placeholders}) ORDER BY b.id"
            ),
            params,
        )
    ][:limit]


def _series_ids_matching(db: Session, predicate) -> list[int]:
    return [
        series_id
        for series_id, name in db.execute(text("SELECT id, name FROM book_series"))
        if predicate(title_key(name or ""))
    ]


def _books_by_series_ids(
    db: Session, series_ids: list[int], limit: int
) -> list[int]:
    if not series_ids:
        return []
    placeholders = ", ".join(f":s{i}" for i in range(len(series_ids)))
    params = {f"s{i}": series_id for i, series_id in enumerate(series_ids)}
    return [
        row[0]
        for row in db.execute(
            text(
                "SELECT id FROM books "
                f"WHERE book_series_id IN ({placeholders}) ORDER BY id"
            ),
            params,
        )
    ][:limit]


def _author_ids_for_book(db: Session, book_id: int) -> list[int]:
    return [
        row[0]
        for row in db.execute(
            text("SELECT author_id FROM book_authors WHERE book_id = :book_id"),
            {"book_id": book_id},
        )
    ]


def _author_ids_for_name(db: Session, value: str, limit: int) -> list[int]:
    """Books written by an author named by the intent parser."""
    value_key = title_key(value)
    if not value_key:
        return []
    author_ids = _author_ids_matching(
        db,
        lambda parts: any(
            part == value_key
            or (len(part) >= 3 and part in value_key)
            or (len(value_key) >= 3 and value_key in part)
            for part in parts
        ),
    )
    return _books_by_author_ids(db, author_ids, limit)


def _series_ids_for_name(db: Session, value: str, limit: int) -> list[int]:
    """Books belonging to a series named by the intent parser."""
    value_key = title_key(value)
    if not value_key:
        return []
    series_ids = _series_ids_matching(
        db,
        lambda key: bool(key)
        and (key == value_key or value_key in key),
    )
    return _books_by_series_ids(db, series_ids, limit)


def _series_match_ids(db: Session, query: str, limit: int) -> list[int]:
    """Book ids belonging to a series the query names ("尚古一读有哪些书").

    Series names live only in ``book_series``; the FTS index does carry the
    series name, but a direct lookup is deterministic and returns the whole
    series even when the book titles never mention it.
    """
    query_key = title_key(normalize_query(query))
    if not query_key:
        return []
    series_ids = _series_ids_matching(
        db, lambda key: len(key) >= 3 and key in query_key
    )
    return _books_by_series_ids(db, series_ids, limit)


def _author_match_ids(db: Session, query: str, limit: int) -> list[int]:
    """Books written by an author the query names ("托尔金著的书有哪些").

    BM25 ranks books whose *title* mentions the author above books they wrote,
    so "托尔金" surfaces his biographies and never his novels. Resolving the
    author directly fixes that; a title match still wins (see
    ``retrieve_candidates``) so "托尔金传" keeps returning the biography.
    """
    query_key = title_key(normalize_query(query))
    if not query_key:
        return []
    author_ids = _author_ids_matching(
        db,
        lambda parts: any(
            part in query_key and (len(part) >= 3 or part == query_key)
            for part in parts
        ),
    )
    return _books_by_author_ids(db, author_ids, limit)


def _exact_author_query(db: Session, query: str) -> bool:
    """True when the question itself *is* the author's name ("苏轼的书")."""
    query_key = title_key(normalize_query(query))
    if not query_key:
        return False
    return any(
        query_key in parts
        for _, name, name_cn in db.execute(
            text("SELECT id, name, name_cn FROM authors")
        )
        for parts in (_author_name_parts(name, name_cn),)
    )


_INTENT_RESOLVERS = {
    "author": _author_ids_for_name,
    "series": _series_ids_for_name,
}

_INTENT_NOTES = {
    "author": (
        "用户意图：查找作者「{value}」的著作。"
        "只把该作者写的书算作其著作，不要把他人的传记、评传、研究算进来。"
    ),
    "series": "用户意图：查找系列「{value}」包含的书。",
}


