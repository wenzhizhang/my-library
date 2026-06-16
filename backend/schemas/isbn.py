"""Pydantic schemas for the ISBN lookup API."""

from typing import Optional

from pydantic import BaseModel, ConfigDict


class IsbnLookupResponse(BaseModel):
    """Book metadata returned by ISBN lookup from external sources."""

    isbn: str
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
    author_names: list[str] = []
    author_ids: list[int] = []
    translator: str = ""
    language: str = ""
    source: str = ""
    link: str = ""
    catalog: str = ""
    tag_names: list[str] = []
    author_intro: str = ""
