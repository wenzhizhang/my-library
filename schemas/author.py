from pydantic import BaseModel, ConfigDict
from typing import Optional


class BookSimple(BaseModel):
    id: int
    title: str
    title_cn: Optional[str] = None
    thumb_image: Optional[str] = None

    model_config = ConfigDict(from_attributes=True)


class AuthorBase(BaseModel):
    id: int

    model_config = ConfigDict(from_attributes=True)


class AuthorCreation(BaseModel):
    """
    作者模型
     - name: 作者姓名
     - name_cn: 作者中文名
     - nation: 作者国籍
     - dynasty: 作者所属朝代
     - intro: 作者简介
     - photo: 作者照片URL
    """
    name: str
    name_cn: str
    nation: str
    dynasty: Optional[str] = None
    intro: Optional[str] = None
    photo: Optional[str] = None


class AuthorUpdate(BaseModel):
    """
    作者更新模型
     - name: 作者姓名
     - name_cn: 作者中文名
     - nation: 作者国籍
     - dynasty: 作者所属朝代
     - intro: 作者简介
     - photo: 作者照片URL
    """
    name: Optional[str] = None
    name_cn: Optional[str] = None
    nation: Optional[str] = None
    dynasty: Optional[str] = None
    intro: Optional[str] = None
    photo: Optional[str] = None


class AuthorResponse(BaseModel):
    """
    作者响应模型
     - id: 作者ID
     - name: 作者姓名
     - name_cn: 作者中文名
     - nation: 作者国籍
     - dynasty: 作者所属朝代
     - intro: 作者简介
     - photo: 作者照片URL
    """
    id: int
    name: str
    name_cn: Optional[str] = None
    nation: str
    dynasty: Optional[str] = None
    intro: Optional[str] = None
    photo: Optional[str] = None
    books: Optional[list[BookSimple]]