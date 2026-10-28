"""Foreign-ISBN fallback built on the existing ISBN lookup chain."""

from __future__ import annotations

import asyncio
from typing import Optional

from services.isbn_lookup import lookup_isbn as legacy_lookup

from .fields import SourceResult

SOURCE = "foreign"


async def fetch(isbn: str) -> Optional[SourceResult]:
    try:
        info = await asyncio.wait_for(legacy_lookup(isbn), timeout=14)
    except Exception:
        return None
    fields = {
        "isbn": info.isbn or isbn,
        "title": info.title or "",
        "title_cn": info.title_cn or info.title or "",
        "publisher_name": info.publisher_name or "",
        "publish_date": info.publish_date or "",
        "pages": info.pages,
        "price": info.price,
        "summary": info.summary or "",
        "introduction": info.introduction or "",
        "thumb_image": info.thumb_image or "",
        "binding_type": info.binding_type or "",
        "douban_score": info.douban_score,
        "author_names": info.author_names or [],
        "translator": info.translator or "",
        "language": info.language or "",
        "catalog": info.catalog or "",
        "tags": info.tag_names or [],
    }
    fields = {key: value for key, value in fields.items() if value not in (None, "", [])}
    if not fields.get("title_cn"):
        return None
    return SourceResult(source=SOURCE, fields=fields, link=info.link or "")
