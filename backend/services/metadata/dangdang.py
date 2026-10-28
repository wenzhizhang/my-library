"""Dangdang search adapter (fallback for price / publisher / cover)."""

from __future__ import annotations

import re
from typing import Optional
from urllib.parse import quote

from bs4 import BeautifulSoup

from .fields import SourceResult, clean_text, parse_price
from .http import fetch

SOURCE = "dangdang"
SEARCH_URL = "https://search.dangdang.com/"
USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/124.0 Safari/537.36"
_DATE_RE = re.compile(r"(\d{4}-\d{2}-\d{2})")


def _decode(content: bytes) -> str:
    # Real responses are GB18030; fixtures and some mirrors are UTF-8.  Try
    # UTF-8 first because GB18030 bytes usually fail its strict decoder.
    for encoding in ("utf-8", "gb18030"):
        try:
            return content.decode(encoding)
        except UnicodeDecodeError:
            continue
    return content.decode("gb18030", errors="ignore")


def _items(soup: BeautifulSoup):
    return soup.select("ul.bigimg > li") or soup.select("#component_59 li")


def parse_search(content: bytes, isbn: str = "") -> list[SourceResult]:
    soup = BeautifulSoup(_decode(content), "html.parser")
    results: list[SourceResult] = []
    for item in _items(soup)[:20]:
        title_el = item.select_one('a[name="itemlist-title"]') or item.select_one(
            "p.name a"
        )
        if title_el is None:
            continue
        title = clean_text(title_el.get("title") or title_el.get_text(" "))
        if not title:
            continue
        link_el = item.select_one("a.pic") or title_el
        href = clean_text(link_el.get("href")) if link_el is not None else ""
        if href.startswith("//"):
            href = f"https:{href}"

        fields: dict = {"title_cn": title, "title": title}
        if isbn:
            fields["isbn"] = isbn

        author_el = item.select_one('a[name="itemlist-author"]')
        if author_el is not None:
            author = clean_text(author_el.get("title") or author_el.get_text(" "))
            if author:
                fields["author_names"] = [author]

        publisher_el = item.select_one('a[name="P_cbs"]')
        if publisher_el is not None:
            publisher = clean_text(publisher_el.get("title") or publisher_el.get_text(" "))
            if publisher:
                fields["publisher_name"] = publisher

        meta_text = clean_text(
            item.select_one("p.search_book_author").get_text(" ", strip=True)
            if item.select_one("p.search_book_author")
            else ""
        )
        date_match = _DATE_RE.search(meta_text)
        if date_match:
            fields["publish_date"] = date_match.group(1)

        list_price = item.select_one("span.search_pre_price")
        sale_price = item.select_one("span.search_now_price")
        price = parse_price(
            (list_price or sale_price).get_text(" ") if (list_price or sale_price) else ""
        )
        if price:
            fields["price"] = price

        cover_el = item.select_one("a.pic img")
        if cover_el is not None:
            cover = clean_text(cover_el.get("src") or "")
            if cover and "url_none" not in cover:
                fields["thumb_image"] = (
                    f"https:{cover}" if cover.startswith("//") else cover
                )

        detail_el = item.select_one("p.detail")
        if detail_el is not None:
            detail = clean_text(detail_el.get_text(" ", strip=True))
            if detail:
                fields["summary"] = detail[:4000]

        results.append(SourceResult(source=SOURCE, fields=fields, link=href))
    return results


async def _search(query: str) -> Optional[bytes]:
    # Dangdang reads the keyword as GB18030; httpx's UTF-8 params would turn
    # Chinese titles into mojibake and an empty result page.
    url = f"{SEARCH_URL}?key={quote(query.encode('gb18030'))}&act=input"
    response = await fetch(
        SOURCE,
        url,
        headers={"User-Agent": USER_AGENT, "Accept-Language": "zh-CN,zh;q=0.9"},
    )
    if response is None:
        return None
    return response.content


async def fetch_by_isbn(isbn: str) -> Optional[SourceResult]:
    content = await _search(isbn)
    if not content:
        return None
    candidates = parse_search(content, isbn=isbn)
    # Dangdang ranks an exact ISBN query by relevance; the first product is the
    # match we want.  The card still shows the source so the user can verify.
    return candidates[0] if candidates else None


async def search(title: str, author: str = "") -> list[SourceResult]:
    query = " ".join(part for part in (title, author) if part).strip()
    if not query:
        return []
    content = await _search(query)
    if not content:
        return []
    return parse_search(content)
