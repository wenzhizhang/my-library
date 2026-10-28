"""Author filters used by the assistant (dynasty/nation lists)."""

from __future__ import annotations

import re
from typing import Optional

from sqlalchemy import text
from sqlalchemy.orm import Session

DYNASTY_WORDS = ("宋", "唐", "元", "明", "清", "汉", "魏晋", "南北朝", "民国")


def normalize_dynasty(value: str) -> str:
    text_value = re.sub(r"(朝|代|时期)$", "", (value or "").strip())
    return text_value


def is_dynasty_author_query(query: str) -> bool:
    return any(era in query for era in DYNASTY_WORDS) and any(
        word in query for word in ("作者", "诗人", "作家", "文人")
    )


def author_filter_from_query(query: str) -> Optional[dict]:
    if not is_dynasty_author_query(query):
        return None
    if any(word in query for word in ("排行", "排名", "统计", "分布", "多少")):
        return None
    for era in DYNASTY_WORDS:
        if era in query:
            value = {"dynasty": era}
            if "中国" in query:
                value["nation"] = "中国"
            return value
    return None


def authors_by_dynasty(
    db: Session, dynasty: str, nation: str = "", limit: int = 30
) -> list[dict]:
    normalized = normalize_dynasty(dynasty)
    if not normalized:
        return []
    params: dict = {"dyn": f"%{normalized}%"}
    if "宋" == normalized:
        dynasty_clause = (
            "(a.dynasty LIKE :dyn AND a.dynasty NOT LIKE '%南朝%' "
            "AND a.dynasty NOT LIKE '%刘宋%')"
        )
    else:
        dynasty_clause = "a.dynasty LIKE :dyn"

    def run(where_nation: str) -> list[dict]:
        clause = dynasty_clause
        if where_nation:
            clause += " AND a.nation LIKE :nation"
            params["nation"] = f"%{where_nation}%"
        rows = db.execute(
            text(
                "SELECT a.id, a.name, a.name_cn, a.dynasty, a.nation, "
                "(SELECT COUNT(*) FROM book_authors ba WHERE ba.author_id = a.id) AS books "
                "FROM authors a WHERE a.dynasty IS NOT NULL AND "
                + clause
                + " ORDER BY books DESC, a.id LIMIT :limit"
            ),
            {**params, "limit": max(1, min(50, limit))},
        ).fetchall()
        return [
            {
                "author_id": row.id,
                "name": row.name_cn or row.name or "",
                "dynasty": row.dynasty or "",
                "nation": row.nation or "",
                "books": row.books or 0,
            }
            for row in rows
            if (row.books or 0) > 0
        ]

    authors = run(national_filter := (nation or "").strip())
    if not authors and national_filter:
        authors = run("")
    return authors


def is_author_rating_query(query: str) -> bool:
    return "作者" in query and "评分" in query and any(
        word in query for word in ("最高", "最高分", "均分", "排名")
    )


def authors_by_rating(db: Session, limit: int = 10) -> list[dict]:
    rows = db.execute(
        text(
            "SELECT a.id, a.name, a.name_cn, a.dynasty, "
            "COUNT(b.id) AS books, AVG(b.douban_score) AS score "
            "FROM authors a "
            "JOIN book_authors ba ON ba.author_id = a.id "
            "JOIN books b ON b.id = ba.book_id "
            "WHERE b.douban_score IS NOT NULL AND b.douban_score > 0 "
            "GROUP BY a.id "
            "ORDER BY score DESC, books DESC, a.id LIMIT :limit"
        ),
        {"limit": max(1, min(30, limit))},
    ).fetchall()
    return [
        {
            "author_id": row.id,
            "name": row.name_cn or row.name or "",
            "dynasty": row.dynasty or "",
            "books": row.books or 0,
            "score": round(float(row.score or 0), 2),
        }
        for row in rows
    ]
