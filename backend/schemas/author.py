"""Author Pydantic schemas.  Nation/dynasty values loaded from config files."""

import json
from pathlib import Path
from typing import Optional, List

from pydantic import BaseModel, ConfigDict, field_validator


def _load_config(filename: str) -> list[str]:
    """Load a JSON list from the config directory."""
    config_dir = Path(__file__).parent.parent / "config"
    path = config_dir / filename
    key = filename.replace(".json", "")
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)
    result = data.get(key)
    if not result:
        raise ValueError(f"Config file {filename} missing key '{key}'")
    return result


NATIONS: list[str] = _load_config("nations.json")
DYNASTIES: list[str] = _load_config("dynasties.json")


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
    dynasty: Optional[str] = None
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
