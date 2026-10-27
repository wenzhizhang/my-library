"""Request/response models for the assistant's book actions."""

from typing import Optional

from pydantic import BaseModel

from schemas.rag import PageContext


class AssistantActionRequest(BaseModel):
    message: str = ""
    draft: Optional[dict] = None
    focus_book_id: Optional[int] = None
    action: Optional[str] = None
    page_context: Optional[PageContext] = None


class AssistantCommitRequest(BaseModel):
    draft: dict
    duplicate_choice: Optional[str] = None
