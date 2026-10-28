"""Answer library-statistics questions from the existing stats aggregate."""

from __future__ import annotations

import re
from dataclasses import asdict, dataclass
from datetime import date
from typing import Any, Optional

from sqlalchemy.orm import Session

from services.library_stats import EXTREME_FIELDS, build_book_stats

READ_STATE_LABELS = {"unread": "未读", "reading": "在读", "read": "已读"}
MAX_STATS_LIMIT = 20

@dataclass(frozen=True)
class StatsQuery:
    metric: str
    group_by: str = ""
    field: str = ""
    order: str = "max"
    period: str = "month"
    year: Optional[int] = None
    month: Optional[int] = None
    limit: int = 5

    @classmethod
    def from_dict(cls, data: dict) -> "StatsQuery":
        return cls(
            metric=str(data.get("metric") or ""),
            group_by=str(data.get("group_by") or ""),
            field=str(data.get("field") or ""),
            order=str(data.get("order") or "max"),
            period=str(data.get("period") or "month"),
            year=data.get("year"),
            month=data.get("month"),
            limit=int(data.get("limit") or 5),
        )

    def to_dict(self) -> dict:
        return {key: value for key, value in asdict(self).items() if value not in ("", None)}


STATS_METRICS = frozenset({
    "overview", "read_state", "average", "spend", "spend_month",
    "purchase_list", "extreme", "top", "breakdown", "timeline",
    "timeline_month", "purchase_timeline",
})
STATS_GROUPS = frozenset({"category", "author", "publisher", "binding", "language", "score"})
GROUP_SOURCES = {
    "category": ("分类", "by_category"),
    "author": ("作者", "top_authors"),
    "publisher": ("出版社", "top_publishers"),
    "binding": ("装帧", "by_binding"),
    "language": ("语言", "by_language"),
    "score": ("评分区间", "by_score"),
}
_GROUPS = {
    "分类": "category",
    "类别": "category",
    "作者": "author",
    "作家": "author",
    "出版社": "publisher",
    "装帧": "binding",
    "语言": "language",
    "评分": "score",
    "豆瓣": "score",
}


def _target_year(message: str) -> Optional[int]:
    today = date.today()
    if "今年" in message or "本年" in message:
        return today.year
    if "去年" in message:
        return today.year - 1
    if "前年" in message:
        return today.year - 2
    match = re.search(r"(20\d{2})\s*年?", message)
    return int(match.group(1)) if match else None


def _target_month(message: str) -> Optional[tuple[int, int]]:
    """Resolve 这个月 / 本月 / 上个月 / 去年这个月 / YYYY年M月."""
    today = date.today()
    explicit = re.search(r"(20\d{2})\s*年\s*(\d{1,2})\s*月", message)
    if explicit:
        return int(explicit.group(1)), max(1, min(12, int(explicit.group(2))))

    if "去年这个月" in message or "去年本月" in message:
        return today.year - 1, today.month
    if "上上个月" in message:
        offset = 2
    elif "上个月" in message:
        offset = 1
    elif any(word in message for word in ("这个月", "本月", "当月")):
        offset = 0
    else:
        return None

    year, month = today.year, today.month - offset
    while month <= 0:
        month += 12
        year -= 1
    return year, month


def wants_listing(message: str) -> bool:
    return any(
        word in message
        for word in ("列出来", "列出", "哪些书", "什么书", "书单", "明细", "具体")
    )


def _limit(message: str) -> int:
    match = re.search(r"(?:前|top\s*|排行前\s*)(\d+)", message, flags=re.I)
    if match:
        return max(1, min(MAX_STATS_LIMIT, int(match.group(1))))
    return 5


def parse_stats_query(message: str) -> Optional[dict[str, Any]]:
    text = (message or "").strip()
    if not text:
        return None
    year = _target_year(text)
    month = _target_month(text)
    limit = _limit(text)
    ranking = any(word in text for word in ("排行", "排名", "最多", "top", "前几"))
    timeline = any(word in text for word in ("每月", "每年", "新增", "入库", "时间线"))
    purchase_words = any(word in text for word in ("花费", "花了", "支出", "购书", "买书", "买了", "购买", "购入"))
    read_words = any(
        word in text for word in ("阅读状态", "读了多少", "读过", "已读", "未读", "在读")
    )
    group = next((value for label, value in _GROUPS.items() if label in text), None)

    if any(word in text for word in ("最贵", "定价最高", "价格最高", "购入最贵")):
        field = "purchase_price" if "购入" in text else "price"
        return {"metric": "extreme", "field": field, "order": "max"}
    if any(word in text for word in ("最便宜", "定价最低", "价格最低", "购入最低")):
        field = "purchase_price" if "购入" in text else "price"
        return {"metric": "extreme", "field": field, "order": "min"}
    if any(word in text for word in ("页数最多", "页数最少")):
        return {
            "metric": "extreme",
            "field": "pages",
            "order": "min" if "最少" in text else "max",
        }
    if any(word in text for word in ("评分最高", "评分最低", "豆瓣最高", "豆瓣最低")):
        return {
            "metric": "extreme",
            "field": "douban_score",
            "order": "min" if "最低" in text else "max",
        }
    if "册数最多" in text:
        return {"metric": "extreme", "field": "book_count", "order": "max"}
    if purchase_words and wants_listing(text):
        return {
            "metric": "purchase_list",
            "year": month[0] if month else year,
            "month": month[1] if month else None,
        }
    if purchase_words and month:
        return {"metric": "spend_month", "year": month[0], "month": month[1]}
    if timeline and month:
        return {"metric": "timeline_month", "year": month[0], "month": month[1]}
    if any(word in text for word in ("平均价格", "均价", "平均定价", "平均购入")) or (
        "平均" in text and any(word in text for word in ("多少", "价格", "钱", "贵"))
    ):
        return {"metric": "average", "year": year}
    if read_words:
        return {
            "metric": "read_state",
            "year": year,
            "month": month[1] if month else None,
        }
    if purchase_words and not timeline:
        return {"metric": "spend", "year": year}
    if timeline or (purchase_words and any(word in text for word in ("每月", "每年"))):
        return {
            "metric": "purchase_timeline" if purchase_words else "timeline",
            "period": "month" if "每月" in text else "year",
            "year": year,
        }
    if ranking and group:
        return {"metric": "top", "group_by": group, "limit": limit}
    if group and (ranking or any(word in text for word in ("统计", "分布", "各", "多少", "分别"))):
        return {"metric": "breakdown", "group_by": group, "limit": limit}
    if any(word in text for word in ("多少本", "几本", "总数", "总共有", "一共", "藏书")):
        return {"metric": "overview"}
    if "多少" in text and any(word in text for word in ("作者", "出版社", "分类")):
        return {"metric": "overview"}
    if any(word in text for word in ("数量", "几个", "多少个")) and any(
        word in text for word in ("作者", "出版社", "分类")
    ):
        return {"metric": "overview"}
    if "总览" in text:
        return {"metric": "overview"}
    return None


def _table(columns: list[str], rows: list[list[Any]]) -> dict[str, Any]:
    return {"columns": columns, "rows": rows}


def _rows_from(data: list[dict], limit: Optional[int] = None) -> list[dict]:
    rows = [row for row in data if (row.get("count") or 0) > 0]
    rows.sort(key=lambda row: row.get("count") or 0, reverse=True)
    return rows[:limit] if limit else rows


def _breakdown_answer(label: str, data: list[dict], limit: int = 10) -> tuple[str, dict]:
    rows = _rows_from(data, limit)
    if not rows:
        return f"书库里没有可统计的{label}数据。", _table([label, "数量"], [])
    listing = "、".join(f"{row['name']} {row['count']} 本" for row in rows)
    return (
        f"按{label}统计（前 {len(rows)} 项）：{listing}。",
        _table([label, "数量"], [[row["name"], row["count"]] for row in rows]),
    )


def _timeline_answer(
    data: list[dict], period: str, year: Optional[int], purchase: bool
) -> tuple[str, dict]:
    rows = data
    if year is not None:
        rows = [row for row in rows if row.get("year") == year]
    rows = rows[-24:]
    if not rows:
        if year:
            return f"没有找到 {year} 年的入库记录。", _table(["时间", "数量"], [])
        return "没有找到入库记录。", _table(["时间", "数量"], [])
    total = sum(row.get("count") or 0 for row in rows)
    scope = f"{year} 年" if year else "全部时间"
    grain = "月" if period == "month" else "年"
    listing = "、".join(
        f"{row['label']} {row['count']} 本" + (f"（{row.get('price', 0)} 元）" if purchase else "")
        for row in rows[-8:]
    )
    money = f"，购书花费 {sum(row.get('price') or 0 for row in rows):.2f} 元" if purchase else ""
    answer = f"{scope}按{grain}统计：共 {total} 本{money}。{'最近：' + listing if listing else ''}"
    columns = ["时间", "数量"] + (["花费"] if purchase else [])
    values = [
        [row["label"], row["count"]] + ([round(row.get("price") or 0, 2)] if purchase else [])
        for row in rows
    ]
    return answer, _table(columns, values)


def render_purchase_list(
    db: Session, query: StatsQuery, rows: Optional[list[dict]] = None
) -> tuple[str, dict]:
    from services.library_stats import purchased_books

    year = query.year
    month = query.month
    if rows is None:
        rows = purchased_books(db, year, month)
    label = (
        f"{year}-{month:02d}"
        if year and month
        else f"{year} 年" if year else "全部时间"
    )
    if not rows:
        return f"{label} 没有购书记录。", _table(["书名", "作者", "购入价"], [])
    titles = "、".join(f"《{row['title']}》" for row in rows[:20])
    answer = f"{label} 买了 {len(rows)} 本书：{titles}"
    if len(rows) > 20:
        answer += f" 等共 {len(rows)} 本"
    answer += "。"
    table = _table(
        ["书名", "作者", "购入价"],
        [
            [
                row["title"],
                "、".join(row["authors"]) or "—",
                row["purchase_price"] if row["purchase_price"] is not None else "—",
            ]
            for row in rows
        ],
    )
    return answer, table


def books_for_query(db: Session, query: StatsQuery) -> list[dict]:
    from services.library_stats import extreme_books, purchased_books

    metric = query.metric
    if metric == "purchase_list":
        return purchased_books(db, query.year, query.month)
    if metric == "extreme":
        return extreme_books(db, query.field or "price", query.order)
    return []


def render_extreme(
    db: Session, query: StatsQuery, rows: Optional[list[dict]] = None
) -> tuple[str, dict]:
    from services.library_stats import extreme_books

    field = query.field or "price"
    order = query.order
    labels = {
        "price": ("定价", "元"),
        "purchase_price": ("购入价", "元"),
        "pages": ("页数", "页"),
        "douban_score": ("豆瓣评分", ""),
        "book_count": ("册数", "册"),
    }
    label, unit = labels.get(field, ("定价", "元"))
    if rows is None:
        rows = extreme_books(db, field, order)
    if not rows:
        return f"没有可用的{label}数据。", _table(["书名", "作者", label], [])
    best = rows[0]
    value = best.get(field)
    shown = f"{value}{unit}" if unit else f"{value}"
    set_note = ""
    if field == "price" and best.get("book_count") and best["book_count"] > 1:
        set_note = f"，共 {best['book_count']} 册"
    answer = (
        f"书库中{label}{'最高' if order != 'min' else '最低'}的书是"
        f"《{best['title']}》（{label} {shown}{set_note}）。"
    )
    table = _table(
        ["书名", "作者", label],
        [
            [row["title"], "、".join(row["authors"]) or "—", row.get(field)]
            for row in rows
        ],
    )
    return answer, table


def render_stats(
    stats: dict,
    query: StatsQuery,
    db: Session,
    books: Optional[list[dict]] = None,
) -> tuple[str, dict]:
    overview = stats.get("overview", {})
    metric = query.metric
    if metric == "purchase_list":
        return render_purchase_list(db, query, books)
    if metric == "extreme":
        return render_extreme(db, query, books)

    if metric == "overview":
        answer = (
            f"书库共 {overview.get('total_books', 0)} 本书，"
            f"{overview.get('total_authors', 0)} 位作者，"
            f"{overview.get('total_publishers', 0)} 家出版社，"
            f"{overview.get('total_categories', 0)} 个分类；"
            f"总花费 {overview.get('total_spent', 0)} 元，"
            f"平均定价 {overview.get('avg_price', 0)} 元。"
        )
        table = _table(
            ["项目", "数值"],
            [
                ["藏书", overview.get("total_books", 0)],
                ["作者", overview.get("total_authors", 0)],
                ["出版社", overview.get("total_publishers", 0)],
                ["分类", overview.get("total_categories", 0)],
                ["总花费（元）", overview.get("total_spent", 0)],
                ["平均定价（元）", overview.get("avg_price", 0)],
            ],
        )
        return answer, table

    if metric == "average":
        answer = (
            f"平均定价 {overview.get('avg_price', 0)} 元，"
            f"平均购入价 {overview.get('avg_purchase_price', 0)} 元。"
        )
        return answer, _table(
            ["项目", "数值"],
            [
                ["平均定价（元）", overview.get("avg_price", 0)],
                ["平均购入价（元）", overview.get("avg_purchase_price", 0)],
            ],
        )

    if metric == "read_state":
        rows = _rows_from(stats.get("by_read_state", []))
        if not rows:
            return "还没有阅读状态数据。", _table(["状态", "数量"], [])
        total = sum(row["count"] for row in rows)
        parts = [
            f"{READ_STATE_LABELS.get(row['name'], row['name'])} {row['count']} 本"
            for row in rows
        ]
        answer = f"阅读状态分布（共 {total} 本）：" + "、".join(parts) + "。"
        if query.year or query.month:
            answer += " 阅读状态目前没有按时间记录。"
        return answer, _table(
            ["状态", "数量"],
            [[READ_STATE_LABELS.get(row["name"], row["name"]), row["count"]] for row in rows],
        )

    if metric == "top":
        group = query.group_by
        if group not in GROUP_SOURCES:
            return "这个统计维度暂不支持。", _table([], [])
        label, key = GROUP_SOURCES[group]
        data = stats.get(key, [])
        rows = _rows_from(data, query.limit)
        listing = "、".join(f"{row['name']} {row['count']} 本" for row in rows)
        return (
            f"{label}排行（前 {len(rows)} 名）：{listing}。",
            _table([label, "数量"], [[row["name"], row["count"]] for row in rows]),
        )

    if metric == "breakdown":
        group = query.group_by
        if group not in GROUP_SOURCES:
            return "这个统计维度暂不支持。", _table([], [])
        label, key = GROUP_SOURCES[group]
        data = stats.get(key, [])
        return _breakdown_answer(label, data, query.limit)


    if metric == "spend_month":
        year = query.year
        month = query.month
        label = f"{year}-{month:02d}"
        row = next(
            (
                item
                for item in stats.get("purchase_months", [])
                if item.get("year") == year and item.get("month") == month
            ),
            None,
        )
        if row is None:
            return f"{label} 没有购书记录。", _table(["时间", "数量", "花费"], [])
        return (
            f"{label} 购书 {row['count']} 本，花费 {row['price']} 元。",
            _table(["时间", "数量", "花费"], [[label, row["count"], row["price"]]]),
        )

    if metric == "timeline_month":
        year = query.year
        month = query.month
        label = f"{year}-{month:02d}"
        rows = [
            item
            for item in stats.get("timeline_months", [])
            if item.get("year") == year and item.get("month") == month
        ]
        if not rows:
            return f"{label} 没有入库记录。", _table(["时间", "数量"], [])
        total = sum(row["count"] for row in rows)
        return (
            f"{label} 新增 {total} 本书。",
            _table(["时间", "数量"], [[row["label"], row["count"]] for row in rows]),
        )

    if metric == "spend":
        year = query.year
        if year is not None:
            row = next(
                (item for item in stats.get("purchase_years", []) if item.get("year") == year),
                None,
            )
            if row is None:
                return f"{year} 年没有购书记录。", _table(["年份", "数量", "花费"], [])
            return (
                f"{year} 年购书 {row['count']} 本，花费 {row['price']} 元。",
                _table(["年份", "数量", "花费"], [[year, row["count"], row["price"]]]),
            )
        total_spent = overview.get("total_spent", 0)
        rows = sorted(
            stats.get("purchase_years", []), key=lambda row: row.get("year") or 0
        )
        listing = "、".join(f"{row['label']} 年 {row['price']} 元" for row in rows[-5:])
        return (
            f"购书总花费 {total_spent} 元。{'近年：' + listing + '。' if listing else ''}",
            _table(
                ["年份", "数量", "花费"],
                [[row["label"], row["count"], row["price"]] for row in rows[-10:]],
            ),
        )

    if metric in ("timeline", "purchase_timeline"):
        purchase = metric == "purchase_timeline"
        period = query.period
        key = ("purchase_" if purchase else "timeline_") + ("months" if period == "month" else "years")
        return _timeline_answer(
            stats.get(key, []), period, query.year, purchase
        )

    return "这个统计问题暂时还不会处理。", _table([], [])


def answer_stats_query(
    db: Session, value: Any, query: str
) -> Optional[dict[str, Any]]:
    """Validate one semantic stats value and render the answer/table/books."""
    from services.library_stats import EXTREME_FIELDS, build_book_stats

    parsed = dict(value or {}) if isinstance(value, dict) else {}
    if parsed.get("metric") not in STATS_METRICS:
        parsed = parse_stats_query(query) or {}
    if parsed.get("metric") in ("top", "breakdown") and "作者" not in query:
        hint = parse_stats_query(query)
        if hint and hint.get("metric") == "extreme":
            parsed = hint
    if not parsed:
        return None
    if parsed.get("metric") == "extreme" and "作者" in query:
        return None
    if parsed.get("metric") == "extreme" and parsed.get("field") not in EXTREME_FIELDS:
        fallback = parse_stats_query(query) or {}
        if fallback.get("metric") == "extreme":
            parsed = fallback
        else:
            return None
    if parsed.get("metric") in ("top", "breakdown") and parsed.get("group_by") not in STATS_GROUPS:
        fallback = parse_stats_query(query) or {}
        if fallback.get("group_by") in STATS_GROUPS:
            parsed = fallback
        else:
            return None
    if parsed.get("metric") in ("timeline", "purchase_timeline"):
        parsed.setdefault("period", "month")
    if parsed.get("metric") in ("spend_month", "timeline_month") and not (
        parsed.get("year") and parsed.get("month")
    ):
        fallback = parse_stats_query(query) or {}
        if fallback.get("metric") in ("spend_month", "timeline_month"):
            parsed = fallback
        else:
            return None
    if parsed.get("limit") is not None:
        try:
            parsed["limit"] = max(1, min(MAX_STATS_LIMIT, int(parsed["limit"])))
        except (TypeError, ValueError):
            parsed.pop("limit", None)
    if parsed.get("metric") == "spend_month" and wants_listing(query):
        fallback = parse_stats_query(query) or {}
        if fallback.get("metric") == "purchase_list":
            parsed = fallback
    if parsed.get("metric") == "purchase_list" and not (
        parsed.get("year") or parsed.get("month")
    ):
        fallback = parse_stats_query(query) or {}
        if fallback.get("metric") == "purchase_list":
            parsed = fallback

    stats_query = StatsQuery.from_dict(parsed)
    books = books_for_query(db, stats_query)
    answer, table = render_stats(
        build_book_stats(db), stats_query, db, books=books
    )
    if books:
        table = _table([], [])
    return {
        "answer": answer,
        "table": table,
        "books": books,
        "query": stats_query.to_dict(),
    }
