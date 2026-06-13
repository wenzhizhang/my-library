from pydantic import BaseModel, ConfigDict, field_validator, model_validator
from typing import Optional, List
from datetime import datetime


class BookSimple(BaseModel):
    id: int
    title: str
    title_cn: Optional[str] = None
    thumb_image: Optional[str] = None
    isbn: Optional[str] = None
    authors: Optional[List[str]] = None

    model_config = ConfigDict(from_attributes=True)

    @field_validator("authors", mode="before")
    @classmethod
    def extract_author_names(cls, v):
        if v is None:
            return None
        return [str(a) for a in v]


class BookCollectionCreation(BaseModel):
    """书单创建模型
     - name: 书单名称
     - intro: 书单简介
    """
    name: str
    intro: Optional[str] = None


class BookCollectionUpdate(BaseModel):
    """书单更新模型
     - name: 书单名称
     - intro: 书单简介
    """
    name: Optional[str] = None
    intro: Optional[str] = None


class AddBookToCollection(BaseModel):
    """向书单添加图书"""
    book_id: int


class BatchAddBooks(BaseModel):
    """批量向书单添加图书"""
    book_ids: list[int]


class BookCollectionResponse(BaseModel):
    """书单响应模型
     - id: 书单ID
     - name: 书单名称
     - intro: 书单简介
     - created_at: 创建时间
     - total_books: 图书数量
     - books: 书单中的图书列表
    """
    id: int
    name: str
    intro: Optional[str] = None
    created_at: Optional[datetime] = None
    total_books: Optional[int] = None
    books: Optional[List[BookSimple]] = None

    model_config = ConfigDict(from_attributes=True)

    @model_validator(mode="after")
    def compute_total_books(self):
        if self.total_books is None and self.books is not None:
            self.total_books = len(self.books)
        return self
