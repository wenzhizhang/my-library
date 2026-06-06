"""Build a structured plain-text document from a Book + related entities.

This document is used both for embedding (vector search) and for populating
the FTS5 index fields.  Including relational context (author, publisher, …)
makes search results richer without extra joins at query time.
"""

from typing import Optional

from models import Book


def build_book_document(book: Book) -> str:
    """Assemble a book's information into a single structured document string.

    The output is a flat, labelled text block suitable for embedding.
    Empty/missing fields are omitted to keep the document compact.
    """
    parts: list[str] = []

    # ── Titles ──
    if book.title:
        title = book.title
        if book.title_cn:
            title = f"{book.title_cn} ({book.title})"
        else:
            title = book.title
        parts.append(f"书名: {title}")

    if book.title and book.title_cn:
        # both present → "书名" already covers it
        pass

    # ── Authors ──
    author_names = []
    for a in book.authors or []:
        if a.name_cn:
            label = a.name_cn
        else:
            label = a.name
        if a.dynasty:
            label = f"{a.dynasty}·{label}"
        elif a.nation:
            label = f"[{a.nation}] {label}"
        author_names.append(label)

    if author_names:
        parts.append(f"作者: {'; '.join(author_names)}")

    # ── Translator ──
    if book.translator:
        parts.append(f"译者: {book.translator}")

    # ── Publisher / Brand ──
    if book.publisher:
        parts.append(f"出版社: {book.publisher.name}")
    if book.brand:
        parts.append(f"品牌: {book.brand.name}")

    # ── Series ──
    if book.book_series:
        parts.append(f"丛书: {book.book_series.name}")

    # ── Category ──
    if book.category:
        parts.append(f"分类: {book.category.path or book.category.name}")

    # ── Tags ──
    if book.tags:
        parts.append(f"标签: {', '.join(book.tags)}")

    # ── Introduction / Summary / Catalog ──
    if book.introduction:
        parts.append(f"简介: {book.introduction}")
    if book.summary:
        parts.append(f"概述: {book.summary}")
    if book.catalog:
        parts.append(f"目录: {book.catalog}")

    # ── Edition / ISBN / Language / Pages ──
    meta_parts = []
    if book.isbn:
        meta_parts.append(f"ISBN: {book.isbn}")
    if book.edition:
        meta_parts.append(f"版次: {book.edition}")
    if book.language:
        meta_parts.append(f"语言: {book.language}")
    if book.pages:
        meta_parts.append(f"页数: {book.pages}")
    if meta_parts:
        parts.append(" | ".join(meta_parts))

    return "\n".join(parts)


def build_book_fts_fields(book: Book) -> dict[str, str]:
    """Extract individual FTS5 field values from a Book + relations.

    Returns a dict matching the FTS5 table columns so callers can pass it
    directly to ``upsert_book_fts()``.
    """
    # Author names (plain, no dynasty/nation decoration for FTS matching)
    authors_plain = []
    for a in book.authors or []:
        authors_plain.append(a.name_cn or a.name)
    # Also add dynasty/nation as searchable tokens
    for a in book.authors or []:
        if a.dynasty:
            authors_plain.append(a.dynasty)
        if a.nation:
            authors_plain.append(a.nation)

    return {
        "title": book.title or "",
        "title_cn": book.title_cn or "",
        "authors_text": "; ".join(authors_plain),
        "tags_text": ", ".join(book.tags or []),
        "introduction": book.introduction or "",
        "summary": book.summary or "",
        "catalog": book.catalog or "",
        "publisher_name": book.publisher.name if book.publisher else "",
        "series_name": book.book_series.name if book.book_series else "",
        "brand_name": book.brand.name if book.brand else "",
    }
