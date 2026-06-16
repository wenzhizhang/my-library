from pydantic import BaseModel, ConfigDict, field_validator
from typing import Optional, List

NATIONS = [
    "中国", "俄罗斯", "前苏联", "希腊", "美国", "英国", "法国", "德国",
    "古巴", "西班牙", "古罗马", "加拿大", "爱尔兰", "澳大利亚", "瑞士",
    "阿根廷", "哥伦比亚", "奥地利", "挪威", "瑞典", "意大利", "比利时",
    "墨西哥", "荷兰", "巴西", "波兰", "伊朗", "波斯", "智利", "南非",
    "马来西亚", "捷克", "毛里求斯", "丹麦", "葡萄牙", "黎巴嫩", "冰岛",
    "以色列", "日本",
]

DYNASTIES = [
    "上古", "夏", "商", "西周", "东周", "春秋", "战国",
    "秦", "西汉", "东汉", "魏", "蜀", "吴", "西晋", "东晋",
    "南北朝", "隋", "唐", "五代", "北宋", "南宋",
    "元", "明", "清", "民国", "现代", "当代",
]


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


class AuthorBase(BaseModel):
    id: int

    model_config = ConfigDict(from_attributes=True)


class AuthorCreation(BaseModel):
    name: str
    name_cn: str
    nation: str = "无"
    dynasty: Optional[str] = "当代"
    intro: Optional[str] = None
    photo: Optional[str] = None

    @field_validator("nation")
    @classmethod
    def validate_nation(cls, v):
        if v not in NATIONS:
            raise ValueError(f"Invalid nation '{v}'. Must be one of: {', '.join(NATIONS)}")
        return v

    @field_validator("dynasty")
    @classmethod
    def validate_dynasty(cls, v):
        if v is None:
            return None
        if v not in DYNASTIES:
            raise ValueError(f"Invalid dynasty '{v}'. Must be one of: {', '.join(DYNASTIES)}")
        return v


class AuthorUpdate(BaseModel):
    name: Optional[str] = None
    name_cn: Optional[str] = None
    nation: Optional[str] = None
    dynasty: Optional[str] = None
    intro: Optional[str] = None
    photo: Optional[str] = None

    @field_validator("nation")
    @classmethod
    def validate_nation(cls, v):
        if v is None:
            return None
        if v not in NATIONS:
            raise ValueError(f"Invalid nation '{v}'. Must be one of: {', '.join(NATIONS)}")
        return v

    @field_validator("dynasty")
    @classmethod
    def validate_dynasty(cls, v):
        if v is None:
            return None
        if v not in DYNASTIES:
            raise ValueError(f"Invalid dynasty '{v}'. Must be one of: {', '.join(DYNASTIES)}")
        return v


class AuthorResponse(BaseModel):
    id: int
    name: str
    name_cn: Optional[str] = None
    nation: Optional[str] = None
    dynasty: Optional[str] = None
    intro: Optional[str] = None
    photo: Optional[str] = None
    books: Optional[list[BookSimple]] = None

    model_config = ConfigDict(from_attributes=True)
