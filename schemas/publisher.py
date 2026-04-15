from pydantic import BaseModel, ConfigDict
from typing import Optional, List


class BookSimple(BaseModel):
    id: int
    title: str
    title_cn: Optional[str] = None
    thumb_image: Optional[str] = None

    model_config = ConfigDict(from_attributes=True)


class PublisherBase(BaseModel):
    id: int

    model_config = ConfigDict(from_attributes=True)

class PublisherCreation(BaseModel):
    """
    出版社模型
     - name: 出版社名称
     - intro: 出版社简介
    """
    name: str
    intro: Optional[str] = None
    logo: Optional[str] = None


class PublisherUpdate(BaseModel):
    """
    出版社更新模型
     - name: 出版社名称
     - intro: 出版社简介
    """
    name: Optional[str] = None
    intro: Optional[str] = None
    logo: Optional[str] = None


class PublisherResponse(BaseModel):
    """
    出版社响应模型
     - id: 出版社ID
     - name: 出版社名称
     - intro: 出版社简介
     - logo: 出版社Logo URL
     - books: 出版社出版的图书列表
    """
    id: int
    name: str
    intro: Optional[str] = None
    logo: Optional[str] = None
    books: Optional[List[BookSimple]]


class BrandCreation(BaseModel):
    """
    品牌模型
     - name: 品牌名称
     - intro: 品牌简介
    """
    name: str
    intro: Optional[str] = None


class BrandUpdate(BaseModel):
    """
    品牌更新模型
     - name: 品牌名称
     - intro: 品牌简介
    """
    name: Optional[str] = None
    intro: Optional[str] = None


class BrandResponse(BaseModel):
    """
    品牌响应模型
     - id: 品牌ID
     - name: 品牌名称
     - intro: 品牌简介
     - books: 品牌旗下的图书列表
    """
    id: int
    name: str
    intro: Optional[str] = None
    books: Optional[List[BookSimple]]