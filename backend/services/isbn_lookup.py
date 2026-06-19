"""
Multi-source ISBN book metadata lookup.

Primary: Douban Books API v2 (requires DOUBAN_KEY env var).
Fallbacks: Open Library (free), Google Books (optional GOOGLE_BOOKS_API_KEY).

Rate-limited to 1 request per 2 seconds across all sources.
"""

import asyncio
import logging
import os
import re
import time
from dataclasses import dataclass, field
from typing import Any, Optional

import httpx

logging.basicConfig(level=logging.INFO, format="%(name)s: %(message)s")

logger = logging.getLogger(__name__)
SOURCE_TIMEOUT = 5.0
RATE_LIMIT_INTERVAL = 2.0  # seconds between requests to external APIs

_LAST_REQUEST_TIME: float = 0.0
_RATE_LOCK = asyncio.Lock()


class IsbnNotFoundError(Exception):
    """No source returned usable book data for this ISBN."""


@dataclass
class BookInfo:
    isbn: str = ""
    title: str = ""
    title_cn: str = ""
    publisher_name: str = ""
    publish_date: str = ""
    pages: Optional[int] = None
    price: Optional[float] = None
    summary: str = ""
    introduction: str = ""
    thumb_image: str = ""
    binding_type: str = ""
    douban_score: Optional[float] = None
    author_names: list[str] = field(default_factory=list)
    translator: str = ""
    language: str = ""
    source: str = ""
    link: str = ""
    catalog: str = ""
    tag_names: list[str] = field(default_factory=list)
    author_intro: str = ""


# ---------------------------------------------------------------------------
# ISBN helpers
# ---------------------------------------------------------------------------

_ISBN_CLEAN = re.compile(r"[^0-9Xx]")


def normalize_isbn(raw: str) -> str:
    """Strip hyphens/spaces and convert ISBN-10 to ISBN-13."""
    isbn = _ISBN_CLEAN.sub("", raw).upper()
    if not isbn:
        raise IsbnNotFoundError("ISBN is empty")
    if len(isbn) == 10:
        isbn = _isbn10_to_13(isbn)
    if len(isbn) != 13 or not isbn.isdigit():
        raise IsbnNotFoundError(f"Invalid ISBN after normalization: {raw}")
    return isbn


def _isbn10_to_13(isbn10: str) -> str:
    digits = "978" + isbn10[:9]
    total = sum(int(d) * (1 if i % 2 == 0 else 3) for i, d in enumerate(digits))
    check = (10 - (total % 10)) % 10
    return digits + str(check)


# ---------------------------------------------------------------------------
# Rate limiter
# ---------------------------------------------------------------------------

async def _rate_limit():
    """Ensure at least RATE_LIMIT_INTERVAL seconds between API calls."""
    global _LAST_REQUEST_TIME
    async with _RATE_LOCK:
        elapsed = time.monotonic() - _LAST_REQUEST_TIME
        if elapsed < RATE_LIMIT_INTERVAL:
            wait = RATE_LIMIT_INTERVAL - elapsed
            logger.debug("Rate limiting: waiting %.1fs", wait)
            await asyncio.sleep(wait)
        _LAST_REQUEST_TIME = time.monotonic()

# ---------------------------------------------------------------------------
# Source: Douban (PRIMARY)
# ---------------------------------------------------------------------------

DOUBAN_KEY = os.environ.get("DOUBAN_KEY", "")
DOUBAN_BASE = "https://api.douban.com/v2"


async def _fetch_douban(client: httpx.AsyncClient, isbn: str) -> Optional[BookInfo]:
    """Query Douban Books API v2 with apikey auth."""
    if not DOUBAN_KEY:
        logger.info("DOUBAN_KEY not set, skipping Douban")
        return None

    await _rate_limit()
    url = f"{DOUBAN_BASE}/book/isbn/{isbn}?apikey={DOUBAN_KEY}"

    try:
        r = await client.get(url)
        if r.status_code == 403:
            logger.info("Douban API returned 403 (key invalid or access denied)")
            return None
        r.raise_for_status()
        data = r.json()
    except Exception as exc:
        logger.info("Douban API lookup failed: %s", exc)
        return None

    if not data or not isinstance(data, dict):
        return None

    # Douban error response (code + msg)
    if data.get("code") and data.get("msg"):
        logger.info("Douban API error [%s]: %s", data.get("code"), data.get("msg"))
        return None

    info = BookInfo(source="douban")

    info.title = str(data.get("title", ""))
    info.title_cn = str(data.get("title", ""))
    info.publisher_name = str(data.get("publisher", ""))
    info.publish_date = _normalize_pubdate(str(data.get("pubdate", "")))
    info.pages = _as_int(data.get("pages"))

    price_str = data.get("price", "")
    info.price = _normalize_price(_parse_price(price_str))

    # Douban "summary" → book "introduction" (book.summary is RAG-generated)
    info.introduction = str(data.get("summary", ""))
    # Douban "author_intro" → used for new author creation
    info.author_intro = str(data.get("author_intro", ""))
    info.binding_type = str(data.get("binding", ""))

    images = data.get("images") or {}
    info.thumb_image = str(images.get("large", images.get("medium", "")))

    rating = data.get("rating") or {}
    score = rating.get("average")
    if score is not None:
        try:
            info.douban_score = float(score)
        except (ValueError, TypeError):
            pass

    for raw_name in data.get("author", []):
        if raw_name:
            name = _clean_author_name(str(raw_name))
            if name:
                info.author_names.append(name)
    translators = data.get("translator", [])
    if translators:
        info.translator = ", ".join(str(t) for t in translators)

    info.link = str(data.get("alt", ""))

    origin_title = data.get("origin_title", "")
    if origin_title and origin_title != info.title_cn:
        info.title = str(origin_title)

    info.language = str(data.get("language", ""))

    # Catalog (table of contents)
    info.catalog = str(data.get("catalog", ""))

    return info


# ---------------------------------------------------------------------------
# Source: Open Library (FALLBACK)
# ---------------------------------------------------------------------------

async def _fetch_openlibrary(client: httpx.AsyncClient, isbn: str) -> Optional[BookInfo]:
    """Query Open Library ISBN endpoint + Works for description."""
    await _rate_limit()
    try:
        r = await client.get(f"https://openlibrary.org/isbn/{isbn}.json")
        r.raise_for_status()
        data = r.json()
    except Exception as exc:
        logger.info("Open Library ISBN lookup failed: %s", exc)
        return None

    info = BookInfo(source="openlibrary")

    info.title = str(data.get("title", ""))
    info.publish_date = str(data.get("publish_date", ""))
    info.pages = _as_int(data.get("number_of_pages"))

    publishers = data.get("publishers") or []
    if publishers:
        info.publisher_name = str(publishers[0])

    authors = data.get("authors") or []
    for a in authors:
        name = a.get("name") if isinstance(a, dict) else str(a)
        if name:
            info.author_names.append(str(name))

    covers = data.get("covers") or []
    if covers:
        info.thumb_image = f"https://covers.openlibrary.org/b/id/{covers[0]}-M.jpg"

    works = data.get("works") or []
    if works:
        work_key = works[0].get("key") if isinstance(works[0], dict) else str(works[0])
        if work_key:
            try:
                await _rate_limit()
                wr = await client.get(f"https://openlibrary.org{work_key}.json")
                wr.raise_for_status()
                wdata = wr.json()
                desc = wdata.get("description")
                if isinstance(desc, dict):
                    desc = desc.get("value", "")
                if desc:
                    info.summary = str(desc)[:2000]
            except Exception as exc:
                logger.info("Open Library works lookup failed: %s", exc)

    subjects = data.get("subjects") or []
    lang_tags = [s.get("name", "") for s in subjects if isinstance(s, dict)]
    info.language = _guess_language(info.title, lang_tags)

    return info


# ---------------------------------------------------------------------------
# Source: Google Books (FALLBACK)
# ---------------------------------------------------------------------------

async def _fetch_google_books(client: httpx.AsyncClient, isbn: str) -> Optional[BookInfo]:
    """Query Google Books API."""
    api_key = os.environ.get("GOOGLE_BOOKS_API_KEY", "")
    if not api_key:
        logger.info("Google Books API key not set, skipping")
        return None

    await _rate_limit()
    url = f"https://www.googleapis.com/books/v1/volumes?q=isbn:{isbn}&key={api_key}"
    try:
        r = await client.get(url)
        r.raise_for_status()
        data = r.json()
    except Exception as exc:
        logger.info("Google Books API lookup failed: %s", exc)
        return None

    items = data.get("items") or []
    if not items:
        return None

    info = BookInfo(source="google_books")
    vi = items[0].get("volumeInfo", {})

    info.title = str(vi.get("title", ""))
    info.publisher_name = str(vi.get("publisher", ""))
    info.publish_date = str(vi.get("publishedDate", ""))
    info.pages = _as_int(vi.get("pageCount"))

    for name in vi.get("authors", []):
        if name:
            info.author_names.append(str(name))

    desc = vi.get("description", "")
    if desc:
        info.summary = str(desc)

    images = vi.get("imageLinks") or {}
    info.thumb_image = str(images.get("thumbnail", images.get("smallThumbnail", "")))

    info.language = str(vi.get("language", ""))

    identifiers = vi.get("industryIdentifiers") or []
    for ident in identifiers:
        if ident.get("type") == "ISBN_13":
            info.isbn = str(ident.get("identifier", ""))
            break

    return info


# ---------------------------------------------------------------------------
# Aggregation / main entry point
# ---------------------------------------------------------------------------

# Priority: Douban → OpenLibrary → GoogleBooks
_SOURCES = [
    ("douban", _fetch_douban),
    ("openlibrary", _fetch_openlibrary),
    ("google_books", _fetch_google_books),
]


def _merge(target: BookInfo, source: BookInfo) -> None:
    """Merge source into target — target keeps its own non-empty values."""
    for field_name in (
        "isbn", "title", "title_cn", "publisher_name", "publish_date",
        "summary", "introduction", "thumb_image", "binding_type",
        "translator", "language", "link", "catalog", "author_intro",
    ):
        src_val = getattr(source, field_name)
        tgt_val = getattr(target, field_name)
        if src_val and not tgt_val:
            setattr(target, field_name, src_val)

    if source.pages is not None and target.pages is None:
        target.pages = source.pages
    if source.price is not None and target.price is None:
        target.price = source.price
    if source.douban_score is not None and target.douban_score is None:
        target.douban_score = source.douban_score

    existing = set(target.author_names)
    for name in source.author_names:
        if name not in existing:
            target.author_names.append(name)
            existing.add(name)

    # Tag names: union
    existing_tags = set(target.tag_names)
    for tag in source.tag_names:
        if tag not in existing_tags:
            target.tag_names.append(tag)
            existing_tags.add(tag)

    if target.source and source.source and source.source not in target.source:
        target.source = f"{target.source}+{source.source}"
    elif source.source:
        target.source = source.source


async def lookup_isbn(raw_isbn: str) -> BookInfo:
    """Look up book metadata by ISBN from multiple sources.

    Primary: Douban (requires DOUBAN_KEY).  Falls back to Open Library
    and Google Books.  Rate-limited to 1 request/2s across all calls.

    Raises IsbnNotFoundError if no source returns usable data.
    """
    isbn = normalize_isbn(raw_isbn)

    merged = BookInfo(isbn=isbn)
    any_success = False

    async with httpx.AsyncClient(timeout=SOURCE_TIMEOUT) as client:
        for _name, fetcher in _SOURCES:
            try:
                result = await asyncio.wait_for(
                    fetcher(client, isbn),
                    timeout=SOURCE_TIMEOUT,
                )
            except asyncio.TimeoutError:
                logger.info("Source %s timed out", _name)
                continue
            except Exception as exc:
                logger.info("Source %s unexpected error: %s", _name, exc)
                continue

            if result is not None:
                _merge(merged, result)
                any_success = True
                # If Douban succeeded, skip fallbacks
                if _name == "douban":
                    break

    if not any_success:
        raise IsbnNotFoundError(f"No book info found for ISBN {isbn}")

    return merged


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _as_int(val: Any) -> Optional[int]:
    if val is None:
        return None
    try:
        return int(val)
    except (ValueError, TypeError):
        return None


def _parse_price(s: str) -> Optional[float]:
    """Parse a price string like '39.00元' or 'CNY 59.00' into float."""
    if not s:
        return None
    cleaned = re.sub(r"[^\d.]", "", str(s))
    try:
        return float(cleaned)
    except ValueError:
        return None

def _normalize_price(val: Optional[float]) -> Optional[float]:
    """Round price to 2 decimal places."""
    if val is None:
        return None
    return round(val, 2)


def _normalize_pubdate(raw: str) -> str:
    """Normalize pubdate to YYYY-MM-DD format.

    YYYY-M / YYYY-MM → YYYY-MM-01
    YYYY-M-D / YYYY-MM-D → YYYY-MM-0D / YYYY-0M-DD
    YYYY → YYYY-01-01
    YYYY-MM-DD → keep as-is
    """
    if not raw:
        return ""
    raw = raw.strip()
    # Try to match YYYY-M(M)-D(D) or YYYY-M(M)
    m = re.match(r"^(\d{4})-(\d{1,2})(?:-(\d{1,2}))?$", raw)
    if m:
        year, month, day = m.group(1), m.group(2), m.group(3)
        month = month.zfill(2)
        day = day.zfill(2) if day else "01"
        return f"{year}-{month}-{day}"
    if re.match(r"^\d{4}$", raw):
        return raw + "-01-01"
    return raw



async def fetch_publisher_intro(publisher_name: str) -> str:
    """Fetch publisher introduction from Baidu Baike."""
    await _rate_limit()
    url = f"https://baike.baidu.com/item/{publisher_name}"
    headers = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"}
    try:
        async with httpx.AsyncClient(timeout=SOURCE_TIMEOUT, follow_redirects=True) as client:
            r = await client.get(url, headers=headers)
            if r.status_code != 200:
                logger.info("Baidu Baike returned %d for %s", r.status_code, publisher_name)
                return ""
            # Extract intro from .lemma-summary div
            from bs4 import BeautifulSoup as _BS
            try:
                soup = _BS(r.text, "html.parser")
                summary = soup.select_one(".lemma-summary > div")
                if summary:
                    return summary.text.strip()[:2000]
            except Exception:
                pass
            return ""
    except Exception as exc:
        logger.info("Baidu Baike lookup failed for %s: %s", publisher_name, exc)
        return ""


def _clean_author_name(raw: str) -> str:
    """Clean author names from Douban.

    - Strip [宋] / (日) prefixes
    - If name has spaces: drop trailing Chinese segment (著/编/etc.)
    - Western names with spaces kept as-is
    """
    name = raw.strip()
    name = re.sub(r"^\[[^\]]+\]\s*", "", name)
    name = re.sub(r"^\([^\)]+\)\s*", "", name)
    # Space-containing names: if last segment is Chinese, drop it
    if " " in name:
        parts = name.rsplit(" ", 1)
        if re.search(r"[\u4e00-\u9fff]", parts[-1]):
            name = parts[0]
    return name.strip()
def _guess_language(title: str, subjects: list[str]) -> str:
    """Rough language guess from title content and subjects."""
    if re.search(r"[\u4e00-\u9fff]", title):
        return "zh-CN"
    if re.search(r"[\u3040-\u309f\u30a0-\u30ff]", title):
        return "ja"
    return ""
