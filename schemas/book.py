from datetime import datetime

from pydantic import BaseModel, ConfigDict
from typing import List, Optional


class AuthorSimple(BaseModel):
    id: int
    name: str

    model_config = ConfigDict(from_attributes=True)


class PublisherSimple(BaseModel):
    id: int
    name: str

    model_config = ConfigDict(from_attributes=True)


class BrandSimple(BaseModel):
    id: int
    name: str


    model_config = ConfigDict(from_attributes=True)


class SeriesSimple(BaseModel):
    id: int
    name: str

    model_config = ConfigDict(from_attributes=True)


class CategorySimple(BaseModel):
    id: int
    path: str

    model_config = ConfigDict(from_attributes=True)


class BookshelfSimple(BaseModel):
    id: int
    name: str

    model_config = ConfigDict(from_attributes=True)


class BookBase(BaseModel):
    id: int

    model_config = ConfigDict(from_attributes=True)


class FilterParams(BaseModel):
    """
    图书过滤参数模型
     - isbn: 国际标准书号
     - title: 书籍名称（模糊匹配）
     - author: 作者名称（模糊匹配）
     - publisher: 出版社名称（模糊匹配）
     - min_price: 最小价格
     - max_price: 最大价格
    """
    model_config = {"extra": "forbid"}

    isbn: Optional[str] = None
    title: Optional[str] = None
    author: Optional[str] = None
    publisher: Optional[str] = None
    min_price: Optional[float] = None
    max_price: Optional[float] = None


class BookCreation(BaseModel):
    """
    图书模型
     - isbn: 国际标准书号
     - title_cn: 书籍中文名称
     - title: 书籍原名
     - author_ids: 作者ID列表
     - translator: 翻译；整理；校注等
     - publisher_id: 出版社ID
     - publish_date: 出版日期，格式为YYYY-MM-DD
     - brand_id: 图书品牌ID
     - book_series_id: 图书系列ID
     - binding_type: 装帧类型，如平装、精装等
     - paper_type: 纸张类型，如胶版纸、铜版纸等
     - pages: 页数
     - book_count: 套装书册数
     - language: 正文语言，如中文、英文等
     - compose_type: 装帧形式 [精装 | 平装 | 线装 | 盒装]
     - price: 定价，单位为人民币元
     - purchase_price: 购入价格，单位为人民币元
     - purchase_date: 购入日期，格式为YYYY-MM-DD
     - thumb_image: 书籍封面缩略图URL
     - category_id: 书籍分类ID
     - bookshelf_id: 所在书架ID
     - read_state: 阅读状态
     - catalog: 目录
     - introduction: 图书内容简介
     - summary: 图书内容概述
     - registered: 登记状态
     - edition: 图书版号
     - printing_info: 图书印次
     - printed_number: 图书印数
     - douban_score: 豆瓣评分
     - purchase_store: 购买书店
     - tags: 标签列表
     - in_wish: 是否是心愿单
     - created_at: 创建时间
     - updated_at: 更新时间
    """
    isbn: str
    title_cn: str
    title: str
    author_ids: Optional[List[int]] = None
    translator: Optional[str] = None
    publisher_id: Optional[int] = None
    publish_date: datetime
    brand_id: int
    book_series_id: int
    binding_type: str
    paper_type: str
    pages: int
    book_count: int
    language: str
    compose_type: str
    price: float
    purchase_price: float
    purchase_date: datetime
    thumb_image: str
    category_id: int
    bookshelf_id: int
    read_state: str
    catalog: str
    introduction: str
    summary: str
    registered: bool
    edition: str
    printing_info: str
    printed_number: int
    douban_score: float
    purchase_store: str
    tags: Optional[List[str]] = None
    in_wish: bool
    created_at: datetime
    updated_at: datetime


class BookUpdate(BaseModel):
    """
    图书更新模型
     - isbn: 国际标准书号
     - title_cn: 书籍中文名称
     - title: 书籍原名
     - author_ids: 作者ID列表
     - translator: 翻译；整理；校注等
     - publisher_id: 出版社ID
     - publish_date: 出版日期，格式为YYYY-MM-DD
     - brand_id: 图书品牌ID
     - book_series_id: 图书系列ID
     - binding_type: 装帧类型，如平装、精装等
     - paper_type: 纸张类型，如胶版纸、铜版纸等
     - pages: 页数
     - book_count: 套装书册数
     - language: 正文语言，如中文、英文等
     - compose_type: 装帧形式 [精装 | 平装 | 线装 | 盒装]
     - price: 定价，单位为人民币元
     - purchase_price: 购入价格，单位为人民币元
     - purchase_date: 购入日期，格式为YYYY-MM-DD
     - thumb_image: 书籍封面缩略图URL
     - link: 书籍详情链接URL
     - category_id: 书籍分类ID
     - bookshelf_id: 所在书架ID
     - read_state: 阅读状态
     - catalog: 目录
     - introduction: 图书内容简介
     - summary: 图书内容概述
     - registered: 登记状态
     - edition: 图书版号
     - printing_info: 图书印次
     - printed_number: 图书印数
     - douban_score: 豆瓣评分
     - purchase_store: 购买书店
     - tags: 标签列表
     - in_wish: 是否是心愿单
     - updated_at: 更新时间
    """
    isbn: Optional[str] = None
    title_cn: Optional[str] = None
    title: Optional[str] = None
    author_ids: Optional[List[int]] = None
    translator: Optional[str] = None
    publisher_id: Optional[int] = None
    publish_date: Optional[datetime] = None
    brand_id: Optional[int] = None
    book_series_id: Optional[int] = None
    binding_type: Optional[str] = None
    paper_type: Optional[str] = None
    pages: Optional[int] = None
    book_count: Optional[int] = None
    language: Optional[str] = None
    compose_type: Optional[str] = None
    price: Optional[float] = None
    purchase_price: Optional[float] = None
    purchase_date: Optional[datetime] = None
    thumb_image: Optional[str] = None
    link: Optional[str] = None
    category_id: Optional[int] = None
    bookshelf_id: Optional[int] = None
    read_state: Optional[str] = None
    catalog: Optional[str] = None
    introduction: Optional[str] = None
    summary: Optional[str] = None
    registered: Optional[bool] = None
    edition: Optional[str] = None
    printing_info: Optional[str] = None
    printed_number: Optional[int] = None
    douban_score: Optional[float] = None
    purchase_store: Optional[str] = None
    tags: Optional[List[str]] = None
    in_wish: Optional[bool] = None
    updated_at: Optional[datetime] = None


class BookResponse(BaseModel):
    """
    图书模型
     - id: 图书ID
     - isbn: 国际标准书号
     - title_cn: 书籍中文名称
     - title: 书籍原名
     - authors: 作者列表
     - translator: 翻译；整理；校注等
     - publisher: 出版社
     - publish_date: 出版日期，格式为YYYY-MM-DD
     - brand: 图书品牌
     - book_series: 图书系列
     - binding_type: 装帧类型，如平装、精装等
     - paper_type: 纸张类型，如胶版纸、铜版纸等
     - pages: 页数
     - book_count: 套装书册数
     - language: 正文语言，如中文、英文等
     - compose_type: 装帧形式 [精装 | 平装 | 线装 | 盒装]
     - price: 定价，单位为人民币元
     - purchase_price: 购入价格，单位为人民币元
     - purchase_date: 购入日期，格式为YYYY-MM-DD
     - thumb_image: 书籍封面缩略图URL
     - link: 书籍详情链接URL
     - category: 书籍分类
     - bookshelf: 所在书架
     - read_state: 阅读状态
     - catalog: 目录
     - introduction: 图书内容简介
     - summary: 图书内容概述
     - registered: 登记状态
     - edition: 图书版号
     - printing_info: 图书印次
     - printed_number: 图书印数
     - douban_score: 豆瓣评分
     - purchase_store: 购买书店
     - tags: 标签列表
    """
    id: int
    isbn: str
    title_cn: str
    title: str
    authors: Optional[List[AuthorSimple]] = None
    translator: Optional[str] = None
    publisher: Optional[PublisherSimple] = None
    publish_date: Optional[datetime] = None
    brand: Optional[BrandSimple] = None
    book_series: Optional[SeriesSimple] = None
    binding_type: Optional[str] = None
    paper_type: Optional[str] = None
    pages: Optional[int] = None
    book_count: Optional[int] = None
    language: Optional[str] = None
    compose_type: Optional[str] = None
    price: Optional[float] = None
    purchase_price: Optional[float] = None
    purchase_date: Optional[datetime] = None
    thumb_image: Optional[str] = None
    link: Optional[str] = None
    category: Optional[CategorySimple] = None
    bookshelf: Optional[BookshelfSimple] = None
    read_state: Optional[str] = None
    catalog: Optional[str] = None
    introduction: Optional[str] = None
    summary: Optional[str] = None
    registered: Optional[bool] = None
    edition: Optional[str] = None
    printing_info: Optional[str] = None
    printed_number: Optional[int] = None
    douban_score: Optional[float] = None
    purchase_store: Optional[str] = None
    tags: Optional[List[str]]
