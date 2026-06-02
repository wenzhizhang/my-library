from pydantic import BaseModel, ConfigDict
from typing import Optional
from datetime import datetime


class UserRegister(BaseModel):
    username: str
    password: str


class UserLogin(BaseModel):
    username: str
    password: str


class TokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    user_id: int
    username: str
    uuid: str


class UserInfo(BaseModel):
    id: int
    username: str
    uuid: str
    created_at: Optional[datetime] = None

    model_config = ConfigDict(from_attributes=True)
