"""Douban book page adapter.

The v2 API is closed (``apikey_required``), but ``book.douban.com/isbn/<ISBN>``
redirects to the subject page and carries the fields we need.  Covers are
hotlink protected: requests need ``Referer: https://book.douban.com/``.
"""

from __future__ import annotations

import re
from typing import Optional

from bs4 import BeautifulSoup

from .fields import SourceResult, clean_text, parse_int, parse_price
from .http import fetch as fetch_page

SOURCE = "douban"
BASE_URL = "https://book.douban.com/isbn/{isbn}"
REFERER = "https://book.douban.com/"
USER_AGENT = (
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
)
_LABELS = (
    "作者",
    "出版社",
    "出版年",
    "出版时间",
    "ISBN",
    "页数",
    "定价",
    "装帧",
    "译者",
    "副标题",
    "原作名",
    "丛书",
    "语言",
)


def _info_value(info_text: str, label: str) -> str:
    others = "|".join(lbl for lbl in _LABELS if lbl != label)
    match = re.search(
        rf"{label}\s*[:：]\s*(.*?)(?=\s*(?:{others})\s*[:：]|$)",
        info_text,
    )
    return clean_text(match.group(1)) if match else ""


def parse(html: str, isbn: str = "", link: str = "") -> Optional[SourceResult]:
    soup = BeautifulSoup(html, "html.parser")
    title_el = soup.select_one('span[property="v:itemreviewed"]')
    if title_el is None:
        return None
    title_cn = clean_text(title_el.get_text(" ", strip=True))
    if not title_cn:
        return None

    info_el = soup.select_one("div#info")
    info_text = info_el.get_text(" ", strip=True) if info_el else ""

    fields: dict = {
        "isbn": clean_text(_info_value(info_text, "ISBN")) or isbn,
        "title_cn": title_cn,
        "title": clean_text(_info_value(info_text, "原作名")) or title_cn,
    }

    authors_raw = _info_value(info_text, "作者")
    authors = [part.strip() for part in re.split(r"[/、]", authors_raw) if part.strip()]
    if not authors and info_el is not None:
        first_author = info_el.select_one("a")
        if first_author is not None:
            authors = [clean_text(first_author.get_text())]
    if authors:
        fields["author_names"] = authors

    publisher = _info_value(info_text, "出版社")
    if publisher:
        fields["publisher_name"] = publisher
    publish_date = _info_value(info_text, "出版时间") or _info_value(info_text, "出版年")
    if publish_date:
        fields["publish_date"] = publish_date
    price = parse_price(_info_value(info_text, "定价"))
    if price:
        fields["price"] = price
    pages = parse_int(_info_value(info_text, "页数"))
    if pages:
        fields["pages"] = pages
    binding = _info_value(info_text, "装帧")
    if binding:
        fields["binding_type"] = binding
    translator = _info_value(info_text, "译者")
    if translator:
        fields["translator"] = translator
    language = _info_value(info_text, "语言")
    if language:
        fields["language"] = language

    cover_el = soup.select_one("#mainpic img") or soup.select_one('meta[property="og:image"]')
    if cover_el is not None:
        cover = cover_el.get("src") or cover_el.get("content") or ""
        if cover:
            fields["thumb_image"] = clean_text(cover)

    summary_el = soup.select_one("#link-report span.all.hidden") or soup.select_one(
        "#link-report"
    ) or soup.select_one("div.intro")
    if summary_el is not None:
        summary = clean_text(summary_el.get_text(" ", strip=True))
        if summary:
            fields["summary"] = summary[:4000]

    score_el = soup.select_one("strong.rating_num") or soup.select_one(
        'strong[property="v:average"]'
    )
    if score_el is not None:
        score = clean_text(score_el.get_text())
        try:
            if score:
                fields["douban_score"] = float(score)
        except ValueError:
            pass

    for heading in soup.select("div.related_info h2"):
        if "作者简介" in heading.get_text():
            node = heading.find_next_sibling("div")
            if node is not None:
                fields["author_intro"] = clean_text(node.get_text(" "))[:4000]
            break

    tags = [
        clean_text(anchor.get_text())
        for anchor in soup.select("div.tags a, #db-tags-section a")
        if clean_text(anchor.get_text())
    ]
    if tags:
        fields["tags"] = tags[:10]

    return SourceResult(source=SOURCE, fields=fields, link=link)


async def fetch(isbn: str) -> Optional[SourceResult]:
    url = BASE_URL.format(isbn=isbn)
    response = await fetch_page(
        SOURCE,
        url,
        headers={
            "User-Agent": USER_AGENT,
            "Referer": REFERER,
            "Accept-Language": "zh-CN,zh;q=0.9",
        },
    )
    if response is None:
        return None
    try:
        return parse(response.text, isbn=isbn, link=str(response.url))
    except Exception:
        return None
