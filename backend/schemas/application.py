from datetime import datetime
from typing import Optional
from pydantic import BaseModel, ConfigDict


class ApplicationCreation(BaseModel):
    """
    应用注册模型
    - name: 应用名称
    - description: 简要描述
    - url: 应用访问链接
    - icon_url: 图标 URL
    - sort_order: 排序权重（越小越靠前）
    """
    name: str
    description: Optional[str] = None
    url: Optional[str] = None
    icon_url: Optional[str] = None
    sort_order: Optional[int] = 0


class ApplicationUpdate(BaseModel):
    """
    应用更新模型
    """
    name: Optional[str] = None
    description: Optional[str] = None
    url: Optional[str] = None
    icon_url: Optional[str] = None
    sort_order: Optional[int] = None


class ApplicationResponse(BaseModel):
    """
    应用响应模型
    """
    id: int
    name: str
    description: Optional[str] = None
    url: Optional[str] = None
    icon_url: Optional[str] = None
    sort_order: Optional[int] = 0
    created_at: Optional[datetime] = None
    updated_at: Optional[datetime] = None

    model_config = ConfigDict(from_attributes=True)
