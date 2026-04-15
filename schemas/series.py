from pydantic import BaseModel, Field, ConfigDict
from typing import Optional, List


class BookSimple(BaseModel):
    id: int
    title: str
    title_cn: Optional[str] = None
    thumb_image: Optional[str] = None

    model_config = ConfigDict(from_attributes=True)


class SeriesBase(BaseModel):
    id: int

    model_config = ConfigDict(from_attributes=True)


class BookSeriesCreation(BaseModel):
    """
    图书系列模型
     - name: 系列名称
     - intro: 系列简介
    """
    name: str
    intro: Optional[str] = None


class BookSeriesUpdate(BaseModel):
    """
    图书系列更新模型
     - name: 系列名称
     - intro: 系列简介
    """
    name: Optional[str] = None
    intro: Optional[str] = None


class BookSeriesResponse(BaseModel):
    """
    图书系列响应模型
     - id: 系列ID
     - name: 系列名称
     - intro: 系列简介
    """
    id: int
    name: str
    intro: Optional[str] = None
    books: List[BookSimple]
