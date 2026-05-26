from pydantic import BaseModel, ConfigDict, field_validator
from typing import Optional, List


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


class CategoryCreation(BaseModel):
    """
    分类模型
     - name: 分类名称
     - parent: 父分类ID（可选）
     - intro: 分类简介
    """
    name: str
    parent: int | None = None
    intro: Optional[str] = None


class CategoryUpdate(BaseModel):
    """
    分类更新模型
     - name: 分类名称
     - parent: 父分类ID（可选）
     - intro: 分类简介
    """
    name: Optional[str] = None
    parent: Optional[int] = None
    intro: Optional[str] = None


class CategoryResponse(BaseModel):
    """
    分类响应模型
     - id: 分类ID
     - name: 分类名称
     - parent: 父分类ID（可选）
     - intro: 分类简介
     - depth: 分类深度（根分类为0，子分类为1，以此类推）
     - path: 分类路径（以逗号分隔的分类ID列表，从根分类到当前分类）
     - books: 分类下的图书列表
    """
    id: int
    name: str
    parent: Optional[int] = None
    intro: Optional[str] = None
    depth: Optional[int] = None
    path: Optional[str] = None
    books: Optional[List[BookSimple]]