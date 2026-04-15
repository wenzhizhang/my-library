from pydantic import BaseModel, ConfigDict
from typing import Optional, List


class BookSimple(BaseModel):
    id: int
    title: str
    title_cn: Optional[str] = None
    thumb_image: Optional[str] = None

    model_config = ConfigDict(from_attributes=True)


class BookshelfBase(BaseModel):
    id: int

    model_config = ConfigDict(from_attributes=True)


class BookshelfCreation(BaseModel):
    """
    书架模型
     - name: 书架名称
     - intro: 书架简介
    """
    name: str
    intro: Optional[str] = None


class BookshelfUpdate(BaseModel):
    """
    书架更新模型
     - name: 书架名称
     - intro: 书架简介
    """
    name: Optional[str] = None
    intro: Optional[str] = None


class BookshelfResponse(BaseModel):
    """
    书架响应模型
     - id: 书架ID
     - name: 书架名称
     - intro: 书架简介
     - total_books: 图书册数
     - total_sets: 图书套数
     - books: 书架上的图书列表
    """
    id: int
    name: str
    intro: Optional[str] = None
    total_books: Optional[int] = None
    total_sets: Optional[int] = None
    books: Optional[List[BookSimple]]