from typing import Optional

from pydantic import BaseModel


class BackgroundItem(BaseModel):
    id: str
    name: str
    url: str


class BackgroundListResponse(BaseModel):
    default_id: str
    backgrounds: list[BackgroundItem]


class BackgroundSelectionRequest(BaseModel):
    background_id: str


class BackgroundSelectionResponse(BaseModel):
    background_id: Optional[str] = None
