"""Build structured text for embeddings and FTS fields.

``build_book_document`` is the full fallback document;
``build_book_embedding_document`` prefers a generated summary;
``build_book_fts_fields`` keeps the full fields for exact keyword recall.
"""

from models import Book

from .summarize import truncate_text

# BGE truncates its input at 512 tokens.  A CJK character is roughly one
# token (Latin text is cheaper per character), so character counts are a safe,
# slightly conservative proxy.  The margin covers the tokenizer's special
# tokens and drift between the proxy and the real tokenizer.
EMBEDDING_WINDOW_CHARS = 512
EMBEDDING_TOKEN_MARGIN = 16

# A pathological header (dozens of authors/tags) must not starve the summary to
# nothing; at that point the window is already lost.
_MIN_SUMMARY_CHARS = 120


def _title_text(book: Book) -> str:
    title = book.title_cn or book.title or ""
    if book.title_cn and book.title:
        title = f"{book.title_cn} ({book.title})"
    return title


def _author_names(book: Book) -> list[str]:
    names = []
    for a in book.authors or []:
        label = a.name_cn or a.name
        if a.dynasty:
            label = f"{a.dynasty}·{label}"
        elif a.nation:
            label = f"[{a.nation}] {label}"
        names.append(label)
    return names


def build_book_document(book: Book) -> str:
    """Assemble a book's information into a single structured document string.

    The output is a flat, labelled text block suitable for embedding.
    Empty/missing fields are omitted to keep the document compact.
    """
    parts: list[str] = []

    # ── Titles ──
    title = _title_text(book)
    if title:
        parts.append(f"书名: {title}")

    # ── Authors ──
    author_names = _author_names(book)
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


def _embedding_header(book: Book) -> list[str]:
    """Title/author/tag lines shared by both embedding documents."""
    parts = []
    title = _title_text(book)
    if title:
        parts.append(f"书名: {title}")
    author_names = _author_names(book)
    if author_names:
        parts.append(f"作者: {'; '.join(author_names)}")
    if book.tags:
        parts.append(f"标签: {', '.join(book.tags)}")
    return parts


def summary_char_budget(book: Book) -> int:
    """Characters a summary may use beside the header and still fit the window.

    The summary is embedded together with the title/author/tag header, so its
    cap has to shrink when that header grows.  Summarization uses this budget
    to store a summary that already fits; document building applies it again
    as the final guard for a header edited after the summary was written.
    """
    header = "\n".join(_embedding_header(book))
    overhead = len("摘要: ")
    if header:
        overhead += len(header) + 1
    return max(
        _MIN_SUMMARY_CHARS,
        EMBEDDING_WINDOW_CHARS - EMBEDDING_TOKEN_MARGIN - overhead,
    )


def build_book_embedding_document(book: Book) -> str:
    """Build the text that is embedded for one book.

    A generated ``summary`` is the whole-book representation; including the
    full introduction as well would push the summary past the model's 512
    token limit.  The summary is clipped to ``summary_char_budget`` so the
    header and the summary always fit the window — the stored summary keeps
    its full text.  When no summary exists, keep the previous full-document
    behaviour so reindexing remains backward compatible.
    """
    summary = (book.summary or "").strip()
    if not summary:
        return build_book_document(book)

    parts = _embedding_header(book)
    parts.append(f"摘要: {truncate_text(summary, summary_char_budget(book))}")
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
