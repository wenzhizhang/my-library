"""Look up and merge Chinese book metadata from the configured sources."""

from __future__ import annotations

import re
from typing import Optional

from services.isbn_lookup import BookInfo, IsbnNotFoundError, normalize_isbn

from . import cache, covers, dangdang, douban, foreign
from .fields import (
    CORE_FIELDS,
    MergedMetadata,
    SourceResult,
    clean_text,
    is_empty,
    merge_results,
)

CACHE_TTL = 30 * 24 * 3600
_TITLE_NOISE = re.compile(r"[\s《》【】\[\]（）()：:，,、·\-—_]+")


def _needs_fallback(merged: MergedMetadata) -> bool:
    return any(field in merged.missing for field in CORE_FIELDS)


def _normalized_title(value: str) -> str:
    return _TITLE_NOISE.sub("", clean_text(value)).casefold()


def _candidate_score(
    candidate: SourceResult, title: str, author: str, publisher: str, publish_date: str
) -> float:
    wanted = _normalized_title(title)
    got = _normalized_title(candidate.fields.get("title_cn") or candidate.fields.get("title", ""))
    if not wanted or not got:
        return 0.0
    score = 0.0
    if wanted == got:
        score += 0.6
    elif wanted in got or got in wanted:
        score += 0.35
    else:
        return 0.0
    author_wanted = clean_text(author).casefold()
    authors = " ".join(candidate.fields.get("author_names") or []).casefold()
    if author_wanted and author_wanted in authors:
        score += 0.25
    if publisher and publisher.casefold() in clean_text(
        candidate.fields.get("publisher_name")
    ).casefold():
        score += 0.1
    if publish_date and clean_text(publish_date)[:4] in clean_text(
        candidate.fields.get("publish_date")
    ):
        score += 0.05
    return round(score, 3)


async def _download_cover(fields: dict, sources: dict, link: str) -> None:
    url = clean_text(fields.get("thumb_image"))
    if not url or url.startswith("/images/"):
        return
    source = sources.get("thumb_image") or ""
    referer = link or ("https://book.douban.com/" if source == "douban" else "")
    local = await covers.download(url, referer=referer)
    if local:
        fields["thumb_image"] = local


async def lookup_isbn_metadata(raw_isbn: str, *, use_cache: bool = True) -> MergedMetadata:
    isbn = normalize_isbn(raw_isbn)
    key = cache.cache_key("isbn", isbn)
    if use_cache:
        cached = cache.get(key)
        if cached:
            return MergedMetadata(
                fields=cached.get("fields", {}),
                sources=cached.get("sources", {}),
                missing=cached.get("missing", []),
                conflicts=cached.get("conflicts", {}),
                source=cached.get("source", ""),
                link=cached.get("link", ""),
                extra=cached.get("extra", {}),
            )

    results: list[SourceResult] = []
    douban_result = await douban.fetch(isbn)
    if douban_result is not None:
        results.append(douban_result)

    merged = merge_results(results)
    if _needs_fallback(merged):
        dangdang_result = await dangdang.fetch_by_isbn(isbn)
        if dangdang_result is not None:
            results.append(dangdang_result)
            merged = merge_results(results)

    if merged.missing and "title_cn" in merged.missing and not isbn.startswith("9787"):
        foreign_result = await foreign.fetch(isbn)
        if foreign_result is not None:
            results.append(foreign_result)
            merged = merge_results(results)

    if merged.fields:
        await _download_cover(merged.fields, merged.sources, merged.link)
        # Book covers are served through /api/media; an un-downloadable
        # external URL would render as a broken relative path in the library.
        if merged.fields.get("thumb_image") and not str(
            merged.fields["thumb_image"]
        ).startswith("/images/"):
            merged.fields.pop("thumb_image", None)
            merged.sources.pop("thumb_image", None)
            if "thumb_image" not in merged.missing:
                merged.missing.append("thumb_image")
        cache.set(key, merged.source or "unknown", merged.to_dict(), ttl=CACHE_TTL)
    return merged


def to_book_info(merged: MergedMetadata, isbn: str = "") -> BookInfo:
    fields = merged.fields
    return BookInfo(
        isbn=clean_text(fields.get("isbn")) or isbn,
        title=clean_text(fields.get("title")),
        title_cn=clean_text(fields.get("title_cn")),
        publisher_name=clean_text(fields.get("publisher_name")),
        publish_date=clean_text(fields.get("publish_date")),
        pages=fields.get("pages"),
        price=fields.get("price"),
        summary=clean_text(fields.get("summary") or fields.get("introduction")),
        introduction=clean_text(fields.get("introduction") or fields.get("summary")),
        thumb_image=clean_text(fields.get("thumb_image")),
        binding_type=clean_text(fields.get("binding_type")),
        douban_score=fields.get("douban_score"),
        author_names=list(fields.get("author_names") or []),
        translator=clean_text(fields.get("translator")),
        language=clean_text(fields.get("language")),
        source=merged.source,
        link=merged.link,
        catalog=clean_text(fields.get("catalog")),
        tag_names=list(fields.get("tags") or []),
        author_intro=clean_text(merged.extra.get("author_intro")),
    )


async def lookup_book_info(raw_isbn: str) -> BookInfo:
    merged = await lookup_isbn_metadata(raw_isbn)
    if not merged.fields.get("title_cn") and not merged.fields.get("title"):
        raise IsbnNotFoundError(f"No metadata found for ISBN {raw_isbn}")
    return to_book_info(merged, normalize_isbn(raw_isbn))


async def search_metadata(
    title: str = "",
    author: str = "",
    publisher: str = "",
    publish_date: str = "",
) -> list[dict]:
    candidates = await dangdang.search(title, author)
    scored: list[dict] = []
    for candidate in candidates:
        score = _candidate_score(candidate, title, author, publisher, publish_date)
        if score <= 0:
            continue
        scored.append(
            {
                "fields": candidate.fields,
                "sources": {key: candidate.source for key in candidate.fields},
                "link": candidate.link,
                "score": score,
            }
        )
    scored.sort(key=lambda item: item["score"], reverse=True)
    return scored[:3]
