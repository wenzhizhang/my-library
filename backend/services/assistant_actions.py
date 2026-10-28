"""Draft → confirm → commit flow for assistant-driven book writes.

The assistant never writes on its own: ``build_*_draft`` only reads metadata and
the library, and ``commit_draft`` is the single place that creates or updates a
book.  Writes go through the same functions the REST API uses.
"""

from __future__ import annotations

import asyncio
import json
import os
import re
from datetime import datetime
from pathlib import Path
from typing import Any, Optional

from fastapi import HTTPException
from sqlalchemy.orm import Session

from models import Author, Book, Bookshelf, Brand, BookSeries, Category, Publisher
from rag.llm import deepseek_chat
from schemas.book import BookCreation, BookUpdate
from services.metadata import lookup_isbn_metadata, search_metadata
from services.metadata.fields import MergedMetadata
from services.metadata.fields import (
    CANONICAL_FIELDS,
    clean_text,
    parse_int,
    parse_price,
    parse_publish_date,
)
from services.sync_to_root import sync_author, sync_publisher

FIELD_ALIASES = {
    "书名": "title_cn",
    "标题": "title_cn",
    "中文名": "title_cn",
    "原名": "title",
    "原作名": "title",
    "作者": "author_names",
    "出版社": "publisher_name",
    "出版日期": "publish_date",
    "出版时间": "publish_date",
    "出版年": "publish_date",
    "定价": "price",
    "价格": "price",
    "页数": "pages",
    "装帧": "binding_type",
    "译者": "translator",
    "语言": "language",
    "简介": "summary",
    "内容简介": "summary",
    "目录": "catalog",
    "内容概述": "introduction",
    "概述": "introduction",
    "ISBN": "isbn",
    "isbn": "isbn",
    "封面": "thumb_image",
    "标签": "tags",
    "购入价格": "purchase_price",
    "购买价格": "purchase_price",
    "购入日期": "purchase_date",
    "购买日期": "purchase_date",
    "购买书店": "purchase_store",
    "书店": "purchase_store",
    "阅读状态": "read_state",
    "书架": "bookshelf_name",
    "分类": "category_path",
    "品牌": "brand_name",
    "系列": "book_series_name",
    "丛书": "book_series_name",
    "纸张": "paper_type",
    "用纸": "paper_type",
    "装帧形式": "compose_type",
    "册数": "book_count",
    "版次": "edition",
    "版号": "edition",
    "印次": "printing_info",
    "印数": "printed_number",
    "豆瓣评分": "douban_score",
    "链接": "link",
    "登记": "registered",
    "心愿单": "in_wish",
}
EXTRA_EDIT_FIELDS = (
    "purchase_price",
    "purchase_date",
    "purchase_store",
    "read_state",
    "bookshelf_name",
    "category_path",
    "brand_name",
    "book_series_name",
    "paper_type",
    "compose_type",
    "book_count",
    "edition",
    "printing_info",
    "printed_number",
    "registered",
    "in_wish",
    "link",
)
DRAFT_FIELDS = tuple(dict.fromkeys((*CANONICAL_FIELDS, *EXTRA_EDIT_FIELDS)))
SCALAR_BOOK_FIELDS = (
    "isbn",
    "title_cn",
    "title",
    "translator",
    "binding_type",
    "paper_type",
    "language",
    "compose_type",
    "summary",
    "introduction",
    "catalog",
    "thumb_image",
    "link",
    "edition",
    "printing_info",
    "read_state",
    "purchase_store",
)
NUMERIC_BOOK_FIELDS = (
    "pages",
    "price",
    "purchase_price",
    "book_count",
    "printed_number",
    "douban_score",
)
DATE_BOOK_FIELDS = ("publish_date", "purchase_date")
BOOL_BOOK_FIELDS = ("registered", "in_wish")
NUMERIC_FIELDS = {
    "pages",
    "price",
    "purchase_price",
    "book_count",
    "printed_number",
    "douban_score",
}
BOOL_FIELDS = {"registered", "in_wish"}
DATE_FIELDS = {"publish_date", "purchase_date"}
LIST_FIELDS = {"author_names", "tags"}
READ_STATES = {
    "未读": "unread",
    "在读": "reading",
    "阅读中": "reading",
    "已读": "read",
    "读完": "read",
}
_FIELD_PATTERN = "|".join(sorted(FIELD_ALIASES, key=len, reverse=True))
# Keep in sync with frontend/src/components/assistant/useAssistantChat.js.
_ACTION_WORDS = ("录入", "新增", "添加", "加入", "收录", "入库", "建档")
_EDIT_WORDS = ("修改", "更正", "更新", "改一下", "改改", "编辑", "改成", "改为", "换成", "设为")
_MAX_LEN = {
    "title_cn": 255,
    "title": 255,
    "isbn": 50,
    "publisher_name": 255,
    "binding_type": 64,
    "translator": 255,
    "language": 64,
    "summary": 8000,
    "introduction": 8000,
    "catalog": 8000,
    "thumb_image": 500,
    "purchase_store": 255,
    "read_state": 32,
    "bookshelf_name": 255,
    "category_path": 500,
    "brand_name": 255,
    "book_series_name": 255,
    "paper_type": 64,
    "compose_type": 64,
    "edition": 255,
    "printing_info": 255,
    "link": 500,
}


def _find_isbn(message: str) -> str:
    from services.isbn_lookup import IsbnNotFoundError, normalize_isbn

    for raw in re.findall(r"[0-9Xx][0-9Xx\s-]{8,16}[0-9Xx]", message or ""):
        try:
            return normalize_isbn(raw)
        except (IsbnNotFoundError, ValueError):
            continue
    return ""


def _has_any(message: str, words: tuple[str, ...]) -> bool:
    return any(word in (message or "") for word in words)


def _extract_title(message: str) -> str:
    match = re.search(r"《([^》]{1,120})》", message or "")
    if match:
        return match.group(1).strip()
    text = re.sub(
        r"《|》|修改|更正|更新|编辑|录入|新增|添加|收录|入库|建档|补全|完善|元数据",
        " ",
        message or "",
    )
    text = re.split(
        r"的?(?:出版社|作者|出版日期|出版时间|定价|价格|页数|装帧|译者|语言|简介|ISBN|信息)",
        text,
    )[0]
    text = text.replace("这本书", " ").replace("该书", " ")
    return clean_text(text)[:120]


def _coerce_number(key: str, value: Any) -> Optional[float | int]:
    if key in ("pages", "book_count", "printed_number"):
        return parse_int(value)
    return parse_price(value)


def _sync_title(fields: dict[str, Any], old_title: Any) -> None:
    """Keep a copied original title in step when title_cn changes."""
    if "title_cn" not in fields:
        return
    title = fields.get("title")
    if title in (None, "", old_title):
        fields["title"] = fields["title_cn"]


def clean_fields(raw: dict[str, Any]) -> dict[str, Any]:
    fields: dict[str, Any] = {}
    for key, value in (raw or {}).items():
        if key not in DRAFT_FIELDS:
            continue
        if key in LIST_FIELDS:
            items = value if isinstance(value, (list, tuple)) else [value]
            cleaned = [clean_text(item) for item in items if clean_text(item)]
            if cleaned:
                fields[key] = cleaned[:20]
            continue
        if key in NUMERIC_FIELDS:
            number = _coerce_number(key, value)
            if number is not None:
                fields[key] = number
            continue
        if key in BOOL_FIELDS:
            if isinstance(value, str):
                truthy = value.strip().lower() not in ("", "0", "false", "no", "否", "未")
                fields[key] = truthy
            else:
                fields[key] = bool(value)
            continue
        text = clean_text(value)
        if not text:
            continue
        fields[key] = text[: _MAX_LEN.get(key, 255)]
    return fields


def _book_summary(book: Book) -> dict[str, Any]:
    return {
        "book_id": book.id,
        "title_cn": book.title_cn or book.title or "",
        "title": book.title or "",
        "authors": [author.name_cn or author.name for author in book.authors],
        "updated_at": book.updated_at.isoformat() if book.updated_at else None,
    }


def _draft(
    action: str,
    fields: dict[str, Any],
    sources: dict[str, str],
    *,
    isbn: str = "",
    missing: Optional[list[str]] = None,
    conflicts: Optional[dict] = None,
    existing: Optional[dict] = None,
    updated_at: Optional[str] = None,
    book_id: Optional[int] = None,
    current: Optional[dict] = None,
    diff: Optional[list[dict]] = None,
    lookup_failed: bool = False,
    link: str = "",
    message: str = "",
) -> dict[str, Any]:
    return {
        "action": action,
        "book_id": book_id,
        "isbn": isbn,
        "fields": fields,
        "sources": sources,
        "missing": missing or [],
        "conflicts": conflicts or {},
        "existing": existing,
        "updated_at": updated_at,
        "current": current or {},
        "diff": diff or [],
        "lookup_failed": lookup_failed,
        "link": link,
        "message": message,
    }


async def build_create_draft(db: Session, raw_isbn: str) -> dict[str, Any]:
    merged = await lookup_isbn_metadata(raw_isbn)
    isbn = clean_text(merged.fields.get("isbn")) or raw_isbn
    fields = clean_fields(merged.fields)
    fields["isbn"] = isbn
    existing = db.query(Book).filter(Book.isbn == isbn).first()
    missing = [name for name in CANONICAL_FIELDS if name not in fields]
    return _draft(
        "create_book",
        fields,
        merged.sources,
        isbn=isbn,
        missing=missing,
        conflicts=merged.conflicts,
        existing=_book_summary(existing) if existing else None,
        lookup_failed=not fields.get("title_cn"),
        link=merged.link,
    )


def _coerce_change(field: str, value: Any) -> Any:
    text = clean_text(value).strip("。.!！")
    if not text:
        return None
    if field in LIST_FIELDS:
        separator = r"[、,，/和]" if field == "author_names" else r"[、,，/\s]+"
        items = [part.strip() for part in re.split(separator, text) if part.strip()]
        return items or None
    if field in NUMERIC_FIELDS:
        return _coerce_number(field, text)
    if field in BOOL_FIELDS:
        return text.lower() not in ("否", "未", "false", "0", "no", "移出", "取消")
    if field == "read_state":
        return READ_STATES.get(text, text)
    if field == "isbn":
        from services.isbn_lookup import IsbnNotFoundError, normalize_isbn

        try:
            return normalize_isbn(text)
        except (IsbnNotFoundError, ValueError):
            return None
    return text


def _parse_field_changes(message: str) -> dict[str, Any]:
    text = message or ""
    matches = list(re.finditer(rf"(?:把|将)?\s*({_FIELD_PATTERN})", text))
    changes: dict[str, Any] = {}
    for index, match in enumerate(matches):
        field = FIELD_ALIASES[match.group(1)]
        value_start = match.end()
        value_end = matches[index + 1].start() if index + 1 < len(matches) else len(text)
        raw = text[value_start:value_end].strip(" ，,；;。")
        raw = re.sub(
            r"^(?:改成|改为|设为|换成|写成|修改为|更新为|[:：]|是|为)\s*", "", raw
        )
        value = _coerce_change(field, raw)
        if value is not None:
            changes[field] = value
    return changes


def _diff(current: dict[str, Any], fields: dict[str, Any]) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for field, value in fields.items():
        old = current.get(field)
        if isinstance(old, (list, tuple)):
            old = "、".join(str(item) for item in old)
        if isinstance(value, (list, tuple)):
            new = "、".join(str(item) for item in value)
        else:
            new = value
        rows.append({"field": field, "current": "" if old is None else old, "value": new})
    return rows


def _apply_change(draft: dict[str, Any], field: str, value: Any) -> dict[str, Any]:
    draft = json.loads(json.dumps(draft, ensure_ascii=False))
    fields = draft.setdefault("fields", {})
    old_title = fields.get("title_cn")
    fields[field] = value
    _sync_title(fields, old_title)
    draft.setdefault("sources", {})[field] = "user"
    draft["missing"] = [name for name in draft.get("missing", []) if name != field]
    draft.setdefault("conflicts", {}).pop(field, None)
    draft["diff"] = _diff(draft.get("current") or {}, fields)
    return draft


async def revise_draft(draft: dict[str, Any], message: str) -> dict[str, Any]:
    changes = _parse_field_changes(message)
    if changes:
        for field, value in changes.items():
            draft = _apply_change(draft, field, value)
        return draft

    # LLM fallback for free-form instructions ("出版社换成中华书局吧").
    if os.environ.get("DEEPSEEK_API_KEY", "").strip():
        allowed = "|".join(sorted(FIELD_ALIASES))
        system = (
            "你是图书信息抽取器。用户会要求修改一本书的字段。只返回 JSON，"
            f'格式 {{"field": "{allowed}", "value": ...}}；无法识别时返回 {{}}。不要解释。'
        )
        prompt = f"当前字段：{json.dumps(draft.get('fields', {}), ensure_ascii=False)}\n用户指令：{message}"
        raw = await asyncio.to_thread(
            deepseek_chat, prompt, system=system, max_tokens=200, temperature=0
        )
        if raw:
            match = re.search(r"\{.*\}", raw, flags=re.S)
            if match:
                try:
                    payload = json.loads(match.group())
                except json.JSONDecodeError:
                    payload = {}
                field = FIELD_ALIASES.get(clean_text(payload.get("field")))
                value = payload.get("value")
                if field and value not in (None, ""):
                    coerced = _coerce_change(field, value)
                    if coerced is not None:
                        return _apply_change(draft, field, coerced)

    draft = json.loads(json.dumps(draft, ensure_ascii=False))
    draft["message"] = "没有识别出要修改的字段，请说“出版社改成中华书局”这样的格式。"
    return draft


def _book_current(book: Book) -> dict[str, Any]:
    def stamp(value):
        return value.isoformat() if hasattr(value, "isoformat") else (value or "")

    current: dict[str, Any] = {}
    for key in SCALAR_BOOK_FIELDS:
        current[key] = getattr(book, key, "") or ""
    for key in NUMERIC_BOOK_FIELDS:
        current[key] = getattr(book, key, None)
    for key in BOOL_BOOK_FIELDS:
        current[key] = getattr(book, key, None)
    for key in DATE_BOOK_FIELDS:
        current[key] = stamp(getattr(book, key, None))
    current["author_names"] = [author.name_cn or author.name for author in book.authors]
    current["publisher_name"] = book.publisher.name if book.publisher else ""
    current["bookshelf_name"] = book.bookshelf.name if book.bookshelf else ""
    current["category_path"] = book.category.path if book.category else ""
    current["brand_name"] = book.brand.name if book.brand else ""
    current["book_series_name"] = book.book_series.name if book.book_series else ""
    current["tags"] = book.tags or []
    return current


def build_update_draft(book: Book, message: str) -> dict[str, Any]:
    changes = _parse_field_changes(message)
    current = _book_current(book)
    old_title = current.get("title_cn")
    if "title_cn" in changes and current.get("title") == old_title:
        changes.setdefault("title", changes["title_cn"])
    return _draft(
        "update_book",
        changes,
        {field: "user" for field in changes},
        isbn=book.isbn or "",
        book_id=book.id,
        updated_at=book.updated_at.isoformat() if book.updated_at else None,
        current=current,
        diff=_diff(current, changes),
        message="" if changes else "要修改这本书的哪个字段？例如“出版社改成中华书局”。",
    )


async def build_fill_draft(db: Session, book: Book) -> dict[str, Any]:
    merged: Optional[MergedMetadata] = None
    if book.isbn:
        try:
            merged = await lookup_isbn_metadata(book.isbn)
        except Exception:
            merged = None
    if (merged is None or not merged.fields) and (book.title_cn or book.title):
        authors = " ".join(author.name_cn or author.name for author in book.authors)
        candidates = await search_metadata(
            book.title_cn or book.title or "", authors
        )
        if candidates:
            best = candidates[0]
            merged = MergedMetadata(
                fields=dict(best["fields"]),
                sources=dict(best["sources"]),
                source="dangdang",
            )
    current = _book_current(book)
    if merged is None:
        return _draft(
            "update_book",
            {},
            {},
            isbn=book.isbn or "",
            book_id=book.id,
            updated_at=book.updated_at.isoformat() if book.updated_at else None,
            current=current,
            message="没有查到可用的元数据，无法自动补全。",
        )

    proposals: dict[str, Any] = {}
    sources: dict[str, str] = {}
    for field in CANONICAL_FIELDS:
        value = merged.fields.get(field)
        if value in (None, "", []):
            continue
        if current.get(field) not in (None, "", []):
            continue
        proposals[field] = value
        sources[field] = merged.sources.get(field, merged.source or "")
    return _draft(
        "update_book",
        proposals,
        sources,
        isbn=book.isbn or "",
        book_id=book.id,
        updated_at=book.updated_at.isoformat() if book.updated_at else None,
        current=current,
        diff=_diff(current, proposals),
        message=(
            f"将补全 {len(proposals)} 个字段，确认后写入。"
            if proposals
            else "没有可补全的字段。"
        ),
    )


def find_books(db: Session, query: str) -> list[dict[str, Any]]:
    title = _extract_title(query)
    if not title:
        return []
    books = (
        db.query(Book)
        .filter(
            (Book.title_cn.ilike(f"%{title}%")) | (Book.title.ilike(f"%{title}%"))
        )
        .limit(5)
        .all()
    )
    return [_book_summary(book) for book in books]


def _author_ids(db: Session, names: list[str]) -> list[int]:
    ids: list[int] = []
    for name in names:
        author = (
            db.query(Author)
            .filter((Author.name == name) | (Author.name_cn == name))
            .first()
        )
        if author is None:
            author = Author(name=name, name_cn=name, nation="无")
            db.add(author)
            db.flush()
            db.refresh(author)
            try:
                sync_author(author)
            except Exception:
                pass
        ids.append(author.id)
    return ids


def _publisher_id(db: Session, name: str) -> Optional[int]:
    if not name:
        return None
    publisher = db.query(Publisher).filter(Publisher.name == name).first()
    if publisher is None:
        publisher = Publisher(name=name)
        db.add(publisher)
        db.flush()
        db.refresh(publisher)
        try:
            sync_publisher(publisher)
        except Exception:
            pass
    return publisher.id


def _named_id(db: Session, model, name: str, label: str) -> int:
    obj = db.query(model).filter(model.name == name).first()
    if obj is None:
        raise HTTPException(
            status_code=422, detail=f"未找到{label}「{name}」，请先创建{label}。"
        )
    return obj.id


def _category_id(db: Session, value: str) -> int:
    category = (
        db.query(Category)
        .filter((Category.path == value) | (Category.name == value))
        .first()
    )
    if category is None:
        raise HTTPException(
            status_code=422, detail=f"未找到分类「{value}」，请先在分类页创建。"
        )
    return category.id


def _book_payload(
    fields: dict[str, Any], db: Session, *, for_create: bool = False
) -> dict[str, Any]:
    payload: dict[str, Any] = {}
    if for_create:
        payload.update(
            isbn=fields.get("isbn", ""),
            title_cn=fields.get("title_cn") or fields.get("title") or "",
            title=fields.get("title") or fields.get("title_cn") or "",
        )
    for key in SCALAR_BOOK_FIELDS:
        if fields.get(key) not in (None, ""):
            payload[key] = fields[key]
    for key in NUMERIC_BOOK_FIELDS:
        if key in fields and fields[key] is not None:
            payload[key] = fields[key]
    for key in BOOL_BOOK_FIELDS:
        if key in fields:
            payload[key] = bool(fields[key])
    for key in DATE_BOOK_FIELDS:
        if fields.get(key):
            parsed = parse_publish_date(fields[key])
            if parsed is not None:
                payload[key] = datetime.combine(parsed, datetime.min.time())
    if fields.get("tags"):
        payload["tags"] = fields["tags"]
    if for_create:
        payload["author_ids"] = _author_ids(db, fields.get("author_names") or [])
    elif fields.get("author_names"):
        payload["author_ids"] = _author_ids(db, fields["author_names"])
    if fields.get("publisher_name"):
        publisher_id = _publisher_id(db, fields["publisher_name"])
        if publisher_id is not None:
            payload["publisher_id"] = publisher_id
    if fields.get("brand_name"):
        payload["brand_id"] = _named_id(db, Brand, fields["brand_name"], "品牌")
    if fields.get("book_series_name"):
        payload["book_series_id"] = _named_id(
            db, BookSeries, fields["book_series_name"], "系列"
        )
    if fields.get("category_path"):
        payload["category_id"] = _category_id(db, fields["category_path"])
    if fields.get("bookshelf_name"):
        payload["bookshelf_id"] = _named_id(
            db, Bookshelf, fields["bookshelf_name"], "书架"
        )
    return payload


def _audit(user_id: int, action: str, book_id: Optional[int], fields: dict, sources: dict, status: str) -> None:
    path = Path(os.environ.get("DATA_DIR", "/app/data")) / "assistant_actions.jsonl"
    try:
        path.parent.mkdir(parents=True, exist_ok=True)
        with path.open("a", encoding="utf-8") as handle:
            handle.write(
                json.dumps(
                    {
                        "user_id": user_id,
                        "action": action,
                        "book_id": book_id,
                        "fields": sorted(fields.keys()),
                        "sources": sources,
                        "status": status,
                        "at": datetime.now().isoformat(timespec="seconds"),
                    },
                    ensure_ascii=False,
                )
                + "\n"
            )
    except OSError:
        pass


def commit_draft(
    db: Session, draft: dict[str, Any], user_id: int, duplicate_choice: Optional[str] = None
) -> dict[str, Any]:
    action = draft.get("action")
    if action not in ("create_book", "update_book"):
        raise HTTPException(status_code=422, detail="Unknown draft action")
    fields = clean_fields(draft.get("fields") or {})
    if action == "update_book" and not fields:
        raise HTTPException(status_code=422, detail="No changes to save")
    from services.isbn_lookup import IsbnNotFoundError, normalize_isbn

    if action == "create_book":
        try:
            fields["isbn"] = normalize_isbn(draft.get("isbn") or fields.get("isbn") or "")
        except (IsbnNotFoundError, ValueError) as exc:
            raise HTTPException(status_code=422, detail="Invalid ISBN") from exc
    elif fields.get("isbn"):
        try:
            fields["isbn"] = normalize_isbn(fields["isbn"])
        except (IsbnNotFoundError, ValueError) as exc:
            raise HTTPException(status_code=422, detail="Invalid ISBN") from exc

    if action == "update_book" or duplicate_choice == "update":
        book_id = draft.get("book_id")
        if action == "create_book":
            existing = db.query(Book).filter(Book.isbn == fields["isbn"]).first()
            book_id = existing.id if existing else None
        book = db.query(Book).filter(Book.id == book_id).first() if book_id else None
        if book is None:
            raise HTTPException(status_code=404, detail="Book not found")
        expected = draft.get("updated_at")
        if expected and book.updated_at and book.updated_at.isoformat() != expected:
            raise HTTPException(status_code=409, detail="Book changed since the draft was built")
        payload = _book_payload(fields, db)
        # Keep the existing ISBN when the draft only changed other fields.
        update = BookUpdate(**payload)
        from routers.book import update_book as update_book_route

        try:
            result = update_book_route(book.id, update, db, user_id)
        except Exception:
            db.rollback()
            raise
        _audit(user_id, "update_book", book.id, fields, draft.get("sources", {}), "updated")
        return {"status": "updated", "book_id": book.id, "book": result}

    if duplicate_choice == "abandon":
        _audit(user_id, "create_book", None, fields, draft.get("sources", {}), "abandoned")
        return {"status": "abandoned"}

    existing = db.query(Book).filter(Book.isbn == fields["isbn"]).first()
    if existing is not None and duplicate_choice not in ("add", "update"):
        raise HTTPException(
            status_code=409,
            detail={
                "message": "A book with this ISBN already exists",
                "existing": _book_summary(existing),
            },
        )
    if not fields.get("title_cn") and not fields.get("title"):
        raise HTTPException(status_code=422, detail="Title is required")
    payload = _book_payload(fields, db, for_create=True)
    try:
        book_in = BookCreation(**payload)
        from routers.book import create_book as create_book_route

        result = create_book_route(book_in, db, user_id)
    except Exception:
        db.rollback()
        raise
    _audit(user_id, "create_book", result.get("id"), fields, draft.get("sources", {}), "created")
    return {"status": "created", "book_id": result.get("id"), "book": result}


async def handle_action(
    db: Session,
    message: str,
    draft: Optional[dict] = None,
    focus_book_id: Optional[int] = None,
    action: Optional[str] = None,
    page_context: Optional[dict] = None,
) -> dict[str, Any]:
    # Editing is book-scoped; a non-book page context does not identify a book.
    if page_context and page_context.get("kind") == "book":
        focus_book_id = focus_book_id or int(page_context.get("id") or 0)
        page_context = None
    if draft:
        return {"intent": "revise_draft", "draft": await revise_draft(draft, message)}

    if action == "edit_book":
        book = (
            db.query(Book).filter(Book.id == focus_book_id).first()
            if focus_book_id
            else None
        )
        if book is None:
            return {
                "intent": "none",
                "draft": None,
                "candidates": [],
                "message": "没有找到要修改的书。",
            }
        return {
            "intent": "update_book",
            "draft": build_update_draft(book, ""),
            "candidates": [],
            "message": "",
        }

    if action == "refresh_draft":
        book = (
            db.query(Book).filter(Book.id == focus_book_id).first()
            if focus_book_id
            else None
        )
        if book is None:
            return {
                "intent": "none",
                "draft": None,
                "candidates": [],
                "message": "没有找到要刷新的书。",
            }
        db.refresh(book)
        fresh = build_update_draft(book, "")
        for field, value in ((draft or {}).get("fields") or {}).items():
            if field in DRAFT_FIELDS:
                fresh = _apply_change(fresh, field, value)
        return {
            "intent": "update_book",
            "draft": fresh,
            "candidates": [],
            "message": "",
        }

    if action == "fill_missing":
        book = (
            db.query(Book).filter(Book.id == focus_book_id).first()
            if focus_book_id
            else None
        )
        if book is None:
            return {
                "intent": "none",
                "draft": None,
                "candidates": [],
                "message": "没有找到要补全的书。",
            }
        return {
            "intent": "update_book",
            "draft": await build_fill_draft(db, book),
            "candidates": [],
            "message": "",
        }

    isbn = _find_isbn(message)
    edit_requested = _has_any(message, _EDIT_WORDS)
    fill_requested = _has_any(message, ("补全", "完善"))
    create_requested = _has_any(message, _ACTION_WORDS)

    if edit_requested or fill_requested:
        book = (
            db.query(Book).filter(Book.id == focus_book_id).first()
            if focus_book_id
            else None
        )
        if book is None and isbn:
            book = db.query(Book).filter(Book.isbn == isbn).first()
        if book is None:
            candidates = find_books(db, message)
            if not candidates:
                return {
                    "intent": "none",
                    "draft": None,
                    "candidates": [],
                    "message": "没有找到要修改的书，请引用一本书或说出书名。",
                }
            if len(candidates) > 1:
                return {
                    "intent": "update_book",
                    "draft": None,
                    "candidates": candidates,
                    "message": "找到多本书，请先选择要修改的那一本。",
                }
            book = db.query(Book).filter(Book.id == candidates[0]["book_id"]).first()
        if fill_requested:
            return {
                "intent": "update_book",
                "draft": await build_fill_draft(db, book),
                "candidates": [],
                "message": "",
            }
        return {
            "intent": "update_book",
            "draft": build_update_draft(book, message),
            "candidates": [],
            "message": "",
        }

    if isbn and create_requested:
        return {
            "intent": "create_book",
            "draft": await build_create_draft(db, isbn),
            "candidates": [],
            "message": "",
        }

    return {"intent": "none", "draft": None, "candidates": [], "message": ""}
