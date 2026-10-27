"""Pydantic schemas for the ISBN lookup API."""

from typing import Optional

from pydantic import BaseModel, ConfigDict, Field


class IsbnLookupResponse(BaseModel):
    """Book metadata returned by ISBN lookup from external sources."""

    isbn: str
    title: str = ""
    title_cn: str = ""
    publisher_name: str = ""
    publisher_id: Optional[int] = None
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
    brand_id: Optional[int] = None
    brand_name: Optional[str] = None
    book_id: Optional[int] = None
    book_name: str = ""
    book_series_id: Optional[int] = None
    book_series_name: str = ""
    paper_type: str = ""
    book_count: Optional[int] = None
    compose_type: str = ""
    edition: str = ""
    printing_info: str = ""
    printed_number: Optional[int] = None
    category_id: Optional[int] = None
    category_path: Optional[str] = None


class IsbnLookupRequest(BaseModel):
    isbn: Optional[str] = None
    title: Optional[str] = None
    author: Optional[str] = None
    publisher: Optional[str] = None
    publish_date: Optional[str] = None


class IsbnLookupResult(BaseModel):
    fields: dict = Field(default_factory=dict)
    sources: dict = Field(default_factory=dict)
    missing: list[str] = Field(default_factory=list)
    conflicts: dict = Field(default_factory=dict)
    candidates: list[dict] = Field(default_factory=list)
